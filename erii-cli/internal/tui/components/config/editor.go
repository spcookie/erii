package config

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"

	"erii-cli/internal/config/tree"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
)

// EditorKeyMap defines keybindings for the leaf editor.
type EditorKeyMap struct {
	Save    key.Binding
	Back    key.Binding
	PickEnv key.Binding
	Enter   key.Binding
	Quit    key.Binding
}

func (k EditorKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Save, k.Back, k.PickEnv, k.Enter, k.Quit}
}

func (k EditorKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Save, k.PickEnv, k.Enter},
		{k.Back, k.Quit},
	}
}

var DefaultEditorKeys = EditorKeyMap{
	Save: key.NewBinding(
		key.WithKeys("ctrl+s"),
		key.WithHelp("ctrl+s", "save"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "cancel"),
	),
	PickEnv: key.NewBinding(
		key.WithKeys("ctrl+e"),
		key.WithHelp("ctrl+e", "pick from env"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "submit"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
}

// envItem represents an env variable for the picker.
type envItem struct {
	key   string
	value string
}

func (i envItem) Title() string       { return i.key }
func (i envItem) Description() string { return i.value }
func (i envItem) FilterValue() string { return i.key + " " + i.value }

// LeafEditorModel edits a single leaf node using huh fields.
type LeafEditorModel struct {
	leaf          *tree.LeafNode
	width         int
	height        int
	keys          EditorKeyMap
	help          help.Model
	form          *huh.Form
	formValue     string
	formValueBool bool
	envList       list.Model
	pickingEnv    bool
	onSave        func() tea.Cmd
	quitting      bool
	errMsg        string
}

func NewLeafEditorModel(leaf *tree.LeafNode, onSave func() tea.Cmd) *LeafEditorModel {
	m := &LeafEditorModel{
		leaf:   leaf,
		keys:   DefaultEditorKeys,
		help:   help.New(),
		onSave: onSave,
	}
	m.formValue = leafValueToString(leaf)
	if v, ok := leaf.Value().(bool); ok {
		m.formValueBool = v
	}
	m.buildForm()
	// Set default dimensions so form renders correctly on first frame
	m.width = 80
	m.height = 24
	if m.form != nil {
		m.form = m.form.WithWidth(60)
	}
	return m
}

func leafValueToString(leaf *tree.LeafNode) string {
	switch leaf.ValueType() {
	case tree.TypeString, tree.TypeText, tree.TypeNumber:
		if v := leaf.Value(); v != nil {
			return fmt.Sprintf("%v", v)
		}
	case tree.TypeEnum:
		if v, ok := leaf.Value().(string); ok {
			return v
		}
	case tree.TypeArray:
		switch v := leaf.Value().(type) {
		case []string:
			return strings.Join(v, "\n")
		case []any:
			var items []string
			for _, a := range v {
				items = append(items, fmt.Sprintf("%v", a))
			}
			return strings.Join(items, "\n")
		}
	default:
		panic("unhandled default case")
	}
	return ""
}

func (m *LeafEditorModel) buildForm() {
	var fields []huh.Field

	switch m.leaf.ValueType() {
	case tree.TypeString:
		placeholder := ""
		if m.formValue == "" {
			placeholder = "(empty)"
		}
		fields = append(fields, huh.NewInput().
			Title(m.leaf.Title()).
			Description(m.leaf.Description()).
			Placeholder(placeholder).
			Value(&m.formValue).
			Key("value"))

	case tree.TypeText:
		placeholder := ""
		if m.formValue == "" {
			placeholder = "(empty)"
		}
		fields = append(fields, huh.NewText().
			Title(m.leaf.Title()).
			Description(m.leaf.Description()).
			Placeholder(placeholder).
			Value(&m.formValue).
			Key("value"))

	case tree.TypeNumber:
		placeholder := ""
		if m.formValue == "" {
			placeholder = "(empty)"
		}
		fields = append(fields, huh.NewInput().
			Title(m.leaf.Title()).
			Description(m.leaf.Description()).
			Placeholder(placeholder).
			Value(&m.formValue).
			Key("value"))

	case tree.TypeBool:
		fields = append(fields, huh.NewConfirm().
			Title(m.leaf.Title()).
			Description(m.leaf.Description()).
			Value(&m.formValueBool).
			Key("value"))

	case tree.TypeEnum:
		var opts []huh.Option[string]
		for _, o := range m.leaf.Options() {
			opts = append(opts, huh.NewOption(o, o))
		}
		fields = append(fields, huh.NewSelect[string]().
			Title(m.leaf.Title()).
			Description(m.leaf.Description()).
			Options(opts...).
			Value(&m.formValue).
			Key("value"))

	case tree.TypeArray:
		placeholder := ""
		if m.formValue == "" {
			placeholder = "(empty)"
		}
		fields = append(fields, huh.NewText().
			Title(m.leaf.Title()).
			Description("Enter values, one per line").
			Placeholder(placeholder).
			Value(&m.formValue).
			Key("value"))
	default:
		panic("unhandled default case")
	}

	if len(fields) > 0 {
		m.form = huh.NewForm(huh.NewGroup(fields...)).
			WithWidth(60).
			WithShowHelp(false)
	}
}

func (m *LeafEditorModel) loadEnvValues() map[string]string {
	data, err := os.ReadFile(path.EnvFile)
	if err != nil {
		return nil
	}
	result := make(map[string]string)
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx == -1 {
			continue
		}
		space := strings.TrimSpace(line[:idx])
		val := strings.TrimSpace(line[idx+1:])
		if space != "" {
			result[space] = val
		}
	}
	return result
}

func (m *LeafEditorModel) initEnvList() {
	if m.envList.Items() != nil {
		return
	}
	envMap := m.loadEnvValues()
	items := make([]list.Item, 0, len(envMap))
	for k, v := range envMap {
		items = append(items, envItem{key: k, value: v})
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Pick env value")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(true)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	if m.width > 0 && m.height > 0 {
		l.SetSize(m.width, m.height-4)
	}
	m.envList = l
}

func (m *LeafEditorModel) pickEnvValue(key string) tea.Cmd {
	m.pickingEnv = false
	m.formValue = "${" + key + "}"
	m.buildForm()
	if m.form != nil {
		if m.width > 0 {
			m.form = m.form.WithWidth(min(60, m.width-8))
		}
		return m.form.Init()
	}
	return nil
}

func (m *LeafEditorModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *LeafEditorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		if m.form != nil {
			m.form = m.form.WithWidth(min(60, msg.Width-8))
		}
		if m.envList.Items() != nil {
			m.envList.SetSize(msg.Width, msg.Height-4)
		}
		return m, nil

	case tea.KeyMsg:
		if m.pickingEnv {
			return m.handleEnvKey(msg)
		}
		if key.Matches(msg, m.keys.Quit) {
			m.quitting = true
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return components.PopScreenMsg{} }
		}
		if key.Matches(msg, m.keys.Save) {
			m.saveValue()
			var cmds []tea.Cmd
			if m.onSave != nil {
				cmds = append(cmds, m.onSave())
			}
			cmds = append(cmds, func() tea.Msg { return components.PopScreenMsg{} })
			return m, tea.Batch(cmds...)
		}
		if key.Matches(msg, m.keys.PickEnv) {
			m.pickingEnv = true
			m.initEnvList()
			return m, nil
		}
	}

	if m.form != nil {
		newForm, cmd := m.form.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.form = f
		}
		if m.form.State == huh.StateCompleted {
			m.saveValue()
			var cmds []tea.Cmd
			if m.onSave != nil {
				cmds = append(cmds, m.onSave())
			}
			cmds = append(cmds, func() tea.Msg { return components.PopScreenMsg{} })
			return m, tea.Batch(cmds...)
		}
		return m, cmd
	}

	return m, nil
}

