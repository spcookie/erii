package stats

import (
	"erii-cli/internal/ipc"
	"erii-cli/internal/tui/components"
	"fmt"

	"github.com/charmbracelet/bubbletea"
)

func Start() error {
	for {
		config, err := ipc.ReadConfig()
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

		port := config.Port
		if port == 0 {
			port = 8080
		}

		username := config.Username
		password := config.Password

		api := NewAPI(port, username, password)

		initial := NewBotListModel(api)
		root := NewRootModel(initial)

		p := tea.NewProgram(root, tea.WithAltScreen())
		if _, err := p.Run(); err != nil {
			return fmt.Errorf("failed to run TUI: %w", err)
		}

		return nil
	}
}
