package manage

import (
	"erii-cli/internal/api"
	style "erii-cli/internal/ui/theme"
	"fmt"

	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type memoryMenuItem struct {
	action string
	title  string
	desc   string
	mode   MemorySearchMode
}

func (i memoryMenuItem) Title() string       { return i.title }
func (i memoryMenuItem) Description() string { return i.desc }
func (i memoryMenuItem) FilterValue() string { return i.title }

type MemoryMenuModel struct {
	bot    api.BotInfo
	group  api.GroupInfo
	list   list.Model
	keys   menuKeys
	width  int
	height int
}

func NewMemoryMenuModel(bot api.BotInfo, group api.GroupInfo) *MemoryMenuModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Memory")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)
	l.SetItems(memoryMenuItems(webMenuIconsEnabled()))

	return &MemoryMenuModel{
		bot:   bot,
		group: group,
		list:  l,
		keys:  menuDefaultKeys,
	}
}

func memoryMenuItems(web bool) []list.Item {
	return []list.Item{
		memoryMenuItem{action: "record", title: menuTitle(web, "\uf0ce", "📋", "Record"), desc: "Open the existing memory facts table"},
		memoryMenuItem{action: "search", title: menuTitle(web, "\uf14e", "🧭", "Vector"), desc: "Embedding search over memory facts", mode: MemorySearchVector},
		memoryMenuItem{action: "search", title: menuTitle(web, "\uefce", "🕸️", "Graph"), desc: "Embedding search plus one-hop entity graph", mode: MemorySearchGraph},
	}
}

func (m *MemoryMenuModel) Init() tea.Cmd {
	return nil
}

func (m *MemoryMenuModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
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
			if idx < 0 || idx >= len(items) {
				return m, nil
			}
			item := items[idx].(memoryMenuItem)
			if item.action == "record" {
				return m, func() tea.Msg {
					return PushTableMsg{
						ResourceType: ResourceFacts,
						Bot:          m.bot,
						Group:        m.group,
					}
				}
			}
			return m, func() tea.Msg {
				return PushMemorySearchMsg{
					Mode:  item.mode,
					Bot:   m.bot,
					Group: m.group,
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *MemoryMenuModel) View() string {
	header := lipgloss.NewStyle().
		Foreground(style.Secondary).
		MarginBottom(1).
		Render(fmt.Sprintf("Bot: %s  |  Group: %s", m.bot.BotName, m.group.GroupName))
	return header + "\n" + m.list.View()
}
