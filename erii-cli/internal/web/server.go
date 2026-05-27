package web

import (
	"context"
	"embed"
	"fmt"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

//go:embed static
var staticFiles embed.FS

// Config holds the server configuration.
type Config struct {
	Port    string
	Host    string
	Token   string
	EriiBin string
	ConfDir string
}

// Start starts the HTTP + WebSocket server. Blocks until SIGINT/SIGTERM.
func Start(cfg Config) error {
	addr := net.JoinHostPort(cfg.Host, cfg.Port)

	session := &Session{}
	wsHandler := &WSHandler{
		Session: session,
		Token:   cfg.Token,
		EriiBin: cfg.EriiBin,
		ConfDir: cfg.ConfDir,
	}

	staticFS, err := fs.Sub(staticFiles, "static")
	if err != nil {
		return fmt.Errorf("embedded static files not found: %w", err)
	}
	fileServer := contentTypeHandler{http.FileServer(http.FS(staticFS))}

	mux := http.NewServeMux()
	mux.Handle("/ws", wsHandler)
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		// Only validate token for the HTML page, not static assets
		if (path == "/" || path == "/index.html") && r.URL.Query().Get("token") != cfg.Token {
			w.WriteHeader(http.StatusUnauthorized)
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Write(tokenErrorPage)
			return
		}
		fileServer.ServeHTTP(w, r)
	})

	server := &http.Server{
		Addr:    addr,
		Handler: mux,
	}

	// Print startup info.
	localURL := fmt.Sprintf("http://localhost:%s/?token=%s", cfg.Port, cfg.Token)
	fmt.Printf("\n  \x1b[1;36mErii Console\x1b[0m  \x1b[1;32mready\x1b[0m\n\n")
	fmt.Printf("  \x1b[90m➜\x1b[0m  Local:   \x1b[1;36m%s\x1b[0m\n", localURL)
	if cfg.Host != "127.0.0.1" {
		networkHost := cfg.Host
		if networkHost == "0.0.0.0" {
			if ip := getLocalIP(); ip != "" {
				networkHost = ip
			}
		}
		networkURL := fmt.Sprintf("http://%s:%s/?token=%s", networkHost, cfg.Port, cfg.Token)
		fmt.Printf("  \x1b[90m➜\x1b[0m  Network: \x1b[1;36m%s\x1b[0m\n", networkURL)
	}
	fmt.Printf("\n  \x1b[90mPress Ctrl+C to stop\x1b[0m\n\n")

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

var tokenErrorPage = []byte(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Erii Console - Unauthorized</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; }
body {
    background: #11111b;
    color: #cdd6f4;
    font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
}
.card {
    background: #1e1e1e;
    border: 1px solid #333;
    border-radius: 8px;
    padding: 32px 40px;
    text-align: center;
    max-width: 480px;
}
.card h1 {
    color: #f38ba8;
    font-size: 16px;
    margin-bottom: 16px;
}
.card p {
    color: #a6adc8;
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
</html>`)
