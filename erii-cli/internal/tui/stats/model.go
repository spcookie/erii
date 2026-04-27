package stats

import (
	"fmt"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// Navigation messages
type (
	PopMsg            struct{}
	PushGroupListMsg  struct{ Bot BotInfo }
	PushStatusViewMsg struct {
		Bot   BotInfo
		Group GroupInfo
	}
)

// Data messages
type (
	BotsLoadedMsg struct {
		Bots  []BotInfo
		Error error
	}
	GroupsLoadedMsg struct {
		Bot    BotInfo
		Groups []GroupInfo
		Error  error
	}
)

// Key bindings
type navKeys struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Back  key.Binding
	Quit  key.Binding
	Help  key.Binding
}

func (k navKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Help, k.Quit}
}

func (k navKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down},
		{k.Enter, k.Back, k.Help, k.Quit},
	}
}

var defaultKeys = navKeys{
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
		key.WithHelp("enter", "select"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc/backspace", "back"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "help"),
	),
}

// ── Generic Unicode symbols (safe in all fonts) ──
const (
	SymArrow    = ">"
	SymBarFull  = "█"
	SymBarEmpty = "░"
)

var (
	mutedLabelStyle = lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Width(10)

	infoValueStyle = lipgloss.NewStyle().
			Foreground(style.Info).
			Bold(true)
)

// ── List item ──

type item struct {
	title, desc string
}

func (i item) Title() string       { return i.title }
func (i item) Description() string { return i.desc }
func (i item) FilterValue() string { return i.title }

// ── BotListModel ──

type BotListModel struct {
	api    *API
	bots   []BotInfo
	list   list.Model
	help   help.Model
	keys   navKeys
	width  int
	height int
}

func NewBotListModel(api *API) *BotListModel {
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Bot")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &BotListModel{
		api:  api,
		list: l,
		help: help.New(),
		keys: defaultKeys,
	}
}

func (m *BotListModel) Init() tea.Cmd {
	return func() tea.Msg {
		bots, err := m.api.GetBots()
		return BotsLoadedMsg{Bots: bots, Error: err}
	}
}

func (m *BotListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case BotsLoadedMsg:
		if msg.Error != nil {
			m.list.SetItems([]list.Item{item{title: style.ErrorText("Error: " + msg.Error.Error()), desc: ""}})
			return m, nil
		}
		m.bots = msg.Bots
		items := make([]list.Item, len(msg.Bots))
		for i, b := range msg.Bots {
			items[i] = item{
				title: SymArrow + " " + b.BotName,
				desc:  "   ID: " + b.BotID,
			}
		}
		m.list.SetItems(items)
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			if idx >= 0 && idx < len(m.bots) {
				return m, func() tea.Msg {
					return PushGroupListMsg{Bot: m.bots[idx]}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *BotListModel) View() string {
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}

// ── GroupListModel ──

type GroupListModel struct {
	api    *API
	bot    BotInfo
	groups []GroupInfo
	list   list.Model
	help   help.Model
	keys   navKeys
	width  int
	height int
}

func NewGroupListModel(api *API, bot BotInfo) *GroupListModel {
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(style.Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(style.TextMuted)

	l := list.New([]list.Item{}, delegate, 0, 0)
	l.Title = style.Title("Select Group  —  " + bot.BotName)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	l.SetShowHelp(false)
	l.Styles.Title = style.ListTitle
	l.Styles.HelpStyle = lipgloss.NewStyle().Foreground(style.TextMuted)

	return &GroupListModel{
		api:  api,
		bot:  bot,
		list: l,
		help: help.New(),
		keys: defaultKeys,
	}
}

func (m *GroupListModel) Init() tea.Cmd {
	return func() tea.Msg {
		groups, err := m.api.GetGroups(m.bot.BotID)
		return GroupsLoadedMsg{Bot: m.bot, Groups: groups, Error: err}
	}
}

func (m *GroupListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4)
		m.help.Width = msg.Width
		return m, nil

	case GroupsLoadedMsg:
		if msg.Error != nil {
			m.list.SetItems([]list.Item{item{title: style.ErrorText("Error: " + msg.Error.Error()), desc: ""}})
			return m, nil
		}
		m.groups = msg.Groups
		items := make([]list.Item, len(msg.Groups))
		for i, g := range msg.Groups {
			items[i] = item{
				title: SymArrow + " " + g.GroupName,
				desc:  "   ID: " + g.GroupID,
			}
		}
		m.list.SetItems(items)
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		if key.Matches(msg, m.keys.Enter) {
			idx := m.list.Index()
			if idx >= 0 && idx < len(m.groups) {
				return m, func() tea.Msg {
					return PushStatusViewMsg{Bot: m.bot, Group: m.groups[idx]}
				}
			}
		}
	}

	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)
	return m, cmd
}

