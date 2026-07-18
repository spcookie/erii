package cmd

import (
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"erii-cli/internal/path"
	"erii-cli/internal/web"
)

func TestWebCommandShape(t *testing.T) {
	if webCmd.Use != "web" {
		t.Fatalf("web command use = %q", webCmd.Use)
	}
	if webCmd.RunE == nil {
		t.Fatal("web parent command should render help")
	}
	if webStartCmd.Use != "start" || webStopCmd.Use != "stop" {
		t.Fatalf("unexpected web subcommands: %q %q", webStartCmd.Use, webStopCmd.Use)
	}
	for _, name := range []string{"host", "port", "token", "daemon"} {
		if webStartCmd.Flags().Lookup(name) == nil {
			t.Fatalf("web start is missing --%s", name)
		}
		if webCmd.Flags().Lookup(name) != nil {
			t.Fatalf("web parent must not expose --%s", name)
		}
		if webStopCmd.Flags().Lookup(name) != nil {
			t.Fatalf("web stop must not expose --%s", name)
		}
	}
	daemon := webStartCmd.Flags().Lookup("daemon")
	if daemon.Shorthand != "d" {
		t.Fatalf("--daemon shorthand = %q, want d", daemon.Shorthand)
	}
}

func TestWebPathsUseConfiguredDirectories(t *testing.T) {
	oldEriiDir := path.EriiDir
	oldLogsPath := logsPathFlag
	t.Cleanup(func() {
		path.EriiDir = oldEriiDir
		logsPathFlag = oldLogsPath
	})

	root := t.TempDir()
	path.EriiDir = filepath.Join(root, "runtime")
	logsPathFlag = filepath.Join(root, "custom-logs")

	if got, want := webPIDFilePath(), filepath.Join(path.EriiDir, "erii.web.pid"); got != want {
		t.Fatalf("web PID path = %q, want %q", got, want)
	}
	logPath, err := webLogFilePath()
	if err != nil {
		t.Fatal(err)
	}
	if got, want := logPath, filepath.Join(logsPathFlag, "erii.web.log"); got != want {
		t.Fatalf("web log path = %q, want %q", got, want)
	}
}

func TestClaimWebPIDReplacesStalePID(t *testing.T) {
	oldEriiDir := path.EriiDir
	t.Cleanup(func() { path.EriiDir = oldEriiDir })
	path.EriiDir = t.TempDir()

	if err := os.WriteFile(webPIDFilePath(), []byte("99999999"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := claimWebPID(os.Getpid()); err != nil {
		t.Fatal(err)
	}
	pid, err := readWebPID()
	if err != nil {
		t.Fatal(err)
	}
	if pid != os.Getpid() {
		t.Fatalf("claimed PID = %d, want %d", pid, os.Getpid())
	}
	removeWebPIDIfOwned(os.Getpid())
	if _, err := os.Stat(webPIDFilePath()); !os.IsNotExist(err) {
		t.Fatalf("owned PID file was not removed: %v", err)
	}
}

func TestWebDaemonArgsPreserveResolvedConfiguration(t *testing.T) {
	cfg := web.Config{
		Host:        "0.0.0.0",
		Port:        "9876",
		Token:       "fixed-token",
		ConfDir:     "/tmp/conf",
		MetaConfDir: "/tmp/meta",
		EriiDir:     "/tmp/runtime",
		PluginDir:   "/tmp/plugins",
		OptsPath:    "/tmp/opts",
		LogsPath:    "/tmp/logs",
		Theme:       "dark",
	}
	args := strings.Join(webDaemonArgs(cfg), " ")
	for _, want := range []string{
		"--conf-dir /tmp/conf",
		"--meta-conf-dir /tmp/meta",
		"--erii-dir /tmp/runtime",
		"--plugin-dir /tmp/plugins",
		"--opts-path /tmp/opts",
		"--logs-path /tmp/logs",
		"--theme dark",
		"web start",
		"--host 0.0.0.0",
		"--port 9876",
		"--token fixed-token",
	} {
		if !strings.Contains(args, want) {
			t.Fatalf("daemon args missing %q: %s", want, args)
		}
	}
	if strings.Contains(args, "--daemon") || strings.Contains(args, " -d") {
		t.Fatalf("daemon child must run without daemon flag: %s", args)
	}
}

func TestProbeWebRequiresValidToken(t *testing.T) {
	server := &http.Server{}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_ = server.Close()
		_ = listener.Close()
	})
	server.Handler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("token") != "expected" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.WriteHeader(http.StatusOK)
	})
	go func() { _ = server.Serve(listener) }()

	port := strconv.Itoa(listener.Addr().(*net.TCPAddr).Port)
	if !probeWeb(web.Config{Host: "127.0.0.1", Port: port, Token: "expected"}) {
		t.Fatal("probe should accept the configured token")
	}
	if probeWeb(web.Config{Host: "127.0.0.1", Port: port, Token: "wrong"}) {
		t.Fatal("probe should reject an invalid token")
	}
}