func (m *LeafEditorModel) handleEnvKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch msg.String() {
	case "esc", "q", "ctrl+c":
		m.pickingEnv = false
		return m, nil
	case "enter":
		if item, ok := m.envList.SelectedItem().(envItem); ok {
			return m, m.pickEnvValue(item.key)
		}
		return m, nil
	}
	var cmd tea.Cmd
	m.envList, cmd = m.envList.Update(msg)
	return m, cmd
}

func (m *LeafEditorModel) saveValue() {
	switch m.leaf.ValueType() {
	case tree.TypeString, tree.TypeText:
		m.leaf.SetValue(m.formValue)

	case tree.TypeNumber:
		if f, err := strconv.ParseFloat(m.formValue, 64); err == nil {
			m.leaf.SetValue(f)
		} else {
			m.leaf.SetValue(m.formValue)
		}

	case tree.TypeBool:
		m.leaf.SetValue(m.formValueBool)

	case tree.TypeEnum:
		m.leaf.SetValue(m.formValue)

	case tree.TypeArray:
		lines := strings.Split(m.formValue, "\n")
		var items []string
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line != "" {
				items = append(items, line)
			}
		}
		m.leaf.SetValue(items)
	default:
		panic("unhandled default case")
	}
}

// envPickKeyMap is shown in the env picker help bar.
type envPickKeyMap struct {
	Select key.Binding
	Back   key.Binding
}

