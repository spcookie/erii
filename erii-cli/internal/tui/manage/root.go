package manage

import (
	"github.com/charmbracelet/bubbletea"
)

// Navigation messages
type (
	PopMsg            struct{}
	PushGroupListMsg  struct{ Bot BotInfo }
	PushManageMenuMsg struct {
		Bot   BotInfo
		Group GroupInfo
	}
	PushTableMsg struct {
		ResourceType ResourceType
		Bot          BotInfo
		Group        GroupInfo
	}
	PushEditMsg struct {
		ResourceType ResourceType
		Bot          BotInfo
		Group        GroupInfo
		Data         any
		IsCreate     bool
	}
	RefreshMsg struct{}
)

type NavigationStack []tea.Model

type RootModel struct {
	stack  NavigationStack
	width  int
	height int
}

func NewRootModel(initial tea.Model) *RootModel {
	return &RootModel{
		stack: NavigationStack{initial},
	}
}

func (m *RootModel) Push(screen tea.Model) {
	m.stack = append(m.stack, screen)
}

func (m *RootModel) Pop() bool {
	if len(m.stack) <= 1 {
		return false
	}
	m.stack = m.stack[:len(m.stack)-1]
	return true
}

func (m *RootModel) Current() tea.Model {
	if len(m.stack) == 0 {
		return nil
	}
	return m.stack[len(m.stack)-1]
}

func (m *RootModel) Init() tea.Cmd {
	if top := m.Current(); top != nil {
		return top.Init()
	}
	return nil
}

func (m *RootModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if top := m.Current(); top != nil {
			newTop, _ := top.Update(msg)
			m.stack[len(m.stack)-1] = newTop
		}
		return m, nil

	case PopMsg:
		if !m.Pop() {
			return m, tea.Quit
		}
		if len(m.stack) > 0 && m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, nil

	case PushGroupListMsg:
		m.Push(NewGroupListModel(getAPI(m.stack[0]), msg.Bot))
		cmd := m.Current().Init()
		if m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, cmd

	case PushManageMenuMsg:
		m.Push(NewManageMenuModel(msg.Bot, msg.Group))
		cmd := m.Current().Init()
		if m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, cmd

	case PushTableMsg:
		api := getAPI(m.stack[0])
		m.Push(NewDataTableModel(api, msg.ResourceType, msg.Bot, msg.Group))
		cmd := m.Current().Init()
		if m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, cmd

	case PushEditMsg:
		api := getAPI(m.stack[0])
		m.Push(NewEditFormModel(api, msg.ResourceType, msg.Bot, msg.Group, msg.Data, msg.IsCreate))
		cmd := m.Current().Init()
		if m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, cmd
	}

	if top := m.Current(); top != nil {
		newTop, cmd := top.Update(msg)
		m.stack[len(m.stack)-1] = newTop
		return m, cmd
	}
	return m, nil
}

func (m *RootModel) View() string {
	if top := m.Current(); top != nil {
		return top.View()
	}
	return ""
}

func getAPI(model tea.Model) *API {
	switch m := model.(type) {
	case *BotListModel:
		return m.api
	default:
		return nil
	}
}
