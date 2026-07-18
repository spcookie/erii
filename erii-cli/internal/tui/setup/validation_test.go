package setup

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/x/ansi"
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

	if got := strings.Count(view, "API Key is required"); got != 1 {
		t.Errorf("huh form should render exactly one validation error, got %d", got)
	}
	rendered := renderFormStep("LLM Configuration", form, nil)
	if strings.Contains(rendered, "Validation failed:") {
		t.Error("setup should not render a custom validation message")
	}
	if form.State != huh.StateNormal {
		t.Errorf("Form state should be StateNormal after failed validation, got %v", form.State)
	}
}

func TestRenderAdvancedTabs(t *testing.T) {
	caps := defaultLLMCapability()
	pager := newCapabilityPager()
	rendered := renderFormStep("LLM Configuration", buildLLMCapabilityForm("Lite", &caps.Lite, pager), []tabItem{
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
	if strings.Contains(rendered, "Pro  \n\n") {
		t.Error("tabs should not add an empty line before capability fields")
	}
}

func TestCapabilityFormPaginatesVisionFields(t *testing.T) {
	caps := defaultLLMCapability()
	render := func(form *huh.Form) string {
		if cmd := form.Init(); cmd != nil {
			if msg := cmd(); msg != nil {
				updated, _ := form.Update(msg)
				form = updated.(*huh.Form)
			}
		}
		return form.View()
	}
	firstPager := newCapabilityPager()
	lastPager := newCapabilityPager()
	lastPager.Page = capabilityPageCount - 1
	first := render(buildLLMCapabilityForm("All", &caps.Default, firstPager))
	last := render(buildLLMCapabilityForm("All", &caps.Default, lastPager))
	if strings.Contains(first, "Vision Image") {
		t.Fatal("Vision Image should be on the second capability page")
	}
	if strings.Contains(ansi.Strip(first), "Completion\n\n") {
		t.Fatalf("capability title and Yes/No controls should not have a blank line: %s", ansi.Strip(first))
	}
	if !strings.Contains(last, "Vision Image") || !strings.Contains(last, "3/3") {
		t.Fatalf("last capability page is incomplete: %s", last)
	}
	plainLines := strings.Split(ansi.Strip(last), "\n")
	for _, title := range []string{"Thinking", "Vision Image"} {
		for i, line := range plainLines {
			titleColumn := strings.Index(line, title)
			if titleColumn < 0 || i+1 >= len(plainLines) {
				continue
			}
			yesColumn := strings.Index(plainLines[i+1], "Yes")
			if yesColumn != titleColumn {
				t.Fatalf("%s controls start at %d, title starts at %d\n%s", title, yesColumn, titleColumn, ansi.Strip(last))
			}
			break
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

	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyPgDown})
	if m.capabilityPager.Page != 1 {
		t.Fatalf("pgdown should switch to capability page 2, got %d", m.capabilityPager.Page+1)
	}
	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyPgDown})
	if m.capabilityPager.Page != capabilityPageCount-1 {
		t.Fatalf("second pgdown should switch to final capability page, got %d", m.capabilityPager.Page+1)
	}
	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyPgUp})
	if m.capabilityPager.Page != capabilityPageCount-2 {
		t.Fatalf("pgup should switch to previous capability page, got %d", m.capabilityPager.Page+1)
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

func TestCoreAuthenticationDefaultsAndTimeline(t *testing.T) {
	m := newModel(
		[]Provider{{Name: "Test", Key: "test", API: "openai"}},
		DefaultsConfig{},
		ToolProvidersConfig{},
	)
	if m.data.ServerUsername != defaultServerUsername {
		t.Fatalf("server username = %q, want %q", m.data.ServerUsername, defaultServerUsername)
	}
	if m.data.ServerPassword != defaultServerPassword {
		t.Fatalf("server password = %q, want default", m.data.ServerPassword)
	}

	m.step = StepCoreAuth
	m.rebuildCurrentStep()
	if cmd := m.form.Init(); cmd != nil {
		if msg := cmd(); msg != nil {
			updated, _ := m.form.Update(msg)
			m.form = updated.(*huh.Form)
		}
	}
	rendered := ansi.Strip(m.renderContent())
	if !strings.Contains(rendered, "Core Authentication") ||
		!strings.Contains(rendered, "Username") ||
		!strings.Contains(rendered, "Password") {
		t.Fatalf("core authentication form is incomplete:\n%s", rendered)
	}

	timeline := ansi.Strip(renderTimeline(m.step.node(), m.data))
	lines := strings.Split(timeline, "\n")
	if len(lines) != 5 {
		t.Fatalf("timeline has %d lines, want 5:\n%s", len(lines), timeline)
	}
	for _, want := range []string{"LLM Configuration", "Tools & Features", "Default Bot (erii)", "Groups", "Core Authentication", "├─", "└─"} {
		if !strings.Contains(timeline, want) {
			t.Fatalf("timeline missing %q:\n%s", want, timeline)
		}
	}
}

func TestCoreAuthenticationNavigationHistory(t *testing.T) {
	m := newModel(
		[]Provider{{Name: "Test", Key: "test", API: "openai"}},
		DefaultsConfig{},
		ToolProvidersConfig{},
	)
	m.navigateTo(StepGroups)
	m.navigateTo(StepCoreAuth)
	m.navigateTo(StepDone)

	if !m.navigateBack() || m.step != StepCoreAuth {
		t.Fatalf("summary back step = %v, want Core Authentication", m.step)
	}
	if !m.navigateBack() || m.step != StepGroups {
		t.Fatalf("Core Authentication back step = %v, want Groups", m.step)
	}
}

func TestEscUsesActualSetupNavigationHistory(t *testing.T) {
	m := newModel(
		[]Provider{{Name: "Test", Key: "test", API: "openai"}},
		DefaultsConfig{},
		ToolProvidersConfig{},
	)
	m.navigateTo(StepLLMConfig)
	m.navigateTo(StepLLMAdvancedAsk)
	m.navigateTo(StepLLMProviderSettings)
	m.rebuildCurrentStep()

	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEsc})
	if m.step != StepLLMAdvancedAsk {
		t.Fatalf("esc from provider settings returned to %v, want advanced ask", m.step)
	}
	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEsc})
	if m.step != StepLLMConfig {
		t.Fatalf("second esc returned to %v, want LLM config", m.step)
	}
}

