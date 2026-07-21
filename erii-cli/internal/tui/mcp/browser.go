package mcp

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"erii-cli/internal/tui/components"
	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

type item struct {
	name string
	path string
}

func (i item) Title() string       { return i.name }
func (i item) Description() string { return i.path }
func (i item) FilterValue() string { return i.name }

type keyMap struct {
	Up     key.Binding
	Down   key.Binding
	Enter  key.Binding
	New    key.Binding
	Delete key.Binding
	Back   key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k keyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.New, k.Back, k.Help, k.Quit}
}

func (k keyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter},
		{k.New, k.Delete},
		{k.Back, k.Help, k.Quit},
	}
}

var keys = keyMap{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "open"),
	),
	New: key.NewBinding(
		key.WithKeys("ctrl+n"),
		key.WithHelp("ctrl+n", "new"),
	),
	Delete: key.NewBinding(
		key.WithKeys("ctrl+d"),
		key.WithHelp("ctrl+d", "delete"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "left", "h"),
		key.WithHelp("esc/←/h", "back"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("q", "ctrl+c"),
		key.WithHelp("q/ctrl+c", "quit"),
	),
}

type BrowserModel struct {
	dir           string
	list          list.Model
	width         int
	height        int
	help          help.Model
	onSelect      func(name string, filePath string)
	newing        bool
	newName       string
	newTransport  string
	newForm       *huh.Form
	deleting      bool
	deleteConfirm bool
	deleteForm    *huh.Form
	errMsg        string
}

func NewBrowserModel(dir string, onSelect func(name string, filePath string)) *BrowserModel {
	m := &BrowserModel{
		dir:          dir,
		help:         help.New(),
		onSelect:     onSelect,
		width:        80,
		height:       24,
		newTransport: "stdio",
	}
	m.refreshList()
	return m
}

func (m *BrowserModel) Init() tea.Cmd { return nil }

func (m *BrowserModel) refreshList() {
	items, err := loadItems(m.dir)
	if err != nil {
		m.errMsg = err.Error()
	} else {
		m.errMsg = ""
	}
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("MCP")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.SetShowPagination(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)
	m.list = l
	m.updateSize()
}

func loadItems(dir string) ([]list.Item, error) {
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create MCP dir: %w", err)
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("failed to read MCP dir: %w", err)
	}
	var files []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".json") {
			files = append(files, entry.Name())
		}
	}
	sort.Strings(files)
	items := make([]list.Item, 0, len(files))
	for _, name := range files {
		items = append(items, item{
			name: strings.TrimSuffix(name, ".json"),
			path: filepath.Join(dir, name),
		})
	}
	return items, nil
}

func (m *BrowserModel) updateSize() {
	if m.width > 0 && m.height > 0 {
		m.list.SetSize(m.width, m.height-4)
		m.help.Width = m.width
	}
}

func (m *BrowserModel) formWidth() int {
	w := 60
	if m.width > 16 {
		w = m.width - 8
		if w > 60 {
			w = 60
		}
	}
	return w
}

func (m *BrowserModel) buildNewForm() tea.Cmd {
	m.newForm = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("File name").
				Placeholder("server-name").
				Value(&m.newName).
				Key("name"),
			huh.NewSelect[string]().
				Title("Transport").
				Options(
					huh.NewOption("stdio", "stdio"),
					huh.NewOption("sse", "sse"),
					huh.NewOption("streamable_http", "streamable_http"),
					huh.NewOption("websocket", "websocket"),
				).
				Value(&m.newTransport).
				Key("transport"),
		),
	).WithWidth(m.formWidth()).WithShowHelp(false).WithTheme(style.HuhTheme())
	return m.newForm.Init()
}

func (m *BrowserModel) buildDeleteForm() tea.Cmd {
	m.deleteConfirm = false
	m.deleteForm = huh.NewForm(
		huh.NewGroup(
			huh.NewConfirm().
				Title("Delete this MCP config?").
				Affirmative("Yes").
				Negative("No").
				Value(&m.deleteConfirm).
				Key("confirm"),
		),
	).WithWidth(m.formWidth()).WithShowHelp(false).WithTheme(style.DestructiveHuhTheme())
	return m.deleteForm.Init()
}

