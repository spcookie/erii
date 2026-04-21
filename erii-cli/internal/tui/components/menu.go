package components

import (
	"fmt"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

var _ tea.Model = (*MenuModel)(nil)

type MenuItem struct {
	title       string
	description string
}

func (i MenuItem) Title() string       { return i.title }
func (i MenuItem) Description() string { return i.description }
func (i MenuItem) FilterValue() string { return i.title }

type MenuModel struct {
	list     list.Model
	width    int
	height   int
	onSelect func(index int)
}

func NewMenuModel(onSelect func(index int)) MenuModel {
	items := []list.Item{
		MenuItem{"Env Config", "Edit .env.local environment variables"},
		MenuItem{"Application Config", "Edit application.conf (HOCON) settings"},
		MenuItem{"Souls", "Manage soul/persona markdown files"},
		MenuItem{"Rules", "Manage rule markdown files"},
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Erii Configuration")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.Styles.Title = style.NewTheme().PanelTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return MenuModel{list: l, onSelect: onSelect}
}

func (m MenuModel) Init() tea.Cmd { return nil }

func (m MenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-6)
	case tea.KeyMsg:
		if msg.Type == tea.KeyEnter {
			if item, ok := m.list.SelectedItem().(MenuItem); ok {
				_ = item
				if m.onSelect != nil {
					m.onSelect(m.list.Index())
				}
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m MenuModel) View() string {
	help := style.Muted("↑/↓ navigate • enter select • esc quit")
	return fmt.Sprintf("%s\n%s\n\n%s", style.Title("Erii Configuration"), m.list.View(), help)
}
