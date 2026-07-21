package md

import (
	"os"
	"regexp"
	"strconv"
	"strings"

	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

var nameRegex = regexp.MustCompile(`^[a-zA-Z0-9_]+$`)

var emoticonOptions = []huh.Option[string]{
	huh.NewOption("JOY", "JOY"),
	huh.NewOption("OPTIMISM", "OPTIMISM"),
	huh.NewOption("RELAXATION", "RELAXATION"),
	huh.NewOption("SURPRISE", "SURPRISE"),
	huh.NewOption("MILDNESS", "MILDNESS"),
	huh.NewOption("DEPENDENCE", "DEPENDENCE"),
	huh.NewOption("BOREDOM", "BOREDOM"),
	huh.NewOption("SADNESS", "SADNESS"),
	huh.NewOption("FEAR", "FEAR"),
	huh.NewOption("ANXIETY", "ANXIETY"),
	huh.NewOption("CONTEMPT", "CONTEMPT"),
	huh.NewOption("DISGUST", "DISGUST"),
	huh.NewOption("RESENTMENT", "RESENTMENT"),
	huh.NewOption("HOSTILITY", "HOSTILITY"),
}

// NewFileKeyMap defines keybindings
type NewFileKeyMap struct {
	Save key.Binding
	Back key.Binding
}

func (k NewFileKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Save, k.Back}
}

func (k NewFileKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Save}, {k.Back}}
}

// ContentEditorKeyMap defines keybindings for content editing
type ContentEditorKeyMap struct {
	Save   key.Binding
	Back   key.Binding
	Macro1 key.Binding // ctrl+j for newline in textarea
}

func (k ContentEditorKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Macro1, k.Back}
}

func (k ContentEditorKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Macro1}, {k.Back}}
}

var defaultNewFileKeys = NewFileKeyMap{
	Save: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "create")),
	Back: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "cancel")),
}

// NewFileModel handles creating a new markdown file.
type NewFileModel struct {
	dir      string
	width    int
	height   int
	keys     NewFileKeyMap
	help     help.Model
	form     *huh.Form
	fileName string
	onCreate func(path string)
	onCancel func()
	done     bool
	errMsg   string
}

func NewNewFileModel(dir string, onCreate func(string), onCancel func()) *NewFileModel {
	m := &NewFileModel{
		dir:      dir,
		keys:     defaultNewFileKeys,
		help:     help.New(),
		onCreate: onCreate,
		onCancel: onCancel,
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

func (m *NewFileModel) buildForm() {
	m.form = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("File name").
				Description("Letters, numbers and underscores only").
				Placeholder("my_file").
				Value(&m.fileName),
		),
	).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())
}

func (m *NewFileModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *NewFileModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.done {
		return m, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if m.form != nil {
			m.form = m.form.WithWidth(min(60, msg.Width-8))
			newForm, _ := m.form.Update(msg)
			if f, ok := newForm.(*huh.Form); ok {
				m.form = f
			}
		}
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Back) {
			m.done = true
			if m.onCancel != nil {
				m.onCancel()
			}
			return m, nil
		}
	}

	if m.form != nil {
		newForm, cmd := m.form.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			m.form = f
		}
		if m.form.State == huh.StateCompleted {
			fileName := strings.TrimSpace(m.fileName)
			if fileName == "" {
				m.errMsg = "File name cannot be empty"
				m.form = nil
				m.buildForm()
				return m, nil
			}
			if !nameRegex.MatchString(fileName) {
				m.errMsg = "Only letters, numbers and underscores allowed"
				m.form = nil
				m.buildForm()
				return m, nil
			}
			if !strings.HasSuffix(fileName, ".md") {
				fileName += ".md"
			}
			m.done = true
			if m.onCreate != nil {
				m.onCreate(fileName)
			}
			return m, nil
		}
		return m, cmd
	}
	return m, nil
}

func (m *NewFileModel) ShortHelp() []key.Binding {
	return m.keys.ShortHelp()
}

func (m *NewFileModel) FullHelp() [][]key.Binding {
	return m.keys.FullHelp()
}

func (m *NewFileModel) View() string {
	var b strings.Builder
	b.WriteString(style.Title("Create new file") + "\n\n")
	if m.form != nil {
		b.WriteString(m.form.View())
	}
	if m.errMsg != "" {
		b.WriteString("\n" + style.ErrorText(m.errMsg))
	}
	b.WriteString("\n\n" + m.help.View(m.keys))
	return b.String()
}

