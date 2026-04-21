package components

import (
	"strconv"
	"strings"

	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/textarea"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type GroupsFormModel struct {
	debugID   textinput.Model
	enableIDs textarea.Model
	redirect  textarea.Model
	focus     int
	width     int
	height    int
}

func NewGroupsFormModel(w, h int) *GroupsFormModel {
	cfg, _ := config.LoadApp(path.AppFile)

	d := textinput.New()
	d.Placeholder = "Debug Group ID (optional)"
	if cfg.Groups.DebugGroupID != nil {
		d.SetValue(strconv.FormatInt(*cfg.Groups.DebugGroupID, 10))
	}

	e := textarea.New()
	e.Placeholder = "Enable Groups (comma-separated IDs)"
	ids := make([]string, len(cfg.Groups.EnableGroups))
	for i, v := range cfg.Groups.EnableGroups {
		ids[i] = strconv.FormatInt(v, 10)
	}
	e.SetValue(strings.Join(ids, ", "))

	r := textarea.New()
	r.Placeholder = "Message Redirect Map (one per line)"
	r.SetValue(strings.Join(cfg.Groups.MessageRedirectMap, "\n"))

	return &GroupsFormModel{debugID: d, enableIDs: e, redirect: r, width: w, height: h}
}

func (m *GroupsFormModel) Init() tea.Cmd { return m.debugID.Focus() }

func (m *GroupsFormModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyTab:
			m.focus = (m.focus + 1) % 3
			m.updateFocus()
			return m, nil
		case tea.KeyCtrlS:
			m.save()
			return m, nil
		}
	}

	switch m.focus {
	case 0:
		var cmd tea.Cmd
		m.debugID, cmd = m.debugID.Update(msg)
		return m, cmd
	case 1:
		var cmd tea.Cmd
		m.enableIDs, cmd = m.enableIDs.Update(msg)
		return m, cmd
	case 2:
		var cmd tea.Cmd
		m.redirect, cmd = m.redirect.Update(msg)
		return m, cmd
	}
	return m, nil
}

func (m *GroupsFormModel) View() string {
	content := style.Title("Global Groups Configuration") + "\n\n"
	content += style.Subtitle("Debug Group ID") + "\n" + m.debugID.View() + "\n\n"
	content += style.Subtitle("Enable Groups") + "\n" + m.enableIDs.View() + "\n\n"
	content += style.Subtitle("Message Redirect Map") + "\n" + m.redirect.View() + "\n\n"
	content += style.Muted("tab: next • ctrl+s: save • esc: back")
	return style.NewTheme().BorderedPanel.Width(m.width - 4).Render(content)
}

func (m *GroupsFormModel) updateFocus() {
	m.debugID.Blur()
	m.enableIDs.Blur()
	m.redirect.Blur()
	switch m.focus {
	case 0:
		m.debugID.Focus()
	case 1:
		m.enableIDs.Focus()
	case 2:
		m.redirect.Focus()
	}
}

func (m *GroupsFormModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	if v := m.debugID.Value(); v != "" {
		if id, err := strconv.ParseInt(v, 10, 64); err == nil {
			cfg.Groups.DebugGroupID = &id
		}
	} else {
		cfg.Groups.DebugGroupID = nil
	}
	cfg.Groups.EnableGroups = parseIntList(m.enableIDs.Value())
	cfg.Groups.MessageRedirectMap = strings.Split(m.redirect.Value(), "\n")
	config.SaveApp(path.AppFile, cfg)
}
