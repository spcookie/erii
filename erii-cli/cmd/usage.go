package cmd

import (
	"erii-cli/internal/tui/usage"

	"github.com/spf13/cobra"
)

var usageCmd = &cobra.Command{
	Use:   "usage",
	Short: "View token usage statistics",
	RunE: func(cmd *cobra.Command, args []string) error {
		return usage.Start()
	},
}

func init() {
	rootCmd.AddCommand(usageCmd)
}
