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
        currentCmdEl.textContent = cmd ? '▶ erii ' + cmd : '';
        sidebarItems.forEach(function (item) {
            if (item.dataset.cmd === cmd) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
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
        fitAddon.fit();

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

        window.addEventListener('resize', function () {
            if (fitAddon) fitAddon.fit();
        });
    }

    function connect() {
        if (ws) {
            ws.close();
            ws = null;
        }

        setStatus('connecting');
        ws = new WebSocket(wsUrl);

        ws.onopen = function () {
            ws.send(JSON.stringify({ type: 'auth', token: token }));
        };

        ws.onmessage = function (event) {
            var msg = JSON.parse(event.data);
            switch (msg.type) {
                case 'output':
                    if (term) term.write(msg.data);
                    break;
                case 'exit':
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
                var delay = Math.pow(2, reconnectAttempts) * 1000;
                setTimeout(connect, delay);
            }
        };

        ws.onerror = function () {
            ws.close();
        };
    }

    function execCmd(cmd) {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        setActiveCmd(cmd);
        setStatus('running');
        term.clear();
        ws.send(JSON.stringify({ type: 'exec', cmd: cmd }));
    }

    // Sidebar click handlers
    sidebarItems.forEach(function (item) {
        item.addEventListener('click', function () {
            var cmd = this.dataset.cmd;
            if (cmd) execCmd(cmd);
        });
    });

    // Sidebar resize
    var sidebar = document.getElementById('sidebar');
    var isResizing = false;
    var startX, startWidth;

    resizeHandle.addEventListener('mousedown', function (e) {
        isResizing = true;
        startX = e.clientX;
        startWidth = sidebar.offsetWidth;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
    });

    document.addEventListener('mousemove', function (e) {
        if (!isResizing) return;
        var newWidth = startWidth + (e.clientX - startX);
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
