package web

import (
	"log"
	"net/http"
	"os/exec"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
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
	Data string `json:"data,omitempty"`
	Code int    `json:"code,omitempty"`
}

// WSHandler handles WebSocket connections: PTY I/O bridge.
type WSHandler struct {
	Session *Session
	Token   string
	EriiBin string
	ConfDir string
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
				conn.WriteJSON(wsOut{Type: "error", Data: "invalid token"})
				return
			}

		case "exec":
			if !authenticated {
				continue
			}
			bin := h.EriiBin
			if bin == "" {
				var err error
				bin, err = exec.LookPath("erii")
				if err != nil {
					conn.WriteJSON(wsOut{Type: "output", Data: "erii: command not found\r\n"})
					continue
				}
			}

			args := append([]string{"--conf-dir", h.ConfDir}, msg.Args...)
			if err := h.Session.Start(bin, msg.Cmd, args); err != nil {
				conn.WriteJSON(wsOut{Type: "output", Data: "Failed to start: " + err.Error() + "\r\n"})
				continue
			}

			go h.bridgePtyToWS(conn)

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
func (h *WSHandler) bridgePtyToWS(conn *websocket.Conn) {
	buf := make([]byte, 4096)
	for {
		n, err := h.Session.Read(buf)
		if n > 0 {
			conn.WriteJSON(wsOut{Type: "output", Data: string(buf[:n])})
		}
		if err != nil {
			break
		}
	}
	conn.WriteJSON(wsOut{Type: "exit", Code: 0})
}