func (m *BrowserModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.newing && m.newForm != nil {
		if keyMsg, ok := msg.(tea.KeyMsg); ok && keyMsg.String() == "esc" {
			m.newing = false
			m.newForm = nil
			return m, nil
		}
		if ws, ok := msg.(tea.WindowSizeMsg); ok {
			m.width = ws.Width
			m.height = ws.Height
			m.newForm = m.newForm.WithWidth(m.formWidth())
		}
		newForm, cmd := m.newForm.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.newForm = f
		}
		if m.newForm.State == huh.StateCompleted {
			m.newing = false
			m.newForm = nil
			m.createConfig()
		}
		return m, cmd
	}

	if m.deleting && m.deleteForm != nil {
		if keyMsg, ok := msg.(tea.KeyMsg); ok && keyMsg.String() == "esc" {
			m.deleting = false
			m.deleteForm = nil
			return m, nil
		}
		if ws, ok := msg.(tea.WindowSizeMsg); ok {
			m.width = ws.Width
			m.height = ws.Height
			m.deleteForm = m.deleteForm.WithWidth(m.formWidth())
		}
		newForm, cmd := m.deleteForm.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.deleteForm = f
		}
		if m.deleteForm.State == huh.StateCompleted {
			confirmed := m.deleteConfirm
			m.deleting = false
			m.deleteForm = nil
			if confirmed {
				m.deleteSelected()
			}
		}
		return m, cmd
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.updateSize()
		return m, nil
	case tea.KeyMsg:
		if key.Matches(msg, keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, keys.Back) {
			return m, func() tea.Msg { return components.PopScreenMsg{} }
		}
		if key.Matches(msg, keys.New) {
			m.newing = true
			m.newName = ""
			m.newTransport = "stdio"
			return m, m.buildNewForm()
		}
		if key.Matches(msg, keys.Delete) {
			if m.list.SelectedItem() == nil {
				return m, nil
			}
			m.deleting = true
			return m, m.buildDeleteForm()
		}
		if key.Matches(msg, keys.Enter) {
			if item, ok := m.list.SelectedItem().(item); ok && m.onSelect != nil {
				m.onSelect(item.name, item.path)
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BrowserModel) createConfig() {
	name := strings.TrimSpace(m.newName)
	if name == "" {
		return
	}
	name = strings.TrimSuffix(filepath.Base(name), ".json")
	filePath := filepath.Join(m.dir, name+".json")
	if err := os.MkdirAll(m.dir, 0755); err != nil {
		m.errMsg = err.Error()
		return
	}
	if err := os.WriteFile(filePath, NewTemplateWithName(m.newTransport, name), 0644); err != nil {
		m.errMsg = err.Error()
		return
	}
	m.refreshList()
}

func (m *BrowserModel) deleteSelected() {
	item, ok := m.list.SelectedItem().(item)
	if !ok {
		return
	}
	if err := os.Remove(item.path); err != nil {
		m.errMsg = err.Error()
		return
	}
	m.refreshList()
}

func (m *BrowserModel) ShortHelp() []key.Binding {
	return keys.ShortHelp()
}

func (m *BrowserModel) FullHelp() [][]key.Binding {
	return keys.FullHelp()
}

func (m *BrowserModel) View() string {
	var parts []string
	if m.errMsg != "" {
		parts = append(parts, style.ErrorText(m.errMsg))
	}
	if m.newing && m.newForm != nil {
		parts = append(parts, m.newForm.View())
	} else if m.deleting && m.deleteForm != nil {
		parts = append(parts, m.deleteForm.View())
	} else {
		parts = append(parts, m.list.View())
	}
	parts = append(parts, m.help.View(m))
	return strings.Join(parts, "\n")
}
