package components

import (
	"os"

	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type envField struct {
	key   string
	value string
	input textinput.Model
}

type EnvEditorModel struct {
	width    int
	height   int
	fields   []envField
	focus    int
	template string
}

func NewEnvEditorModel(w, h int) *EnvEditorModel {
	cfg, _ := config.LoadEnv(path.EnvFile)
	tmpl, _ := os.ReadFile(path.EnvTemplate)

	keys := []string{
		"GOOGLE_API_KEY", "DEEP_SEEK_API_KEY", "MINIMAX_CODING_PLAN_KEY",
		"CHOICE_MODEL", "EMBEDDING_API_KEY", "SEARCH_API_KEY",
		"NAPCAT_TOKEN", "PLAYWRIGHT_HOST", "STATUS_HOST",
		"HTTP_PROXY", "SOCKS_PROXY", "NAPCAT_UID", "NAPCAT_GID",
	}

	fields := make([]envField, 0, len(keys))
	for i, k := range keys {
		ti := textinput.New()
		ti.Placeholder = k
		ti.SetValue(cfg.Vars[k])
		if i == 0 {
			ti.Focus()
		}
		fields = append(fields, envField{key: k, value: cfg.Vars[k], input: ti})
	}

	return &EnvEditorModel{
		width:    w,
		height:   h,
		fields:   fields,
		template: string(tmpl),
	}
}

func (m *EnvEditorModel) Init() tea.Cmd {
	if len(m.fields) > 0 {
		return m.fields[0].input.Focus()
	}
	return nil
}

func (m *EnvEditorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyTab:
			m.fields[m.focus].input.Blur()
			m.focus = (m.focus + 1) % len(m.fields)
			return m, m.fields[m.focus].input.Focus()
		case tea.KeyShiftTab:
			m.fields[m.focus].input.Blur()
			m.focus = (m.focus - 1 + len(m.fields)) % len(m.fields)
			return m, m.fields[m.focus].input.Focus()
		case tea.KeyCtrlS:
			m.save()
			return m, nil
		}
	}

	if m.focus < len(m.fields) {
		newInput, cmd := m.fields[m.focus].input.Update(msg)
		m.fields[m.focus].input = newInput
		m.fields[m.focus].value = newInput.Value()
		return m, cmd
	}
	return m, nil
}

func (m *EnvEditorModel) View() string {
	left := style.Title("Environment Variables") + "\n\n"
	for i, f := range m.fields {
		label := style.Subtitle(f.key)
		if i == m.focus {
			label = style.NewTheme().StepCurrent.Render(f.key)
		}
		left += label + "\n" + f.input.View() + "\n\n"
	}
	left += style.Muted("tab: next • shift+tab: prev • ctrl+s: save • esc: back")

	right := style.Title("Template") + "\n" + style.Muted(m.template)

	return style.NewTheme().TwoColumn(left, right, m.width)
}

func (m *EnvEditorModel) save() {
	cfg := &config.EnvConfig{Vars: make(map[string]string)}
	for _, f := range m.fields {
		cfg.Vars[f.key] = f.value
	}
	config.SaveEnv(path.EnvFile, cfg)
}
