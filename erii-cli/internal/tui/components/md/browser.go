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
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// BrowserKeyMap defines keybindings for the markdown browser.
type BrowserKeyMap struct {
	Up          key.Binding
	Down        key.Binding
	View        key.Binding
	Edit        key.Binding
	Enter       key.Binding
	Back        key.Binding
	New         key.Binding
	EditContent key.Binding
	EditFront   key.Binding
	Delete      key.Binding
	Help        key.Binding
	Quit        key.Binding
}

func (k BrowserKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.EditContent, k.EditFront, k.Back, k.Help, k.Quit}
}

func (k BrowserKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter, k.New},
		{k.EditContent, k.EditFront, k.Delete},
		{k.Back, k.Help, k.Quit},
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
	New: key.NewBinding(
		key.WithKeys("ctrl+n"),
		key.WithHelp("ctrl+n", "new"),
	),
	EditContent: key.NewBinding(
		key.WithKeys("ctrl+e"),
		key.WithHelp("ctrl+e", "content"),
	),
	EditFront: key.NewBinding(
		key.WithKeys("ctrl+f"),
		key.WithHelp("ctrl+f", "frontmatter"),
	),
	Delete: key.NewBinding(
		key.WithKeys("ctrl+d"),
		key.WithHelp("ctrl+d", "delete"),
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
	dir           string
	title         string
	list          list.Model
	width         int
	height        int
	keys          BrowserKeyMap
	help          help.Model
	viewer        *ViewerModel
	newFileModel  *NewFileModel
	contentEditor *ContentEditorModel
	frontEditor   *FrontmatterEditorModel
	fieldBrowser  *FieldBrowserModel
	deleting      bool
	deleteConfirm bool
	deleteForm    *huh.Form
	quitting      bool
	errMsg        string
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
	// Delegate to viewer/editors if active
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
			m.refreshList()
		}
		return m, cmd
	}

	if m.newFileModel != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newModel, cmd := m.newFileModel.Update(msg)
		if v, ok := newModel.(*NewFileModel); ok {
			m.newFileModel = v
		}
		if m.newFileModel.done {
			m.newFileModel = nil
			m.refreshList()
		}
		return m, cmd
	}

	if m.contentEditor != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newModel, cmd := m.contentEditor.Update(msg)
		if v, ok := newModel.(*ContentEditorModel); ok {
			m.contentEditor = v
		}
		if m.contentEditor.done {
			m.contentEditor = nil
			m.refreshList()
		}
		return m, cmd
	}

	if m.frontEditor != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newModel, cmd := m.frontEditor.Update(msg)
		if v, ok := newModel.(*FrontmatterEditorModel); ok {
			m.frontEditor = v
		}
		if m.frontEditor.done {
			m.frontEditor = nil
			m.refreshList()
		}
		return m, cmd
	}

	if m.fieldBrowser != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newModel, cmd := m.fieldBrowser.Update(msg)
		if v, ok := newModel.(*FieldBrowserModel); ok {
			m.fieldBrowser = v
		}
		if m.fieldBrowser != nil && m.fieldBrowser.done {
			m.fieldBrowser = nil
			m.refreshList()
		}
		return m, cmd
	}

	if m.deleting && m.deleteForm != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
			w := 60
			if msg.Width > 16 {
				w = msg.Width - 8
				if w > 60 {
					w = 60
				}
			}
			m.deleteForm = m.deleteForm.WithWidth(w)
			return m, nil
		case tea.KeyMsg:
			if msg.String() == "esc" {
				m.deleting = false
				m.deleteForm = nil
				return m, nil
			}
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
				if item, ok := m.list.SelectedItem().(mdItem); ok {
					os.Remove(item.path)
				}
			}
			m.refreshList()
			items := m.list.Items()
			if len(items) > 0 && m.list.Index() >= len(items) {
				m.list.Select(len(items) - 1)
			}
			return m, cmd
		}
		return m, cmd
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = m.width
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
		if key.Matches(msg, m.keys.New) {
			isSouls := strings.Contains(strings.ToLower(m.dir), "souls")
			var defaultFM string
			if isSouls {
				defaultFM = "---\ncharacter: |\n  \nemoticon: RESENTMENT\nid: ciallo\nname: Ciallo\n---\n\n"
			} else {
				defaultFM = "---\nglobal: false\ngroupId: \"\"\nbotId: \"\"\n---\n\n"
			}
			m.newFileModel = NewNewFileModel(m.dir, func(fileName string) {
				fullPath := filepath.Join(m.dir, fileName)
				os.WriteFile(fullPath, []byte(defaultFM), 0644)
			}, nil)
			return m, m.newFileModel.Init()
		}
		if key.Matches(msg, m.keys.EditContent) {
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				data, _ := os.ReadFile(item.path)
				content := string(data)
				frontmatter := extractFrontmatterBlock(content)
				content = stripFrontmatter(content)
				m.contentEditor = NewContentEditorModel(item.path, content, frontmatter, func() {
					m.refreshList()
				}, nil)
				return m, m.contentEditor.Init()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.EditFront) {
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				data, _ := os.ReadFile(item.path)
				frontmatter := parseFrontmatter(string(data))
				m.fieldBrowser = NewFieldBrowserModel(item.path, item.name, frontmatter, func() {
					m.refreshList()
				}, nil)
				return m, m.fieldBrowser.Init()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Delete) {
			if _, ok := m.list.SelectedItem().(mdItem); ok {
				m.deleting = true
				return m, m.buildDeleteConfirmForm()
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
	if m.newFileModel != nil {
		return m.newFileModel.View()
	}
	if m.contentEditor != nil {
		return m.contentEditor.View()
	}
	if m.frontEditor != nil {
		return m.frontEditor.View()
	}
	if m.fieldBrowser != nil {
		return m.fieldBrowser.View()
	}
	if m.deleting && m.deleteForm != nil {
		var b strings.Builder
		b.WriteString(style.Title("Delete") + "\n\n")
		b.WriteString(m.deleteForm.View())
		return b.String()
	}

	var b strings.Builder
	b.WriteString(m.list.View())
	if m.errMsg != "" {
		b.WriteString("\n\n" + style.ErrorText(m.errMsg))
	}
	b.WriteString("\n" + m.help.View(m.keys))
	return b.String()
}

func (m *BrowserModel) refreshList() {
	items, err := loadMdItems(m.dir)
	if err != nil {
		m.errMsg = err.Error()
	} else {
		m.errMsg = ""
		if len(items) == 0 {
			m.errMsg = "No files found"
		}
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title(m.title)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	if m.list.Items() != nil && len(items) > 0 {
		idx := m.list.Index()
		if idx >= len(items) {
			idx = len(items) - 1
		}
		if idx < 0 {
			idx = 0
		}
		l.Select(idx)
	}

	m.list = l
	if m.width > 0 && m.height > 0 {
		m.list.SetSize(m.width, m.height-4)
	}
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

func (m *BrowserModel) buildDeleteConfirmForm() tea.Cmd {
	w := 60
	if m.width > 16 {
		w = m.width - 8
		if w > 60 {
			w = 60
		}
	}
	m.deleteConfirm = false
	m.deleteForm = huh.NewForm(
		huh.NewGroup(
			huh.NewConfirm().
				Title("Delete this file?").
				Affirmative("Yes").
				Negative("No").
				Value(&m.deleteConfirm).
				Key("confirm"),
		),
	).WithWidth(w).WithShowHelp(false)
	return m.deleteForm.Init()
}
