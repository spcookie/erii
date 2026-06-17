package config

import (
	"fmt"
	"strings"

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

// BrowserKeyMap defines keybindings for the config browser.
type BrowserKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Enter  key.Binding
	Back   key.Binding
	New    key.Binding
	Rename key.Binding
	Delete key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k BrowserKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Back, k.Help, k.Quit}
}

func (k BrowserKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter},
		{k.New, k.Rename, k.Delete},
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
		key.WithKeys("esc", "left"),
		key.WithHelp("esc/←", "back"),
	),
	New: key.NewBinding(
		key.WithKeys("ctrl+n"),
		key.WithHelp("ctrl+n", "new item"),
	),
	Rename: key.NewBinding(
		key.WithKeys("ctrl+r"),
		key.WithHelp("ctrl+r", "rename"),
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
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
}

// NodeItem wraps a ConfigNode for the list delegate.
type NodeItem struct {
	Node tree.ConfigNode
}

func (i NodeItem) Title() string {
	return i.Node.Title()
}

func (i NodeItem) Description() string {
	if !i.Node.IsLeaf() {
		return i.Node.Description()
	}
	leaf := i.Node.(*tree.LeafNode)
	if leaf.IsNull() {
		return "(null)"
	}
	return formatLeafValue(leaf)
}

func formatLeafValue(leaf *tree.LeafNode) string {
	switch leaf.ValueType() {
	case tree.TypeArray:
		return formatArrayValue(leaf.Value())
	case tree.TypeObject:
		return "object"
	case tree.TypeBool, tree.TypeNumber:
		if leaf.Value() == nil {
			return "(empty)"
		}
		return fmt.Sprintf("%v", leaf.Value())
	case tree.TypeText, tree.TypeString:
		return formatStringValue(leaf.Value())
	default:
		return formatStringValue(leaf.Value())
	}
}

func formatArrayValue(v any) string {
	switch val := v.(type) {
	case []string:
		return fmt.Sprintf("array[%d]", len(val))
	case []any:
		return fmt.Sprintf("array[%d]", len(val))
	default:
		return "array"
	}
}

func formatStringValue(v any) string {
	s := fmt.Sprintf("%v", v)
	if s == "" || s == "<nil>" {
		return "(empty)"
	}
	if len(s) > 40 {
		s = s[:40] + "..."
	}
	return s
}

func (i NodeItem) FilterValue() string { return i.Node.Title() }

// BrowserModel is a generic config file browser using list + node tree.
type BrowserModel struct {
	Root            tree.ConfigNode
	current         *tree.BranchNode
	stack           []*tree.BranchNode
	List            list.Model
	width           int
	height          int
	Keys            BrowserKeyMap
	help            help.Model
	onEdit          func(leaf *tree.LeafNode, onSave func() tea.Cmd)
	OnSaveFile      func(root tree.ConfigNode) error
	title           string
	pluginName      string
	errMsg          string
	adding          bool
	addTitle        string
	addDesc         string
	addType         string
	addForm         *huh.Form
	objectCtxPath   string
	objectCtxResult bool
	renaming        bool
	renameValue     string
	renameDesc      string
	renameForm      *huh.Form
	deleting        bool
	deleteConfirm   bool
	deleteForm      *huh.Form
	editable        bool
	newItemFactory  func(title, desc string) tree.ConfigNode
}

func NewBrowserModel(root tree.ConfigNode, title string, onEdit func(leaf *tree.LeafNode, onSave func() tea.Cmd), OnSaveFile func(root tree.ConfigNode) error) *BrowserModel {
	branch, ok := root.(*tree.BranchNode)
	if !ok {
		// Wrap single leaf in a branch
		branch = tree.NewBranch("root", "Configuration")
		branch.AddChild(root)
	}

	m := &BrowserModel{
		Root:       root,
		current:    branch,
		stack:      []*tree.BranchNode{},
		Keys:       DefaultBrowserKeys,
		help:       help.New(),
		onEdit:     onEdit,
		OnSaveFile: OnSaveFile,
		title:      title,
		width:      80, // Default dimensions to avoid blank first render
		height:     24,
	}
	m.refreshList()
	m.List.SetSize(80, 20)
	return m
}

// WithPlugin sets the plugin context for metadata lookups.
func (m *BrowserModel) WithPlugin(name string) *BrowserModel {
	m.pluginName = name
	return m
}

func (m *BrowserModel) canModify() bool {
	return m.editable || tree.CanCopy(m.pluginName, m.currentPath()) || m.isObjectContext()
}

