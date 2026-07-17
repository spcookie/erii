package web

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestRuntimeMonitorPublishesRuntimeFileChanges(t *testing.T) {
	dir := t.TempDir()
	monitor, err := NewRuntimeMonitor(RuntimeStatusChecker{EriiDir: dir})
	if err != nil {
		t.Fatal(err)
	}
	defer monitor.Close()

	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	go monitor.Run(ctx)
	updates, unsubscribe := monitor.Subscribe()
	defer unsubscribe()

	pidPath := filepath.Join(dir, "erii.pid")
	if err := os.WriteFile(pidPath, []byte("1234"), 0644); err != nil {
		t.Fatal(err)
	}
	starting := waitForRuntimeState(t, updates, "starting")
	if !starting.PIDFile || starting.PID != 1234 || !starting.SetupRequired {
		t.Fatalf("unexpected starting update: %#v", starting)
	}

	if err := os.Remove(pidPath); err != nil {
		t.Fatal(err)
	}
	offline := waitForRuntimeState(t, updates, "offline")
	if offline.PIDFile {
		t.Fatalf("unexpected offline update: %#v", offline)
	}
}

func TestRuntimeEventsHandlerStreamsCurrentStatus(t *testing.T) {
	monitor, err := NewRuntimeMonitor(RuntimeStatusChecker{EriiDir: t.TempDir()})
	if err != nil {
		t.Fatal(err)
	}
	defer monitor.Close()

	unauthorized := httptest.NewRecorder()
	runtimeEventsHandler("secret", monitor).ServeHTTP(
		unauthorized,
		httptest.NewRequest(http.MethodGet, "/api/runtime/events", nil),
	)
	if unauthorized.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized status = %d", unauthorized.Code)
	}

	requestContext, cancel := context.WithCancel(t.Context())
	request := httptest.NewRequest(http.MethodGet, "/api/runtime/events?token=secret", nil).WithContext(requestContext)
	response := httptest.NewRecorder()
	done := make(chan struct{})
	go func() {
		runtimeEventsHandler("secret", monitor).ServeHTTP(response, request)
		close(done)
	}()
	time.Sleep(20 * time.Millisecond)
	cancel()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("runtime event stream did not close")
	}

	if response.Header().Get("Content-Type") != "text/event-stream" {
		t.Fatalf("Content-Type = %q", response.Header().Get("Content-Type"))
	}
	body := response.Body.String()
	if !strings.Contains(body, "event: status") || !strings.Contains(body, `"state":"offline"`) {
		t.Fatalf("unexpected event stream body: %q", body)
	}
}

func waitForRuntimeState(t *testing.T, updates <-chan RuntimeStatus, want string) RuntimeStatus {
	t.Helper()
	timer := time.NewTimer(3 * time.Second)
	defer timer.Stop()
	for {
		select {
		case status := <-updates:
			if status.State == want {
				return status
			}
		case <-timer.C:
			t.Fatalf("timed out waiting for runtime state %q", want)
		}
	}
}
