package stats

import (
	"github.com/charmbracelet/bubbletea"
)

// NavigationStack manages the screen stack for stats TUI
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
		// Forward to current child
		if top := m.Current(); top != nil {
			newTop, _ := top.Update(msg)
			m.stack[len(m.stack)-1] = newTop
		}
		return m, nil
	case PopMsg:
		if !m.Pop() {
			return m, tea.Quit
		}
		// Forward WindowSizeMsg to new current
		if len(m.stack) > 0 && m.width > 0 {
			if top := m.Current(); top != nil {
				newTop, _ := top.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop
			}
		}
		return m, nil
	case PushGroupListMsg:
		m.Push(NewGroupListModel(m.stack[0].(*BotListModel).api, msg.Bot))
		cmd := m.Current().Init()
		// Forward WindowSizeMsg to newly pushed model
		if m.width > 0 {
			newTop, _ := m.Current().Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
			m.stack[len(m.stack)-1] = newTop
		}
		return m, cmd
	case PushStatusViewMsg:
		api := m.stack[0].(*BotListModel).api
		m.Push(NewStatusViewModel(api, msg.Bot, msg.Group))
		cmd := m.Current().Init()
		// Forward WindowSizeMsg to newly pushed model
		if m.width > 0 {
			newTop, _ := m.Current().Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
			m.stack[len(m.stack)-1] = newTop
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
