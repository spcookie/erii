package components

import (
	"fmt"
	"regexp"
	"strings"

	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type botItem struct {
	name string
	bot  config.BotConfig
}

func (i botItem) Title() string       { return i.name }
func (i botItem) Description() string { return i.bot.WS }
func (i botItem) FilterValue() string { return i.name }

type OneBotListModel struct {
	list      list.Model
	bots      map[string]config.BotConfig
	width     int
	height    int
	mode      string // "list", "confirm-delete"
	delTarget string
}

func NewOneBotListModel(w, h int) *OneBotListModel {
	cfg, _ := config.LoadApp(path.AppFile)
	if cfg.OneBot.Bots == nil {
		cfg.OneBot.Bots = make(map[string]config.BotConfig)
	}

	items := make([]list.Item, 0, len(cfg.OneBot.Bots))
	for name, bot := range cfg.OneBot.Bots {
		items = append(items, botItem{name: name, bot: bot})
	}

	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("OneBot Bots")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)

	return &OneBotListModel{list: l, bots: cfg.OneBot.Bots, width: w, height: h}
}

func (m *OneBotListModel) Init() tea.Cmd { return nil }

func (m *OneBotListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-8)
	case tea.KeyMsg:
		if m.mode == "confirm-delete" {
			switch msg.String() {
			case "y", "Y":
				delete(m.bots, m.delTarget)
				m.save()
				m.refreshList()
				m.mode = "list"
			case "n", "N", "esc":
				m.mode = "list"
			}
			return m, nil
		}

		switch msg.String() {
		case "n":
			return m, nil // TODO: create new bot inline or push editor
		case "d":
			if item, ok := m.list.SelectedItem().(botItem); ok {
				m.delTarget = item.name
				m.mode = "confirm-delete"
			}
			return m, nil
		case "enter":
			if item, ok := m.list.SelectedItem().(botItem); ok {
				// Would push OneBotEditModel here; simplified for now
				_ = item
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *OneBotListModel) View() string {
	if m.mode == "confirm-delete" {
		content := lipgloss.NewStyle().Foreground(style.Error).Render(fmt.Sprintf("Delete bot '%s'?", m.delTarget)) + "\n"
		content += style.Muted("y: confirm • n: cancel")
		return style.NewTheme().BorderedPanel.Width(m.width - 4).Render(content)
	}

	help := style.Muted("↑/↓ navigate • enter edit • n new • d delete • esc back")
	return fmt.Sprintf("%s\n%s\n\n%s", style.Title("OneBot Bots"), m.list.View(), help)
}

func (m *OneBotListModel) refreshList() {
	items := make([]list.Item, 0, len(m.bots))
	for name, bot := range m.bots {
		items = append(items, botItem{name: name, bot: bot})
	}
	m.list.SetItems(items)
}

func (m *OneBotListModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	cfg.OneBot.Bots = m.bots
	config.SaveApp(path.AppFile, cfg)
}

// ParseOneBotBots parses the onebot.bots section from raw HOCON text.
// This is a simplified parser.
func ParseOneBotBots(data string) map[string]config.BotConfig {
	bots := make(map[string]config.BotConfig)
	re := regexp.MustCompile(`(?m)^\s*(\w+)\s*=\s*\{`)
	matches := re.FindAllStringSubmatchIndex(data, -1)
	for _, m := range matches {
		name := data[m[2]:m[3]]
		// Very simplified: find matching }
		start := m[1]
		depth := 1
		end := start
		for i := start; i < len(data) && depth > 0; i++ {
			if data[i] == '{' {
				depth++
			} else if data[i] == '}' {
				depth--
				if depth == 0 {
					end = i
				}
			}
		}
		block := data[start:end]
		bot := config.BotConfig{
			WS:     extractValue(block, "ws"),
			Token:  extractValue(block, "token"),
			RoleID: extractValue(block, "role-id"),
			Groups: make(map[int64]config.GroupSettings),
		}
		bots[name] = bot
	}
	return bots
}

func extractValue(block, key string) string {
	re := regexp.MustCompile(fmt.Sprintf(`%s\s*=\s*"?([^"\n]+)"?`, regexp.QuoteMeta(key)))
	matches := re.FindStringSubmatch(block)
	if len(matches) >= 2 {
		return strings.TrimSpace(matches[1])
	}
	return ""
}