// isObjectContext checks if the current path is inside an object-typed config node.
func (m *BrowserModel) isObjectContext() bool {
	path := m.currentPath()
	if path == m.objectCtxPath {
		return m.objectCtxResult
	}
	m.objectCtxPath = path
	// Walk up path ancestors looking for an object-typed value config.
	for p := path; p != ""; {
		vc := tree.GetValueConfig(m.pluginName, p)
		if vc != nil && vc.Type == "object" {
			m.objectCtxResult = true
			return true
		}
		lastDot := strings.LastIndex(p, ".")
		if lastDot < 0 {
			break
		}
		p = p[:lastDot]
	}
	m.objectCtxResult = false
	return false
}

func (m *BrowserModel) autoSave() {
	if m.OnSaveFile != nil {
		if err := m.OnSaveFile(m.Root); err != nil {
			m.errMsg = err.Error()
		}
	}
}

// WithEditable enables add/delete/rename without copy.json permission.
func (m *BrowserModel) WithEditable(v bool) *BrowserModel {
	m.editable = v
	return m
}

// WithNewItemFactory sets a custom factory for creating new items.
func (m *BrowserModel) WithNewItemFactory(f func(title, desc string) tree.ConfigNode) *BrowserModel {
	m.newItemFactory = f
	return m
}

func (m *BrowserModel) refreshList() {
	items := make([]list.Item, 0, len(m.current.Children()))
	for _, child := range m.current.Children() {
		items = append(items, NodeItem{Node: child})
	}

	delegate := style.StyleDelegate(list.NewDefaultDelegate())

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

	if m.List.Items() != nil {
		// Preserve index if possible
		idx := m.List.Index()
		if idx >= len(items) {
			idx = len(items) - 1
		}
		if idx < 0 {
			idx = 0
		}
		l.Select(idx)
	}

	m.List = l
	m.updateSize()
}

func (m *BrowserModel) updateSize() {
	if m.width > 0 && m.height > 0 {
		// Account for borders, padding, title, help
		m.List.SetSize(m.width, m.height-4)
		m.help.Width = m.width
	}
}

// handleFormSizeAndCancel handles WindowSizeMsg and ESC cancellation for forms.
// Returns (formUpdated, cmd) where formUpdated indicates if form was resized.
func (m *BrowserModel) handleFormSizeAndCancel(msg tea.Msg, active *bool, form *huh.Form, setForm func(*huh.Form)) (bool, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		w := m.formWidth()
		newForm := form.WithWidth(w)
		setForm(newForm)
		return true, nil
	case tea.KeyMsg:
		if msg.String() == "esc" {
			*active = false
			setForm(nil)
			return false, nil
		}
	}
	return false, nil
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

