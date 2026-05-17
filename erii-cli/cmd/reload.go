package cmd

import (
	"erii-cli/internal/config/tree"

	"github.com/spf13/cobra"
)

var reloadCmd = &cobra.Command{
	Use:   "reload",
	Short: "Reload plugin configurations and metadata schemas",
	Long: `Re-initializes plugin configuration files using deep JSON merge logic.
Existing user-modified values are preserved. New keys from updated
plugin archives are added. Metadata schemas are reloaded afterward.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return tree.Reload()
	},
}

func init() {
	rootCmd.AddCommand(reloadCmd)
}
