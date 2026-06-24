package components

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// ── Data messages ──

type BotsLoadedMsg struct {
	Bots  []api.BotInfo
	Error error
}

type GroupsLoadedMsg struct {
	Bot    api.BotInfo
	Groups []api.GroupInfo
	Error  error
}

// ── BotListModel ──

type BotListConfig struct {
	ShowAll       bool
	EnterAction   func(idx int, bots []api.BotInfo) tea.Cmd
	BackAction    func() tea.Cmd
	ErrorCardHint string
}

type BotListModel struct {
	api    *api.Client
	bots   []api.BotInfo
	list   list.Model
	help   help.Model
	keys   NavKeys
	width  int
	height int
	err    error
	config BotListConfig
}

func NewBotListModel(api *api.Client, config BotListConfig) *BotListModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Bot")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &BotListModel{
		api:    api,
		list:   l,
		help:   help.New(),
		keys:   DefaultNavKeys,
		config: config,
	}
}

func (m *BotListModel) API() *api.Client { return m.api }

func (m *BotListModel) Init() tea.Cmd {
	return func() tea.Msg {
		bots, err := m.api.GetBots()
		return BotsLoadedMsg{Bots: bots, Error: err}
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

	case BotsLoadedMsg:
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.bots = msg.Bots
		items := make([]list.Item, 0, len(msg.Bots)+1)
		if m.config.ShowAll {
			items = append(items, ListItem{
				TitleText: "> All Bots",
				DescText:  "   Show usage for all bots",
			})
		}
		for _, b := range msg.Bots {
			items = append(items, ListItem{
				TitleText: "> " + b.BotName,
				DescText:  "   ID: " + b.BotID,
			})
		}
		m.list.SetItems(items)
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
		if m.config.BackAction != nil && key.Matches(msg, m.keys.Back) {
			return m, m.config.BackAction()
		}
		if key.Matches(msg, m.keys.Enter) {
			if m.config.EnterAction != nil {
				return m, m.config.EnterAction(m.list.Index(), m.bots)
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BotListModel) View() string {
	if m.err != nil {
		hint := m.config.ErrorCardHint
		if hint == "" {
			hint = "press r to retry    press q to quit"
		}
		return RenderErrorCard(m.width, m.height, m.err.Error(), hint)
	}
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}

// ── GroupListModel ──

type GroupListConfig struct {
	ShowAll       bool
	EnterAction   func(idx int, bot api.BotInfo, groups []api.GroupInfo) tea.Cmd
	BackAction    func() tea.Cmd
	ErrorCardHint string
}

type GroupListModel struct {
	api    *api.Client
	bot    api.BotInfo
	groups []api.GroupInfo
	list   list.Model
	help   help.Model
	keys   NavKeys
	width  int
	height int
	err    error
	config GroupListConfig
}

func NewGroupListModel(api *api.Client, bot api.BotInfo, config GroupListConfig) *GroupListModel {
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Group  —  " + bot.BotName)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &GroupListModel{
		api:    api,
		bot:    bot,
		list:   l,
		help:   help.New(),
		keys:   DefaultNavKeys,
		config: config,
	}
}

func (m *GroupListModel) Init() tea.Cmd {
	return func() tea.Msg {
		groups, err := m.api.GetGroups(m.bot.BotID)
		return GroupsLoadedMsg{Bot: m.bot, Groups: groups, Error: err}
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

	case GroupsLoadedMsg:
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.groups = msg.Groups
		items := make([]list.Item, 0, len(msg.Groups)+1)
		if m.config.ShowAll {
			items = append(items, ListItem{
				TitleText: "> All Groups",
				DescText:  "   Show usage for all groups of " + m.bot.BotName,
			})
		}
		for _, g := range msg.Groups {
			items = append(items, ListItem{
				TitleText: "> " + g.GroupName,
				DescText:  "   ID: " + g.GroupID,
			})
		}
		m.list.SetItems(items)
		return m, nil

	case tea.KeyMsg:
		if m.err != nil {
			switch msg.String() {
			case "q", "ctrl+c":
				return m, tea.Quit
			case "esc", "backspace":
				if m.config.BackAction != nil {
					return m, m.config.BackAction()
				}
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
			if m.config.BackAction != nil {
				return m, m.config.BackAction()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			if m.config.EnterAction != nil {
				return m, m.config.EnterAction(m.list.Index(), m.bot, m.groups)
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *GroupListModel) View() string {
	if m.err != nil {
		hint := m.config.ErrorCardHint
		if hint == "" {
			hint = "press r to retry    esc back    q quit"
		}
		return RenderErrorCard(m.width, m.height, m.err.Error(), hint)
	}
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}