// FrontmatterEditorModel edits markdown frontmatter.
type FrontmatterEditorModel struct {
	filePath   string
	width      int
	height     int
	help       help.Model
	form       *huh.Form
	onSave     func()
	onCancel   func()
	done       bool
	entries    []string // k1, v1, k2, v2, ...
	globalBool bool     // bool value for "global" field in rules
}

func (m *FrontmatterEditorModel) buildForm() {
	isRules := strings.Contains(m.filePath, "rules")

	var groups []*huh.Group
	for i := 0; i < len(m.entries); i += 2 {
		space := strings.TrimSpace(m.entries[i])
		if space == "" {
			continue
		}
		// Ensure there's a value slot
		if i+1 >= len(m.entries) {
			m.entries = append(m.entries, "")
		}

		var fields []huh.Field
		if isRules {
			fields = m.buildRulesField(fields, space, i)
		} else {
			fields = m.buildSoulField(fields, space, i)
		}

		for _, f := range fields {
			groups = append(groups, huh.NewGroup(f))
		}
	}

	if len(groups) > 0 {
		m.form = huh.NewForm(groups...).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())
	}
}

func (m *FrontmatterEditorModel) buildSoulField(fields []huh.Field, key string, idx int) []huh.Field {
	switch key {
	case "id":
		// Read-only: show as title with empty input
		fields = append(fields,
			huh.NewInput().
				Title(key).
				Placeholder(m.entries[idx+1]).
				Value(&m.entries[idx+1]).
				Prompt(""),
		)
	case "name":
		fields = append(fields,
			huh.NewInput().
				Title(key).
				Placeholder("bot name").
				Value(&m.entries[idx+1]),
		)
	case "character":
		// Multiline text for YAML | content
		fields = append(fields,
			huh.NewText().
				Title(key).
				Placeholder("multiline character description...").
				Value(&m.entries[idx+1]),
		)
	case "emoticon":
		// Enum select from EmotionalTendencies
		if m.entries[idx+1] == "" {
			m.entries[idx+1] = "MILDNESS"
		}
		fields = append(fields,
			huh.NewSelect[string]().
				Title(key).
				Description("Bot emotional tendency").
				Options(emoticonOptions...).
				Value(&m.entries[idx+1]).
				Key("value").
				Height(6),
		)
	}
	return fields
}

func (m *FrontmatterEditorModel) buildRulesField(fields []huh.Field, key string, idx int) []huh.Field {
	switch key {
	case "global":
		// Boolean confirm with Yes/No buttons
		fields = append(fields,
			huh.NewConfirm().
				Title(key).
				Description("Global rule, applies to all groups and bots").
				Value(&m.globalBool).
				Key("value"),
		)
	case "botId", "groupId":
		fields = append(fields,
			huh.NewInput().
				Title(key).
				Placeholder("value").
				Value(&m.entries[idx+1]),
		)
	default:
		fields = append(fields,
			huh.NewInput().
				Title(key).
				Placeholder("value").
				Value(&m.entries[idx+1]),
		)
	}
	return fields
}

func (m *FrontmatterEditorModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *FrontmatterEditorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.done {
		return m, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if m.form != nil {
			m.form = m.form.WithWidth(min(60, msg.Width-8))
		}
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.getKeys().Back) {
			m.saveFrontmatter()
			m.done = true
			return m, nil
		}
	}

	if m.form != nil {
		var cmd tea.Cmd
		m.form, cmd = updateForm(m.form, msg)
		if m.form.State == huh.StateCompleted {
			m.saveFrontmatter()
			m.done = true
			if m.onSave != nil {
				m.onSave()
			}
			return m, nil
		}
		return m, cmd
	}
	return m, nil
}

func (m *FrontmatterEditorModel) saveFrontmatter() {
	for i := 0; i < len(m.entries); i += 2 {
		if strings.TrimSpace(m.entries[i]) == "global" {
			if m.globalBool {
				m.entries[i+1] = "true"
			} else {
				m.entries[i+1] = "false"
			}
			break
		}
	}
	writeFrontmatterToFile(m.filePath, m.entries)
}

