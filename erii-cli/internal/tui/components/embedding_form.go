package components

import (
	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type EmbeddingFormModel struct {
	provider textinput.Model
	apiKey   textinput.Model
	focus    int
	width    int
	height   int
}

func NewEmbeddingFormModel(w, h int) *EmbeddingFormModel {
	cfg, _ := config.LoadApp(path.AppFile)
	p := textinput.New()
	p.Placeholder = "Provider (e.g. bytedance)"
	p.SetValue(cfg.Embedding.Provider)

	a := textinput.New()
	a.Placeholder = "API Key"
	a.EchoMode = textinput.EchoPassword
	a.SetValue(cfg.Embedding.APIKey)

	return &EmbeddingFormModel{provider: p, apiKey: a, width: w, height: h}
}

func (m *EmbeddingFormModel) Init() tea.Cmd { return m.provider.Focus() }

func (m *EmbeddingFormModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyTab:
			m.focus = (m.focus + 1) % 2
			if m.focus == 0 {
				m.apiKey.Blur()
				return m, m.provider.Focus()
			}
			m.provider.Blur()
			return m, m.apiKey.Focus()
		case tea.KeyCtrlS:
			m.save()
			return m, nil
		}
	}
	if m.focus == 0 {
		var cmd tea.Cmd
		m.provider, cmd = m.provider.Update(msg)
		return m, cmd
	}
	var cmd tea.Cmd
	m.apiKey, cmd = m.apiKey.Update(msg)
	return m, cmd
}

func (m *EmbeddingFormModel) View() string {
	content := style.Title("Embedding Configuration") + "\n\n"
	content += style.Subtitle("Provider") + "\n" + m.provider.View() + "\n\n"
	content += style.Subtitle("API Key") + "\n" + m.apiKey.View() + "\n\n"
	content += style.Muted("tab: next • ctrl+s: save • esc: back")
	return style.NewTheme().BorderedPanel.Width(m.width - 4).Render(content)
}

func (m *EmbeddingFormModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	cfg.Embedding.Provider = m.provider.Value()
	cfg.Embedding.APIKey = m.apiKey.Value()
	config.SaveApp(path.AppFile, cfg)
}
