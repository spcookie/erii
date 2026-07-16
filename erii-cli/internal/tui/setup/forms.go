package setup

import (
	"fmt"
	"strconv"
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
						return validationError(d, "API Key is required")
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
						return validationError(d, "Base URL is required")
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
						return validationError(d, "Model is required")
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
						return validationError(d, "Lite Model is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Flash Model").
				Value(&d.FlashModel).
				Placeholder(placeholderOrValue(d.FlashModel)).
				Validate(func(s string) error {
					if s == "" {
						return validationError(d, "Flash Model is required")
					}
					return nil
				}),
			huh.NewInput().
				Title("Pro Model").
				Value(&d.ProModel).
				Placeholder(placeholderOrValue(d.ProModel)).
				Validate(func(s string) error {
					if s == "" {
						return validationError(d, "Pro Model is required")
					}
					return nil
				}),
		).Title("Models").
			WithShowHelp(false).
			WithHideFunc(func() bool { return d.ModelMode != "separate" }),
	)
}

func buildLLMAdvancedAskForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewConfirm().
				Title("Configure advanced LLM settings?").
				Description("Provider paths, model capabilities, and usage pricing. Defaults will be used if disabled.").
				Affirmative("Yes").
				Negative("No").
				Value(&d.AdvancedLLM),
		).Title("Advanced").WithShowHelp(false),
	)
}

func buildLLMProviderSettingsForm(d *SetupData, api string) *huh.Form {
	if d.LLMSettings == nil {
		d.LLMSettings = defaultSettingsForAPI(api)
	}
	settings := defaultSettingsForAPI(api)
	for k, v := range d.LLMSettings {
		settings[k] = v
	}
	d.LLMSettings = settings

	fields := make([]huh.Field, 0, len(settings))
	for _, key := range settingsKeysForAPI(api) {
		k := key
		value := settings[k]
		fields = append(fields, huh.NewInput().
			Title(k).
			Value(&value).
			Placeholder(placeholderOrValue(d.LLMSettings[k])).
			Validate(func(s string) error {
				if s == "" {
					return validationError(d, "%s is required", k)
				}
				d.LLMSettings[k] = s
				return nil
			}))
	}
	return wrapForm(huh.NewGroup(fields...).Title("Provider Settings").WithShowHelp(false))
}

func buildLLMCapabilityForm(label string, c *LLMCapabilitySet) *huh.Form {
	return wrapForm(capabilityGroup(label+" Capabilities", c))
}

func capabilityGroup(title string, c *LLMCapabilitySet) *huh.Group {
	return huh.NewGroup(
		huh.NewConfirm().Title("Completion").Value(&c.Completion),
		huh.NewConfirm().Title("Prompt Caching").Value(&c.PromptCaching),
		huh.NewConfirm().Title("Temperature").Value(&c.Temperature),
		huh.NewConfirm().Title("Tools").Value(&c.Tools),
		huh.NewConfirm().Title("Tool Choice").Value(&c.ToolChoice),
		huh.NewConfirm().Title("Multiple Choices").Value(&c.MultipleChoices),
		huh.NewConfirm().Title("Thinking").Value(&c.Thinking),
		huh.NewConfirm().Title("Vision Image").Value(&c.VisionImage),
	).Title(title).WithShowHelp(false)
}

func buildLLMUsagePricingForm(d *SetupData) *huh.Form {
	return wrapForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Price Unit").
				Value(&d.LLMUsagePricing.PriceUnit).
				Placeholder(placeholderOrValue(d.LLMUsagePricing.PriceUnit)).
				Validate(func(s string) error {
					if s == "" {
						return validationError(d, "Price Unit is required")
					}
					return nil
				}),
		).Title("Currency").WithShowHelp(false),
		pricingGroup(d, "Lite Pricing", &d.LLMUsagePricing.Lite),
		pricingGroup(d, "Flash Pricing", &d.LLMUsagePricing.Flash),
		pricingGroup(d, "Pro Pricing", &d.LLMUsagePricing.Pro),
	)
}

