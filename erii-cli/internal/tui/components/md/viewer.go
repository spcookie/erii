package md

import (
	"os"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/glamour"
	"github.com/charmbracelet/lipgloss"
)

// ViewerModel renders markdown using Glamour with viewport scrolling.
type ViewerModel struct {
	viewport viewport.Model
	content  string
	title    string
	done     bool
	width    int
	height   int
}

// stripYamlFrontmatter removes `---\n...\n---\n` from the beginning of markdown.
func stripYamlFrontmatter(s string) string {
	lines := strings.Split(s, "\n")
	if len(lines) > 0 && strings.TrimSpace(lines[0]) == "---" {
		for i := 1; i < len(lines); i++ {
			if strings.TrimSpace(lines[i]) == "---" {
				return strings.TrimLeft(strings.Join(lines[i+1:], "\n"), "\n")
			}
		}
	}
	return s
}

func renderMarkdown(data []byte, width int) string {
	clean := stripYamlFrontmatter(string(data))
	ww := width - 4
	if ww < 20 {
		ww = 20
	}
	renderer, err := glamour.NewTermRenderer(
		glamour.WithAutoStyle(),
		glamour.WithWordWrap(ww),
	)
	if err != nil {
		return clean
	}
	rendered, err := renderer.Render(clean)
	if err != nil {
		return clean
	}
	return rendered
}

func NewViewerModel(path, title string) *ViewerModel {
	data, err := os.ReadFile(path)
	content := ""
	if err != nil {
		content = style.ErrorText("Failed to read file: " + err.Error())
	} else {
		content = renderMarkdown(data, 80)
	}

	v := viewport.New(0, 0)
	v.SetContent(content)

	return &ViewerModel{
		viewport: v,
		content:  content,
		title:    title,
	}
}

func (m *ViewerModel) Init() tea.Cmd {
	return nil
}

func (m *ViewerModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.viewport.Width = msg.Width - 4
		m.viewport.Height = msg.Height - 6
		m.viewport.SetContent(m.content)
		return m, nil
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "esc", "ctrl+c":
			m.done = true
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.viewport, cmd = m.viewport.Update(msg)
	return m, cmd
}

func (m *ViewerModel) View() string {
	titleBar := style.Title("Viewing: " + m.title)
	helpBar := style.Muted("↑/↓ scroll • q/esc/ctrl+c back")
	return lipgloss.JoinVertical(
		lipgloss.Left,
		titleBar,
		m.viewport.View(),
		helpBar,
	)
}
