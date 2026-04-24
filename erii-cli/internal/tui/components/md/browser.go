package md

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// BrowserKeyMap defines keybindings for the markdown browser.
type BrowserKeyMap struct {
	Up    key.Binding
	Down  key.Binding
	View  key.Binding
	Edit  key.Binding
	Enter key.Binding
	Back  key.Binding
	Help  key.Binding
	Quit  key.Binding
}

func (k BrowserKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Edit, k.Back, k.Help, k.Quit}
}

func (k BrowserKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter},
		{k.Edit, k.Back},
		{k.Help, k.Quit},
	}
}

var DefaultBrowserKeys = BrowserKeyMap{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	View: key.NewBinding(
		key.WithKeys(""),
		key.WithHelp("", ""),
	),
	Edit: key.NewBinding(
		key.WithKeys("ctrl+g"),
		key.WithHelp("ctrl+g", "edit ($EDITOR)"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "view"),
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

// mdItem represents a markdown file in the list.
type mdItem struct {
	name        string
	path        string
	description string
}

func (i mdItem) Title() string       { return i.name }
func (i mdItem) Description() string { return i.description }
func (i mdItem) FilterValue() string { return i.name }

// BrowserModel lists markdown files and allows viewing/editing.
type BrowserModel struct {
	dir      string
	title    string
	list     list.Model
	width    int
	height   int
	keys     BrowserKeyMap
	help     help.Model
	viewer   *ViewerModel
	quitting bool
	errMsg   string
}

func NewBrowserModel(dir, title string) *BrowserModel {
	items, err := loadMdItems(dir)
	errMsg := ""
	if err != nil {
		errMsg = fmt.Sprintf("Failed to load %s: %v", dir, err)
	} else if len(items) == 0 {
		errMsg = fmt.Sprintf("No files found in %s", dir)
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title(title)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &BrowserModel{
		dir:    dir,
		title:  title,
		list:   l,
		keys:   DefaultBrowserKeys,
		help:   help.New(),
		errMsg: errMsg,
	}
}

func (m *BrowserModel) Init() tea.Cmd { return nil }

func (m *BrowserModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	// Delegate to viewer if active
	if m.viewer != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newViewer, cmd := m.viewer.Update(msg)
		if v, ok := newViewer.(*ViewerModel); ok {
			m.viewer = v
		}
		if m.viewer.done {
			m.viewer = nil
		}
		return m, cmd
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			m.quitting = true
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return components.PopScreenMsg{} }
		}
		if key.Matches(msg, m.keys.Enter) {
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				m.viewer = NewViewerModel(item.path, item.name)
				return m, func() tea.Msg {
					return tea.WindowSizeMsg{Width: m.width, Height: m.height}
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Edit) {
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				editor := os.Getenv("EDITOR")
				if editor == "" {
					editor = "notepad"
				}
				cmd := exec.Command(editor, item.path)
				cmd.Stdin = os.Stdin
				cmd.Stdout = os.Stdout
				cmd.Stderr = os.Stderr
				return m, tea.ExecProcess(cmd, func(err error) tea.Msg {
					return components.RefreshMsg{}
				})
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BrowserModel) View() string {
	if m.quitting {
		return ""
	}
	if m.viewer != nil {
		return m.viewer.View()
	}

	var b strings.Builder
	b.WriteString(m.list.View())
	if m.errMsg != "" {
		b.WriteString("\n\n" + style.ErrorText(m.errMsg))
	}
	b.WriteString("\n" + m.help.View(m.keys))
	return b.String()
}

func loadMdItems(dir string) ([]list.Item, error) {
	items := []list.Item{}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return items, err
	}
	for _, e := range entries {
		if e.IsDir() || filepath.Ext(e.Name()) != ".md" {
			continue
		}
		path := filepath.Join(dir, e.Name())
		desc := extractMdDescription(path)
		items = append(items, mdItem{name: e.Name(), path: path, description: desc})
	}
	return items, nil
}

func extractMdDescription(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	lines := strings.Split(string(data), "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		// Skip title
		if strings.HasPrefix(line, "#") {
			continue
		}
		if len(line) > 80 {
			line = line[:80] + "..."
		}
		return line
	}
	return ""
}
