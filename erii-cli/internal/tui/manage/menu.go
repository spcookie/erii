package manage

import (
	"erii-cli/internal/tui/style"
	"fmt"

	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type menuItem struct {
	resourceType ResourceType
	title        string
	desc         string
}

func (i menuItem) Title() string       { return i.title }
func (i menuItem) Description() string { return i.desc }
func (i menuItem) FilterValue() string { return i.title }

type menuKeys struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Back  key.Binding
	Quit  key.Binding
}

func (k menuKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Back, k.Quit}
}

func (k menuKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter},
		{k.Back, k.Quit},
	}
}

var menuDefaultKeys = menuKeys{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("\xe2\x86\x91/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("\xe2\x86\x93/j", "down"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "select"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc", "back"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
}

type ManageMenuModel struct {
	bot    BotInfo
	group  GroupInfo
	list   list.Model
	keys   menuKeys
	width  int
	height int
}

func NewManageMenuModel(bot BotInfo, group GroupInfo) *ManageMenuModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Data Type")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	items := []list.Item{
		menuItem{resourceType: ResourceFacts, title: "\xf0\x9f\xa7\xa0  Memory (Facts)", desc: "Manage group memory facts"},
		menuItem{resourceType: ResourceProfiles, title: "\xf0\x9f\x91\xa4  User Profiles", desc: "Manage user profiles"},
		menuItem{resourceType: ResourceSummaries, title: "\xf0\x9f\x93\x9d  Summaries", desc: "Manage conversation summaries"},
		menuItem{resourceType: ResourceMemes, title: "\xf0\x9f\x96\xbc\xef\xb8\x8f  Memes", desc: "Manage meme metadata"},
		menuItem{resourceType: ResourceVocabularies, title: "\xf0\x9f\x94\xa5  Vocabulary", desc: "Manage learned vocabulary"},
	}
	l.SetItems(items)

	return &ManageMenuModel{
		bot:   bot,
		group: group,
		list:  l,
		keys:  menuDefaultKeys,
	}
}

func (m *ManageMenuModel) Init() tea.Cmd {
	return nil
}

func (m *ManageMenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-2)
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			items := m.list.Items()
			if idx >= 0 && idx < len(items) {
				item := items[idx].(menuItem)
				return m, func() tea.Msg {
					return PushTableMsg{
						ResourceType: item.resourceType,
						Bot:          m.bot,
						Group:        m.group,
					}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *ManageMenuModel) View() string {
	header := lipgloss.NewStyle().
		Foreground(style.Secondary).
		MarginBottom(1).
		Render(fmt.Sprintf("Bot: %s  |  Group: %s", m.bot.BotName, m.group.GroupName))
	return header + "\n" + m.list.View()
}
