package components

import (
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// MenuKeyMap defines keybindings for the main menu.
type MenuKeyMap struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Help  key.Binding
	Quit  key.Binding
}

func (k MenuKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Help, k.Quit}
}

func (k MenuKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down},
		{k.Enter, k.Help, k.Quit},
	}
}

var DefaultMenuKeys = MenuKeyMap{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "select"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("esc", "ctrl+c"),
		key.WithHelp("esc/ctrl+c", "quit"),
	),
}

// menuItem represents a main menu entry.
type menuItem struct {
	title       string
	description string
}

func (i menuItem) Title() string       { return i.title }
func (i menuItem) Description() string { return i.description }
func (i menuItem) FilterValue() string { return i.title }

// MainMenuModel is the root menu of the config TUI.
type MainMenuModel struct {
	list     list.Model
	width    int
	height   int
	onSelect func(index int)
	help     help.Model
	keys     MenuKeyMap
}

func NewMainMenuModel(onSelect func(index int)) *MainMenuModel {
	items := []list.Item{
		menuItem{"Env Config", "Edit .env.local environment variables"},
		menuItem{"App Config", "Edit application.conf settings"},
		menuItem{"Plugins", "Edit plugin configurations"},
		menuItem{"Souls", "Manage soul/persona markdown files"},
		menuItem{"Rules", "Manage rule markdown files"},
	}

	delegate := style.StyleDelegate(list.NewDefaultDelegate())

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Erii Configuration")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &MainMenuModel{
		list:     l,
		onSelect: onSelect,
		help:     help.New(),
		keys:     DefaultMenuKeys,
	}
}

func (m *MainMenuModel) Init() tea.Cmd { return nil }

func (m *MainMenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
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

func (m *MainMenuModel) View() string {
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}
