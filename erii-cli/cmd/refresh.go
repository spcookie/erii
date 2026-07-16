package cmd

import (
	"fmt"
	"strings"

	"erii-cli/internal/api"

	"github.com/spf13/cobra"
)

var refreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Refresh backend config cache",
	Long:  `Send a request to the Erii backend to clear and reload the plugin configuration cache.`,
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, err := api.NewClientFromIPC()
		if err != nil {
			fmt.Print(renderRefreshError("Connection", err))
			return nil
		}

		result, err := client.RefreshConfig()
		if err != nil {
			fmt.Print(renderRefreshError("Backend", err))
			return nil
		}

		fmt.Print(renderRefreshSuccess(result))
		return nil
	},
}

func renderRefreshSuccess(result *api.ConfigRefreshResponse) string {
	var b strings.Builder
	status := "ok"
	if result != nil && result.Status != "" {
		status = result.Status
	}
	b.WriteString(pluginRefreshTitleStyle.Render("Config refresh"))
	b.WriteString(" ")
	b.WriteString(pluginRefreshStatusBadge(status))
	if result != nil && result.HTTPStatus >= 400 {
		b.WriteString(" ")
		b.WriteString(pluginRefreshMutedStyle.Render(fmt.Sprintf("HTTP %d", result.HTTPStatus)))
	}
	b.WriteString("\n")
	if result != nil && result.Message != "" {
		b.WriteString(pluginRefreshRow("Message", result.Message))
	}
	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Summary"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshRow("Status", "refreshed"))
	b.WriteString(pluginRefreshRow("Target", "backend config cache"))
	if result == nil {
		return b.String()
	}

	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Reloaded"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshRow("Config", fmt.Sprintf("%t", result.Reloaded.Config)))
	b.WriteString(pluginRefreshRow("Roles", fmt.Sprintf("%d", result.Reloaded.Roles)))
	b.WriteString(pluginRefreshRow("Rules", fmt.Sprintf("%d", result.Reloaded.Rules)))
	b.WriteString(pluginRefreshRow("MCP", fmt.Sprintf("%d", result.Reloaded.MCP)))

	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Bots"))
	b.WriteString("\n")
	b.WriteString(renderBotRefreshItems("Added", result.Bots.Added))
	b.WriteString(renderBotRefreshItems("Removed", result.Bots.Removed))
	b.WriteString(renderBotRefreshItems("Reconnected", result.Bots.Reconnected))
	b.WriteString(renderBotRefreshItems("Role updated", result.Bots.RoleUpdated))
	b.WriteString(renderBotRefreshItems("Failed", result.Bots.Failed))
	return b.String()
}

func renderBotRefreshItems(label string, items api.BotRefreshItems) string {
	value := fmt.Sprintf("%d", items.Count())
	if len(items) > 0 && strings.TrimSpace(strings.Join(items, "")) != "" {
		value += " " + pluginRefreshMutedStyle.Render(strings.Join(items, ", "))
	}
	return pluginRefreshRow(label, value)
}

func renderRefreshError(scope string, err error) string {
	var b strings.Builder
	b.WriteString(pluginRefreshTitleStyle.Render("Config refresh"))
	b.WriteString(" ")
	b.WriteString(pluginRefreshStatusBadge("error"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshSectionStyle.Render("Error"))
	b.WriteString("\n")
	b.WriteString(pluginRefreshRow("Scope", scope))
	b.WriteString(pluginRefreshRow("Message", err.Error()))
	return b.String()
}

func init() {
	rootCmd.AddCommand(refreshCmd)
}
