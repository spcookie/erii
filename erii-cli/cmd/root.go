package cmd

import (
	"os"

	"erii-cli/internal/path"
	"erii-cli/internal/version"

	"github.com/spf13/cobra"
)

var (
	confDirFlag     string
	confMetaDirFlag string
	pluginDirFlag   string
	logsPathFlag    string
)

var rootCmd = &cobra.Command{
	Use:     "erii",
	Short:   "Erii CLI - Configuration and management tool for Erii",
	Long:    `A command-line tool with TUI for managing Erii bot configuration.`,
	Version: version.Version,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		path.InitPaths(confDirFlag, confMetaDirFlag, pluginDirFlag)
		return nil
	},
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.PersistentFlags().StringVar(&confDirFlag, "conf-dir", "", "Path to conf directory (default: auto-detected)")
	rootCmd.PersistentFlags().StringVar(&confMetaDirFlag, "meta-conf-dir", "", "Path to .conf meta directory (default: auto-detected)")
	rootCmd.PersistentFlags().StringVar(&pluginDirFlag, "plugin-dir", "", "Path to plugins directory (default: auto-detected)")
	rootCmd.PersistentFlags().StringVar(&logsPathFlag, "logs-path", "", "Path to logs directory (default: ./logs)")
}
