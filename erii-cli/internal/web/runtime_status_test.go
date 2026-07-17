package web

import (
	"encoding/binary"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"

	"erii-cli/internal/ipc"

	"github.com/vmihailenco/msgpack/v5"
)

func TestRuntimeStatusOfflineWithoutPIDOrSocket(t *testing.T) {
	status := (RuntimeStatusChecker{EriiDir: t.TempDir()}).Check(t.Context())
	if status.State != "offline" || status.PIDFile || status.SocketFile || status.CoreReachable {
		t.Fatalf("unexpected offline status: %#v", status)
	}
	if !status.SetupRequired {
		t.Fatal("missing socket should require setup")
	}
}

func TestRuntimeStatusStartingWithPIDButNoSocket(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "erii.pid"), []byte("1234"), 0644); err != nil {
		t.Fatal(err)
	}

	status := (RuntimeStatusChecker{EriiDir: dir}).Check(t.Context())
	if status.State != "starting" || !status.PIDFile || status.PID != 1234 || status.SocketFile {
		t.Fatalf("unexpected starting status: %#v", status)
	}
}

func TestRuntimeStatusCallsAuthenticatedCoreHealth(t *testing.T) {
	const username = "status-user"
	const password = "status-password"
	var healthCalled atomic.Bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		healthCalled.Store(true)
		if r.URL.Path != coreHealthPath {
			t.Errorf("health path = %q, want %q", r.URL.Path, coreHealthPath)
		}
		gotUsername, gotPassword, ok := r.BasicAuth()
		if !ok || gotUsername != username || gotPassword != password {
			t.Errorf("unexpected basic auth: %q %q %v", gotUsername, gotPassword, ok)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	}))
	defer server.Close()

	_, portString, err := net.SplitHostPort(strings.TrimPrefix(server.URL, "http://"))
	if err != nil {
		t.Fatal(err)
	}
	port, err := strconv.Atoi(portString)
	if err != nil {
		t.Fatal(err)
	}

	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "erii.pid"), []byte("4321"), 0644); err != nil {
		t.Fatal(err)
	}
	writeIPCConfig(t, dir, ipc.ServerConfig{
		Type:     "config",
		Port:     port,
		Username: username,
		Password: password,
	})

	status := (RuntimeStatusChecker{EriiDir: dir}).Check(t.Context())
	if !healthCalled.Load() || status.State != "online" || !status.CoreReachable || status.SetupRequired {
		t.Fatalf("unexpected online status: %#v, healthCalled=%v", status, healthCalled.Load())
	}

	if err := os.Remove(filepath.Join(dir, "erii.pid")); err != nil {
		t.Fatal(err)
	}
	healthCalled.Store(false)
	status = (RuntimeStatusChecker{EriiDir: dir}).Check(t.Context())
	if !healthCalled.Load() || status.State != "online" || !status.CoreReachable || status.PIDFile {
		t.Fatalf("externally managed core should remain online: %#v, healthCalled=%v", status, healthCalled.Load())
	}
	if !strings.Contains(status.Message, "externally managed") {
		t.Fatalf("externally managed status message = %q", status.Message)
	}
}

func TestRuntimeStatusSnapshotDoesNotResizeIncompleteSocket(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "erii.pid"), []byte("1234"), 0644); err != nil {
		t.Fatal(err)
	}
	socketPath := filepath.Join(dir, "erii.sock")
	if err := os.WriteFile(socketPath, []byte{0, 0}, 0644); err != nil {
		t.Fatal(err)
	}

	status := (RuntimeStatusChecker{EriiDir: dir}).Check(t.Context())
	if status.State != "unavailable" {
		t.Fatalf("state = %q, want unavailable", status.State)
	}
	info, err := os.Stat(socketPath)
	if err != nil {
		t.Fatal(err)
	}
	if info.Size() != 2 {
		t.Fatalf("status poll resized socket to %d bytes", info.Size())
	}
}

func TestRuntimeStatusHandlerRequiresWebToken(t *testing.T) {
	checker := RuntimeStatusChecker{EriiDir: t.TempDir()}
	handler := runtimeStatusHandler("secret", func() RuntimeStatus {
		return checker.Check(t.Context())
	})

	unauthorized := httptest.NewRecorder()
	handler.ServeHTTP(unauthorized, httptest.NewRequest(http.MethodGet, "/api/runtime/status", nil))
	if unauthorized.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized status = %d", unauthorized.Code)
	}

	authorizedRequest := httptest.NewRequest(http.MethodGet, "/api/runtime/status", nil)
	authorizedRequest.Header.Set("X-Erii-Token", "secret")
	authorized := httptest.NewRecorder()
	handler.ServeHTTP(authorized, authorizedRequest)
	if authorized.Code != http.StatusOK {
		t.Fatalf("authorized status = %d", authorized.Code)
	}
	if authorized.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("Cache-Control = %q", authorized.Header().Get("Cache-Control"))
	}
	var status RuntimeStatus
	if err := json.NewDecoder(authorized.Body).Decode(&status); err != nil {
		t.Fatal(err)
	}
	if status.State != "offline" {
		t.Fatalf("state = %q, want offline", status.State)
	}
}

func writeIPCConfig(t *testing.T, dir string, config ipc.ServerConfig) {
	t.Helper()
	encoded, err := msgpack.Marshal(config)
	if err != nil {
		t.Fatal(err)
	}
	data := make([]byte, ipc.SIZE)
	binary.BigEndian.PutUint32(data[:4], uint32(len(encoded)))
	copy(data[4:], encoded)
	if err := os.WriteFile(filepath.Join(dir, "erii.sock"), data, 0644); err != nil {
		t.Fatal(err)
	}
}
