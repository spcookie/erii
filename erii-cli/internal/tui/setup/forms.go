package setup

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/huh"
)

// wrapForm creates a huh.Form with the unified theme and help disabled.
func wrapForm(groups ...*huh.Group) *huh.Form {
	return huh.NewForm(groups...).WithTheme(huhTheme())
}

func buildLLMForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewInput().
				Title("API Key").
				Value(&d.APIKey).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter API key (leave empty for env var)").
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("API Key is required")
					}
					return nil
				}),
		).Title("Authentication").WithShowHelp(false),
		huh.NewGroup(
			huh.NewInput().
				Title("Base URL").
				Value(&d.BaseURL).
				Placeholder(placeholderOrValue(d.BaseURL)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Base URL is required")
					}
					return nil
				}),
			huh.NewSelect[string]().
				Title("Model configuration mode").
				Options(
					huh.NewOption("Configure separately (lite / flash / pro)", "separate"),
					huh.NewOption("Use one model for all tiers", "all"),
				).
				Value(&d.ModelMode),
		).Title("Endpoint").WithShowHelp(false),
		huh.NewGroup(
			huh.NewInput().
				Title("Model (all tiers)").
				Value(&d.AllModel).
				Placeholder(placeholderOrValue(d.AllModel)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Model is required")
					}
					return nil
				}),
		).Title("Models").
			WithShowHelp(false).
			WithHideFunc(func() bool { return d.ModelMode != "all" }),

		huh.NewGroup(
			huh.NewInput().
				Title("Lite Model").
				Value(&d.LiteModel).
				Placeholder(placeholderOrValue(d.LiteModel)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Lite Model is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Flash Model").
				Value(&d.FlashModel).
				Placeholder(placeholderOrValue(d.FlashModel)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Flash Model is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Pro Model").
				Value(&d.ProModel).
				Placeholder(placeholderOrValue(d.ProModel)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Pro Model is required")
					}
					return nil
				}),
		).Title("Models").
			WithShowHelp(false).
			WithHideFunc(func() bool { return d.ModelMode != "separate" }),
	)
}

func buildEmbeddingForm(d *SetupData) *huh.Form {
	if d.EmbeddingProvider == "" && len(d.ToolProviders.Embedding) > 0 {
		d.EmbeddingProvider = d.ToolProviders.Embedding[0].Name
	}
	if d.EmbeddingURL == "" {
		d.EmbeddingURL = defaultToolURL(d.ToolProviders.Embedding, d.EmbeddingProvider)
	}
	if d.EmbeddingModel == "" {
		d.EmbeddingModel = defaultToolModel(d.ToolProviders.Embedding, d.EmbeddingProvider)
	}
	return wrapForm(
		huh.NewGroup(
			huh.NewSelect[string]().
				Title("Provider").
				Options(buildToolProviderOptions(d.ToolProviders.Embedding)...).
				Value(&d.EmbeddingProvider),
			huh.NewInput().
				Title("API Key").
				Value(&d.EmbeddingAPIKey).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter API key"),
			huh.NewInput().
				Title("URL").
				Value(&d.EmbeddingURL).
				Placeholder(placeholderOrValue(d.EmbeddingURL)),
			huh.NewInput().
				Title("Model").
				Value(&d.EmbeddingModel).
				Placeholder(placeholderOrValue(d.EmbeddingModel)),
		).WithShowHelp(false),
	)
}

func buildSearchForm(d *SetupData) *huh.Form {
	if d.SearchProvider == "" && len(d.ToolProviders.Search) > 0 {
		d.SearchProvider = d.ToolProviders.Search[0].Name
	}
	if d.SearchURL == "" {
		d.SearchURL = defaultToolURL(d.ToolProviders.Search, d.SearchProvider)
	}
	return wrapForm(
		huh.NewGroup(
			huh.NewSelect[string]().
				Title("Provider").
				Options(buildToolProviderOptions(d.ToolProviders.Search)...).
				Value(&d.SearchProvider),
			huh.NewInput().
				Title("API Key").
				Value(&d.SearchAPIKey).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter API key"),
			huh.NewInput().
				Title("URL").
				Value(&d.SearchURL).
				Placeholder(placeholderOrValue(d.SearchURL)),
		).WithShowHelp(false),
	)
}

func buildVisionForm(d *SetupData) *huh.Form {
	if d.VisionProvider == "" && len(d.ToolProviders.Vision) > 0 {
		d.VisionProvider = d.ToolProviders.Vision[0].Name
	}
	if d.VisionURL == "" {
		d.VisionURL = defaultToolURL(d.ToolProviders.Vision, d.VisionProvider)
	}
	return wrapForm(
		huh.NewGroup(
			huh.NewSelect[string]().
				Title("Provider").
				Options(buildToolProviderOptions(d.ToolProviders.Vision)...).
				Value(&d.VisionProvider),
			huh.NewInput().
				Title("API Key").
				Value(&d.VisionAPIKey).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter API key"),
			huh.NewInput().
				Title("URL").
				Value(&d.VisionURL).
				Placeholder(placeholderOrValue(d.VisionURL)),
		).WithShowHelp(false),
	)
}