func (k envPickKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Select, k.Back}
}

func (k envPickKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Select}, {k.Back}}
}

var envPickKeys = envPickKeyMap{
	Select: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "select"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "cancel"),
	),
}

func (m *LeafEditorModel) ShortHelp() []key.Binding {
	bindings := []key.Binding{m.keys.Save, m.keys.Back, m.keys.Enter, m.keys.Quit}
	if m.leaf.ValueType() != tree.TypeEnum && m.leaf.ValueType() != tree.TypeBool {
		bindings = append(bindings, m.keys.PickEnv)
	}
	if m.leaf.ValueType() == tree.TypeText || m.leaf.ValueType() == tree.TypeArray {
		bindings = append(bindings, key.NewBinding(
			key.WithKeys("ctrl+j"),
			key.WithHelp("ctrl+j", "newline"),
		))
	}
	if m.leaf.ValueType() == tree.TypeEnum || m.leaf.ValueType() == tree.TypeBool {
		bindings = append(bindings, key.NewBinding(
			key.WithKeys("j", "k", "up", "down"),
			key.WithHelp("j/k/↑/↓", "select"),
		))
	}
	return bindings
}

func (m *LeafEditorModel) FullHelp() [][]key.Binding {
	var first []key.Binding
	first = append(first, m.keys.Save, m.keys.Enter)
	if m.leaf.ValueType() != tree.TypeEnum && m.leaf.ValueType() != tree.TypeBool {
		first = append(first, m.keys.PickEnv)
	}
	if m.leaf.ValueType() == tree.TypeText || m.leaf.ValueType() == tree.TypeArray {
		first = append(first, key.NewBinding(
			key.WithKeys("ctrl+j"),
			key.WithHelp("ctrl+j", "newline"),
		))
	}
	if m.leaf.ValueType() == tree.TypeEnum || m.leaf.ValueType() == tree.TypeBool {
		first = append(first, key.NewBinding(
			key.WithKeys("j", "k", "up", "down"),
			key.WithHelp("j/k/↑/↓", "select"),
		))
	}
	return [][]key.Binding{
		first,
		{m.keys.Back, m.keys.Quit},
	}
}

func (m *LeafEditorModel) View() string {
	if m.quitting {
		return ""
	}

	if m.pickingEnv {
		return m.envList.View() + "\n\n" + m.help.View(envPickKeys)
	}

	var b strings.Builder
	b.WriteString(style.Title("Edit: "+m.leaf.Title()) + "\n\n")

	if m.form != nil {
		b.WriteString(m.form.View())
	} else {
		b.WriteString(style.ErrorText("Unsupported value type: " + m.leaf.ValueType().String()))
	}

	if m.errMsg != "" {
		b.WriteString("\n" + style.ErrorText(m.errMsg))
	}

	b.WriteString("\n\n" + m.help.View(m))

	return b.String()
}
