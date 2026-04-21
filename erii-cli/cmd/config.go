package cmd

import (
	"erii-cli/internal/tui"

	"github.com/spf13/cobra"
)

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "Open TUI to manage Erii configuration",
	RunE: func(cmd *cobra.Command, args []string) error {
		return tui.Start()
	},
}

func init() {
	rootCmd.AddCommand(configCmd)
}
