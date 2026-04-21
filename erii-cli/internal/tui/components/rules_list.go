package components

import (
	"fmt"

	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
)

type RulesListModel struct {
	list   list.Model
	width  int
	height int
}

func NewRulesListModel(w, h int) *RulesListModel {
	items := loadMdItems(path.RulesDir)
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Rules")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	return &RulesListModel{list: l, width: w, height: h}
}

func (m *RulesListModel) Init() tea.Cmd { return nil }

func (m *RulesListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-8)
	case tea.KeyMsg:
		switch msg.String() {
		case "v":
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				viewMd(item.path)
			}
			return m, nil
		case "e":
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				editMd(item.path)
			}
			return m, nil
		}
	}
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *RulesListModel) View() string {
	help := style.Muted("↑/↓ navigate • v view (glow) • e edit ($EDITOR) • esc back")
	return fmt.Sprintf("%s\n%s\n\n%s", style.Title("Rules"), m.list.View(), help)
}