func (m *FrontmatterEditorModel) getKeys() NewFileKeyMap {
	return NewFileKeyMap{
		Save: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "save")),
		Back: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "cancel")),
	}
}

func (m *FrontmatterEditorModel) ShortHelp() []key.Binding {
	return m.getKeys().ShortHelp()
}

func (m *FrontmatterEditorModel) FullHelp() [][]key.Binding {
	return m.getKeys().FullHelp()
}

func (m *FrontmatterEditorModel) View() string {
	var b strings.Builder
	b.WriteString(style.Title("Edit Frontmatter: "+m.filePath) + "\n\n")
	if m.form != nil {
		b.WriteString(m.form.View())
	}
	b.WriteString("\n\n" + m.help.View(m.getKeys()))
	return b.String()
}

// ContentEditorModel edits markdown content.
type ContentEditorModel struct {
	filePath    string
	width       int
	height      int
	help        help.Model
	form        *huh.Form
	content     string
	frontmatter string // stored frontmatter to preserve on save
	onSave      func()
	onCancel    func()
	done        bool
}

func NewContentEditorModel(filePath string, content string, frontmatter string, onSave func(), onCancel func()) *ContentEditorModel {
	m := &ContentEditorModel{
		filePath:    filePath,
		help:        help.New(),
		onSave:      onSave,
		onCancel:    onCancel,
		content:     content,
		frontmatter: frontmatter,
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

func (m *ContentEditorModel) buildForm() {
	m.form = huh.NewForm(
		huh.NewGroup(
			huh.NewText().
				Title("Content").
				Placeholder("Enter content here...").
				Value(&m.content),
		),
	).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())
}

func (m *ContentEditorModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *ContentEditorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.done {
		return m, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if m.form != nil {
			m.form = m.form.WithWidth(min(60, msg.Width-8))
			newForm, _ := m.form.Update(msg)
			if f, ok := newForm.(*huh.Form); ok {
				m.form = f
			}
		}
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.getKeys().Back) {
			m.saveContent()
			m.done = true
			return m, nil
		}
		// Handle ctrl+j for newline insertion before form processes it
		if key.Matches(msg, m.getKeys().Macro1) {
			m.content += "\n"
			return m, nil
		}
	}

	if m.form != nil {
		var cmd tea.Cmd
		m.form, cmd = updateForm(m.form, msg)
		if m.form.State == huh.StateCompleted {
			m.saveContent()
			m.done = true
			if m.onSave != nil {
				m.onSave()
			}
			return m, nil
		}
		return m, cmd
	}
	return m, nil
}

func (m *ContentEditorModel) saveContent() {
	var data string
	if m.frontmatter != "" {
		data = m.frontmatter + "\n\n" + m.content
	} else {
		data = m.content
	}
	_ = os.WriteFile(m.filePath, []byte(data), 0644)
}

func (m *ContentEditorModel) getKeys() ContentEditorKeyMap {
	return ContentEditorKeyMap{
		Save:   key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "save")),
		Back:   key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "back")),
		Macro1: key.NewBinding(key.WithKeys("ctrl+j"), key.WithHelp("ctrl+j", "newline")),
	}
}

func (m *ContentEditorModel) ShortHelp() []key.Binding {
	return m.getKeys().ShortHelp()
}

func (m *ContentEditorModel) FullHelp() [][]key.Binding {
	return m.getKeys().FullHelp()
}

func (m *ContentEditorModel) View() string {
	var b strings.Builder
	b.WriteString(style.Title("Edit Content: "+m.filePath) + "\n\n")
	if m.form != nil {
		b.WriteString(m.form.View())
	}
	b.WriteString("\n\n" + m.help.View(m.getKeys()))
	return b.String()
}

// updateForm updates a form and returns the updated form and command.
func updateForm(form *huh.Form, msg tea.Msg) (*huh.Form, tea.Cmd) {
	if form != nil {
		newForm, cmd := form.Update(msg)
		if f, ok := newForm.(*huh.Form); ok {
			form = f
		}
		return form, cmd
	}
	return form, nil
}

// formatFrontmatterEntry formats a key-value pair as YAML frontmatter line(s).
func formatFrontmatterEntry(key, value string) []string {
	var lines []string
	if strings.Contains(value, "\n") {
		lines = append(lines, key+": |")
		for _, l := range strings.Split(value, "\n") {
			lines = append(lines, "  "+l)
		}
	} else {
		if value != "" {
			lines = append(lines, key+": "+value)
		} else {
			lines = append(lines, key+":")
		}
	}
	return lines
}

