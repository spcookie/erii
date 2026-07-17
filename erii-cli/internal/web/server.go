package web

import (
	"context"
	"embed"
	"fmt"
	"io"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	uioutput "erii-cli/internal/ui/output"
)

//go:embed static
var staticFiles embed.FS

// Config holds the server configuration.
type Config struct {
	Port        string
	Host        string
	Token       string
	EriiBin     string
	ConfDir     string
	MetaConfDir string
	EriiDir     string
	PluginDir   string
	OptsPath    string
	Theme       string
	Output      io.Writer
}

// Start starts the HTTP + WebSocket server. Blocks until SIGINT/SIGTERM.
func Start(cfg Config) error {
	addr := net.JoinHostPort(cfg.Host, cfg.Port)
	monitor, err := NewRuntimeMonitor(RuntimeStatusChecker{EriiDir: cfg.EriiDir})
	if err != nil {
		return err
	}
	monitorCtx, stopMonitor := context.WithCancel(context.Background())
	defer stopMonitor()
	defer monitor.Close()
	go monitor.Run(monitorCtx)

	session := &Session{}
	wsHandler := &WSHandler{
		Session:     session,
		Token:       cfg.Token,
		EriiBin:     cfg.EriiBin,
		ConfDir:     cfg.ConfDir,
		MetaConfDir: cfg.MetaConfDir,
		EriiDir:     cfg.EriiDir,
		PluginDir:   cfg.PluginDir,
		OptsPath:    cfg.OptsPath,
		Theme:       normalizedTheme(cfg.Theme),
	}

	staticFS, err := fs.Sub(staticFiles, "static")
	if err != nil {
		return fmt.Errorf("embedded static files not found: %w", err)
	}
	fileServer := contentTypeHandler{http.FileServer(http.FS(staticFS))}
	indexTemplate, err := fs.ReadFile(staticFS, "index.html")
	if err != nil {
		return fmt.Errorf("embedded index not found: %w", err)
	}
	assetVersion := fmt.Sprintf("%x", time.Now().UnixNano())
	indexHTML := renderIndexPage(string(indexTemplate), cfg.Theme, assetVersion)

	mux := http.NewServeMux()
	mux.Handle("/ws", wsHandler)
	mux.Handle("/api/runtime/status", runtimeStatusHandler(cfg.Token, monitor.Current))
	mux.Handle("/api/runtime/events", runtimeEventsHandler(cfg.Token, monitor))
	mux.Handle("/api/plugin/match", pluginMatchHandler(cfg.Token, cfg.EriiDir))
	mux.Handle("/api/plugin/send", pluginSendHandler(cfg.Token, cfg.EriiDir))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		requestPath := r.URL.Path
		// Only validate token for the HTML page, not static assets
		if (requestPath == "/" || requestPath == "/index.html") && r.URL.Query().Get("token") != cfg.Token {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.WriteHeader(http.StatusUnauthorized)
			_, _ = w.Write([]byte(renderTokenErrorPage(normalizedTheme(cfg.Theme))))
			return
		}
		if requestPath == "/" || requestPath == "/index.html" {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Header().Set("Cache-Control", "no-store")
			_, _ = io.WriteString(w, indexHTML)
			return
		}
		fileServer.ServeHTTP(w, r)
	})

	server := &http.Server{
		Addr:    addr,
		Handler: mux,
	}

	output := cfg.Output
	if output == nil {
		output = os.Stdout
	}
	localURL := fmt.Sprintf("http://localhost:%s/?token=%s", cfg.Port, cfg.Token)
	fmt.Fprintln(output)
	fmt.Fprintln(output, uioutput.Title("Erii Console")+"  "+uioutput.Status("ready"))
	fmt.Fprint(output, uioutput.Row("Local", localURL))
	if cfg.Host != "127.0.0.1" {
		networkHost := cfg.Host
		if networkHost == "0.0.0.0" {
			if ip := getLocalIP(); ip != "" {
				networkHost = ip
			}
		}
		networkURL := fmt.Sprintf("http://%s:%s/?token=%s", networkHost, cfg.Port, cfg.Token)
		fmt.Fprint(output, uioutput.Row("Network", networkURL))
	}
	fmt.Fprintln(output, uioutput.Muted("Press Ctrl+C to stop"))

	// Graceful shutdown on SIGINT/SIGTERM.
	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh
		log.Println("Shutting down...")
		session.Close()
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		server.Shutdown(ctx)
	}()

	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return fmt.Errorf("server error: %w", err)
	}
	return nil
}

// contentTypeHandler sets Content-Type based on file extension to avoid
// MIME type detection issues with embedded filesystems.
type contentTypeHandler struct {
	h http.Handler
}

func (c contentTypeHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ext := filepath.Ext(r.URL.Path)
	if ct := mime.TypeByExtension(ext); ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	if strings.HasPrefix(r.URL.Path, "/fonts/") && r.URL.Query().Get("v") != "" {
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	} else {
		w.Header().Set("Cache-Control", "no-cache, must-revalidate")
	}
	c.h.ServeHTTP(w, r)
}

// getLocalIP returns the preferred outbound IP address of the current machine.
func getLocalIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return ""
	}
	defer conn.Close()
	host, _, err := net.SplitHostPort(conn.LocalAddr().String())
	if err != nil {
		return ""
	}
	return host
}

func normalizedTheme(value string) string {
	switch value {
	case "dark", "light":
		return value
	default:
		return "auto"
	}
}

func renderIndexPage(template, theme, assetVersion string) string {
	page := strings.ReplaceAll(template, "__ERII_THEME__", normalizedTheme(theme))
	return strings.ReplaceAll(page, "__ERII_ASSET_VERSION__", assetVersion)
}

func renderTokenErrorPage(theme string) string {
	return strings.ReplaceAll(tokenErrorPage, "__ERII_THEME__", normalizedTheme(theme))
}

const tokenErrorPage = `<!DOCTYPE html>
<html lang="en" data-theme="__ERII_THEME__">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Erii Console - Unauthorized</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; }
:root, html[data-theme="light"] {
    --page: #ffffff; --surface: #fafafa; --text: #171717;
    --muted: #666666; --border: #eaeaea; --error: #d70022;
    color-scheme: light;
}
html[data-theme="dark"] {
    --page: #000000; --surface: #111111; --text: #ededed;
    --muted: #a1a1a1; --border: #333333; --error: #e5484d;
    color-scheme: dark;
}
@media (prefers-color-scheme: dark) {
    html[data-theme="auto"] {
        --page: #000000; --surface: #111111; --text: #ededed;
        --muted: #a1a1a1; --border: #333333; --error: #e5484d;
        color-scheme: dark;
    }
}
body {
    background: var(--page);
    color: var(--text);
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
}
.card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 32px 40px;
    text-align: center;
    max-width: 480px;
}
.card h1 {
    color: var(--error);
    font-size: 16px;
    margin-bottom: 16px;
}
.card p {
    color: var(--muted);
    font-size: 12px;
    line-height: 1.8;
}
</style>
</head>
<body>
<div class="card">
    <h1>Unauthorized</h1>
    <p>You do not have permission to access this page.</p>
</div>
</body>
</html>`
