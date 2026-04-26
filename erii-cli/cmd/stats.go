package cmd

import (
	"erii-cli/internal/tui/stats"

	"github.com/spf13/cobra"
)

var statsCmd = &cobra.Command{
	Use:   "stats",
	Short: "View bot and group statistics",
	RunE: func(cmd *cobra.Command, args []string) error {
		return stats.Start()
	},
}

func init() {
	rootCmd.AddCommand(statsCmd)
}