func (m *GroupListModel) View() string {
	return m.list.View() + "\n\n" + m.help.View(m.keys)
}

// ── StatusViewModel ──

type StatusViewModel struct {
	api      *API
	bot      BotInfo
	group    GroupInfo
	status   *GroupStatus
	keys     navKeys
	help     help.Model
	viewport viewport.Model
	width    int
	height   int
	loading  bool
	err      error
}

func NewStatusViewModel(api *API, bot BotInfo, group GroupInfo) *StatusViewModel {
	return &StatusViewModel{
		api:      api,
		bot:      bot,
		group:    group,
		keys:     defaultKeys,
		help:     help.New(),
		viewport: viewport.New(0, 0),
		loading:  true,
	}
}

func (m *StatusViewModel) Init() tea.Cmd {
	return func() tea.Msg {
		status, err := m.api.GetGroupStatus(m.bot.BotID, m.group.GroupID)
		if err != nil {
			return err
		}
		return status
	}
}

func (m *StatusViewModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.viewport.Width = msg.Width
		m.viewport.Height = msg.Height - 3
		if !m.loading && m.err == nil && m.status != nil {
			m.viewport.SetContent(m.buildContent())
		}
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}
		// Scroll viewport
		if key.Matches(msg, m.keys.Up) || key.Matches(msg, m.keys.Down) {
			var cmd tea.Cmd
			m.viewport, cmd = m.viewport.Update(msg)
			return m, cmd
		}
		return m, nil

	case *GroupStatus:
		m.status = msg
		m.loading = false
		m.viewport.SetContent(m.buildContent())
		return m, nil

	case error:
		m.err = msg
		m.loading = false
		return m, nil
	}

	// Forward other messages to viewport
	var cmd tea.Cmd
	m.viewport, cmd = m.viewport.Update(msg)
	return m, cmd
}

func (m *StatusViewModel) View() string {
	if m.loading {
		return "\n  " + style.Muted("Loading group status...")
	}
	if m.err != nil {
		return "\n  " + style.ErrorText("Error: "+m.err.Error())
	}
	if m.status == nil {
		return "\n  " + style.Muted("No data available")
	}
	return m.viewport.View() + "\n" + m.help.View(m.keys)
}

// ── Content builder ──

