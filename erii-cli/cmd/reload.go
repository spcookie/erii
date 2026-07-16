package cmd

import (
	"fmt"
	"strings"

	"erii-cli/internal/config/tree"

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
		if err := tree.Reload(); err != nil {
			fmt.Print(renderReloadError(err))
			return err
		}
		return nil
	},
}

func renderReloadError(err error) string {
	var b strings.Builder
	b.WriteString(pluginRefreshTitleStyle.Render("Reload result"))
	b.WriteString(" ")
	b.WriteString(pluginRefreshStatusBadge("error"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Error"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshRow("Message", err.Error()))
	return b.String()
}

func init() {
	rootCmd.AddCommand(reloadCmd)
}
