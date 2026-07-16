package setup

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestSetupJSONSupportsProviderPresetsAndAdvancedDefaults(t *testing.T) {
	data, err := os.ReadFile(filepath.Join("..", "..", "..", ".conf", "setup.json"))
	if err != nil {
		t.Fatalf("cannot read setup.json: %v", err)
	}

	var sf SetupFile
	if err := json.Unmarshal(data, &sf); err != nil {
		t.Fatalf("cannot parse setup.json: %v", err)
	}

	if len(sf.Providers) < 4 {
		t.Fatalf("expected at least 4 provider presets, got %d", len(sf.Providers))
	}

	foundDeepSeek := false
	for _, p := range sf.Providers {
		if p.Key == "deepseek" {
			foundDeepSeek = true
			if p.API != "openai" {
				t.Fatalf("deepseek api = %q, want openai", p.API)
			}
		}
	}
	if !foundDeepSeek {
		t.Fatal("deepseek provider preset missing")
	}

	deepseek := sf.Defaults.LLM["deepseek"]
	if deepseek.Settings["chat-completions"] == "" {
		t.Fatal("deepseek openai-compatible settings missing")
	}
	if !sf.Defaults.LLMCapability.Default.Tools {
		t.Fatal("llm capability defaults missing")
	}
	if sf.Defaults.LLMUsagePricing.PriceUnit == "" {
		t.Fatal("llm usage pricing defaults missing")
	}
}

func TestOldSetupJSONDefaultsRemainUsable(t *testing.T) {
	sf := SetupFile{
		Providers: []Provider{{Name: "OpenAI", Key: "openai", BaseURL: "https://api.openai.com/v1"}},
		Defaults: DefaultsConfig{
			LLM: map[string]LLMDefaults{
				"openai": {ModelMode: "all", AllModel: "gpt-test"},
			},
		},
	}

	for i := range sf.Providers {
		if sf.Providers[i].API == "" {
			sf.Providers[i].API = providerAPI(sf.Providers[i])
		}
	}
	m := newModel(sf.Providers, sf.Defaults, ToolProvidersConfig{})

	if providerAPI(m.data.Providers[0]) != "openai" {
		t.Fatalf("provider api fallback failed: %q", providerAPI(m.data.Providers[0]))
	}
	if m.data.LLMUsagePricing.PriceUnit != "USD" {
		t.Fatalf("usage pricing default fallback failed: %q", m.data.LLMUsagePricing.PriceUnit)
	}
}
