package manage

import (
	"erii-cli/internal/ipc"
	"fmt"

	"github.com/charmbracelet/bubbletea"
)

func Start() error {
	config, err := ipc.ReadConfig()
	if err != nil {
		return err
	}

	port := config.Port
	if port == 0 {
		port = 8080
	}

	api := NewAPI(port, config.Username, config.Password)
	initial := NewBotListModel(api)
	root := NewRootModel(initial)

	p := tea.NewProgram(root, tea.WithAltScreen())
	if _, err := p.Run(); err != nil {
		return fmt.Errorf("failed to run TUI: %w", err)
	}

	return nil
}