func TestEscRestoresPreviousCapabilityPage(t *testing.T) {
	m := newModel(
		[]Provider{{Name: "Test", Key: "test", API: "openai"}},
		DefaultsConfig{},
		ToolProvidersConfig{},
	)
	m.navigateTo(StepLLMConfig)
	m.navigateTo(StepLLMAdvancedAsk)
	m.navigateTo(StepLLMProviderSettings)
	m.navigateTo(StepLLMCapabilityDefault)
	m.capabilityPager.Page = capabilityPageCount - 1
	m.navigateTo(StepLLMCapabilityLite)
	m.capabilityPager.Page = 0
	m.rebuildCurrentStep()

	m = updateHelper(m, tea.KeyMsg{Type: tea.KeyEsc})
	if m.step != StepLLMCapabilityDefault {
		t.Fatalf("esc from lite returned to %v, want all capabilities", m.step)
	}
	if m.capabilityPager.Page != capabilityPageCount-1 {
		t.Fatalf("restored capability page = %d, want %d", m.capabilityPager.Page+1, capabilityPageCount)
	}
}

func TestToolCompletionReturnsToMenuWithoutHistoryLoop(t *testing.T) {
	m := newModel(
		[]Provider{{Name: "Test", Key: "test", API: "openai"}},
		DefaultsConfig{},
		ToolProvidersConfig{},
	)
	m.navigateTo(StepLLMConfig)
	m.navigateTo(StepToolsMenu)
	m.navigateTo(StepToolsSearch)
	m.returnToStep(StepToolsMenu)

	if m.step != StepToolsMenu {
		t.Fatalf("tool completion returned to %v, want tools menu", m.step)
	}
	if !m.navigateBack() || m.step != StepLLMConfig {
		t.Fatalf("esc path from tools menu returned to %v, want LLM config", m.step)
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
