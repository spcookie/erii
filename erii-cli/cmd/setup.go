package cmd

import (
	"erii-cli/internal/tui/setup"

	"github.com/spf13/cobra"
)

var setupCmd = &cobra.Command{
	Use:   "setup",
	Short: "Run the initial setup wizard",
	Long:  `Interactive setup wizard to configure LLM providers, tools, bot connection, and groups.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return setup.Start()
	},
}

func init() {
	rootCmd.AddCommand(setupCmd)
}
