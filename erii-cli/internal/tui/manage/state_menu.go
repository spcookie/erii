package manage

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/style"
	"fmt"

	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type stateMenuItem struct {
	stateType StateType
	title     string
	desc      string
}

func (i stateMenuItem) Title() string       { return i.title }
func (i stateMenuItem) Description() string { return i.desc }
func (i stateMenuItem) FilterValue() string { return i.title }

type StateMenuModel struct {
	bot    api.BotInfo
	group  api.GroupInfo
	list   list.Model
	keys   menuKeys
	width  int
	height int
}

func NewStateMenuModel(bot api.BotInfo, group api.GroupInfo) *StateMenuModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select State Type")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	items := []list.Item{
		stateMenuItem{stateType: StateEmotion, title: StateEmotion.Icon() + "  Emotion", desc: "View and edit emotional state"},
		stateMenuItem{stateType: StateFlow, title: StateFlow.Icon() + "  Flow", desc: "View and edit flow state"},
		stateMenuItem{stateType: StateVolition, title: StateVolition.Icon() + "  Volition", desc: "View and edit volition state"},
	}
	l.SetItems(items)

	return &StateMenuModel{
		bot:   bot,
		group: group,
		list:  l,
		keys:  menuDefaultKeys,
	}
}

func (m *StateMenuModel) Init() tea.Cmd {
	return nil
}

func (m *StateMenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
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
				item := items[idx].(stateMenuItem)
				return m, func() tea.Msg {
					return PushStateDetailMsg{
						StateType: item.stateType,
						Bot:       m.bot,
						Group:     m.group,
					}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *StateMenuModel) View() string {
	header := lipgloss.NewStyle().
		Foreground(style.Secondary).
		MarginBottom(1).
		Render(fmt.Sprintf("Bot: %s  |  Group: %s", m.bot.BotName, m.group.GroupName))
	return header + "\n" + m.list.View()
}
