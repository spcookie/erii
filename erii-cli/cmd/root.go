package cmd

import (
	"os"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "erii",
	Short: "Erii CLI - Configuration and management tool for Erii",
	Long:  `A command-line tool with TUI for managing Erii bot configuration.`,
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}
