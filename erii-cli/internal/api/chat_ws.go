package api

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/gorilla/websocket"
)

// ChatWSConn wraps a WebSocket connection to the chat endpoint.
type ChatWSConn struct {
	conn *websocket.Conn
}

// NewChatWSConn creates a WebSocket connection to /api/chat/ws.
func NewChatWSConn(baseURL, username, password string) (*ChatWSConn, error) {
	wsURL := strings.Replace(baseURL, "https://", "wss://", 1)
	wsURL = strings.Replace(wsURL, "http://", "ws://", 1)
	wsURL += "/api/chat/ws"

	header := http.Header{}
	auth := base64.StdEncoding.EncodeToString([]byte(username + ":" + password))
	header.Set("Authorization", "Basic "+auth)

	dialer := websocket.Dialer{}
	conn, _, err := dialer.Dial(wsURL, header)
	if err != nil {
		return nil, fmt.Errorf("ws connect failed: %w", err)
	}
	return &ChatWSConn{conn: conn}, nil
}

// SendMessage sends a chat message over the WebSocket.
func (w *ChatWSConn) SendMessage(text string) error {
	return w.conn.WriteJSON(map[string]string{
		"content": text,
	})
}

// ReadResponse reads and parses a chat response from the WebSocket.
func (w *ChatWSConn) ReadResponse() (*ChatSendResponse, error) {
	_, msg, err := w.conn.ReadMessage()
	if err != nil {
		return nil, fmt.Errorf("ws read failed: %w", err)
	}
	var resp ChatSendResponse
	if err := json.Unmarshal(msg, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse ws response: %w", err)
	}
	return &resp, nil
}

// Close closes the WebSocket connection.
func (w *ChatWSConn) Close() error {
	return w.conn.Close()
}
