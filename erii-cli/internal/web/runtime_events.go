package web

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

func runtimeEventsHandler(token string, monitor *RuntimeMonitor) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			w.Header().Set("Allow", http.MethodGet)
			http.Error(w, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
			return
		}
		providedToken := r.URL.Query().Get("token")
		if subtle.ConstantTimeCompare([]byte(providedToken), []byte(token)) != 1 {
			http.Error(w, http.StatusText(http.StatusUnauthorized), http.StatusUnauthorized)
			return
		}
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming is not supported", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache, no-transform")
		w.Header().Set("Connection", "keep-alive")
		w.Header().Set("X-Accel-Buffering", "no")

		updates, unsubscribe := monitor.Subscribe()
		defer unsubscribe()
		if err := writeRuntimeStatusEvent(w, monitor.Current()); err != nil {
			return
		}
		flusher.Flush()

		keepAlive := time.NewTicker(20 * time.Second)
		defer keepAlive.Stop()
		for {
			select {
			case <-r.Context().Done():
				return
			case <-monitor.Done():
				return
			case status := <-updates:
				if err := writeRuntimeStatusEvent(w, status); err != nil {
					return
				}
				flusher.Flush()
			case <-keepAlive.C:
				if _, err := fmt.Fprint(w, ": keep-alive\n\n"); err != nil {
					return
				}
				flusher.Flush()
			}
		}
	}
}

func writeRuntimeStatusEvent(w http.ResponseWriter, status RuntimeStatus) error {
	data, err := json.Marshal(status)
	if err != nil {
		return err
	}
	_, err = fmt.Fprintf(w, "event: status\ndata: %s\n\n", data)
	return err
}