func (m *StatusViewModel) buildContent() string {
	s := m.status
	w := m.width
	if w < 60 {
		w = 80
	}
	panelW := w - 4
	innerW := panelW - 2

	var rows []string

	// Header
	rows = append(rows, fmt.Sprintf("Bot:  %s  %s    Group: %s  %s",
		style.Title(s.BotName), style.Muted("("+s.BotID+")"),
		style.InfoText(s.GroupName), style.Muted("("+s.GroupID+")"),
	))

	// Plugins (inline)
	rows = append(rows, hline(innerW))
	rows = append(rows, sectionTitle("Plugins")+
		fmt.Sprintf("  Ext:%s  Cmd:%s  Route:%s  Pass:%s",
			style.InfoText(fmt.Sprintf("%d", s.PluginStats.TotalExtensions)),
			style.InfoText(fmt.Sprintf("%d", s.PluginStats.CmdExtensions)),
			style.InfoText(fmt.Sprintf("%d", s.PluginStats.RouteExtensions)),
			style.InfoText(fmt.Sprintf("%d", s.PluginStats.PassiveExtensions)),
		))

	// PAD Emotion
	rows = append(rows, hline(innerW))
	rows = append(rows, sectionTitle("Emotion"))
	if s.BehaviorProfile != nil {
		emoName := emotionName(s.BehaviorProfile.Emotion)
		rows = append(rows, fmt.Sprintf("Status: %s %s",
			lipgloss.NewStyle().Foreground(style.Primary).Bold(true).Render(emoName),
			style.Muted("("+s.BehaviorProfile.Emotion+")"),
		))
	}
	if s.PAD != nil {
		bw := innerW - 22
		rows = append(rows, padBar(bw, "Pleasure", s.PAD.P, lipgloss.Color("#50FA7B")))
		rows = append(rows, padBar(bw, "Arousal", s.PAD.A, lipgloss.Color("#FFB86C")))
		rows = append(rows, padBar(bw, "Dominance", s.PAD.D, lipgloss.Color("#BD93F9")))
	} else {
		rows = append(rows, style.Muted("No emotion data available"))
	}

	// Flow
	rows = append(rows, hline(innerW))
	rows = append(rows, sectionTitle("Flow State"))
	fc := flowColor(s.FlowState.State)
	rows = append(rows, fmt.Sprintf("State: %s %s    Meter: %s",
		lipgloss.NewStyle().Foreground(fc).Bold(true).Render(flowStateName(s.FlowState.State)),
		style.Muted("("+s.FlowState.State+")"),
		lipgloss.NewStyle().Foreground(fc).Bold(true).Render(fmt.Sprintf("%.1f", s.FlowState.Meter)),
	))
	rows = append(rows, renderBar(innerW-4, s.FlowState.Meter, fc, style.Surface))
	rows = append(rows, flowScale(innerW-4))

	// Volition
	rows = append(rows, hline(innerW))
	vstatus := "Observing"
	vstatusCN := "保持观察"
	vcolor := style.TextMuted
	if s.VolitionState.ShouldSpeak {
		vstatus = "Triggered"
		vstatusCN = "主动发言"
		vcolor = style.Success
	}
	rows = append(rows, sectionTitle("Volition"))
	rows = append(rows, "Status: "+lipgloss.NewStyle().Foreground(vcolor).Bold(true).Render(vstatusCN)+" "+style.Muted("("+vstatus+")"))
	bw := innerW - 22
	rows = append(rows, metricBar(bw, "Stimulus", s.VolitionState.Stimulus, lipgloss.Color("#50FA7B")))
	rows = append(rows, metricBar(bw, "Fatigue", s.VolitionState.Fatigue, lipgloss.Color("#BD93F9")))

	// Memory (inline)
	rows = append(rows, hline(innerW))
	rows = append(rows, sectionTitle("Memory")+
		fmt.Sprintf("  Facts:%s  Profiles:%s  Memes:%s(%s)  Vocab:%s",
			style.InfoText(fmt.Sprintf("%d", s.FactSize)),
			style.InfoText(fmt.Sprintf("%d", s.UserProfileSize)),
			style.InfoText(fmt.Sprintf("%d", s.MemeSize)),
			style.Muted(fmt.Sprintf("%d", s.AnalyzedMemeSize)),
			style.InfoText(fmt.Sprintf("%d", len(s.Vocabularies))),
		))

	// Summary
	if s.Summary != nil && *s.Summary != "" {
		rows = append(rows, hline(innerW))
		rows = append(rows, sectionTitle("Summary"))
		rows = append(rows, lipgloss.NewStyle().Foreground(style.Text).Italic(true).Render(*s.Summary))
	}

	content := strings.Join(rows, "\n")

	panel := lipgloss.NewStyle().
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderColor).
		Padding(0, 1).
		Width(panelW).
		Render(content)

	return panel
}

