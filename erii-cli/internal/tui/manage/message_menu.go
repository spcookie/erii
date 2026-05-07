package manage

import (
	"erii-cli/internal/tui/style"
	"fmt"

	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type messageMenuItem struct {
	resourceType ResourceType
	title        string
	desc         string
}

func (i messageMenuItem) Title() string       { return i.title }
func (i messageMenuItem) Description() string { return i.desc }
func (i messageMenuItem) FilterValue() string { return i.title }

type MessageMenuModel struct {
	bot    BotInfo
	group  GroupInfo
	list   list.Model
	keys   menuKeys
	width  int
	height int
}

func NewMessageMenuModel(bot BotInfo, group GroupInfo) *MessageMenuModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Messages")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	items := []list.Item{
		messageMenuItem{resourceType: ResourceHistory, title: "📜  History", desc: "View and edit message history"},
		messageMenuItem{resourceType: ResourceResource, title: "📎  Resources", desc: "View message resources"},
	}
	l.SetItems(items)

	return &MessageMenuModel{
		bot:   bot,
		group: group,
		list:  l,
		keys:  menuDefaultKeys,
	}
}

func (m *MessageMenuModel) Init() tea.Cmd {
	return nil
}

func (m *MessageMenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
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
				item := items[idx].(messageMenuItem)
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

func (m *MessageMenuModel) View() string {
	header := lipgloss.NewStyle().
		Foreground(style.Secondary).
		MarginBottom(1).
		Render(fmt.Sprintf("Bot: %s  |  Group: %s", m.bot.BotName, m.group.GroupName))
	return header + "\n" + m.list.View()
}
