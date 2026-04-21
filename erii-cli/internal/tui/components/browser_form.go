package components

import (
	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type BrowserFormModel struct {
	playwright textinput.Model
	status     textinput.Model
	focus      int
	width      int
	height     int
}

func NewBrowserFormModel(w, h int) *BrowserFormModel {
	cfg, _ := config.LoadApp(path.AppFile)
	p := textinput.New()
	p.Placeholder = "Playwright Host (e.g. ws://127.0.0.1:13001)"
	p.SetValue(cfg.Browser.PlaywrightHost)

	s := textinput.New()
	s.Placeholder = "Status Host"
	s.SetValue(cfg.Browser.StatusHost)

	return &BrowserFormModel{playwright: p, status: s, width: w, height: h}
}

func (m *BrowserFormModel) Init() tea.Cmd { return m.playwright.Focus() }

func (m *BrowserFormModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyTab:
			m.focus = (m.focus + 1) % 2
			if m.focus == 0 {
				m.status.Blur()
				return m, m.playwright.Focus()
			}
			m.playwright.Blur()
			return m, m.status.Focus()
		case tea.KeyCtrlS:
			m.save()
			return m, nil
		}
	}
	if m.focus == 0 {
		var cmd tea.Cmd
		m.playwright, cmd = m.playwright.Update(msg)
		return m, cmd
	}
	var cmd tea.Cmd
	m.status, cmd = m.status.Update(msg)
	return m, cmd
}

func (m *BrowserFormModel) View() string {
	content := style.Title("Browser Configuration") + "\n\n"
	content += style.Subtitle("Playwright Host") + "\n" + m.playwright.View() + "\n\n"
	content += style.Subtitle("Status Host") + "\n" + m.status.View() + "\n\n"
	content += style.Muted("tab: next • ctrl+s: save • esc: back")
	return style.NewTheme().BorderedPanel.Width(m.width - 4).Render(content)
}

func (m *BrowserFormModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	cfg.Browser.PlaywrightHost = m.playwright.Value()
	cfg.Browser.StatusHost = m.status.Value()
	config.SaveApp(path.AppFile, cfg)
}
