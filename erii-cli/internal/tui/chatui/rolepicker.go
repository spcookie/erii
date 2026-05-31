package chatui

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// --- role item for list ---

type roleItem struct {
	id        string
	name      string
	character string
	emoticon  string
}

func (r roleItem) Title() string       { return r.name }
func (r roleItem) Description() string { return r.character }
func (r roleItem) FilterValue() string { return r.name + " " + r.character }

// --- tea messages ---

type rolesFetchedMsg []api.ChatRole
type rolesFetchErrMsg struct{ err error }

// --- key bindings ---

type rolePickerKeys struct {
	Enter key.Binding
	Quit  key.Binding
}

func (k rolePickerKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Enter, k.Quit}
}
func (k rolePickerKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Enter, k.Quit}}
}

var rolePickerKeyMap = rolePickerKeys{
	Enter: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "select")),
	Quit:  key.NewBinding(key.WithKeys("esc", "ctrl+c"), key.WithHelp("esc", "quit")),
}

type rolePickerErrKeys struct {
	Retry key.Binding
	Quit  key.Binding
}

func (k rolePickerErrKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Retry, k.Quit}
}
func (k rolePickerErrKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Retry, k.Quit}}
}

var rolePickerErrKeyMap = rolePickerErrKeys{
	Retry: key.NewBinding(key.WithKeys("r"), key.WithHelp("r", "retry")),
	Quit:  key.NewBinding(key.WithKeys("q", "ctrl+c"), key.WithHelp("q", "quit")),
}

// --- role picker model ---

type RolePickerModel struct {
	client       *api.Client
	list         list.Model
	vp           viewport.Model
	help         help.Model
	err          error
	loading      bool
	width        int
	height       int
	selectedRole *roleItem
}

func newRolePickerModel(client *api.Client) *RolePickerModel {
	del := list.NewDefaultDelegate()
	style.StyleDelegate(del)

	l := list.New([]list.Item{}, del, 0, 0)
	l.SetShowTitle(false)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.KeyMap.Quit.SetEnabled(false)

	return &RolePickerModel{
		client:  client,
		list:    l,
		vp:      viewport.New(0, 0),
		help:    help.New(),
		loading: true,
	}
}

func (m *RolePickerModel) Init() tea.Cmd {
	return func() tea.Msg {
		roles, err := m.client.ListChatRoles()
		if err != nil {
			return rolesFetchErrMsg{err}
		}
		return rolesFetchedMsg(roles)
	}
}

func (m *RolePickerModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width

		footerH := lipgloss.Height(m.helpView())
		vpHeight := msg.Height - footerH - 1
		if vpHeight < 3 {
			vpHeight = 3
		}
		m.vp.Width = msg.Width
		m.vp.Height = vpHeight

		listHeight := vpHeight - 3
		if listHeight < 3 {
			listHeight = 3
		}
		m.list.SetSize(msg.Width-4, listHeight)
		m.syncViewportContent()
		return m, nil

	case rolesFetchedMsg:
		m.loading = false
		items := make([]list.Item, len(msg))
		for i, r := range msg {
			items[i] = roleItem{
				id:        r.ID,
				name:      r.Name,
				character: r.Character,
				emoticon:  r.Emoticon,
			}
		}
		m.list.SetItems(items)
		m.syncViewportContent()
		return m, nil

	case rolesFetchErrMsg:
		m.loading = false
		m.err = msg.err
		m.syncViewportContent()
		return m, nil

	case tea.KeyMsg:
		if m.err != nil {
			switch msg.String() {
			case "r":
				m.err = nil
				m.loading = true
				return m, m.Init()
			case "q", "ctrl+c", "esc":
				return m, tea.Quit
			}
			return m, nil
		}
		switch msg.String() {
		case "q", "ctrl+c", "esc":
			return m, tea.Quit
		case "enter":
			sel, ok := m.list.SelectedItem().(roleItem)
			if ok {
				m.selectedRole = &sel
				return m, tea.Quit
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	m.syncViewportContent()
	return m, cmd
}

func (m *RolePickerModel) helpView() string {
	if m.err != nil {
		return m.help.View(rolePickerErrKeyMap)
	}
	return m.help.View(rolePickerKeyMap)
}

func (m *RolePickerModel) syncViewportContent() {
	var body string
	if m.loading {
		body = style.Title("Select Bot Role") + "\n\n" +
			style.Muted("Loading available roles...")
	} else {
		body = style.Title("Select Bot Role") + "\n\n" + m.list.View()
	}
	m.vp.SetContent(body)
}

func (m *RolePickerModel) View() string {
	if m.err != nil {
		if m.width == 0 || m.height == 0 {
			return ""
		}
		return components.RenderErrorCard(m.width, m.height,
			components.FriendlyErrorMessage(m.err),
			"press r to retry    press q to quit")
	}
	return lipgloss.JoinVertical(lipgloss.Left,
		m.vp.View(),
		m.helpView(),
	)
}
