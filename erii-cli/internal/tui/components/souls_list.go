package components

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"

	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
)

type mdItem struct {
	name string
	path string
}

func (i mdItem) Title() string       { return i.name }
func (i mdItem) Description() string { return i.path }
func (i mdItem) FilterValue() string { return i.name }

type SoulsListModel struct {
	list   list.Model
	width  int
	height int
}

func NewSoulsListModel(w, h int) *SoulsListModel {
	items := loadMdItems(path.SoulsDir)
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Souls")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	return &SoulsListModel{list: l, width: w, height: h}
}

func (m *SoulsListModel) Init() tea.Cmd { return nil }

func (m *SoulsListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-8)
	case tea.KeyMsg:
		switch msg.String() {
		case "v":
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				exec.Command("glow", item.path).Run()
			}
			return m, nil
		case "e":
			if item, ok := m.list.SelectedItem().(mdItem); ok {
				editor := os.Getenv("EDITOR")
				if editor == "" {
					editor = "vim"
				}
				exec.Command(editor, item.path).Run()
			}
			return m, nil
		}
	}
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *SoulsListModel) View() string {
	help := style.Muted("↑/↓ navigate • v view (glow) • e edit ($EDITOR) • esc back")
	return fmt.Sprintf("%s\n%s\n\n%s", style.Title("Souls"), m.list.View(), help)
}

func loadMdItems(dir string) []list.Item {
	items := []list.Item{}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return items
	}
	for _, e := range entries {
		if e.IsDir() || filepath.Ext(e.Name()) != ".md" {
			continue
		}
		items = append(items, mdItem{name: e.Name(), path: filepath.Join(dir, e.Name())})
	}
	return items
}
