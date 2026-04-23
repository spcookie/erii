package tui

import (
	"erii-cli/internal/tui/components"

	tea "github.com/charmbracelet/bubbletea"
)

type Screen tea.Model

type NavigationStack []Screen

type RootModel struct {
	stack       NavigationStack
	width       int
	height      int
	quit        bool
	pendingPush Screen
}

func NewRootModel(initial Screen) *RootModel {
	m := &RootModel{stack: make(NavigationStack, 0)}
	m.Push(initial)
	return m
}

func (m *RootModel) Push(s Screen) {
	m.stack = append(m.stack, s)
}

func (m *RootModel) Pop() (Screen, bool) {
	if len(m.stack) <= 1 {
		return nil, false
	}
	top := m.stack[len(m.stack)-1]
	m.stack = m.stack[:len(m.stack)-1]
	return top, true
}

func (m *RootModel) Current() Screen {
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
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyCtrlC:
			m.quit = true
			return m, tea.Quit
		}
	case components.PopScreenMsg:
		m.Pop()
		return m, func() tea.Msg { return components.RefreshMsg{} }
	}

	if top := m.Current(); top != nil {
		newTop, cmd := top.Update(msg)
		m.stack[len(m.stack)-1] = newTop
		if m.pendingPush != nil {
			m.Push(m.pendingPush)
			m.pendingPush = nil
			if newTop := m.Current(); newTop != nil {
				initCmd := newTop.Init()
				newTop2, cmd2 := newTop.Update(tea.WindowSizeMsg{Width: m.width, Height: m.height})
				m.stack[len(m.stack)-1] = newTop2
				return m, tea.Batch(initCmd, cmd2)
			}
		}
		return m, cmd
	}
	return m, nil
}

func (m *RootModel) View() string {
	if m.quit {
		return ""
	}
	if top := m.Current(); top != nil {
		return top.View()
	}
	return ""
}

func (m *RootModel) Width() int  { return m.width }
func (m *RootModel) Height() int { return m.height }
