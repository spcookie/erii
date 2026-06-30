package cmd

import (
	"erii-cli/internal/tui/logview"
	"path/filepath"

	"github.com/spf13/cobra"
)

var logLines int

var logCmd = &cobra.Command{
	Use:   "log [error]",
	Short: "View log files in TUI (default: info.log)",
	Long:  `View log files in a scrollable TUI. Defaults to info.log. Use "log error" to view error.log.`,
	Args:  cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		logFile := "info.log"
		if len(args) > 0 && args[0] == "error" {
			logFile = "error.log"
		}

		logsPath := logsPathFlag
		if logsPath == "" {
			logsPath = "logs"
		}

		logPath := filepath.Join(logsPath, logFile)
		return logview.Start(logPath, logLines)
	},
}

func init() {
	logCmd.Flags().IntVarP(&logLines, "lines", "n", 500, "Number of lines to show")
	rootCmd.AddCommand(logCmd)
}