// ── UI helpers ──

func renderBar(width int, percent float64, filledColor, emptyColor lipgloss.TerminalColor) string {
	if width < 3 {
		width = 20
	}
	filled := int(float64(width) * percent / 100.0)
	if filled < 0 {
		filled = 0
	}
	if filled > width {
		filled = width
	}
	empty := width - filled
	f := lipgloss.NewStyle().Foreground(filledColor).Render(strings.Repeat(SymBarFull, filled))
	e := lipgloss.NewStyle().Foreground(emptyColor).Render(strings.Repeat(SymBarEmpty, empty))
	return f + e
}

func sectionTitle(title string) string {
	return lipgloss.NewStyle().Foreground(style.Secondary).Bold(true).Render(title)
}

func hline(width int) string {
	return lipgloss.NewStyle().Foreground(style.BorderColor).Render(strings.Repeat("─", width))
}

func padBar(width int, name string, val float64, barColor lipgloss.TerminalColor) string {
	pct := (val + 4.0) / 8.0 * 100.0
	bar := renderBar(width, pct, barColor, style.Surface)
	ns := mutedLabelStyle.Render(name)
	vs := infoValueStyle.Render(fmt.Sprintf("%.2f", val))
	return fmt.Sprintf("%s  %s  %s", ns, bar, vs)
}

func metricBar(width int, name string, val float64, barColor lipgloss.TerminalColor) string {
	bar := renderBar(width, val, barColor, style.Surface)
	ns := mutedLabelStyle.Render(name)
	vs := infoValueStyle.Render(fmt.Sprintf("%.1f", val))
	return fmt.Sprintf("%s  %s  %s", ns, bar, vs)
}

func flowScale(width int) string {
	if width < 12 {
		width = 12
	}

	// Position markers aligned to progress bar percentages
	pos30 := width * 30 / 100
	if pos30 < 1 {
		pos30 = 1
	}
	pos70 := width * 70 / 100
	if pos70 < pos30+2 {
		pos70 = pos30 + 2
	}
	pos100 := width - 3
	if pos100 < pos70+2 {
		pos100 = pos70 + 2
	}

	var b strings.Builder
	b.Grow(width)

	b.WriteString("0")
	for i := 1; i < pos30; i++ {
		b.WriteString("─")
	}
	b.WriteString("30")
	for i := pos30 + 2; i < pos70; i++ {
		b.WriteString("─")
	}
	b.WriteString("70")
	for i := pos70 + 2; i < pos100; i++ {
		b.WriteString("─")
	}
	b.WriteString("100")

	return lipgloss.NewStyle().Foreground(style.TextMuted).Render(b.String())
}

func flowColor(state string) lipgloss.TerminalColor {
	switch state {
	case "STANDBY":
		return style.TextMuted
	case "FLOW_BURST":
		return lipgloss.Color("#ef4444")
	default:
		return style.Info
	}
}

func emotionName(en string) string {
	switch en {
	case "JOY":
		return "喜悦"
	case "OPTIMISM":
		return "乐观"
	case "RELAXATION":
		return "轻松"
	case "SURPRISE":
		return "惊奇"
	case "MILDNESS":
		return "温和"
	case "DEPENDENCE":
		return "依赖"
	case "BOREDOM":
		return "无聊"
	case "SADNESS":
		return "悲伤"
	case "FEAR":
		return "恐惧"
	case "ANXIETY":
		return "焦虑"
	case "CONTEMPT":
		return "藐视"
	case "DISGUST":
		return "厌恶"
	case "RESENTMENT":
		return "愤懑"
	case "HOSTILITY":
		return "敌意"
	default:
		return en
	}
}

func flowStateName(en string) string {
	switch en {
	case "STANDBY":
		return "状态未开"
	case "GETTING_BETTER":
		return "渐入佳境"
	case "FLOW_BURST":
		return "心流爆发"
	default:
		return en
	}
}
