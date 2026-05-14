package cmd

import (
	"erii-cli/internal/tui"

	"github.com/spf13/cobra"
)

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "Manage Erii configuration (default: open TUI)",
	Long: `Manage Erii configuration via subcommands or TUI.

Subcommands:
  app  - Manage HOCON application.conf
  env  - Manage .env.local environment variables
  plugin - Manage plugin JSON config files

When run without subcommands, opens the interactive TUI editor.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return tui.Start()
	},
}

func init() {
	rootCmd.AddCommand(configCmd)
}
