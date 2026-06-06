package cmd

import (
	"erii-cli/internal/tui/chatui"

	"github.com/spf13/cobra"
)

var chatCmd = &cobra.Command{
	Use:   "chat",
	Short: "Chat with Erii through the CLI",
	Long:  `Open an interactive chat TUI to talk with Erii bot via a mock OneBot bridge.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return chatui.Start()
	},
}

func init() {
	rootCmd.AddCommand(chatCmd)
}
