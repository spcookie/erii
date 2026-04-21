package components

import (
	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type ProxyFormModel struct {
	http   textinput.Model
	socks  textinput.Model
	focus  int
	width  int
	height int
}

func NewProxyFormModel(w, h int) *ProxyFormModel {
	cfg, _ := config.LoadApp(path.AppFile)
	httpIn := textinput.New()
	httpIn.Placeholder = "HTTP Proxy (optional)"
	httpIn.SetValue(cfg.Proxy.HTTP)

	socksIn := textinput.New()
	socksIn.Placeholder = "SOCKS Proxy (optional)"
	socksIn.SetValue(cfg.Proxy.SOCKS)

	return &ProxyFormModel{http: httpIn, socks: socksIn, width: w, height: h}
}

func (m *ProxyFormModel) Init() tea.Cmd { return m.http.Focus() }

func (m *ProxyFormModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyTab:
			m.focus = (m.focus + 1) % 2
			if m.focus == 0 {
				m.socks.Blur()
				return m, m.http.Focus()
			}
			m.http.Blur()
			return m, m.socks.Focus()
		case tea.KeyCtrlS:
			m.save()
			return m, nil
		}
	}
	if m.focus == 0 {
		var cmd tea.Cmd
		m.http, cmd = m.http.Update(msg)
		return m, cmd
	}
	var cmd tea.Cmd
	m.socks, cmd = m.socks.Update(msg)
	return m, cmd
}

func (m *ProxyFormModel) View() string {
	content := style.Title("Proxy Configuration (Optional)") + "\n\n"
	content += style.Subtitle("HTTP Proxy") + "\n" + m.http.View() + "\n\n"
	content += style.Subtitle("SOCKS Proxy") + "\n" + m.socks.View() + "\n\n"
	content += style.Muted("tab: next • ctrl+s: save • esc: back")
	return style.NewTheme().BorderedPanel.Width(m.width - 4).Render(content)
}

func (m *ProxyFormModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	cfg.Proxy.HTTP = m.http.Value()
	cfg.Proxy.SOCKS = m.socks.Value()
	config.SaveApp(path.AppFile, cfg)
}
