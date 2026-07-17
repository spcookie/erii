package cmd

import (
	"fmt"
	"sort"
	"strings"

	"erii-cli/internal/api"
	uioutput "erii-cli/internal/ui/output"
	"github.com/spf13/cobra"
)

var pluginCmd = &cobra.Command{
	Use:   "plugin",
	Short: "Manage Erii plugins",
}

var pluginRefreshCmd = &cobra.Command{
	Use:   "refresh [plugin-id]",
	Short: "Refresh plugin lifecycle",
	Long:  "Unload and reload all plugins, or refresh a single plugin when a plugin id is provided.",
	Args:  cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		pluginID := ""
		if len(args) > 0 {
			pluginID = args[0]
		}

		client, err := api.NewClientFromIPC()
		if err != nil {
			fmt.Fprint(cmd.OutOrStdout(), uioutput.ErrorResult("Plugin refresh", "Connection", err))
			return nil
		}

		result, err := client.RefreshPlugins(pluginID)
		if err != nil {
			fmt.Fprint(cmd.OutOrStdout(), uioutput.ErrorResult("Plugin refresh", "Backend", err))
			return nil
		}

		fmt.Fprint(cmd.OutOrStdout(), renderPluginRefreshResult(result))
		return nil
	},
}

func printPluginRefreshResult(result *api.PluginRefreshResponse) {
	fmt.Print(renderPluginRefreshResult(result))
}

func renderPluginRefreshResult(result *api.PluginRefreshResponse) string {
	if result == nil {
		return uioutput.Title("Plugin refresh") + "\n" + uioutput.Muted("No result returned.") + "\n"
	}

	var b strings.Builder
	status := result.Status
	if status == "" {
		status = "unknown"
	}
	badge := uioutput.Status(status)

	b.WriteString(uioutput.Title("Plugin refresh"))
	b.WriteString("  ")
	b.WriteString(badge)
	if result.HTTPStatus >= 400 {
		b.WriteString(" ")
		b.WriteString(uioutput.HTTPStatus(result.HTTPStatus))
	}
	b.WriteString("\n")

	if result.Message != "" {
		b.WriteString(uioutput.Row("Message", result.Message))
	}
	if result.RequestedPluginID != "" {
		b.WriteString(uioutput.Row("Requested", result.RequestedPluginID))
	}
	b.WriteString("\n")
	b.WriteString(uioutput.Section("Summary"))
	b.WriteString("\n")
	b.WriteString(uioutput.Row("Loaded", fmt.Sprintf("%d extensions", result.LoadedExtensions)))
	b.WriteString(uioutput.Row("Refreshed", fmt.Sprintf("%d plugins", len(result.RefreshedPlugins))))
	b.WriteString(uioutput.Row("Failed", fmt.Sprintf("%d plugins", len(result.FailedPlugins))))

	if len(result.RefreshedPlugins) > 0 {
		b.WriteString("\n")
		b.WriteString(uioutput.Section("Refreshed plugins"))
		b.WriteString("\n")
		for _, line := range wrapCommaList(result.RefreshedPlugins, 76) {
			b.WriteString("  ")
			b.WriteString(uioutput.Plugin(line))
			b.WriteString("\n")
		}
	}

	if len(result.FailedPlugins) > 0 {
		b.WriteString("\n")
		b.WriteString(uioutput.Section("Failed plugins"))
		b.WriteString("\n")
		pluginIDs := make([]string, 0, len(result.FailedPlugins))
		for pluginID := range result.FailedPlugins {
			pluginIDs = append(pluginIDs, pluginID)
		}
		sort.Strings(pluginIDs)
		for _, pluginID := range pluginIDs {
			reason := result.FailedPlugins[pluginID]
			b.WriteString("  ")
			b.WriteString(uioutput.Error(pluginID))
			b.WriteString("\n")
			b.WriteString("    ")
			b.WriteString(uioutput.Muted(reason))
			b.WriteString("\n")
		}
	}

	return strings.TrimRight(b.String(), "\n") + "\n"
}

func pluginRefreshStatusBadge(status string) string {
	return uioutput.Status(status)
}

func pluginRefreshRow(label, value string) string {
	return uioutput.Row(label, value)
}

func wrapCommaList(items []string, maxWidth int) []string {
	if len(items) == 0 {
		return nil
	}
	lines := make([]string, 0, 1)
	current := ""
	for _, item := range items {
		if current == "" {
			current = item
			continue
		}
		next := current + ", " + item
		if len(next) > maxWidth {
			lines = append(lines, current)
			current = item
			continue
		}
		current = next
	}
	if current != "" {
		lines = append(lines, current)
	}
	return lines
}

func init() {
	pluginCmd.AddCommand(pluginRefreshCmd)
	rootCmd.AddCommand(pluginCmd)
}
