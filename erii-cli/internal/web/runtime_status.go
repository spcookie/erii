package web

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"erii-cli/internal/ipc"
)

const coreHealthPath = "/api/chat/health"

type RuntimeStatus struct {
	State         string `json:"state"`
	PIDFile       bool   `json:"pidFile"`
	PID           int    `json:"pid,omitempty"`
	SocketFile    bool   `json:"socketFile"`
	CoreReachable bool   `json:"coreReachable"`
	SetupRequired bool   `json:"setupRequired"`
	Message       string `json:"message,omitempty"`
}

type RuntimeStatusChecker struct {
	EriiDir    string
	HTTPClient *http.Client
}

func (c RuntimeStatusChecker) Check(ctx context.Context) RuntimeStatus {
	status := RuntimeStatus{State: "offline"}
	pidPath := filepath.Join(c.EriiDir, "erii.pid")
	socketPath := filepath.Join(c.EriiDir, "erii.sock")

	if data, err := os.ReadFile(pidPath); err == nil {
		status.PIDFile = true
		status.PID, _ = strconv.Atoi(strings.TrimSpace(string(data)))
	}
	if info, err := os.Stat(socketPath); err == nil && !info.IsDir() {
		status.SocketFile = true
	}
	status.SetupRequired = !status.SocketFile

	if !status.SocketFile {
		if status.PIDFile {
			status.State = "starting"
			status.Message = "Erii core connection file is not available yet"
		} else {
			status.Message = "Erii core PID and connection files were not found"
		}
		return status
	}

	config, err := ipc.ReadConfigSnapshotFromDir(c.EriiDir)
	if err != nil {
		status.State = unavailableRuntimeState(status.PIDFile)
		status.Message = "Erii core connection information is invalid"
		return status
	}

	port := config.Port
	if port == 0 {
		port = 8080
	}
	req, err := http.NewRequestWithContext(
		ctx,
		http.MethodGet,
		fmt.Sprintf("http://127.0.0.1:%d%s", port, coreHealthPath),
		nil,
	)
	if err != nil {
		status.State = unavailableRuntimeState(status.PIDFile)
		status.Message = "Failed to create Erii core health request"
		return status
	}
	req.SetBasicAuth(config.Username, config.Password)

	client := c.HTTPClient
	if client == nil {
		client = &http.Client{Timeout: 1500 * time.Millisecond}
	}
	resp, err := client.Do(req)
	if err != nil {
		status.State = unavailableRuntimeState(status.PIDFile)
		status.Message = "Erii core health check failed"
		return status
	}
	defer resp.Body.Close()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		status.State = unavailableRuntimeState(status.PIDFile)
		status.Message = fmt.Sprintf("Erii core health check returned HTTP %d", resp.StatusCode)
		return status
	}

	var health struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 64*1024)).Decode(&health); err != nil || !strings.EqualFold(health.Status, "ok") {
		status.State = unavailableRuntimeState(status.PIDFile)
		status.Message = "Erii core health response is invalid"
		return status
	}

	status.State = "online"
	status.CoreReachable = true
	if status.PIDFile {
		status.Message = "Erii core is online"
	} else {
		status.Message = "Erii core is online (externally managed)"
	}
	return status
}

func unavailableRuntimeState(pidFile bool) string {
	if pidFile {
		return "unavailable"
	}
	return "offline"
}

func runtimeStatusHandler(token string, current func() RuntimeStatus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			w.Header().Set("Allow", http.MethodGet)
			http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
			return
		}
		providedToken := r.Header.Get("X-Erii-Token")
		if subtle.ConstantTimeCompare([]byte(providedToken), []byte(token)) != 1 {
			http.Error(w, http.StatusText(http.StatusUnauthorized), http.StatusUnauthorized)
			return
		}

		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_ = json.NewEncoder(w).Encode(current())
	}
}
