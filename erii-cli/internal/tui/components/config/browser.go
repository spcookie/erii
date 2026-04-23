package config

import (
	"fmt"

	"erii-cli/internal/config/tree"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// BrowserKeyMap defines keybindings for the config browser.
type BrowserKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Enter  key.Binding
	Back   key.Binding
	New    key.Binding
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
		{k.New, k.Delete, k.Save},
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
		key.WithKeys("n"),
		key.WithHelp("n", "new item"),
	),
	Delete: key.NewBinding(
		key.WithKeys("d"),
		key.WithHelp("d", "delete"),
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
	root       tree.ConfigNode
	current    *tree.BranchNode
	stack      []*tree.BranchNode
	list       list.Model
	width      int
	height     int
	keys       BrowserKeyMap
	help       help.Model
	onEdit     func(leaf *tree.LeafNode, onSave func())
	onSaveFile func(root tree.ConfigNode) error
	title      string
	errMsg     string
	successMsg string
}

func NewBrowserModel(root tree.ConfigNode, title string, onEdit func(leaf *tree.LeafNode, onSave func()), onSaveFile func(root tree.ConfigNode) error) *BrowserModel {
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

func (m *BrowserModel) Init() tea.Cmd {
	return nil
}

func (m *BrowserModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
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
				} else {
					m.successMsg = "Saved!"
					m.errMsg = ""
				}
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			if item, ok := m.list.SelectedItem().(nodeItem); ok {
				if item.node.IsLeaf() {
					leaf := item.node.(*tree.LeafNode)
					if m.onEdit != nil {
						m.onEdit(leaf, func() {
							m.refreshList()
							if m.onSaveFile != nil {
								if err := m.onSaveFile(m.root); err != nil {
									m.errMsg = err.Error()
									m.successMsg = ""
								} else {
									m.successMsg = "Saved!"
									m.errMsg = ""
								}
							}
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
			// Allow adding new items to array-like or empty branches
			desc := m.current.Description()
			isArray := len(desc) >= 5 && (desc[:5] == "Array" || desc[:5] == "array")
			if len(m.current.Children()) == 0 || isArray {
				idx := len(m.current.Children())
				newBranch := tree.NewBranch(fmt.Sprintf("[%d]", idx), "")
				m.current.AddChild(newBranch)
				m.refreshList()
				m.list.Select(len(m.current.Children()) - 1)
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Delete) {
			idx := m.list.Index()
			if m.current.RemoveChildAt(idx) {
				m.refreshList()
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BrowserModel) View() string {
	var b string
	b = m.list.View()

	if m.errMsg != "" {
		b += "\n" + style.ErrorText("Error: "+m.errMsg)
	}
	if m.successMsg != "" {
		b += "\n" + style.SuccessText(m.successMsg)
	}

	b += "\n" + m.help.View(m.keys)
	return b
}
