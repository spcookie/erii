package web

import (
	"log"
	"net/http"
	"os/exec"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

// wsMessage is the JSON protocol message from the frontend.
type wsMessage struct {
	Type  string   `json:"type"`
	Cmd   string   `json:"cmd,omitempty"`
	Args  []string `json:"args,omitempty"`
	Data  string   `json:"data,omitempty"`
	Token string   `json:"token,omitempty"`
	Cols  int      `json:"cols,omitempty"`
	Rows  int      `json:"rows,omitempty"`
}

// wsOut is the JSON protocol message sent to the frontend.
type wsOut struct {
	Type string `json:"type"`
	Code int    `json:"code,omitempty"`
}

// WSHandler handles WebSocket connections: PTY I/O bridge.
type WSHandler struct {
	Session     *Session
	Token       string
	EriiBin     string
	ConfDir     string
	MetaConfDir string
	PluginDir   string
	binPath     string
	binReady    bool
}

func (h *WSHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("ws upgrade error: %v", err)
		return
	}
	defer conn.Close()
	defer h.Session.Close()

	authenticated := false

	for {
		var msg wsMessage
		if err := conn.ReadJSON(&msg); err != nil {
			log.Printf("ws read error: %v", err)
			return
		}

		switch msg.Type {
		case "auth":
			if msg.Token == h.Token {
				authenticated = true
			} else {
				return
			}

		case "exec":
			if !authenticated {
				continue
			}
			bin := h.EriiBin
			if bin == "" {
				if !h.binReady {
					var err error
					h.binPath, err = exec.LookPath("erii")
					if err != nil {
						conn.WriteMessage(websocket.BinaryMessage, []byte("erii: command not found\r\n"))
						conn.WriteJSON(wsOut{Type: "exit", Code: -1})
						continue
					}
					h.binReady = true
				}
				bin = h.binPath
			}

			args := []string{"--conf-dir", h.ConfDir}
			if h.MetaConfDir != "" {
				args = append(args, "--meta-conf-dir", h.MetaConfDir)
			}
			if h.PluginDir != "" {
				args = append(args, "--plugin-dir", h.PluginDir)
			}
			args = append(args, msg.Args...)
			if err := h.Session.Start(bin, msg.Cmd, args, msg.Rows, msg.Cols); err != nil {
				conn.WriteMessage(websocket.BinaryMessage, []byte("Failed to start: "+err.Error()+"\r\n"))
				conn.WriteJSON(wsOut{Type: "exit", Code: -1})
				continue
			}

			go h.bridgePtyToWS(conn, h.Session.Generation())

		case "input":
			if !authenticated {
				continue
			}
			h.Session.Write([]byte(msg.Data))

		case "resize":
			if !authenticated {
				continue
			}
			if msg.Cols > 0 && msg.Rows > 0 {
				h.Session.Resize(msg.Rows, msg.Cols)
			}
		}
	}
}

// bridgePtyToWS reads from PTY and writes to WebSocket until PTY closes.
// Uses binary messages to preserve raw terminal bytes (no JSON string encoding).
// Only sends exit message if this generation is still the active one.
func (h *WSHandler) bridgePtyToWS(conn *websocket.Conn, gen int) {
	buf := make([]byte, 4096)
	for {
		n, err := h.Session.Read(buf)
		if n > 0 {
			conn.WriteMessage(websocket.BinaryMessage, buf[:n])
		}
		if err != nil {
			break
		}
	}
	if h.Session.Generation() == gen {
		conn.WriteJSON(wsOut{Type: "exit", Code: h.Session.ExitCode()})
	}
}
