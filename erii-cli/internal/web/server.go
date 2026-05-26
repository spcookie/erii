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
	mux.Handle("/", fileServer)

	server := &http.Server{
		Addr:    addr,
		Handler: mux,
	}

	// Print startup info.
	fmt.Printf("Web console started at http://localhost:%s\n", cfg.Port)
	fmt.Printf("Token: %s\n", cfg.Token)
	fmt.Println("Press Ctrl+C to stop")

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