// writeFrontmatterToFile writes entries as YAML frontmatter to file.
func writeFrontmatterToFile(filePath string, entries []string) {
	var lines []string
	lines = append(lines, "---")
	for i := 0; i < len(entries); i += 2 {
		space := strings.TrimSpace(entries[i])
		if space == "" {
			continue
		}
		value := ""
		if i+1 < len(entries) {
			value = entries[i+1]
		}
		lines = append(lines, formatFrontmatterEntry(space, value)...)
	}
	lines = append(lines, "---")

	data, err := os.ReadFile(filePath)
	if err != nil {
		return
	}
	content := string(data)
	if hasFrontmatter(content) {
		content = replaceFrontmatter(content, strings.Join(lines, "\n"))
	} else {
		content = strings.Join(lines, "\n") + "\n\n" + content
	}
	_ = os.WriteFile(filePath, []byte(content), 0644)
}

// Helper functions
func hasFrontmatter(content string) bool {
	lines := strings.Split(content, "\n")
	if len(lines) < 3 {
		return false
	}
	if strings.TrimSpace(lines[0]) != "---" {
		return false
	}
	for i := 1; i < len(lines); i++ {
		if strings.TrimSpace(lines[i]) == "---" {
			return true
		}
	}
	return false
}

func replaceFrontmatter(content string, newFM string) string {
	lines := strings.Split(content, "\n")
	start := -1
	end := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == "---" {
			if start == -1 {
				start = i
			} else {
				end = i
				break
			}
		}
	}
	if start == -1 || end == -1 {
		return newFM + "\n" + content
	}
	return newFM + "\n" + strings.Join(lines[end+1:], "\n")
}

// parseFrontmatter extracts frontmatter from markdown content.
func parseFrontmatter(content string) map[string]string {
	result := make(map[string]string)
	if !hasFrontmatter(content) {
		return result
	}
	lines := strings.Split(content, "\n")

	// Find frontmatter block boundaries
	start := -1
	end := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == "---" {
			if start == -1 {
				start = i
			} else {
				end = i
				break
			}
		}
	}
	if start == -1 || end == -1 || end <= start+1 {
		return result
	}

	// Parse line by line, handling YAML | literal block scalar
	i := start + 1
	for i < end {
		line := lines[i]
		trimmed := strings.TrimSpace(line)

		// Skip empty lines at start of block
		if trimmed == "" {
			i++
			continue
		}

		// Find key: value pattern
		colonIdx := strings.Index(line, ":")
		if colonIdx <= 0 {
			i++
			continue
		}

		space := strings.TrimSpace(line[:colonIdx])
		rest := strings.TrimSpace(line[colonIdx+1:])

		// Check for literal block scalar |
		if rest == "|" || rest == "|+" || rest == "|-" {
			// Collect all indented lines that follow
			var valueLines []string
			i++
			for i < end {
				nextLine := lines[i]
				// Check if line is indented (at least 2 spaces, or a tab, or more)
				if len(nextLine) > 0 && (nextLine[0] == ' ' || nextLine[0] == '\t') {
					// Remove leading indentation
					stripped := strings.TrimLeft(nextLine, " \t")
					valueLines = append(valueLines, stripped)
					i++
				} else if strings.TrimSpace(nextLine) == "" {
					// Empty lines are part of the block
					valueLines = append(valueLines, "")
					i++
				} else {
					break
				}
			}
			result[space] = strings.Join(valueLines, "\n")
		} else if rest != "" {
			// Simple key: value
			result[space] = rest
			i++
		} else {
			// Empty value, check if next non-empty line starts a literal block
			result[space] = ""
			i++
		}
	}
	return result
}

// stripFrontmatter removes frontmatter from markdown content.
func stripFrontmatter(content string) string {
	if !hasFrontmatter(content) {
		return content
	}
	lines := strings.Split(content, "\n")
	start := -1
	end := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == "---" {
			if start == -1 {
				start = i
			} else {
				end = i
				break
			}
		}
	}
	if start == -1 || end == -1 {
		return content
	}
	result := strings.Join(lines[end+1:], "\n")
	for strings.HasPrefix(result, "\n") {
		result = result[1:]
	}
	return result
}

