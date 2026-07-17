package md

import (
	"os"
	"strings"

	style "erii-cli/internal/ui/theme"

	"charm.land/glamour/v2"
	"charm.land/glamour/v2/ansi"
	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// ViewerModel renders Markdown using Glamour with viewport scrolling.
type ViewerModel struct {
	viewport viewport.Model
	content  string
	body     string // raw markdown body
	title    string
	done     bool
	width    int
	height   int
	keys     viewerKeyMap
	help     help.Model
	fmTable  table.Model

	// glamour renderer, recreated only when viewport width changes
	renderer *glamour.TermRenderer
}

type viewerKeyMap struct {
	Back key.Binding
	Help key.Binding
}

func (k viewerKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Back, k.Help}
}

func (k viewerKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Back, k.Help}}
}

var defaultViewerKeys = viewerKeyMap{
	Back: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "back"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
}

// parseYamlFrontmatter extracts `---\n...\n---\n` and returns parsed entries plus body.
func parseYamlFrontmatter(s string) (entries [][2]string, body string) {
	lines := strings.Split(s, "\n")
	if len(lines) == 0 || strings.TrimSpace(lines[0]) != "---" {
		return nil, s
	}

	var fmLines []string
	endIdx := -1
	for i := 1; i < len(lines); i++ {
		if strings.TrimSpace(lines[i]) == "---" {
			endIdx = i
			break
		}
		fmLines = append(fmLines, lines[i])
	}
	if endIdx == -1 {
		return nil, s
	}
	body = strings.TrimLeft(strings.Join(lines[endIdx+1:], "\n"), "\n")
	return flattenYamlLines(fmLines), body
}

// flattenYamlLines parses simple YAML into flat key-value pairs.
func flattenYamlLines(lines []string) [][2]string {
	var result [][2]string
	var stack []string
	var prevIndent int
	var inLiteral bool
	var literalKey string
	var literalLines []string
	var literalBaseIndent int

	flushLiteral := func() {
		if literalKey != "" && len(literalLines) > 0 {
			val := strings.Join(literalLines, "\n")
			result = append(result, [2]string{literalKey, val})
		}
		inLiteral = false
		literalKey = ""
		literalLines = nil
	}

	for _, line := range lines {
		if inLiteral {
			if line == "" {
				literalLines = append(literalLines, "")
				continue
			}
			indent := len(line) - len(strings.TrimLeft(line, " "))
			if indent >= literalBaseIndent {
				literalLines = append(literalLines, strings.TrimPrefix(line, strings.Repeat(" ", literalBaseIndent)))
				continue
			}
			flushLiteral()
		}

		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}

		indent := len(line) - len(strings.TrimLeft(line, " "))

		if strings.HasPrefix(trimmed, "- ") {
			itemVal := strings.TrimSpace(trimmed[2:])
			if strings.Contains(itemVal, ":") {
				parts := splitFirst(itemVal, ":")
				k := ""
				if len(stack) > 0 {
					k = stack[len(stack)-1] + "." + strings.TrimSpace(parts[0])
				} else {
					k = strings.TrimSpace(parts[0])
				}
				result = append(result, [2]string{k, strings.TrimSpace(parts[1])})
			} else {
				k := ""
				if len(stack) > 0 {
					k = stack[len(stack)-1] + "[]"
				} else {
					k = "[]"
				}
				result = append(result, [2]string{k, itemVal})
			}
			continue
		}

		for len(stack) > 0 && indent <= prevIndent {
			stack = stack[:len(stack)-1]
			prevIndent = 0
		}

		if !strings.Contains(trimmed, ":") {
			continue
		}
		parts := splitFirst(trimmed, ":")
		rawKey := strings.TrimSpace(parts[0])
		rawVal := strings.TrimSpace(parts[1])

		k := rawKey
		if len(stack) > 0 {
			k = stack[len(stack)-1] + "." + rawKey
		}

		if rawVal == "|" || rawVal == ">" || rawVal == "|-" || rawVal == ">-" {
			inLiteral = true
			literalKey = k
			literalBaseIndent = indent + 2
			literalLines = nil
			stack = append(stack, rawKey)
			prevIndent = indent
			continue
		}

		if rawVal == "" {
			stack = append(stack, k)
			prevIndent = indent
			continue
		}

		result = append(result, [2]string{k, rawVal})
		prevIndent = indent
	}
	flushLiteral()
	return result
}

func splitFirst(s, sep string) []string {
	idx := strings.Index(s, sep)
	if idx == -1 {
		return []string{s, ""}
	}
	return []string{s[:idx], s[idx+len(sep):]}
}

func fmColumnWidths(innerWidth int) (keyWidth, valWidth int) {
	keyWidth = innerWidth / 3
	if keyWidth < 10 {
		keyWidth = 10
	}
	valWidth = innerWidth - keyWidth - 8
	if valWidth < 10 {
		valWidth = 10
	}
	return
}

