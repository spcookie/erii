package cmd

import (
	"fmt"
	"strings"

	"erii-cli/internal/api"
	uioutput "erii-cli/internal/ui/output"

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
			fmt.Fprint(cmd.OutOrStdout(), renderRefreshError("Connection", err))
			return nil
		}

		result, err := client.RefreshConfig()
		if err != nil {
			fmt.Fprint(cmd.OutOrStdout(), renderRefreshError("Backend", err))
			return nil
		}

		fmt.Fprint(cmd.OutOrStdout(), renderRefreshSuccess(result))
		return nil
	},
}

func renderRefreshSuccess(result *api.ConfigRefreshResponse) string {
	var b strings.Builder
	status := "ok"
	if result != nil && result.Status != "" {
		status = result.Status
	}
	b.WriteString(uioutput.Title("Config refresh"))
	b.WriteString(" ")
	b.WriteString(uioutput.Status(status))
	if result != nil && result.HTTPStatus >= 400 {
		b.WriteString(" ")
		b.WriteString(uioutput.HTTPStatus(result.HTTPStatus))
	}
	b.WriteString("\n")
	if result != nil && result.Message != "" {
		b.WriteString(uioutput.Row("Message", result.Message))
	}
	b.WriteString("\n")
	b.WriteString(uioutput.Section("Summary"))
	b.WriteString("\n")
	b.WriteString(uioutput.Row("Status", "refreshed"))
	b.WriteString(uioutput.Row("Target", "backend config cache"))
	if result == nil {
		return b.String()
	}

	b.WriteString("\n")
	b.WriteString(uioutput.Section("Reloaded"))
	b.WriteString("\n")
	b.WriteString(uioutput.Row("Config", fmt.Sprintf("%t", result.Reloaded.Config)))
	b.WriteString(uioutput.Row("Roles", fmt.Sprintf("%d", result.Reloaded.Roles)))
	b.WriteString(uioutput.Row("Rules", fmt.Sprintf("%d", result.Reloaded.Rules)))
	b.WriteString(uioutput.Row("MCP", fmt.Sprintf("%d", result.Reloaded.MCP)))

	b.WriteString("\n")
	b.WriteString(uioutput.Section("Bots"))
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
		value += " " + uioutput.Muted(strings.Join(items, ", "))
	}
	return uioutput.Row(label, value)
}

func renderRefreshError(scope string, err error) string {
	return uioutput.ErrorResult("Config refresh", scope, err)
}

func init() {
	rootCmd.AddCommand(refreshCmd)
}
