package chatui

import (
	"fmt"

	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"

	tea "github.com/charmbracelet/bubbletea"
)

// Start initializes and runs the chat TUI with role selection.
func Start() error {
	for {
		client, err := api.NewClientFromIPC()
		if err != nil {
			model := components.NewConnectionErrorModel(err)
			p := tea.NewProgram(model, tea.WithAltScreen())
			if _, err := p.Run(); err != nil {
				return fmt.Errorf("failed to run TUI: %w", err)
			}
			if !model.ShouldRetry() {
				return nil
			}
			continue
		}

		// Step 1: show role picker
		pickerModel := newRolePickerModel(client)
		pickerProg := tea.NewProgram(pickerModel, tea.WithAltScreen())
		finalModel, pickerErr := pickerProg.Run()

		if pickerErr != nil {
			return fmt.Errorf("role picker failed: %w", pickerErr)
		}

		picker := finalModel.(*RolePickerModel)
		if picker.selectedRole == nil {
			return nil // user quit without selecting
		}

		// Step 2: send role selection to server
		_, err = client.SelectChatRole(picker.selectedRole.id)
		if err != nil {
			connErrModel := components.NewConnectionErrorModel(
				fmt.Errorf("failed to select role %q: %w", picker.selectedRole.name, err),
			)
			errProg := tea.NewProgram(connErrModel, tea.WithAltScreen())
			_, errProgErr := errProg.Run()
			if errProgErr != nil {
				return fmt.Errorf("failed to show error: %w", errProgErr)
			}
			if !connErrModel.ShouldRetry() {
				return nil
			}
			continue
		}

		// Step 3: create WebSocket connection
		wsConn, err := api.NewChatWSConn(client.BaseURL(), client.Username(), client.Password())
		if err != nil {
			connErrModel := components.NewConnectionErrorModel(
				fmt.Errorf("failed to connect chat WebSocket: %w", err),
			)
			errProg := tea.NewProgram(connErrModel, tea.WithAltScreen())
			_, _ = errProg.Run()
			if !connErrModel.ShouldRetry() {
				return nil
			}
			continue
		}
		wsMsgCh := make(chan tea.Msg, 10)
		startChatWSReader(wsConn, wsMsgCh)

		// Step 4: start chat with selected role
		chatModel := initialModel(client, wsConn, picker.selectedRole.name, wsMsgCh)
		chatProg := tea.NewProgram(chatModel, tea.WithAltScreen())
		finalChat, chatErr := chatProg.Run()
		if chatErr != nil {
			_ = chatModel.wsConn.Close()
			return fmt.Errorf("failed to run chat TUI: %w", chatErr)
		}
		if m, ok := finalChat.(*Model); ok && m.BackToRole {
			_ = m.wsConn.Close()
			continue
		}
		if m, ok := finalChat.(*Model); ok {
			_ = m.wsConn.Close()
		} else {
			_ = wsConn.Close()
		}
		return nil
	}
}

func startChatWSReader(conn *api.ChatWSConn, ch chan<- tea.Msg) {
	go func() {
		defer func() { recover() }()
		for {
			resp, readErr := conn.ReadResponse()
			if readErr != nil {
				sendNonBlocking(ch, chatErrorMsg{err: readErr, disconnected: true})
				return
			}
			if resp.Error != "" {
				sendNonBlocking(ch, chatErrorMsg{err: fmt.Errorf("%s", resp.Error), requestID: resp.RequestID})
				continue
			}
			sendNonBlocking(ch, botResponseMsg{requestID: resp.RequestID, response: resp.Response})
		}
	}()
}

func sendNonBlocking(ch chan<- tea.Msg, msg tea.Msg) {
	select {
	case ch <- msg:
	default:
	}
}