// currentPath builds the dot-separated path from root to current branch (without "root." prefix).
func (m *BrowserModel) currentPath() string {
	var parts []string
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

var objectValueTypes = []string{"number", "string", "boolean", "array", "object"}

func (m *BrowserModel) buildAddForm() tea.Cmd {
	w := m.formWidth()
	fields := []huh.Field{
		huh.NewInput().
			Title("Title").
			Placeholder("(empty)").
			Value(&m.addTitle).
			Key("title"),
		huh.NewInput().
			Title("Description").
			Placeholder("(empty)").
			Value(&m.addDesc).
			Key("desc"),
	}
	if m.isObjectContext() {
		fields = append(fields,
			huh.NewSelect[string]().
				Title("Type").
				Options(huh.NewOptions(objectValueTypes...)...).
				Value(&m.addType).
				Key("type"),
		)
	}
	m.addForm = huh.NewForm(
		huh.NewGroup(fields...),
	).WithWidth(w).WithShowHelp(false)
	return m.addForm.Init()
}

func (m *BrowserModel) buildRenameForm() tea.Cmd {
	w := m.formWidth()
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
	w := m.formWidth()
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
		if handled, cmd := m.handleFormSizeAndCancel(msg, &m.adding, m.addForm, func(f *huh.Form) { m.addForm = f }); handled {
			return m, cmd
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
				if m.isObjectContext() {
					m.current.AddChild(m.createObjectValueNode(title, desc, m.addType))
				} else {
					m.current.AddChild(m.createNodeFromTemplate(title, desc))
				}
				m.refreshList()
				m.List.Select(len(m.current.Children()) - 1)
				nodePath := m.currentPath()
				if nodePath != "" {
					nodePath = nodePath + "." + title
				} else {
					nodePath = title
				}
				if rawDesc := strings.TrimSpace(m.addDesc); rawDesc != "" {
					_ = tree.SaveDesc(nodePath, rawDesc)
				}
				m.autoSave()
			}
		}
		return m, cmd
	}

	if m.renaming && m.renameForm != nil {
		if handled, cmd := m.handleFormSizeAndCancel(msg, &m.renaming, m.renameForm, func(f *huh.Form) { m.renameForm = f }); handled {
			return m, cmd
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
				idx := m.List.Index()
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
					m.List.Select(idx)
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
				m.autoSave()
			}
			return m, cmd
		}
		return m, cmd
	}

	if m.deleting && m.deleteForm != nil {
		if handled, cmd := m.handleFormSizeAndCancel(msg, &m.deleting, m.deleteForm, func(f *huh.Form) { m.deleteForm = f }); handled {
			return m, cmd
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
				idx := m.List.Index()
				if m.current.RemoveChildAt(idx) {
					m.refreshList()
					m.autoSave()
				}
			}
			return m, cmd
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
		if key.Matches(msg, m.Keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.Keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.Keys.Back) {
			if len(m.stack) > 0 {
				m.current = m.stack[len(m.stack)-1]
				m.stack = m.stack[:len(m.stack)-1]
				m.refreshList()
			} else {
				return m, func() tea.Msg { return components.PopScreenMsg{} }
			}
			return m, nil
		}
		if key.Matches(msg, m.Keys.Enter) {
			if item, ok := m.List.SelectedItem().(NodeItem); ok {
				if item.Node.IsLeaf() {
					leaf := item.Node.(*tree.LeafNode)
					if m.onEdit != nil {
						m.onEdit(leaf, func() tea.Cmd {
							// Save to file when editor triggers save
							if m.OnSaveFile != nil {
								if err := m.OnSaveFile(m.Root); err != nil {
									m.errMsg = err.Error()
								} else {
									m.errMsg = ""
								}
							}
							m.refreshList()
							return nil
						})
					}
				} else {
					branch := item.Node.(*tree.BranchNode)
					m.stack = append(m.stack, m.current)
					m.current = branch
					m.refreshList()
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.Keys.New) {
			if !m.canModify() {
				return m, nil
			}
			m.adding = true
			m.addTitle = ""
			m.addDesc = ""
			m.addType = "number"
			if m.current.IsArray() {
				m.addTitle = fmt.Sprintf("[%d]", len(m.current.Children()))
			}
			return m, m.buildAddForm()
		}
		if key.Matches(msg, m.Keys.Rename) {
			if !m.canModify() {
				return m, nil
			}
			if item, ok := m.List.SelectedItem().(NodeItem); ok {
				if !item.Node.IsLeaf() {
					m.renaming = true
					m.renameValue = item.Node.Title()
					m.renameDesc = item.Node.Description()
					return m, m.buildRenameForm()
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.Keys.Delete) {
			if !m.canModify() {
				return m, nil
			}
			m.deleting = true
			return m, m.buildDeleteConfirmForm()
		}
	}

	var cmd tea.Cmd
	m.List, cmd = m.List.Update(msg)
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
		case tree.TypeObject:
			emptyVal = map[string]any{}
		default:
			panic("unhandled default case")
		}
		// Copy original value if not null, otherwise use empty default
		val := emptyVal
		if !leaf.IsNull() && leaf.Value() != nil {
			val = leaf.Value()
		}
		newLeaf := tree.NewLeaf(leaf.Title(), leaf.Description(), leaf.ValueType(), val)
		newLeaf.SetNull(leaf.IsNull())
		newLeaf.SetEnvRef(leaf.IsEnvRef())
		newLeaf.SetValueConfig(leaf.ValueConfig())
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
	if m.newItemFactory != nil {
		return m.newItemFactory(title, desc)
	}
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

func (m *BrowserModel) createObjectValueNode(title, desc, valueType string) tree.ConfigNode {
	switch valueType {
	case "object":
		return tree.NewBranch(title, desc)
	case "array":
		return tree.NewLeaf(title, desc, tree.TypeArray, []string{})
	case "boolean":
		return tree.NewLeaf(title, desc, tree.TypeBool, false)
	case "number":
		return tree.NewLeaf(title, desc, tree.TypeNumber, float64(0))
	default:
		return tree.NewLeaf(title, desc, tree.TypeString, "")
	}
}

func (m *BrowserModel) ShortHelp() []key.Binding {
	return m.Keys.ShortHelp()
}

func (m *BrowserModel) FullHelp() [][]key.Binding {
	var middle []key.Binding
	if m.canModify() {
		middle = append(middle, m.Keys.New)
		if item, ok := m.List.SelectedItem().(NodeItem); ok {
			if !item.Node.IsLeaf() {
				middle = append(middle, m.Keys.Rename)
			}
			middle = append(middle, m.Keys.Delete)
		}
	}
	return [][]key.Binding{
		{m.Keys.Up, m.Keys.Down, m.Keys.Enter},
		middle,
		{m.Keys.Back, m.Keys.Help, m.Keys.Quit},
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
	b = m.List.View()

	if m.errMsg != "" {
		b += "\n" + style.ErrorText("Error: "+m.errMsg)
	}

	b += "\n" + m.help.View(m)
	return b
}
