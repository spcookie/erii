(function () {
    'use strict';

    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token') || '';
    const requestedTheme = document.documentElement.dataset.theme || 'auto';
    const resolvedTheme = requestedTheme === 'dark' || requestedTheme === 'light'
        ? requestedTheme
        : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');

    const terminalThemes = {
        dark: {
            background: '#050505',
            foreground: '#ededed',
            cursor: '#ffffff',
            selectionBackground: '#333333',
            black: '#171717',
            red: '#e5484d',
            green: '#46a758',
            yellow: '#f5a623',
            blue: '#3291ff',
            magenta: '#8b5cf6',
            cyan: '#50e3c2',
            white: '#ededed',
            brightBlack: '#a1a1a1',
            brightRed: '#ff6369',
            brightGreen: '#58c26d',
            brightYellow: '#ffc45c',
            brightBlue: '#79b8ff',
            brightMagenta: '#a78bfa',
            brightCyan: '#79ffe1',
            brightWhite: '#ffffff'
        },
        light: {
            background: '#ffffff',
            foreground: '#171717',
            cursor: '#171717',
            selectionBackground: '#d6ebff',
            black: '#171717',
            red: '#d70022',
            green: '#1a7f37',
            yellow: '#b86e00',
            blue: '#0070f3',
            magenta: '#6d28d9',
            cyan: '#0f766e',
            white: '#666666',
            brightBlack: '#666666',
            brightRed: '#e5484d',
            brightGreen: '#2f9e44',
            brightYellow: '#d98b00',
            brightBlue: '#0058c7',
            brightMagenta: '#7c3aed',
            brightCyan: '#0d9488',
            brightWhite: '#171717'
        }
    };

    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = wsProto + '//' + window.location.host + '/ws?token=' + encodeURIComponent(token);

    let ws = null;
    let term = null;
    let fitAddon = null;
    let rendererAddon = null;
    let terminalOpened = false;
    let fitFrame = 0;
    let welcomePending = false;
    let reconnectAttempts = 0;
    let reconnectTimer = 0;
    const MAX_RECONNECT_DELAY = 10000;
    const MIN_COLS = 40;
    let activeCmd = null;
    let activeCommandKey = null;
    let runtimeEvents = null;
    let runtimeState = 'checking';
    let cliConnected = false;

    // DOM elements
    const terminalContainer = document.getElementById('terminal-container');
    const terminalMount = document.getElementById('terminal-mount');
    const statusDot = document.getElementById('status-dot');
    const statusText = document.getElementById('status-text');
    const coreStatus = document.getElementById('core-status');
    const coreStatusDot = document.getElementById('core-status-dot');
    const coreStatusText = document.getElementById('core-status-text');
    const currentCmdEl = document.getElementById('current-cmd');
    const sidebarItems = document.querySelectorAll('.sidebar-item');
    const coreDependentItems = document.querySelectorAll('[data-requires-core="true"]');
    const setupCommand = document.getElementById('setup-command');
    const resizeHandle = document.getElementById('resize-handle');
    const serverControl = document.querySelector('.server-control');
    const serverMenuButton = document.getElementById('server-menu-button');
    const serverMenu = document.getElementById('server-menu');
    const serverActionButtons = Array.from(document.querySelectorAll('[data-server-action]'));
    const debugLogButton = document.getElementById('debug-log-button');

    // Default header content (shown when no command is active)
    const defaultHeaderHTML = currentCmdEl.innerHTML;

    function setStatus(state) {
        cliConnected = state === 'connected' || state === 'running';
        statusDot.className = 'status-dot';
        switch (state) {
            case 'connecting':
                statusText.textContent = 'Connecting';
                break;
            case 'reconnecting':
                statusText.textContent = 'Reconnecting';
                break;
            case 'connected':
                statusDot.classList.add('connected');
                statusText.textContent = 'Connected';
                break;
            case 'running':
                statusDot.classList.add('connected');
                statusText.textContent = 'Connected';
                break;
            case 'disconnected':
                statusText.textContent = 'Disconnected';
                break;
            default:
                statusText.textContent = 'Offline';
        }
        serverMenuButton.disabled = !cliConnected;
        debugLogButton.disabled = !cliConnected;
        if (!cliConnected) closeServerMenu();
        updateServerActions();
    }

    function updateServerActions() {
        serverActionButtons.forEach(function (button) {
            const action = button.dataset.serverAction;
            if (action === 'start') {
                button.disabled = !cliConnected || runtimeState !== 'offline';
            } else {
                button.disabled = !cliConnected || runtimeState !== 'online';
            }
        });
        serverMenuButton.title = cliConnected
            ? 'Manage Erii server (' + runtimeState + ')'
            : 'CLI connection is required to manage the server';
    }

    function closeServerMenu(restoreFocus) {
        if (serverMenu.hidden) return;
        serverMenu.hidden = true;
        serverMenuButton.setAttribute('aria-expanded', 'false');
        if (restoreFocus) serverMenuButton.focus();
    }

    function openServerMenu(focusFirst) {
        if (serverMenuButton.disabled) return;
        serverMenu.hidden = false;
        serverMenuButton.setAttribute('aria-expanded', 'true');
        if (focusFirst) {
            const firstEnabled = serverActionButtons.find(function (button) {
                return !button.disabled;
            });
            if (firstEnabled) firstEnabled.focus();
        }
    }

    function setCommandTag(item, text, tone) {
        let tag = item.querySelector('.command-tag');
        if (!text) {
            if (tag) tag.remove();
            return;
        }
        if (!tag) {
            tag = document.createElement('span');
            item.appendChild(tag);
        }
        tag.className = 'command-tag ' + tone;
        tag.textContent = text;
    }

    function applyRuntimeStatus(status) {
        const knownStates = ['checking', 'offline', 'starting', 'unavailable', 'online'];
        const state = knownStates.indexOf(status.state) === -1 ? 'unavailable' : status.state;
        runtimeState = state;
        const coreAvailable = state === 'online' && status.coreReachable;
        let statusLabel = 'Unavailable';
        let commandLabel = 'UNAVAILABLE';

        switch (state) {
            case 'checking':
                statusLabel = 'Checking';
                commandLabel = 'CHECKING';
                break;
            case 'offline':
                statusLabel = 'Offline';
                commandLabel = 'CORE OFFLINE';
                break;
            case 'starting':
                statusLabel = 'Starting';
                commandLabel = 'STARTING';
                break;
            case 'online':
                statusLabel = 'Online';
                commandLabel = '';
                break;
        }

        coreStatus.className = 'topbar-status ' + state;
        coreStatus.title = status.message || 'Erii core status';
        coreStatusText.textContent = statusLabel;
        coreStatusDot.setAttribute('aria-label', statusLabel);

        coreDependentItems.forEach(function (item) {
            item.disabled = !coreAvailable;
            if (coreAvailable) {
                item.removeAttribute('title');
                setCommandTag(item, '', '');
            } else {
                item.title = status.message || 'Erii core is not available';
                setCommandTag(item, commandLabel, 'unavailable');
            }
        });

        if (status.setupRequired) {
            setupCommand.title = 'Run setup to create the Erii core connection configuration';
            setCommandTag(setupCommand, 'START HERE', 'guide');
        } else {
            setupCommand.removeAttribute('title');
            setCommandTag(setupCommand, '', '');
        }
        updateServerActions();
    }

    function connectRuntimeStatus() {
        if (runtimeEvents) runtimeEvents.close();
        runtimeEvents = new EventSource('/api/runtime/events?token=' + encodeURIComponent(token));
        runtimeEvents.addEventListener('status', function (event) {
            try {
                applyRuntimeStatus(JSON.parse(event.data));
            } catch (e) {
                console.warn('Invalid Erii runtime status event', e);
            }
        });
        runtimeEvents.onerror = function () {
            applyRuntimeStatus({
                state: 'unavailable',
                pidFile: false,
                coreReachable: false,
                setupRequired: false,
                message: 'Erii runtime status stream is reconnecting'
            });
        };
    }

    function itemCommandKey(item) {
        const args = item.dataset.args || '';
        return item.dataset.cmd + (args ? ' ' + args : '');
    }

    function commandKey(cmd, args) {
        return cmd + (args && args.length ? ' ' + args.join(' ') : '');
    }

    function setActiveCmd(cmd, args, title) {
        activeCmd = cmd;
        const logActive = cmd === 'log';
        debugLogButton.setAttribute('aria-pressed', logActive ? 'true' : 'false');
        debugLogButton.setAttribute('aria-label', logActive ? 'Close Erii logs' : 'Open Erii logs');
        debugLogButton.title = logActive ? 'Close Erii logs' : 'Open Erii logs';
        activeCommandKey = cmd ? commandKey(cmd, args || []) : null;
        let matched = false;
        sidebarItems.forEach(function (item) {
            if (itemCommandKey(item) === activeCommandKey) {
                matched = true;
                item.classList.add('active');
                currentCmdEl.innerHTML = item.innerHTML;
                currentCmdEl.querySelectorAll('.command-tag').forEach(function (tag) {
                    tag.remove();
                });
            } else {
                item.classList.remove('active');
            }
        });
        if (!cmd) {
            currentCmdEl.innerHTML = defaultHeaderHTML;
        } else if (!matched) {
            currentCmdEl.innerHTML = defaultHeaderHTML;
            const label = currentCmdEl.querySelector('span');
            if (label) label.textContent = title || cmd;
        }
    }

    function showWelcome() {
        let i;
        if (!term) return;
        term.clear();
        const cols = term.cols || 80;
        const rows = term.rows || 24;
        const banner = [
            '███████╗██████╗ ██╗██╗██████╗  ██████╗ ████████╗',
            '██╔════╝██╔══██╗██║██║██╔══██╗██╔═══██╗╚══██╔══╝',
            '█████╗  ██████╔╝██║██║██████╔╝██║   ██║   ██║',
            '██╔══╝  ██╔══██╗██║██║██╔══██╗██║   ██║   ██║',
            '███████╗██║  ██║██║██║██████╔╝╚██████╔╝   ██║',
            '╚══════╝╚═╝  ╚═╝╚═╝╚═╝╚═════╝  ╚═════╝    ╚═╝',
        ];
        const subtitle1 = 'Erii Console';
        const subtitle2 = 'Ready for local bot operations';

        // Compute max visible widths, pad shorter lines for perfect alignment
        let maxBanner = 0;
        for (i = 0; i < banner.length; i++) {
            if (banner[i].length > maxBanner) maxBanner = banner[i].length;
        }
        for (i = 0; i < banner.length; i++) {
            while (banner[i].length < maxBanner) banner[i] += ' ';
        }

        // Build all output lines
        const lines = [];
        lines.push(''); // blank line before banner
        for (i = 0; i < banner.length; i++) {
            const pad = Math.max(0, Math.floor((cols - banner[i].length) / 2));
            lines.push(' '.repeat(pad) + '\x1b[1m' + banner[i] + '\x1b[0m');
        }
        lines.push(''); // blank separator
        lines.push(' '.repeat(Math.max(0, Math.floor((cols - subtitle1.length) / 2))) + '\x1b[90m' + subtitle1 + '\x1b[0m');
        lines.push(' '.repeat(Math.max(0, Math.floor((cols - subtitle2.length) / 2))) + '\x1b[90m' + subtitle2 + '\x1b[0m');

        // Vertical centering: prepend blank lines
        const vpad = Math.max(0, Math.floor((rows - lines.length) / 2));
        for (let j = 0; j < vpad; j++) {
            term.writeln('');
        }

        // Write all content lines
        for (let k = 0; k < lines.length; k++) {
            term.writeln(lines[k]);
        }
    }

    function setRenderer(name) {
        terminalMount.dataset.renderer = name;
    }

    function activateCanvasRenderer(reason) {
        if (!window.CanvasAddon) {
            setRenderer('dom');
            console.warn(reason + '; using the DOM renderer');
            return;
        }

        let canvasAddon = null;
        try {
            canvasAddon = new CanvasAddon.CanvasAddon();
            term.loadAddon(canvasAddon);
            rendererAddon = canvasAddon;
            setRenderer('canvas');
            console.info('xterm renderer: canvas (' + reason + ')');
        } catch (e) {
            if (canvasAddon) canvasAddon.dispose();
            rendererAddon = null;
            setRenderer('dom');
            console.warn('Canvas renderer unavailable; using the DOM renderer', e);
        }
    }

    function activateRenderer() {
        if (!window.WebglAddon) {
            activateCanvasRenderer('WebGL addon unavailable');
            return;
        }

        let webglAddon = null;
        try {
            webglAddon = new WebglAddon.WebglAddon();
            term.loadAddon(webglAddon);
            rendererAddon = webglAddon;
            setRenderer('webgl');
            console.info('xterm renderer: webgl');
            webglAddon.onContextLoss(function () {
                // Disposing while the context-loss event is being dispatched can
                // leave xterm's renderer service between states.
                setTimeout(function () {
                    if (rendererAddon !== webglAddon) return;
                    rendererAddon = null;
                    webglAddon.dispose();
                    activateCanvasRenderer('WebGL context lost');
                    scheduleFit(false);
                }, 0);
            });
        } catch (e) {
            if (webglAddon) webglAddon.dispose();
            rendererAddon = null;
            activateCanvasRenderer('WebGL initialization failed');
        }
    }

    function openTerminalLink(event, uri) {
        let target;
        try {
            target = new URL(uri);
        } catch (e) {
            return;
        }
        if (target.protocol !== 'http:' && target.protocol !== 'https:') return;
        event.preventDefault();
        const opened = window.open(target.href, '_blank', 'noopener,noreferrer');
        if (opened) opened.opener = null;
    }

    function scheduleFit(showBanner) {
        welcomePending = welcomePending || showBanner;
        if (!terminalOpened || fitFrame) return;
        fitFrame = requestAnimationFrame(function () {
            fitFrame = 0;
            fitAddon.fit();
            if (welcomePending) {
                welcomePending = false;
                showWelcome();
            }
        });
    }

    function initTerminal() {
        term = new Terminal({
            cursorBlink: true,
            cursorStyle: 'bar',
            fontSize: 14,
            fontFamily: '"JetBrains Mono", "Erii Nerd Icons", monospace',
            letterSpacing: 0,
            lineHeight: 1.2,
            theme: terminalThemes[resolvedTheme],
            allowProposedApi: true,
        });

        fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);

        if (window.Unicode11Addon) {
            const unicodeAddon = new Unicode11Addon.Unicode11Addon();
            term.loadAddon(unicodeAddon);
            term.unicode.activeVersion = '11';
            terminalMount.dataset.unicode = term.unicode.activeVersion;
        }

        if (window.WebLinksAddon) {
            term.loadAddon(new WebLinksAddon.WebLinksAddon(openTerminalLink));
        }

        function doOpen() {
            // open() MUST be called when the container has final dimensions;
            // xterm.js does DOM-based measurements during open().
            term.open(terminalMount);
            terminalOpened = true;
            activateRenderer();
            scheduleFit(true);
        }

        function doOpenLater() {
            requestAnimationFrame(function () {
                requestAnimationFrame(doOpen);
            });
        }

        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(doOpenLater);
        } else {
            doOpenLater();
        }

        term.onData(function (data) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ type: 'input', data: data }));
            }
        });

        term.onResize(function (size) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'resize',
                    cols: Math.max(size.cols, MIN_COLS),
                    rows: size.rows
                }));
            }
        });

        if (window.ResizeObserver) {
            var ro = new ResizeObserver(function () {
                scheduleFit(false);
            });
            ro.observe(terminalMount);
        }
        window.addEventListener('resize', function () {
            scheduleFit(false);
        });
    }

    function scheduleReconnect() {
        if (reconnectTimer) clearTimeout(reconnectTimer);
        reconnectAttempts++;
        const exponent = Math.min(reconnectAttempts - 1, 4);
        const delay = Math.min(Math.pow(2, exponent) * 1000, MAX_RECONNECT_DELAY);
        setStatus('reconnecting');
        reconnectTimer = setTimeout(function () {
            reconnectTimer = 0;
            connect();
        }, delay);
    }

    function connect() {
        if (ws) {
            ws.onclose = null;
            ws.close();
            ws = null;
        }

        setStatus(reconnectAttempts > 0 ? 'reconnecting' : 'connecting');
        const socket = new WebSocket(wsUrl);
        ws = socket;
        socket.binaryType = 'arraybuffer';

        socket.onopen = function () {
            if (ws !== socket) return;
            reconnectAttempts = 0;
            socket.send(JSON.stringify({ type: 'auth', token: token }));
            setStatus('connected');
        };

        socket.onmessage = function (event) {
            if (ws !== socket) return;
            if (event.data instanceof ArrayBuffer) {
                if (term) term.write(new Uint8Array(event.data));
                return;
            }
            const msg = JSON.parse(event.data);
            switch (msg.type) {
                case 'exit':
                    const exitedCmd = activeCmd;
                    setActiveCmd(null, []);
                    setStatus('connected');
                    if (tuiCommands.indexOf(exitedCmd) !== -1) {
                        showWelcome();
                    }
                    break;
                case 'error':
                    if (term) term.writeln('\r\n\x1b[31m' + msg.data + '\x1b[0m');
                    break;
            }
        };

        socket.onclose = function () {
            if (ws !== socket) return;
            ws = null;
            setActiveCmd(null, []);
            scheduleReconnect();
        };

        socket.onerror = function () {
            socket.close();
        };
    }

    // Commands that use BubbleTea TUI (alternate screen)
    const tuiCommands = ['config', 'setup', 'manage', 'stats', 'chat', 'usage', 'log'];

    function parseArgs(value) {
        if (!value) return [];
        return value.split(/\s+/).filter(Boolean);
    }

    function execCmd(cmd, args, title) {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        args = args || [];
        closeServerMenu();
        setActiveCmd(cmd, args, title);
        setStatus('running');
        if (tuiCommands.indexOf(cmd) !== -1) {
            // TUI: refresh banner on main screen (alt-screen covers it while TUI runs)
            showWelcome();
        } else {
            // Reset viewport and scrollback; the backend clears again after
            // the previous alternate-screen process has fully exited.
            term.reset();
        }
        ws.send(JSON.stringify({
            type: 'exec',
            cmd: cmd,
            args: args,
            theme: resolvedTheme,
            cols: Math.max(term.cols, MIN_COLS),
            rows: term.rows
        }));
        term.focus();
    }

    // Sidebar click handlers
    sidebarItems.forEach(function (item) {
        item.addEventListener('click', function () {
            if (this.disabled) return;
            const cmd = this.dataset.cmd;
            if (cmd) execCmd(cmd, parseArgs(this.dataset.args));
        });
    });

    debugLogButton.addEventListener('click', function () {
        if (this.disabled) return;
        if (activeCmd === 'log') {
            ws.send(JSON.stringify({type: 'input', data: '\x1b'}));
            term.focus();
            return;
        }
        execCmd('log', [], 'Logs');
    });

    serverMenuButton.addEventListener('click', function () {
        if (serverMenu.hidden) {
            openServerMenu(false);
        } else {
            closeServerMenu(false);
        }
    });

    serverMenuButton.addEventListener('keydown', function (event) {
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            openServerMenu(true);
        }
    });

    serverActionButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            if (this.disabled) return;
            const action = this.dataset.serverAction;
            const title = 'Server ' + action.charAt(0).toUpperCase() + action.slice(1);
            execCmd('server', [action], title);
        });
    });

    serverMenu.addEventListener('keydown', function (event) {
        const enabled = serverActionButtons.filter(function (button) { return !button.disabled; });
        if (!enabled.length) return;
        const current = enabled.indexOf(document.activeElement);
        let next = current;
        if (event.key === 'ArrowDown') next = (current + 1) % enabled.length;
        else if (event.key === 'ArrowUp') next = (current <= 0 ? enabled.length : current) - 1;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = enabled.length - 1;
        else return;
        event.preventDefault();
        enabled[next].focus();
    });

    document.addEventListener('click', function (event) {
        if (!serverControl.contains(event.target)) closeServerMenu(false);
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && !serverMenu.hidden) {
            event.preventDefault();
            closeServerMenu(true);
        }
    });

    // Sidebar resize
    const sidebar = document.getElementById('sidebar');
    let isResizing = false;
    let startX, startWidth;

    resizeHandle.addEventListener('mousedown', function (e) {
        isResizing = true;
        startX = e.clientX;
        startWidth = sidebar.offsetWidth;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
    });

    document.addEventListener('mousemove', function (e) {
        if (!isResizing) return;
        const newWidth = startWidth + (e.clientX - startX);
        if (newWidth >= 160 && newWidth <= 400) {
            sidebar.style.width = newWidth + 'px';
        }
    });

    document.addEventListener('mouseup', function () {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            scheduleFit(false);
        }
    });

    // Init
    applyRuntimeStatus({
        state: 'checking',
        pidFile: false,
        coreReachable: false,
        setupRequired: false,
        message: 'Checking Erii core'
    });
    connectRuntimeStatus();
    initTerminal();
    connect();
})();
