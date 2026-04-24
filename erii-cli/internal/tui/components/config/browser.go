package config

import (
	"fmt"
	"strings"
	"time"

	"erii-cli/internal/config/tree"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

type clearMsg struct{}

func clearAfter(d time.Duration) tea.Cmd {
	return tea.Tick(d, func(time.Time) tea.Msg { return clearMsg{} })
}

// BrowserKeyMap defines keybindings for the config browser.
type BrowserKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Enter  key.Binding
	Back   key.Binding
	New    key.Binding
	Rename key.Binding
	Delete key.Binding
	Save   key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k BrowserKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Back, k.Help, k.Quit}
}

func (k BrowserKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter},
		{k.New, k.Rename, k.Delete, k.Save},
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
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "open/edit"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "left", "h"),
		key.WithHelp("esc/←/h", "back"),
	),
	New: key.NewBinding(
		key.WithKeys("n", "ctrl+n"),
		key.WithHelp("n/ctrl+n", "new item"),
	),
	Rename: key.NewBinding(
		key.WithKeys("ctrl+r"),
		key.WithHelp("ctrl+r", "rename"),
	),
	Delete: key.NewBinding(
		key.WithKeys("ctrl+d"),
		key.WithHelp("ctrl+d", "delete"),
	),
	Save: key.NewBinding(
		key.WithKeys("ctrl+s"),
		key.WithHelp("ctrl+s", "save"),
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

// nodeItem wraps a ConfigNode for the list delegate.
type nodeItem struct {
	node tree.ConfigNode
}

func (i nodeItem) Title() string {
	return i.node.Title()
}

func (i nodeItem) Description() string {
	if i.node.IsLeaf() {
		leaf := i.node.(*tree.LeafNode)
		switch leaf.ValueType() {
		case tree.TypeArray:
			switch v := leaf.Value().(type) {
			case []string:
				return fmt.Sprintf("array[%d]", len(v))
			case []any:
				return fmt.Sprintf("array[%d]", len(v))
			default:
				return "array"
			}
		case tree.TypeObject:
			return "object"
		case tree.TypeBool:
			if leaf.Value() == nil {
				return "(empty)"
			}
			return fmt.Sprintf("%v", leaf.Value())
		case tree.TypeNumber:
			if leaf.Value() == nil {
				return "(empty)"
			}
			return fmt.Sprintf("%v", leaf.Value())
		case tree.TypeText:
			s := fmt.Sprintf("%v", leaf.Value())
			if s == "" || s == "<nil>" {
				return "(empty)"
			}
			if len(s) > 40 {
				s = s[:40] + "..."
			}
			return s
		default:
			s := fmt.Sprintf("%v", leaf.Value())
			if s == "" || s == "<nil>" {
				return "(empty)"
			}
			if len(s) > 40 {
				s = s[:40] + "..."
			}
			return s
		}
	}
	return i.node.Description()
}

func (i nodeItem) FilterValue() string { return i.node.Title() }

// BrowserModel is a generic config file browser using list + node tree.
type BrowserModel struct {
	root          tree.ConfigNode
	current       *tree.BranchNode
	stack         []*tree.BranchNode
	list          list.Model
	width         int
	height        int
	keys          BrowserKeyMap
	help          help.Model
	onEdit        func(leaf *tree.LeafNode, onSave func() tea.Cmd)
	onSaveFile    func(root tree.ConfigNode) error
	title         string
	errMsg        string
	successMsg    string
	adding        bool
	addTitle      string
	addDesc       string
	addForm       *huh.Form
	renaming      bool
	renameValue   string
	renameDesc    string
	renameForm    *huh.Form
	deleting      bool
	deleteConfirm bool
	deleteForm    *huh.Form
}

func NewBrowserModel(root tree.ConfigNode, title string, onEdit func(leaf *tree.LeafNode, onSave func() tea.Cmd), onSaveFile func(root tree.ConfigNode) error) *BrowserModel {
	branch, ok := root.(*tree.BranchNode)
	if !ok {
		// Wrap single leaf in a branch
		branch = tree.NewBranch("root", "Configuration")
		branch.AddChild(root)
	}

	m := &BrowserModel{
		root:       root,
		current:    branch,
		stack:      []*tree.BranchNode{},
		keys:       DefaultBrowserKeys,
		help:       help.New(),
		onEdit:     onEdit,
		onSaveFile: onSaveFile,
		title:      title,
	}
	m.refreshList()
	return m
}

func (m *BrowserModel) refreshList() {
	items := make([]list.Item, 0, len(m.current.Children()))
	for _, child := range m.current.Children() {
		items = append(items, nodeItem{node: child})
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New(items, delegate, 0, 0)
	title := m.current.Title()
	if len(m.stack) == 0 && title == "root" && m.title != "" {
		title = m.title
	}
	l.Title = style.Title(title)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	if m.list.Items() != nil {
		// Preserve index if possible
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
	m.updateSize()
}

func (m *BrowserModel) updateSize() {
	if m.width > 0 && m.height > 0 {
		// Account for borders, padding, title, help
		m.list.SetSize(m.width, m.height-4)
		m.help.Width = m.width
	}
}

// currentPath builds the dot-separated path from root to current branch (without "root." prefix).
func (m *BrowserModel) currentPath() string {
	parts := []string{}
	for _, b := range m.stack {
		if b.Title() != "root" {
			parts = append(parts, b.Title())
		}
	}
	if m.current.Title() != "root" {
		parts = append(parts, m.current.Title())
	}
	return strings.Join(parts, ".")
}

func (m *BrowserModel) saveAndNotify() tea.Cmd {
	if m.onSaveFile == nil {
		return nil
	}
	if err := m.onSaveFile(m.root); err != nil {
		m.errMsg = err.Error()
		m.successMsg = ""
	} else {
		m.successMsg = "Saved!"
		m.errMsg = ""
	}
	return clearAfter(500 * time.Millisecond)
}

func (m *BrowserModel) buildAddForm() tea.Cmd {
	w := 60
	if m.width > 16 {
		w = m.width - 8
		if w > 60 {
			w = 60
		}
	}
	m.addForm = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Title").
				Placeholder("e.g. your_bot").
				Value(&m.addTitle).
				Key("title"),
			huh.NewInput().
				Title("Description").
				Placeholder("(empty)").
				Value(&m.addDesc).
				Key("desc"),
		),
	).WithWidth(w).WithShowHelp(false)
	return m.addForm.Init()
}

func (m *BrowserModel) buildRenameForm() tea.Cmd {
	w := 60
	if m.width > 16 {
		w = m.width - 8
		if w > 60 {
			w = 60
		}
	}
	m.renameForm = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Name").
				Placeholder("new name").
				Value(&m.renameValue).
				Key("name"),
			huh.NewInput().
				Title("Description").
				Placeholder("(empty)").
				Value(&m.renameDesc).
				Key("desc"),
		),
	).WithWidth(w).WithShowHelp(false)
	return m.renameForm.Init()
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
				Title("Delete this item?").
				Affirmative("Yes").
				Negative("No").
				Value(&m.deleteConfirm).
				Key("confirm"),
		),
	).WithWidth(w).WithShowHelp(false)
	return m.deleteForm.Init()
}