// extractFrontmatterBlock extracts the frontmatter block as a string (--- ... ---).
func extractFrontmatterBlock(content string) string {
	lines := strings.Split(content, "\n")
	start := -1
	end := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == "---" {
			if start == -1 {
				start = i
			} else {
				end = i
				break
			}
		}
	}
	if start == -1 || end == -1 {
		return ""
	}
	return strings.Join(lines[start:end+1], "\n")
}

// FieldBrowserModel is a list-based frontmatter editor (like app config).
type FieldBrowserModel struct {
	filePath    string
	fileName    string
	title       string
	list        list.Model
	width       int
	height      int
	keys        FieldBrowserKeyMap
	help        help.Model
	entries     []string // k1, v1, k2, v2, ...
	fieldEditor *FieldEditorModel
	done        bool
	stack       []int // stack of list indices for back navigation
}

// FieldBrowserKeyMap defines keybindings for field browser.
type FieldBrowserKeyMap struct {
	Enter key.Binding
	Back  key.Binding
	New   key.Binding
	Help  key.Binding
	Quit  key.Binding
}

func (k FieldBrowserKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Enter, k.Back, k.Help, k.Quit}
}

func (k FieldBrowserKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Enter, k.New},
		{k.Back, k.Help, k.Quit},
	}
}

var defaultFieldBrowserKeys = FieldBrowserKeyMap{
	Enter: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "edit")),
	Back:  key.NewBinding(key.WithKeys("esc", "left", "h"), key.WithHelp("esc/←/h", "back")),
	New:   key.NewBinding(key.WithKeys("ctrl+n"), key.WithHelp("ctrl+n", "new")),
	Help:  key.NewBinding(key.WithKeys("?"), key.WithHelp("?", "help")),
	Quit:  key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

// FieldItem wraps a key-value pair for the list.
type FieldItem struct {
	key   string
	value string
}

func (i FieldItem) Title() string { return i.key }
func (i FieldItem) Description() string {
	runes := []rune(i.value)
	if len(runes) > 60 {
		return string(runes[:60]) + "..."
	}
	return i.value
}
func (i FieldItem) FilterValue() string { return i.key }

// NewFieldBrowserModel creates a field browser for frontmatter editing.
func NewFieldBrowserModel(filePath, fileName string, frontmatter map[string]string) *FieldBrowserModel {
	var entries []string
	for k, v := range frontmatter {
		entries = append(entries, k, v)
	}

	items := make([]list.Item, 0, len(entries)/2)
	for i := 0; i < len(entries); i += 2 {
		items = append(items, FieldItem{key: entries[i], value: entries[i+1]})
	}

	delegate := style.StyleDelegate(list.NewDefaultDelegate())

	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Edit: " + fileName)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)
	l.SetSize(80, 20)

	return &FieldBrowserModel{
		filePath: filePath,
		fileName: fileName,
		list:     l,
		keys:     defaultFieldBrowserKeys,
		help:     help.New(),
		entries:  entries,
	}
}

func (m *FieldBrowserModel) Init() tea.Cmd { return nil }

