package cmd

import (
	"errors"
	"strings"
	"testing"

	"erii-cli/internal/api"
)

func TestPluginRefreshCommandShape(t *testing.T) {
	if pluginCmd.Use != "plugin" {
		t.Fatalf("plugin command use = %q", pluginCmd.Use)
	}
	if pluginRefreshCmd.Use != "refresh [plugin-id]" {
		t.Fatalf("plugin refresh command use = %q", pluginRefreshCmd.Use)
	}
	if err := pluginRefreshCmd.Args(pluginRefreshCmd, []string{"demo"}); err != nil {
		t.Fatalf("one plugin id should be accepted: %v", err)
	}
	if err := pluginRefreshCmd.Args(pluginRefreshCmd, []string{"demo", "extra"}); err == nil {
		t.Fatal("more than one plugin id should be rejected")
	}
}

func TestRenderReloadErrorShowsStyledLayout(t *testing.T) {
	output := renderReloadError(errors.New("metadata reload failed: broken schema"))

	for _, want := range []string{
		"Reload result",
		"ERROR",
		"Error",
		"Message",
		"metadata reload failed: broken schema",
	} {
		if !strings.Contains(output, want) {
			t.Fatalf("rendered output missing %q:\n%s", want, output)
		}
	}
}

func TestRenderRefreshSuccessShowsStyledLayout(t *testing.T) {
	output := renderRefreshSuccess(&api.ConfigRefreshResponse{
		Status:  "ok",
		Message: "config refreshed",
		Reloaded: api.ConfigReloadedSummary{
			Config: true,
			Roles:  3,
			Rules:  7,
			MCP:    2,
		},
		Bots: api.BotRefreshSummary{
			Added:       api.BotRefreshItems{"bot-a"},
			Removed:     api.BotRefreshItems{"bot-b", "bot-c"},
			Reconnected: api.BotRefreshItems{"bot-d", "bot-e", "bot-f"},
			RoleUpdated: api.BotRefreshItems{"bot-g", "bot-h", "bot-i", "bot-j"},
			Failed:      api.BotRefreshItems{"bot-k", "bot-l", "bot-m", "bot-n", "bot-o"},
		},
	})

	for _, want := range []string{
		"Config refresh",
		"OK",
		"config refreshed",
		"Summary",
		"Reloaded",
		"Roles",
		"3",
		"Bots",
		"Reconnected",
		"bot-d",
		"Role updated",
		"5",
		"backend config cache",
	} {
		if !strings.Contains(output, want) {
			t.Fatalf("rendered output missing %q:\n%s", want, output)
		}
	}
}

func TestRenderRefreshErrorShowsStyledLayout(t *testing.T) {
	output := renderRefreshError("Connection", errors.New("cannot connect to Erii service"))

	for _, want := range []string{
		"Config refresh",
		"ERROR",
		"Error",
		"Scope",
		"Connection",
		"cannot connect to Erii service",
	} {
		if !strings.Contains(output, want) {
			t.Fatalf("rendered output missing %q:\n%s", want, output)
		}
	}
}

func TestRenderPluginRefreshResultShowsFailureLayout(t *testing.T) {
	output := renderPluginRefreshResult(&api.PluginRefreshResponse{
		Status:           "error",
		Message:          "plugin refresh completed with failures",
		RefreshedPlugins: []string{"builtin", "chat-heatmap", "seeddream"},
		LoadedExtensions: 16,
		FailedPlugins: map[string]string{
			"official-qq-adapter_GeneratedPassive_default": "Address already in use",
		},
		HTTPStatus: 500,
	})

	for _, want := range []string{
		"Plugin refresh",
		"ERROR",
		"HTTP 500",
		"Summary",
		"16 extensions",
		"3 plugins",
		"Failed plugins",
		"official-qq-adapter_GeneratedPassive_default",
		"Address already in use",
	} {
		if !strings.Contains(output, want) {
			t.Fatalf("rendered output missing %q:\n%s", want, output)
		}
	}
}
