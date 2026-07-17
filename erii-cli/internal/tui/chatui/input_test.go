package chatui

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"erii-cli/internal/api"
	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/cursor"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/x/ansi"
	"github.com/gorilla/websocket"
)

func TestTextareaBlinkMessagesAreForwarded(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.textarea.Cursor.BlinkSpeed = time.Millisecond

	_, cmd := m.Update(cursor.Blink())
	if cmd == nil {
		t.Fatal("initial cursor blink message did not schedule the next blink")
	}
	blink := cmd()
	before := m.textarea.Cursor.Blink
	_, next := m.Update(blink)
	if next == nil {
		t.Fatal("cursor blink did not remain active")
	}
	if m.textarea.Cursor.Blink == before {
		t.Fatal("cursor blink state did not toggle")
	}
}

func TestDisabledInputHasVisibleState(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.disableInput("Waiting for response...", style.Info)

	if m.textarea.Focused() {
		t.Fatal("disabled textarea should not retain focus")
	}
	if !strings.Contains(ansi.Strip(m.textarea.View()), "Waiting for response...") {
		t.Fatalf("disabled textarea does not explain its state:\n%s", m.textarea.View())
	}
}

func TestChatErrorUsesInputStyleLeftBorder(t *testing.T) {
	line := ansi.Strip(renderChatError(errors.New("boom")))
	if !strings.HasPrefix(line, "┃ ") {
		t.Fatalf("error line does not use the textarea-style left border: %q", line)
	}
	if !strings.Contains(line, "Error: boom") {
		t.Fatalf("error line is missing its message: %q", line)
	}
}

func TestEscapeDismissesRecoverableError(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.err = errors.New("temporary failure")
	m.disableInput("Dismiss error to continue", style.Error)

	m.Update(tea.KeyMsg{Type: tea.KeyEsc})

	if m.err != nil {
		t.Fatalf("escape did not dismiss the error: %v", m.err)
	}
	if !m.textarea.Focused() {
		t.Fatal("input was not restored after dismissing the error")
	}
}

func TestMatchingResponseStopsThinking(t *testing.T) {
	m := initialModel(nil, nil, "Erii", make(chan tea.Msg, 1))
	m.sending = true
	m.activeRequestID = "request-2"
	m.lastSendTime = time.Now().Add(-time.Second)
	m.disableInput("Waiting for response...", style.Info)

	m.Update(botResponseMsg{requestID: "request-2", response: "done"})

	if m.sending || m.activeRequestID != "" {
		t.Fatalf("matching response did not finish sending: sending=%v active=%q", m.sending, m.activeRequestID)
	}
	if !m.textarea.Focused() {
		t.Fatal("input was not restored after the matching response")
	}
}

func TestLateCanceledResponseDoesNotStopCurrentThinking(t *testing.T) {
	m := initialModel(nil, nil, "Erii", make(chan tea.Msg, 1))
	m.sending = true
	m.activeRequestID = "request-2"
	m.disableInput("Waiting for response...", style.Info)

	m.Update(botResponseMsg{requestID: "request-1", response: "late"})

	if !m.sending || m.activeRequestID != "request-2" {
		t.Fatalf("late response changed the active request: sending=%v active=%q", m.sending, m.activeRequestID)
	}
}

func TestErrorHelpShowsBothDismissKeys(t *testing.T) {
	if got := errorKeys.Dismiss.Help().Key; got != "esc/r" {
		t.Fatalf("dismiss help key = %q, want esc/r", got)
	}
}

func TestDisconnectedErrorHelpSeparatesDismissAndRetry(t *testing.T) {
	if got := connectionErrorKeys.Dismiss.Help().Key; got != "esc" {
		t.Fatalf("disconnect dismiss key = %q, want esc", got)
	}
	if got := connectionErrorKeys.Retry.Help().Key; got != "r" {
		t.Fatalf("disconnect retry key = %q, want r", got)
	}
}

func TestEscapeDismissesDisconnectedErrorWithoutImmediateRetry(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.err = errors.New("service unavailable")
	m.wsDisconnected = true
	m.disableInput("Press r to retry or esc to dismiss", style.Error)

	_, cmd := m.Update(tea.KeyMsg{Type: tea.KeyEsc})

	if cmd != nil || m.err != nil || !m.wsDisconnected || m.reconnecting {
		t.Fatalf("escape did not leave a quiet disconnected state: cmd=%v err=%v disconnected=%v reconnecting=%v",
			cmd != nil, m.err, m.wsDisconnected, m.reconnecting)
	}
	disabledView := ansi.Strip(m.textarea.View())
	if !strings.Contains(disabledView, "Chat disconnected") || !strings.Contains(disabledView, "reconnect") {
		t.Fatalf("disconnected input does not explain how to retry:\n%s", m.textarea.View())
	}
}

func TestRetryReconnectsClosedChatSocketBeforeRestoringInput(t *testing.T) {
	upgrader := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}))
	defer server.Close()

	client := api.NewClient(testServerPort(t, server.URL), "", "")
	m := initialModel(client, nil, "Erii", make(chan tea.Msg, 4))
	m.err = errors.New("unexpected EOF")
	m.wsDisconnected = true
	m.disableInput("Dismiss error to continue", style.Error)

	_, cmd := m.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{'r'}})
	if cmd == nil || !m.reconnecting || m.err != nil {
		t.Fatalf("retry did not begin reconnecting: reconnecting=%v err=%v", m.reconnecting, m.err)
	}
	msg, ok := cmd().(chatReconnectMsg)
	if !ok {
		t.Fatalf("reconnect command returned %T", msg)
	}
	if msg.err != nil {
		t.Fatalf("reconnect failed: %v", msg.err)
	}
	_, listenCmd := m.Update(msg)
	defer m.wsConn.Close()

	if m.wsDisconnected || m.reconnecting || m.err != nil {
		t.Fatalf("connection state was not restored: disconnected=%v reconnecting=%v err=%v",
			m.wsDisconnected, m.reconnecting, m.err)
	}
	if !m.textarea.Focused() {
		t.Fatal("input was not restored after reconnecting")
	}
	if listenCmd == nil {
		t.Fatal("message listener was not restored after reconnecting")
	}
}
