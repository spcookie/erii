package cmd

import (
	"fmt"

	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/spf13/cobra"
)

var refreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Refresh backend config cache",
	Long:  `Send a request to the Erii backend to clear and reload the plugin configuration cache.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		const maxRetries = 3
		for attempt := 0; attempt < maxRetries; attempt++ {
			client, err := api.NewClientFromIPC()
			if err != nil {
				model := components.NewConnectionErrorModel(err)
				p := tea.NewProgram(model, tea.WithAltScreen())
				if _, runErr := p.Run(); runErr != nil {
					return fmt.Errorf("failed to run TUI: %w", runErr)
				}
				if !model.ShouldRetry() {
					return nil
				}
				continue
			}

			if err := client.RefreshConfig(); err != nil {
				model := components.NewConnectionErrorModel(err)
				p := tea.NewProgram(model, tea.WithAltScreen())
				if _, runErr := p.Run(); runErr != nil {
					return fmt.Errorf("failed to run TUI: %w", runErr)
				}
				if !model.ShouldRetry() {
					return nil
				}
				continue
			}

			fmt.Println("Config cache refreshed successfully.")
			return nil
		}
		return nil
	},
}

func init() {
	rootCmd.AddCommand(refreshCmd)
}
