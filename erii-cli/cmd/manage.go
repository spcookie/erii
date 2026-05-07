package cmd

import (
	"erii-cli/internal/tui/manage"

	"github.com/spf13/cobra"
)

var manageCmd = &cobra.Command{
	Use:   "manage",
	Short: "Manage bot data (facts, profiles, memes, vocabulary, summaries)",
	RunE: func(cmd *cobra.Command, args []string) error {
		return manage.Start()
	},
}

func init() {
	rootCmd.AddCommand(manageCmd)
}
