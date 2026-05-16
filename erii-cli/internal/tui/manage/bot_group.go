package manage

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// ── Navigation key bindings ──

type navKeys struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Back  key.Binding
	Quit  key.Binding
	Help  key.Binding
}

func (k navKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Help, k.Quit}
}

func (k navKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down},
		{k.Enter, k.Back, k.Help, k.Quit},
	}
}

var defaultNavKeys = navKeys{
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
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "help"),
	),
}

// ── List item ──

type item struct {
	title, desc string
}

func (i item) Title() string       { return i.title }
func (i item) Description() string { return i.desc }
func (i item) FilterValue() string { return i.title }

// ── Bots loaded message ──

type botsLoadedMsg struct {
	Bots  []api.BotInfo
	Error error
}

type groupsLoadedMsg struct {
	Groups []api.GroupInfo
	Error  error
}

// ── BotListModel ──

type BotListModel struct {
	api    *api.Client
	bots   []api.BotInfo
	list   list.Model
	help   help.Model
	keys   navKeys
	width  int
	height int
	err    error
}

func NewBotListModel(api *api.Client) *BotListModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Bot")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &BotListModel{
		api:  api,
		list: l,
		help: help.New(),
		keys: defaultNavKeys,
	}
}

func (m *BotListModel) Init() tea.Cmd {
	return func() tea.Msg {
		bots, err := m.api.GetBots()
		return botsLoadedMsg{Bots: bots, Error: err}
	}
}

func (m *BotListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case botsLoadedMsg:
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.bots = msg.Bots
		m.list.SetItems(setListItems(m.bots, func(b api.BotInfo) list.Item {
			return item{title: "> " + b.BotName, desc: "   ID: " + b.BotID}
		}))
		return m, nil

	case tea.KeyMsg:
		if m.err != nil {
			switch msg.String() {
			case "q", "ctrl+c":
				return m, tea.Quit
			case "r":
				m.err = nil
				return m, m.Init()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			if idx >= 0 && idx < len(m.bots) {
				return m, func() tea.Msg {
					return PushGroupListMsg{Bot: m.bots[idx]}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BotListModel) View() string {
	if m.err != nil {
		return components.RenderErrorCard(m.width, m.height, m.err.Error(), "press r to retry    press q to quit")
	}
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}

// ── GroupListModel ──

type GroupListModel struct {
	api    *api.Client
	bot    api.BotInfo
	groups []api.GroupInfo
	list   list.Model
	help   help.Model
	keys   navKeys
	width  int
	height int
	err    error
}

func NewGroupListModel(api *api.Client, bot api.BotInfo) *GroupListModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Group  \xe2\x80\x94  " + bot.BotName)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &GroupListModel{
		api:  api,
		bot:  bot,
		list: l,
		help: help.New(),
		keys: defaultNavKeys,
	}
}

func (m *GroupListModel) Init() tea.Cmd {
	return func() tea.Msg {
		groups, err := m.api.GetGroups(m.bot.BotID)
		return groupsLoadedMsg{Groups: groups, Error: err}
	}
}

func (m *GroupListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case groupsLoadedMsg:
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.groups = msg.Groups
		m.list.SetItems(setListItems(m.groups, func(g api.GroupInfo) list.Item {
			return item{title: "> " + g.GroupName, desc: "   ID: " + g.GroupID}
		}))
		return m, nil

	case tea.KeyMsg:
		if m.err != nil {
			switch msg.String() {
			case "q", "ctrl+c":
				return m, tea.Quit
			case "esc", "backspace":
				return m, func() tea.Msg { return PopMsg{} }
			case "r":
				m.err = nil
				return m, m.Init()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			if idx >= 0 && idx < len(m.groups) {
				return m, func() tea.Msg {
					return PushManageMenuMsg{Bot: m.bot, Group: m.groups[idx]}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *GroupListModel) View() string {
	if m.err != nil {
		return components.RenderErrorCard(m.width, m.height, m.err.Error(), "press r to retry    esc back    q quit")
	}
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}

// ── Helpers ──

func setListItems[T any](data []T, mapper func(T) list.Item) []list.Item {
	items := make([]list.Item, len(data))
	for i, v := range data {
		items[i] = mapper(v)
	}
	return items
}