func (m *FieldBrowserModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.fieldEditor != nil {
		switch msg := msg.(type) {
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
		}
		newModel, cmd := m.fieldEditor.Update(msg)
		if v, ok := newModel.(*FieldEditorModel); ok {
			m.fieldEditor = v
		}
		if m.fieldEditor != nil && m.fieldEditor.done {
			m.saveField(m.fieldEditor.key, m.fieldEditor.value)
			m.fieldEditor = nil
			if len(m.stack) > 0 {
				m.stack = m.stack[:len(m.stack)-1]
			}
			m.refreshList()
			return m, nil
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
			m.done = true
			return m, nil
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Back) {
			if len(m.stack) > 0 {
				m.stack = m.stack[:len(m.stack)-1]
				return m, nil
			}
			m.done = true
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			if idx >= 0 && idx*2+1 < len(m.entries) {
				m.stack = append(m.stack, idx)
				k := m.entries[idx*2]
				value := m.entries[idx*2+1]
				m.fieldEditor = NewFieldEditorModel(m.filePath, k, value)
				return m, m.fieldEditor.Init()
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *FieldBrowserModel) saveField(key, value string) {
	for i := 0; i < len(m.entries); i += 2 {
		if m.entries[i] == key {
			m.entries[i+1] = value
			break
		}
	}
	// Write back to file
	m.writeFrontmatter()
}

func (m *FieldBrowserModel) writeFrontmatter() {
	writeFrontmatterToFile(m.filePath, m.entries)
}

func (m *FieldBrowserModel) refreshList() {
	items := make([]list.Item, 0, len(m.entries)/2)
	for i := 0; i < len(m.entries); i += 2 {
		items = append(items, FieldItem{key: m.entries[i], value: m.entries[i+1]})
	}
	m.list.SetItems(items)
}

func (m *FieldBrowserModel) View() string {
	if m.fieldEditor != nil {
		return m.fieldEditor.View()
	}

	var b strings.Builder
	b.WriteString(m.list.View())
	b.WriteString("\n\n" + m.help.View(m.keys))
	return b.String()
}

// FieldEditorModel edits a single field value.
type FieldEditorModel struct {
	filePath  string
	key       string
	value     string
	boolValue bool
	isBoolean bool
	width     int
	height    int
	help      help.Model
	form      *huh.Form
	done      bool
}

func NewFieldEditorModel(filePath, key, value string) *FieldEditorModel {
	m := &FieldEditorModel{
		filePath: filePath,
		key:      key,
		value:    value,
		help:     help.New(),
	}
	m.buildForm()
	m.width = 80
	m.height = 24
	if m.form != nil {
		m.form = m.form.WithWidth(60)
	}
	return m
}

func (m *FieldEditorModel) buildForm() {
	isRules := strings.Contains(m.filePath, "rules")

	switch {
	case (isRules && m.key == "global") || strings.EqualFold(m.value, "true") || strings.EqualFold(m.value, "false"):
		m.isBoolean = true
		m.boolValue = strings.EqualFold(m.value, "true")
		description := "Boolean value"
		if isRules && m.key == "global" {
			description = "Global rule, applies to all groups and bots"
		}
		m.form = huh.NewForm(
			huh.NewGroup(
				huh.NewConfirm().
					Title(m.key).
					Description(description).
					Value(&m.boolValue),
			),
		).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())

	case m.key == "emoticon":
		m.form = huh.NewForm(
			huh.NewGroup(
				huh.NewSelect[string]().
					Title(m.key).
					Description("Bot emotional tendency").
					Options(emoticonOptions...).
					Value(&m.value).
					Key("value"),
			),
		).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())

	case m.key == "character" || m.key == "description":
		m.form = huh.NewForm(
			huh.NewGroup(
				huh.NewText().
					Title(m.key).
					Placeholder("multiline value...").
					Value(&m.value),
			),
		).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())

	default:
		m.form = huh.NewForm(
			huh.NewGroup(
				huh.NewInput().
					Title(m.key).
					Placeholder("value").
					Value(&m.value),
			),
		).WithWidth(60).WithShowHelp(false).WithTheme(style.HuhTheme())
	}
}

func (m *FieldEditorModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *FieldEditorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.done {
		return m, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if m.form != nil {
			m.form = m.form.WithWidth(min(60, msg.Width-8))
		}
		return m, nil

	case tea.KeyMsg:
		if msg.String() == "esc" || msg.String() == "ctrl+[" || key.Matches(msg, m.getKeys().Back) {
			m.done = true
			return m, nil
		}
	}

	if m.form != nil {
		var cmd tea.Cmd
		m.form, cmd = updateForm(m.form, msg)
		if m.form.State == huh.StateCompleted {
			if m.isBoolean {
				m.value = strconv.FormatBool(m.boolValue)
			}
			m.done = true
		}
		return m, cmd
	}
	return m, nil
}

func (m *FieldEditorModel) getKeys() NewFileKeyMap {
	return NewFileKeyMap{
		Save: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "save")),
		Back: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "cancel")),
	}
}

func (m *FieldEditorModel) ShortHelp() []key.Binding {
	return m.getKeys().ShortHelp()
}

func (m *FieldEditorModel) FullHelp() [][]key.Binding {
	return m.getKeys().FullHelp()
}

func (m *FieldEditorModel) View() string {
	var b strings.Builder
	b.WriteString(style.Title("Edit: "+m.key) + "\n\n")
	if m.form != nil {
		b.WriteString(m.form.View())
	}
	b.WriteString("\n\n" + m.help.View(m.getKeys()))
	return b.String()
}
