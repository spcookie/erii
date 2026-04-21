package components

import (
	"fmt"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
)

type appMenuItem struct {
	title       string
	description string
}

func (i appMenuItem) Title() string       { return i.title }
func (i appMenuItem) Description() string { return i.description }
func (i appMenuItem) FilterValue() string { return i.title }

type AppSubmenuModel struct {
	list     list.Model
	width    int
	height   int
	onSelect func(index int)
}

func NewAppSubmenuModel(w, h int, onSelect func(index int)) *AppSubmenuModel {
	items := []list.Item{
		appMenuItem{"LLM", "Configure LLM providers and models"},
		appMenuItem{"Embedding", "Configure embedding provider and API key"},
		appMenuItem{"Search", "Configure search provider and API key"},
		appMenuItem{"Browser", "Configure Playwright browser settings"},
		appMenuItem{"Proxy", "Configure HTTP/SOCKS proxy (optional)"},
		appMenuItem{"OneBot", "Manage OneBot bot configurations"},
		appMenuItem{"Groups", "Configure global group settings"},
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Application Config")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.Styles.Title = style.NewTheme().PanelTitle

	return &AppSubmenuModel{list: l, width: w, height: h, onSelect: onSelect}
}

func (m *AppSubmenuModel) Init() tea.Cmd { return nil }

func (m *AppSubmenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-6)
	case tea.KeyMsg:
		if msg.Type == tea.KeyEnter {
			if m.onSelect != nil {
				m.onSelect(m.list.Index())
			}
			return m, nil
		}
	}
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *AppSubmenuModel) View() string {
	help := style.Muted("↑/↓ navigate • enter select • esc back")
	return fmt.Sprintf("%s\n%s\n\n%s", style.Title("Application Config"), m.list.View(), help)
}
