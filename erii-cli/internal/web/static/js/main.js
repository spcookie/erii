(function () {
    'use strict';

    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token') || '';

    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = wsProto + '//' + window.location.host + '/ws?token=' + encodeURIComponent(token);

    let ws = null;
    let term = null;
    let fitAddon = null;
    let reconnectAttempts = 0;
    const maxReconnects = 3;
    let activeCmd = null;

    // DOM elements
    const terminalContainer = document.getElementById('terminal-container');
    const statusDot = document.getElementById('status-dot');
    const statusText = document.getElementById('status-text');
    const currentCmdEl = document.getElementById('current-cmd');
    const sidebarItems = document.querySelectorAll('.sidebar-item');
    const resizeHandle = document.getElementById('resize-handle');

    // Default header content (shown when no command is active)
    const defaultHeaderHTML = currentCmdEl.innerHTML;

    function setStatus(state) {
        statusDot.className = 'status-dot';
        switch (state) {
            case 'connecting':
                statusText.textContent = '连接中...';
                break;
            case 'connected':
                statusDot.classList.add('connected');
                statusText.textContent = '已连接';
                break;
            case 'running':
                statusDot.classList.add('connected');
                statusText.textContent = '运行中';
                break;
            case 'disconnected':
                statusText.textContent = '已断开';
                break;
            default:
                statusText.textContent = '未连接';
        }
    }

    function setActiveCmd(cmd) {
        activeCmd = cmd;
        sidebarItems.forEach(function (item) {
            if (item.dataset.cmd === cmd) {
                item.classList.add('active');
                // Sync header with sidebar item
                currentCmdEl.innerHTML = item.innerHTML;
            } else {
                item.classList.remove('active');
            }
        });
        if (!cmd) {
            currentCmdEl.innerHTML = defaultHeaderHTML;
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
        const subtitle1 = 'Welcome to Erii Console';
        const subtitle2 = 'Select a command from the sidebar to begin';

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
            lines.push(' '.repeat(pad) + '\x1b[36m' + banner[i] + '\x1b[0m');
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

    function initTerminal() {
        term = new Terminal({
            cursorBlink: true,
            cursorStyle: 'bar',
            fontSize: 13,
            fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
            theme: {
                background: '#11111b',
                foreground: '#cdd6f4',
                cursor: '#f5e0dc',
                selectionBackground: '#45475a',
                black: '#45475a',
                red: '#f38ba8',
                green: '#a6e3a1',
                yellow: '#f9e2af',
                blue: '#89b4fa',
                magenta: '#cba6f7',
                cyan: '#94e2d5',
                white: '#bac2de',
                brightBlack: '#585b70',
                brightRed: '#f38ba8',
                brightGreen: '#a6e3a1',
                brightYellow: '#f9e2af',
                brightBlue: '#89b4fa',
                brightMagenta: '#cba6f7',
                brightCyan: '#94e2d5',
                brightWhite: '#a6adc8'
            },
            allowProposedApi: true
        });

        fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(terminalContainer);
        requestAnimationFrame(function () {
            fitAddon.fit();
            showWelcome();
        });

        term.onData(function (data) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ type: 'input', data: data }));
            }
        });

        term.onResize(function (size) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'resize',
                    cols: size.cols,
                    rows: size.rows
                }));
            }
        });

        if (window.ResizeObserver) {
            const ro = new ResizeObserver(function () {
                if (fitAddon) fitAddon.fit();
            });
            ro.observe(terminalContainer);
        }
    }

    function connect() {
        if (ws) {
            ws.close();
            ws = null;
        }

        setStatus('connecting');
        ws = new WebSocket(wsUrl);
        ws.binaryType = 'arraybuffer';

        ws.onopen = function () {
            ws.send(JSON.stringify({ type: 'auth', token: token }));
        };

        ws.onmessage = function (event) {
            if (event.data instanceof ArrayBuffer) {
                if (term) term.write(new Uint8Array(event.data));
                return;
            }
            const msg = JSON.parse(event.data);
            switch (msg.type) {
                case 'exit':
                    setActiveCmd(null);
                    setStatus('connected');
                    break;
                case 'error':
                    if (term) term.writeln('\r\n\x1b[31m' + msg.data + '\x1b[0m');
                    break;
            }
        };

        ws.onclose = function () {
            setStatus('disconnected');
            if (reconnectAttempts < maxReconnects) {
                reconnectAttempts++;
                const delay = Math.pow(2, reconnectAttempts) * 1000;
                setTimeout(connect, delay);
            }
        };

        ws.onerror = function () {
            ws.close();
        };
    }

    // Commands that use BubbleTea TUI (alternate screen)
    const tuiCommands = ['config', 'setup', 'manage', 'stats'];

    function execCmd(cmd) {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        setActiveCmd(cmd);
        setStatus('running');
        if (tuiCommands.indexOf(cmd) !== -1) {
            // TUI: refresh banner on main screen (alt-screen covers it while TUI runs)
            showWelcome();
        } else {
            // Non-TUI: clear main screen for clean output, no banner overlap
            term.clear();
        }
        ws.send(JSON.stringify({
            type: 'exec',
            cmd: cmd,
            cols: term.cols,
            rows: term.rows
        }));
        term.focus();
    }

    // Sidebar click handlers
    sidebarItems.forEach(function (item) {
        item.addEventListener('click', function () {
            const cmd = this.dataset.cmd;
            if (cmd) execCmd(cmd);
        });
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
            if (fitAddon) fitAddon.fit();
        }
    });

    // Init
    initTerminal();
    connect();
})();
