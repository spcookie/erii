package cmd

import (
	"fmt"

	"erii-cli/internal/config/tree"
	uioutput "erii-cli/internal/ui/output"

	"github.com/spf13/cobra"
)

var reloadCmd = &cobra.Command{
	Use:   "reload",
	Short: "Reload plugin configurations and metadata schemas",
	Long: `Re-initializes plugin configuration files using deep JSON merge logic.
Existing user-modified values are preserved. New keys from updated
plugin archives are added. Metadata schemas are reloaded afterward.`,
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := tree.Reload(cmd.OutOrStdout()); err != nil {
			fmt.Fprint(cmd.OutOrStdout(), renderReloadError(err))
			return err
		}
		return nil
	},
}

func renderReloadError(err error) string {
	return uioutput.ErrorResult("Reload result", "", err)
}

func init() {
	rootCmd.AddCommand(reloadCmd)
}