func pricingGroup(d *SetupData, title string, p *LLMPricingTierDefaults) *huh.Group {
	inputHit := fmt.Sprintf("%g", p.InputCacheHit)
	inputMiss := fmt.Sprintf("%g", p.InputCacheMiss)
	output := fmt.Sprintf("%g", p.Output)
	return huh.NewGroup(
		huh.NewInput().
			Title("Input Cache Hit").
			Value(&inputHit).
			Validate(func(s string) error {
				v, err := parseRequiredFloat(d, "Input Cache Hit", s)
				if err != nil {
					return err
				}
				p.InputCacheHit = v
				return nil
			}).
			Key(title+"-input-hit"),
		huh.NewInput().
			Title("Input Cache Miss").
			Value(&inputMiss).
			Validate(func(s string) error {
				v, err := parseRequiredFloat(d, "Input Cache Miss", s)
				if err != nil {
					return err
				}
				p.InputCacheMiss = v
				return nil
			}).
			Key(title+"-input-miss"),
		huh.NewInput().
			Title("Output").
			Value(&output).
			Validate(func(s string) error {
				v, err := parseRequiredFloat(d, "Output", s)
				if err != nil {
					return err
				}
				p.Output = v
				return nil
			}).
			Key(title+"-output"),
	).Title(title).WithShowHelp(false)
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
				Options(huh.NewOption("Disable", false), huh.NewOption("Enable", true)).
				Value(&d.BrowserDownload),
			huh.NewInput().
				Title("Playwright URL").
				Value(&d.PlaywrightURL).
				Placeholder(placeholderOrValue(d.PlaywrightURL)),
			huh.NewInput().
				Title("External Host").
				Value(&d.ExternalHost).
				Placeholder(placeholderOrValue(d.ExternalHost)),
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
						return validationError(d, "WebSocket Address is required")
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
						return validationError(d, "Token is required")
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
				Validate(validateCommaSeparatedNumbers(d, "group ID")),
		).WithShowHelp(false),
	)
}

// splitByCommas splits a string by both English (,) and Chinese (，) commas.
func splitByCommas(s string) []string {
	return strings.Split(strings.ReplaceAll(s, "，", ","), ",")
}

func validateCommaSeparatedNumbers(d *SetupData, label string) func(string) error {
	return func(s string) error {
		if s == "" {
			return nil
		}
		parts := splitByCommas(s)
		for _, p := range parts {
			p = strings.TrimSpace(p)
			if p == "" {
				return validationError(d, "empty %s in list", label)
			}
			for _, r := range p {
				if r < '0' || r > '9' {
					return validationError(d, "invalid %s: %q (must be numeric)", label, p)
				}
			}
		}
		return nil
	}
}

func validateFloat(label string) func(string) error {
	return func(s string) error {
		_, err := parseRequiredFloat(nil, label, s)
		return err
	}
}

func parseRequiredFloat(d *SetupData, label string, s string) (float64, error) {
	if s == "" {
		return 0, validationError(d, "%s is required", label)
	}
	v, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return 0, validationError(d, "%s must be a number", label)
	}
	return v, nil
}

func validationError(d *SetupData, format string, args ...any) error {
	msg := fmt.Sprintf(format, args...)
	if d != nil {
		d.ValidationMessage = msg
	}
	return fmt.Errorf("%s", msg)
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

func defaultSettingsForAPI(api string) map[string]string {
	switch strings.ToLower(api) {
	case "anthropic":
		return map[string]string{
			"api-version": "2023-06-01",
			"messages":    "v1/messages",
			"models":      "v1/models",
		}
	default:
		return map[string]string{
			"chat-completions": "v1/chat/completions",
			"responses-api":    "v1/responses",
			"embeddings":       "v1/embeddings",
			"moderations":      "v1/moderations",
			"models":           "v1/models",
		}
	}
}

func settingsKeysForAPI(api string) []string {
	switch strings.ToLower(api) {
	case "anthropic":
		return []string{"api-version", "messages", "models"}
	default:
		return []string{"chat-completions", "responses-api", "embeddings", "moderations", "models"}
	}
}

func copyStringMap(in map[string]string) map[string]string {
	out := make(map[string]string, len(in))
	for k, v := range in {
		out[k] = v
	}
	return out
}