func buildFrontmatterTable(entries [][2]string, width int) table.Model {
	keyWidth, valWidth := fmColumnWidths(width)

	cols := []table.Column{
		{Title: "Key", Width: keyWidth},
		{Title: "Value", Width: valWidth},
	}

	rows := make([]table.Row, 0, len(entries))
	for _, e := range entries {
		val := strings.ReplaceAll(e[1], "\n", " ")
		if len(val) > valWidth*2 {
			val = val[:valWidth*2] + "..."
		}
		rows = append(rows, table.Row{e[0], val})
	}

	t := table.New(
		table.WithColumns(cols),
		table.WithRows(rows),
		table.WithHeight(len(rows)+1),
		table.WithWidth(width),
	)

	styles := table.DefaultStyles()
	styles.Header = styles.Header.
		Foreground(style.Accent).
		BorderForeground(style.BorderStrong).
		Bold(true)
	styles.Cell = styles.Cell.Foreground(style.Text)
	styles.Selected = styles.Selected.Foreground(style.Accent).Bold(true)
	t.SetStyles(styles)

	return t
}

// createRenderer creates a glamour renderer for the given width.
func createRenderer(width int) (*glamour.TermRenderer, error) {
	ww := width - 4
	if ww < 20 {
		ww = 20
	}
	return glamour.NewTermRenderer(
		glamour.WithStyles(vercelMarkdownStyle()),
		glamour.WithWordWrap(ww),
	)
}

func vercelMarkdownStyle() ansi.StyleConfig {
	color := func(c lipgloss.AdaptiveColor) *string {
		value := style.AdaptiveHex(c)
		return &value
	}
	bold := true
	italic := true
	underline := true
	margin := uint(1)
	indent := uint(2)
	return ansi.StyleConfig{
		Document:       ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Text)}},
		Paragraph:      ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Text)}},
		Heading:        ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Text), Bold: &bold}},
		H1:             ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Accent), Bold: &bold}, Margin: &margin},
		H2:             ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.ChartCyan), Bold: &bold}, Margin: &margin},
		H3:             ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.ChartViolet), Bold: &bold}},
		Text:           ansi.StylePrimitive{Color: color(style.Text)},
		Emph:           ansi.StylePrimitive{Color: color(style.TextMuted), Italic: &italic},
		Strong:         ansi.StylePrimitive{Color: color(style.Text), Bold: &bold},
		Link:           ansi.StylePrimitive{Color: color(style.Accent), Underline: &underline},
		LinkText:       ansi.StylePrimitive{Color: color(style.Accent)},
		Item:           ansi.StylePrimitive{Color: color(style.Accent)},
		Enumeration:    ansi.StylePrimitive{Color: color(style.ChartCyan)},
		HorizontalRule: ansi.StylePrimitive{Color: color(style.BorderStrong)},
		BlockQuote:     ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.TextMuted)}, Indent: &indent},
		Code:           ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.ChartCyan)}},
		CodeBlock:      ansi.StyleCodeBlock{StyleBlock: ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Text)}, Margin: &margin}},
		Table:          ansi.StyleTable{StyleBlock: ansi.StyleBlock{StylePrimitive: ansi.StylePrimitive{Color: color(style.Text)}}},
	}
}

func NewViewerModel(path, title string) *ViewerModel {
	data, err := os.ReadFile(path)
	if err != nil {
		return &ViewerModel{
			content: style.ErrorText("Failed to read file: " + err.Error()),
			title:   title,
			keys:    defaultViewerKeys,
			help:    help.New(),
		}
	}

	entries, body := parseYamlFrontmatter(string(data))
	initWidth := 80

	m := &ViewerModel{
		body:  body,
		title: title,
		keys:  defaultViewerKeys,
		help:  help.New(),
	}

	if len(entries) > 0 {
		m.fmTable = buildFrontmatterTable(entries, initWidth)
	}

	m.renderer, _ = createRenderer(initWidth)
	m.content = m.renderMarkdown()
	v := viewport.New(initWidth, 0)
	v.SetContent(m.content)
	m.viewport = v

	return m
}

func (m *ViewerModel) renderMarkdown() string {
	if m.renderer == nil {
		return m.body
	}
	rendered, err := m.renderer.Render(m.body)
	if err != nil {
		return m.body
	}
	return rendered
}

func (m *ViewerModel) Init() tea.Cmd {
	return nil
}

func (m *ViewerModel) rebuildContent() {
	if m.viewport.Width <= 0 {
		return
	}

	var parts []string
	if len(m.fmTable.Rows()) > 0 {
		parts = append(parts, m.fmTable.View())
		sep := lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Render(strings.Repeat("─", m.viewport.Width))
		parts = append(parts, sep)
	}
	parts = append(parts, m.content)
	m.viewport.SetContent(strings.Join(parts, "\n"))
}

func (m *ViewerModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		innerWidth := msg.Width - 6
		m.viewport.Width = innerWidth
		m.viewport.Height = msg.Height - 6
		m.help.Width = msg.Width

		if len(m.fmTable.Rows()) > 0 {
			keyWidth, valWidth := fmColumnWidths(innerWidth)
			m.fmTable.SetWidth(innerWidth)
			m.fmTable.SetColumns([]table.Column{
				{Title: "Key", Width: keyWidth},
				{Title: "Value", Width: valWidth},
			})
		}

		if m.viewport.Width != innerWidth {
			m.renderer, _ = createRenderer(innerWidth)
			m.content = m.renderMarkdown()
		}
		m.rebuildContent()
		return m, nil
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "esc", "ctrl+c":
			m.done = true
			return m, nil
		case "?":
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.viewport, cmd = m.viewport.Update(msg)
	return m, cmd
}

func (m *ViewerModel) View() string {
	border := lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(style.BorderStrong).
		Padding(0, 2)
	titleBar := style.Title("Viewing: " + m.title)
	content := border.Render(m.viewport.View())
	return titleBar + "\n" + content + "\n" + m.help.View(m.keys)
}