func buildBrowserForm(d *SetupData) *huh.Form {
	if d.PlaywrightURL == "" && len(d.ToolProviders.Browser) > 0 {
		d.PlaywrightURL = d.ToolProviders.Browser[0].URL
	}
	return wrapForm(
		huh.NewGroup(
			huh.NewSelect[bool]().
				Title("Download").
				Options(huh.NewOption("Enable", true), huh.NewOption("Disable", false)).
				Value(&d.BrowserDownload),
			huh.NewInput().
				Title("Playwright URL").
				Value(&d.PlaywrightURL).
				Placeholder(placeholderOrValue(d.PlaywrightURL)),
			huh.NewInput().
				Title("Status Host").
				Value(&d.StatusHost).
				Placeholder(placeholderOrValue(d.StatusHost)),
		).WithShowHelp(false),
	)
}

func buildProxyForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewInput().
				Title("HTTP Proxy").
				Value(&d.HTTPProxy).
				Placeholder(placeholderOrValue(d.HTTPProxy)),
			huh.NewInput().
				Title("SOCKS Proxy").
				Value(&d.SOCKSProxy).
				Placeholder(placeholderOrValue(d.SOCKSProxy)),
		).WithShowHelp(false),
	)
}

func buildBotForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewInput().
				Title("WebSocket Address").
				Value(&d.BotWS).
				Placeholder(placeholderOrValue(d.BotWS)).
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("WebSocket Address is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Token").
				Value(&d.BotToken).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter NapCat token").
				Validate(func(s string) error {
					if s == "" {
						return fmt.Errorf("Token is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Admins (comma-separated, optional)").
				Value(&d.BotAdmins).
				Placeholder(placeholderOrValue(d.BotAdmins)),
		).WithShowHelp(false),
	)
}

func buildGroupsForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Enabled Groups (comma-separated, optional)").
				Value(&d.EnableGroups).
				Placeholder(placeholderOrValue(d.EnableGroups)).
				Validate(validateCommaSeparatedNumbers("group ID")),
			huh.NewInput().
				Title("Message Redirect Map (comma-separated, optional)").
				Value(&d.MessageRedirectMap).
				Placeholder(placeholderOrValue(d.MessageRedirectMap)).
				Validate(validateMessageRedirectMap()),
			huh.NewInput().
				Title("Debug Group ID").
				Value(&d.DebugGroupID).
				Placeholder(placeholderOrValue(d.DebugGroupID)).
				Validate(validateOptionalNumber("debug group ID")),
		).WithShowHelp(false),
	)
}

// splitByCommas splits a string by both English (,) and Chinese (，) commas.
func splitByCommas(s string) []string {
	return strings.Split(strings.ReplaceAll(s, "，", ","), ",")
}

func validateCommaSeparatedNumbers(label string) func(string) error {
	return func(s string) error {
		if s == "" {
			return nil
		}
		parts := splitByCommas(s)
		for _, p := range parts {
			p = strings.TrimSpace(p)
			if p == "" {
				return fmt.Errorf("empty %s in list", label)
			}
			for _, r := range p {
				if r < '0' || r > '9' {
					return fmt.Errorf("invalid %s: %q (must be numeric)", label, p)
				}
			}
		}
		return nil
	}
}

func validateMessageRedirectMap() func(string) error {
	return func(s string) error {
		if s == "" {
			return nil
		}
		parts := splitByCommas(s)
		for _, p := range parts {
			p = strings.TrimSpace(p)
			if p == "" {
				return fmt.Errorf("empty redirect pair in list")
			}
			sub := strings.Split(p, ":")
			if len(sub) != 2 {
				return fmt.Errorf("invalid redirect pair: %q (expected source:target)", p)
			}
			for _, num := range sub {
				num = strings.TrimSpace(num)
				for _, r := range num {
					if r < '0' || r > '9' {
						return fmt.Errorf("invalid group ID in pair: %q (must be numeric)", num)
					}
				}
			}
		}
		return nil
	}
}

func validateOptionalNumber(label string) func(string) error {
	return func(s string) error {
		if s == "" {
			return nil
		}
		for _, r := range s {
			if r < '0' || r > '9' {
				return fmt.Errorf("invalid %s: %q (must be numeric)", label, s)
			}
		}
		return nil
	}
}

// ---- Helpers ----

func buildToolProviderOptions(providers []ToolProvider) []huh.Option[string] {
	opts := make([]huh.Option[string], len(providers))
	for i, tp := range providers {
		opts[i] = huh.NewOption(fmt.Sprintf("%s  |  %s", tp.Name, tp.URL), tp.Name)
	}
	return opts
}

func defaultToolURL(providers []ToolProvider, selected string) string {
	for _, tp := range providers {
		if tp.Name == selected {
			return tp.URL
		}
	}
	if len(providers) > 0 {
		return providers[0].URL
	}
	return ""
}

func defaultToolModel(providers []ToolProvider, selected string) string {
	for _, tp := range providers {
		if tp.Name == selected {
			return tp.Model
		}
	}
	if len(providers) > 0 {
		return providers[0].Model
	}
	return ""
}

func placeholderOrValue(v string) string {
	if v == "" {
		return "(empty)"
	}
	return v
}
