package cmd

import (
	"fmt"

	"erii-cli/internal/api"

	"github.com/spf13/cobra"
)

var refreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Refresh backend config cache",
	Long:  `Send a request to the Erii backend to clear and reload the plugin configuration cache.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, err := api.NewClientFromIPC()
		if err != nil {
			return fmt.Errorf("failed to connect to Erii: %w", err)
		}
		if err := client.RefreshConfig(); err != nil {
			return fmt.Errorf("failed to refresh config: %w", err)
		}

		fmt.Println("Config cache refreshed successfully.")
		return nil
	},
}

func init() {
	rootCmd.AddCommand(refreshCmd)
}
