package components

import (
	"fmt"
	"strconv"
	"strings"

	"erii-cli/internal/config"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/textarea"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type groupListItem struct {
	id  int64
	cfg config.GroupSettings
}

func (i groupListItem) Title() string       { return fmt.Sprintf("Group %d", i.id) }
func (i groupListItem) Description() string { return fmt.Sprintf("Desire: %d", i.cfg.Desire) }
func (i groupListItem) FilterValue() string { return strconv.FormatInt(i.id, 10) }

type OneBotEditModel struct {
	name    string
	ws      textinput.Model
	token   textinput.Model
	roleID  textinput.Model
	groups  list.Model
	plugins []string
	enabled map[string]bool

	mode   string // "fields", "groups", "plugins", "group-edit"
	focus  int
	width  int
	height int
	bot    config.BotConfig

	// group edit inline
	geGroupID int64
	geAdmins  textarea.Model
	geDesire  textinput.Model
}

func NewOneBotEditModel(w, h int, name string, bot config.BotConfig) *OneBotEditModel {
	ws := textinput.New()
	ws.Placeholder = "WebSocket URL"
	ws.SetValue(bot.WS)

	tok := textinput.New()
	tok.Placeholder = "Token"
	tok.SetValue(bot.Token)

	rid := textinput.New()
	rid.Placeholder = "Role ID"
	rid.SetValue(bot.RoleID)

	items := make([]list.Item, 0, len(bot.Groups))
	for gid, gcfg := range bot.Groups {
		items = append(items, groupListItem{id: gid, cfg: gcfg})
	}
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	gl := list.New(items, delegate, 0, 0)
	gl.Title = style.Subtitle("Groups")
	gl.SetShowStatusBar(false)
	gl.SetFilteringEnabled(false)

	gea := textarea.New()
	gea.Placeholder = "Admin IDs (comma-separated)"

	ged := textinput.New()
	ged.Placeholder = "Desire (1-100)"

	return &OneBotEditModel{
		name: name, ws: ws, token: tok, roleID: rid,
		groups: gl, bot: bot, mode: "fields",
		width: w, height: h,
		geAdmins: gea, geDesire: ged,
	}
}

func (m *OneBotEditModel) Init() tea.Cmd { return m.ws.Focus() }

func (m *OneBotEditModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		if m.mode == "group-edit" {
			switch msg.Type {
			case tea.KeyEsc:
				m.mode = "groups"
				return m, nil
			case tea.KeyCtrlS:
				admins := parseIntList(m.geAdmins.Value())
				desire, _ := strconv.Atoi(m.geDesire.Value())
				if desire == 0 {
					desire = 15
				}
				m.bot.Groups[m.geGroupID] = config.GroupSettings{Admins: admins, Desire: desire}
				m.refreshGroups()
				m.mode = "groups"
				return m, nil
			}
			var cmd tea.Cmd
			m.geAdmins, cmd = m.geAdmins.Update(msg)
			return m, cmd
		}

		switch msg.String() {
		case "g":
			if m.mode == "fields" {
				m.mode = "groups"
				return m, nil
			}
		case "p":
			if m.mode == "fields" {
				m.mode = "plugins"
				return m, nil
			}
		case "tab":
			if m.mode == "fields" {
				m.focus = (m.focus + 1) % 3
				m.updateFocus()
			}
			return m, nil
		}

		if m.mode == "groups" {
			if msg.Type == tea.KeyEnter {
				if item, ok := m.groups.SelectedItem().(groupListItem); ok {
					m.geGroupID = item.id
					admins := make([]string, len(item.cfg.Admins))
					for i, a := range item.cfg.Admins {
						admins[i] = strconv.FormatInt(a, 10)
					}
					m.geAdmins.SetValue(strings.Join(admins, ", "))
					m.geDesire.SetValue(strconv.Itoa(item.cfg.Desire))
					m.mode = "group-edit"
					return m, m.geAdmins.Focus()
				}
			}
			var cmd tea.Cmd
			m.groups, cmd = m.groups.Update(msg)
			return m, cmd
		}
	}

	if m.mode == "fields" {
		switch m.focus {
		case 0:
			var cmd tea.Cmd
			m.ws, cmd = m.ws.Update(msg)
			return m, cmd
		case 1:
			var cmd tea.Cmd
			m.token, cmd = m.token.Update(msg)
			return m, cmd
		case 2:
			var cmd tea.Cmd
			m.roleID, cmd = m.roleID.Update(msg)
			return m, cmd
		}
	}
	return m, nil
}

func (m *OneBotEditModel) View() string {
	theme := style.NewTheme()
	switch m.mode {
	case "fields":
		content := style.Title(fmt.Sprintf("Edit Bot: %s", m.name)) + "\n\n"
		labels := []string{"WebSocket URL", "Token", "Role ID"}
		inputs := []*textinput.Model{&m.ws, &m.token, &m.roleID}
		for i, label := range labels {
			l := style.Subtitle(label)
			if i == m.focus {
				l = theme.StepCurrent.Render(label)
			}
			content += l + "\n" + inputs[i].View() + "\n\n"
		}
		content += style.Muted("g: groups • p: plugins • tab: next field • esc: back")
		return theme.BorderedPanel.Width(m.width - 4).Render(content)
	case "groups":
		m.groups.SetSize(m.width-4, m.height-8)
		help := style.Muted("↑/↓ navigate • enter edit • esc back")
		return fmt.Sprintf("%s\n%s\n\n%s", style.Title("Bot Groups"), m.groups.View(), help)
	case "group-edit":
		content := style.Title(fmt.Sprintf("Edit Group %d", m.geGroupID)) + "\n\n"
		content += style.Subtitle("Admins") + "\n" + m.geAdmins.View() + "\n\n"
		content += style.Subtitle("Desire") + "\n" + m.geDesire.View() + "\n\n"
		content += style.Muted("ctrl+s: save • esc: cancel")
		return theme.BorderedPanel.Width(m.width - 4).Render(content)
	case "plugins":
		content := style.Title("Plugins") + "\n\n"
		content += style.Muted("Plugin toggle not yet implemented") + "\n\n"
		content += style.Muted("esc: back")
		return theme.BorderedPanel.Width(m.width - 4).Render(content)
	}
	return ""
}

func (m *OneBotEditModel) updateFocus() {
	m.ws.Blur()
	m.token.Blur()
	m.roleID.Blur()
	switch m.focus {
	case 0:
		m.ws.Focus()
	case 1:
		m.token.Focus()
	case 2:
		m.roleID.Focus()
	}
}

func (m *OneBotEditModel) refreshGroups() {
	items := make([]list.Item, 0, len(m.bot.Groups))
	for gid, gcfg := range m.bot.Groups {
		items = append(items, groupListItem{id: gid, cfg: gcfg})
	}
	m.groups.SetItems(items)
}

func parseIntList(s string) []int64 {
	var out []int64
	for _, p := range strings.Split(s, ",") {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if v, err := strconv.ParseInt(p, 10, 64); err == nil {
			out = append(out, v)
		}
	}
	return out
}
