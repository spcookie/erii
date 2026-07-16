package cmd

import (
	"fmt"
	"sort"
	"strings"

	"erii-cli/internal/api"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/lipgloss"
	"github.com/spf13/cobra"
)

var (
	pluginRefreshTitleStyle = lipgloss.NewStyle().
				Foreground(style.Primary).
				Bold(true)
	pluginRefreshMutedStyle = lipgloss.NewStyle().
				Foreground(style.TextMuted)
	pluginRefreshLabelStyle = lipgloss.NewStyle().
				Foreground(style.TextMuted).
				Width(14)
	pluginRefreshValueStyle = lipgloss.NewStyle().
				Foreground(style.Text).
				Bold(true)
	pluginRefreshSectionStyle = lipgloss.NewStyle().
					Foreground(style.Secondary).
					Bold(true)
	pluginRefreshOkBadge = lipgloss.NewStyle().
				Foreground(style.Background).
				Background(style.Success).
				Bold(true).
				Padding(0, 1)
	pluginRefreshErrorBadge = lipgloss.NewStyle().
				Foreground(style.Background).
				Background(style.Error).
				Bold(true).
				Padding(0, 1)
	pluginRefreshWarningBadge = lipgloss.NewStyle().
					Foreground(style.Background).
					Background(style.Warning).
					Bold(true).
					Padding(0, 1)
	pluginRefreshFailedPluginStyle = lipgloss.NewStyle().
					Foreground(style.Error).
					Bold(true)
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
			fmt.Printf("Plugin refresh error: %v\n", err)
			return nil
		}

		result, err := client.RefreshPlugins(pluginID)
		if err != nil {
			fmt.Printf("Plugin refresh error: %v\n", err)
			return nil
		}

		printPluginRefreshResult(result)
		return nil
	},
}

func printPluginRefreshResult(result *api.PluginRefreshResponse) {
	fmt.Print(renderPluginRefreshResult(result))
}

func renderPluginRefreshResult(result *api.PluginRefreshResponse) string {
	if result == nil {
		return pluginRefreshTitleStyle.Render("Plugin refresh") + "\n" +
			pluginRefreshMutedStyle.Render("No result returned.") + "\n"
	}

	var b strings.Builder
	status := result.Status
	if status == "" {
		status = "unknown"
	}
	badge := pluginRefreshStatusBadge(status)

	b.WriteString(pluginRefreshTitleStyle.Render("Plugin refresh"))
	b.WriteString("  ")
	b.WriteString(badge)
	if result.HTTPStatus >= 400 {
		b.WriteString(" ")
		b.WriteString(pluginRefreshMutedStyle.Render(fmt.Sprintf("HTTP %d", result.HTTPStatus)))
	}
	b.WriteString("\n")

	if result.Message != "" {
		b.WriteString(pluginRefreshRow("Message", result.Message))
	}
	if result.RequestedPluginID != "" {
		b.WriteString(pluginRefreshRow("Requested", result.RequestedPluginID))
	}
	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Summary"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshRow("Loaded", fmt.Sprintf("%d extensions", result.LoadedExtensions)))
	b.WriteString(pluginRefreshRow("Refreshed", fmt.Sprintf("%d plugins", len(result.RefreshedPlugins))))
	b.WriteString(pluginRefreshRow("Failed", fmt.Sprintf("%d plugins", len(result.FailedPlugins))))

	if len(result.RefreshedPlugins) > 0 {
		b.WriteString("\n")
		b.WriteString(pluginRefreshSectionStyle.Render("Refreshed plugins"))
		b.WriteString("\n")
		for _, line := range wrapCommaList(result.RefreshedPlugins, 76) {
			b.WriteString("  ")
			b.WriteString(pluginRefreshValueStyle.Render(line))
			b.WriteString("\n")
		}
	}

	if len(result.FailedPlugins) > 0 {
		b.WriteString("\n")
		b.WriteString(pluginRefreshSectionStyle.Render("Failed plugins"))
		b.WriteString("\n")
		pluginIDs := make([]string, 0, len(result.FailedPlugins))
		for pluginID := range result.FailedPlugins {
			pluginIDs = append(pluginIDs, pluginID)
		}
		sort.Strings(pluginIDs)
		for _, pluginID := range pluginIDs {
			reason := result.FailedPlugins[pluginID]
			b.WriteString("  ")
			b.WriteString(pluginRefreshFailedPluginStyle.Render(pluginID))
			b.WriteString("\n")
			b.WriteString("    ")
			b.WriteString(pluginRefreshMutedStyle.Render(reason))
			b.WriteString("\n")
		}
	}

	return strings.TrimRight(b.String(), "\n") + "\n"
}

func pluginRefreshStatusBadge(status string) string {
	label := strings.ToUpper(status)
	switch strings.ToLower(status) {
	case "ok", "success":
		return pluginRefreshOkBadge.Render(label)
	case "error", "failed":
		return pluginRefreshErrorBadge.Render(label)
	default:
		return pluginRefreshWarningBadge.Render(label)
	}
}

func pluginRefreshRow(label, value string) string {
	return "  " + pluginRefreshLabelStyle.Render(label) + pluginRefreshValueStyle.Render(value) + "\n"
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
