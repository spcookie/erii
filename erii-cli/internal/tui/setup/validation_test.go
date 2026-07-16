package setup

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
)

func updateHelper(m Model, msg tea.Msg) Model {
	result, cmd := m.Update(msg)
	switch v := result.(type) {
	case Model:
		m = v
	case *Model:
		m = *v
	}
	if cmd != nil {
		if next := cmd(); next != nil {
			result, _ = m.Update(next)
			switch v := result.(type) {
			case Model:
				m = v
			case *Model:
				m = *v
			}
		}
	}
	return m
}

func TestFormValidation(t *testing.T) {
	d := &SetupData{ModelMode: "all"}
	form := buildLLMForm(d)

	cmd := form.Init()
	if cmd != nil {
		if msg := cmd(); msg != nil {
			f, _ := form.Update(msg)
			if f2, ok := f.(*huh.Form); ok {
				form = f2
			}
		}
	}

	form.Update(tea.KeyMsg{Type: tea.KeyEnter})
	view := form.View()

	if !strings.Contains(view, "API Key is required") {
		t.Error("View should contain validation error 'API Key is required'")
	}
	if d.ValidationMessage != "API Key is required" {
		t.Errorf("ValidationMessage = %q", d.ValidationMessage)
	}
	rendered := renderFormStep("LLM Configuration", form, d.ValidationMessage, nil)
	if !strings.Contains(rendered, "Validation failed: API Key is required") {
		t.Error("rendered form should contain validation banner")
	}
	if form.State != huh.StateNormal {
		t.Errorf("Form state should be StateNormal after failed validation, got %v", form.State)
	}
}

func TestRenderAdvancedTabs(t *testing.T) {
	caps := defaultLLMCapability()
	rendered := renderFormStep("LLM Configuration", buildLLMCapabilityForm("Lite", &caps.Lite), "", []tabItem{
		{Label: "All"},
		{Label: "Lite", Active: true},
		{Label: "Flash"},
		{Label: "Pro"},
	})
	for _, want := range []string{"All", "Lite", "Flash", "Pro", "Lite Capabilities"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("rendered tabs should contain %q", want)
		}
	}
}

func TestAdvancedTabsSwitchWithPaginatorKeys(t *testing.T) {
	providers := []Provider{
		{Name: "Test", Key: "test", API: "openai", Desc: "Test Provider", BaseURL: "https://test.api"},
	}
	defaults := DefaultsConfig{
		LLM: map[string]LLMDefaults{"test": {ModelMode: "all", AllModel: "test-model"}},
	}

	m := newModel(providers, defaults, ToolProvidersConfig{})
	m.data.SelectedProv = 0
	m.step = StepLLMCapabilityDefault
	m.rebuildCurrentStep()

	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{']'}})
	if m.step != StepLLMCapabilityLite {
		t.Fatalf("] should switch to lite capability tab, got %v", m.step)
	}
	if m.llmTabs.Page != 1 {
		t.Fatalf("llm tab page = %d, want 1", m.llmTabs.Page)
	}
	tabs := m.llmAdvancedTabs()
	if len(tabs) != 4 || tabs[1].Label != "Lite" || !tabs[1].Active {
		t.Fatalf("lite capability tab should be active after switch: %#v", tabs)
	}

	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{'['}})
	if m.step != StepLLMCapabilityDefault {
		t.Fatalf("[ should switch back to all capability tab, got %v", m.step)
	}
}

func TestAdvancedTabsOnlyRenderForCapabilityPages(t *testing.T) {
	m := newModel([]Provider{{Name: "Test", Key: "test", API: "openai"}}, DefaultsConfig{}, ToolProvidersConfig{})

	for _, step := range []Step{StepLLMProviderSettings, StepLLMUsagePricing} {
		m.step = step
		m.rebuildCurrentStep()
		if tabs := m.llmAdvancedTabs(); tabs != nil {
			t.Fatalf("step %v should not render capability tabs: %#v", step, tabs)
		}
		if m.currentKeys().Tab.Enabled() {
			t.Fatalf("step %v should not show switch-tab help", step)
		}
	}
}

func TestFullPipeline_ViewportShowsErrors(t *testing.T) {
	providers := []Provider{
		{Name: "Test", Key: "test", Desc: "Test Provider", BaseURL: "https://test.api"},
	}
	defaults := DefaultsConfig{
		LLM: map[string]LLMDefaults{"test": {ModelMode: "all", AllModel: "test-model"}},
	}

	m := newModel(providers, defaults, ToolProvidersConfig{})
	m = updateHelper(m, tea.WindowSizeMsg{Width: 100, Height: 40})
	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEnter})

	// The full model should render the API Key field after provider selection.
	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEnter})

	vpView := m.vp.View()

	if !strings.Contains(vpView, "API Key") {
		t.Error("Viewport should contain API Key field")
	}
	if m.step != StepLLMConfig {
		t.Errorf("Expected step StepLLMConfig after provider selection, got %v", m.step)
	}
}

func TestViewportOffByOne(t *testing.T) {
	// Verify validation errors are visible at different terminal sizes
	testSizes := []int{24, 30, 40, 60, 100}
	for _, size := range testSizes {
		providers := []Provider{
			{Name: "Test", Key: "test", Desc: "Test Provider", BaseURL: "https://test.api"},
		}
		defaults := DefaultsConfig{
			LLM: map[string]LLMDefaults{"test": {ModelMode: "all", AllModel: "test-model"}},
		}

		m := newModel(providers, defaults, ToolProvidersConfig{})
		m = updateHelper(m, tea.WindowSizeMsg{Width: 100, Height: size})
		m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEnter})
		m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEnter})

		vpView := m.vp.View()
		if !strings.Contains(vpView, "API Key") {
			t.Errorf("At terminal height %d: Viewport should contain API Key field", size)
		}
	}
}