func (m *BrowserModel) Init() tea.Cmd {
	return nil
}

func (m *BrowserModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.adding && m.addForm != nil {
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
			m.addForm = m.addForm.WithWidth(w)
			return m, nil
		case tea.KeyMsg:
			if msg.String() == "esc" {
				m.adding = false
				m.addForm = nil
				return m, nil
			}
		}
		newForm, cmd := m.addForm.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.addForm = f
		}
		if m.addForm.State == huh.StateCompleted {
			title := strings.TrimSpace(m.addTitle)
			desc := strings.TrimSpace(m.addDesc)
			if desc == "" {
				desc = "(empty)"
			}
			m.adding = false
			m.addForm = nil
			if title != "" {
				m.current.AddChild(m.createNodeFromTemplate(title, desc))
				m.refreshList()
				m.list.Select(len(m.current.Children()) - 1)
				nodePath := m.currentPath()
				if nodePath != "" {
					nodePath = nodePath + "." + title
				} else {
					nodePath = title
				}
				if rawDesc := strings.TrimSpace(m.addDesc); rawDesc != "" {
					_ = tree.SaveDesc(nodePath, rawDesc)
				}
			}
		}
		return m, tea.Batch(cmd, m.saveAndNotify())
	}

	if m.renaming && m.renameForm != nil {
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
			m.renameForm = m.renameForm.WithWidth(w)
			return m, nil
		case tea.KeyMsg:
			if msg.String() == "esc" {
				m.renaming = false
				m.renameForm = nil
				return m, nil
			}
		}
		newForm, cmd := m.renameForm.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.renameForm = f
		}
		if m.renameForm.State == huh.StateCompleted {
			newName := strings.TrimSpace(m.renameValue)
			newDesc := strings.TrimSpace(m.renameDesc)
			if newDesc == "" {
				newDesc = "(empty)"
			}
			m.renaming = false
			m.renameForm = nil
			if newName != "" {
				idx := m.list.Index()
				if idx >= 0 && idx < len(m.current.Children()) {
					child := m.current.Children()[idx]
					if b, ok := child.(*tree.BranchNode); ok {
						b.SetTitle(newName)
					} else if l, ok := child.(*tree.LeafNode); ok {
						l.SetTitle(newName)
					}
					if b, ok := child.(*tree.BranchNode); ok {
						b.SetDescription(newDesc)
					} else if l, ok := child.(*tree.LeafNode); ok {
						l.SetDescription(newDesc)
					}
					m.refreshList()
					m.list.Select(idx)
				}
				nodePath := m.currentPath()
				if nodePath != "" {
					nodePath = nodePath + "." + newName
				} else {
					nodePath = newName
				}
				if rawDesc := strings.TrimSpace(m.renameDesc); rawDesc != "" {
					_ = tree.SaveDesc(nodePath, rawDesc)
				}
			}
			return m, tea.Batch(cmd, m.saveAndNotify())
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
				idx := m.list.Index()
				if m.current.RemoveChildAt(idx) {
					m.refreshList()
				}
			}
			return m, tea.Batch(cmd, m.saveAndNotify())
		}
		return m, cmd
	}

	switch msg := msg.(type) {
	case clearMsg:
		m.successMsg = ""
		m.errMsg = ""
		return m, nil
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.updateSize()
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Back) {
			if len(m.stack) > 0 {
				m.current = m.stack[len(m.stack)-1]
				m.stack = m.stack[:len(m.stack)-1]
				m.refreshList()
			} else {
				return m, func() tea.Msg { return components.PopScreenMsg{} }
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Save) {
			if m.onSaveFile != nil {
				if err := m.onSaveFile(m.root); err != nil {
					m.errMsg = err.Error()
					m.successMsg = ""
					return m, clearAfter(500 * time.Millisecond)
				}
				m.successMsg = "Saved!"
				m.errMsg = ""
				return m, clearAfter(500 * time.Millisecond)
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			if item, ok := m.list.SelectedItem().(nodeItem); ok {
				if item.node.IsLeaf() {
					leaf := item.node.(*tree.LeafNode)
					if m.onEdit != nil {
						m.onEdit(leaf, func() tea.Cmd {
							m.refreshList()
							if m.onSaveFile != nil {
								if err := m.onSaveFile(m.root); err != nil {
									m.errMsg = err.Error()
									m.successMsg = ""
								} else {
									m.successMsg = "Saved!"
									m.errMsg = ""
								}
								return clearAfter(500 * time.Millisecond)
							}
							return nil
						})
					}
				} else {
					branch := item.node.(*tree.BranchNode)
					m.stack = append(m.stack, m.current)
					m.current = branch
					m.refreshList()
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.New) {
			if !tree.CanCopy(m.currentPath()) {
				return m, nil
			}
			m.adding = true
			m.addTitle = ""
			m.addDesc = ""
			if m.current.IsArray() {
				m.addTitle = fmt.Sprintf("[%d]", len(m.current.Children()))
			}
			return m, m.buildAddForm()
		}
		if key.Matches(msg, m.keys.Rename) {
			if !tree.CanCopy(m.currentPath()) {
				return m, nil
			}
			if item, ok := m.list.SelectedItem().(nodeItem); ok {
				if !item.node.IsLeaf() {
					m.renaming = true
					m.renameValue = item.node.Title()
					m.renameDesc = item.node.Description()
					return m, m.buildRenameForm()
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Delete) {
			if !tree.CanCopy(m.currentPath()) {
				return m, nil
			}
			m.deleting = true
			return m, m.buildDeleteConfirmForm()
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func cloneStructure(node tree.ConfigNode) tree.ConfigNode {
	if leaf, ok := node.(*tree.LeafNode); ok {
		var emptyVal any
		switch leaf.ValueType() {
		case tree.TypeString, tree.TypeText, tree.TypeEnum:
			emptyVal = ""
		case tree.TypeNumber:
			emptyVal = float64(0)
		case tree.TypeBool:
			emptyVal = false
		case tree.TypeArray:
			emptyVal = []string{}
		}
		newLeaf := tree.NewLeaf(leaf.Title(), leaf.Description(), leaf.ValueType(), emptyVal)
		if leaf.ValueType() == tree.TypeEnum {
			newLeaf.SetOptions(leaf.Options())
		}
		return newLeaf
	}
	if branch, ok := node.(*tree.BranchNode); ok {
		newBranch := tree.NewBranch(branch.Title(), branch.Description())
		newBranch.SetIsArray(branch.IsArray())
		for _, child := range branch.Children() {
			newBranch.AddChild(cloneStructure(child))
		}
		return newBranch
	}
	return nil
}

func (m *BrowserModel) createNodeFromTemplate(title, desc string) tree.ConfigNode {
	if len(m.current.Children()) > 0 {
		template := m.current.Children()[0]
		cloned := cloneStructure(template)
		if branch, ok := cloned.(*tree.BranchNode); ok {
			newBranch := tree.NewBranch(title, desc)
			newBranch.SetIsArray(branch.IsArray())
			for _, child := range branch.Children() {
				newBranch.AddChild(child)
			}
			return newBranch
		}
		if leaf, ok := cloned.(*tree.LeafNode); ok {
			return tree.NewLeaf(title, desc, leaf.ValueType(), leaf.Value())
		}
	}
	return tree.NewBranch(title, desc)
}

func (m *BrowserModel) ShortHelp() []key.Binding {
	return m.keys.ShortHelp()
}

func (m *BrowserModel) FullHelp() [][]key.Binding {
	var middle []key.Binding
	if tree.CanCopy(m.currentPath()) {
		middle = append(middle, m.keys.New)
		if item, ok := m.list.SelectedItem().(nodeItem); ok {
			if !item.node.IsLeaf() {
				middle = append(middle, m.keys.Rename)
			}
			middle = append(middle, m.keys.Delete)
		}
	}
	middle = append(middle, m.keys.Save)
	return [][]key.Binding{
		{m.keys.Up, m.keys.Down, m.keys.Enter},
		middle,
		{m.keys.Back, m.keys.Help, m.keys.Quit},
	}
}

func (m *BrowserModel) View() string {
	if m.adding && m.addForm != nil {
		var b strings.Builder
		b.WriteString(style.Title("Add new item") + "\n\n")
		b.WriteString(m.addForm.View())
		b.WriteString("\n\n" + style.Muted("esc cancel • tab/shift+tab navigate • enter next/submit"))
		return b.String()
	}

	if m.renaming && m.renameForm != nil {
		var b strings.Builder
		b.WriteString(style.Title("Rename") + "\n\n")
		b.WriteString(m.renameForm.View())
		b.WriteString("\n\n" + style.Muted("esc cancel • tab/shift+tab navigate • enter next/submit"))
		return b.String()
	}

	if m.deleting && m.deleteForm != nil {
		var b strings.Builder
		b.WriteString(style.Title("Delete") + "\n\n")
		b.WriteString(m.deleteForm.View())
		b.WriteString("\n\n" + style.Muted("esc cancel • ←/→ select • enter confirm"))
		return b.String()
	}

	var b string
	b = m.list.View()

	if m.errMsg != "" {
		b += "\n" + style.ErrorText("Error: "+m.errMsg)
	}
	if m.successMsg != "" {
		b += "\n" + style.SuccessText(m.successMsg)
	}

	b += "\n" + m.help.View(m)
	return b
}
