package manage

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"
	"fmt"
	"strconv"
	"strings"

	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// ── Data messages ──

type (
	StateLoadedMsg struct {
		Type  StateType
		Data  any
		Error error
	}
	StateUpdatedMsg struct {
		Type StateType
		Data any
	}
)

// ── Key bindings ──

type detailKeys struct {
	Up   key.Binding
	Down key.Binding
	Edit key.Binding
	Back key.Binding
	Quit key.Binding
	Help key.Binding
}

func (k detailKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Edit, k.Back, k.Quit}
}

func (k detailKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down},
		{k.Edit, k.Back, k.Quit},
	}
}

var detailDefaultKeys = detailKeys{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	Edit: key.NewBinding(
		key.WithKeys("e"),
		key.WithHelp("e", "edit"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc", "back"),
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

// ── StateDetailModel ──

type StateDetailModel struct {
	api       *api.Client
	bot       api.BotInfo
	group     api.GroupInfo
	stateType StateType

	// Data
	emotion  *api.EmotionRecord
	flow     *api.FlowRecord
	volition *api.VolitionRecord
	loading  bool
	err      error

	// Editing
	editing bool
	form    *huh.Form

	// Form values (string for input binding)
	moodP             string
	moodA             string
	moodD             string
	emotionalTendency string
	tone              string
	aggressiveness    string
	emojiLevel        string
	flowValue         string
	currentTopic      string
	volitionStimulus  string
	volitionFatigue   string

	width      int
	height     int
	helpHeight int
	keys       detailKeys
	help       help.Model
	viewport   viewport.Model
}

func NewStateDetailModel(api *api.Client, st StateType, bot api.BotInfo, group api.GroupInfo) *StateDetailModel {
	m := &StateDetailModel{
		api:       api,
		bot:       bot,
		group:     group,
		stateType: st,
		loading:   true,
		keys:      detailDefaultKeys,
		help:      help.New(),
		viewport:  viewport.New(0, 0),
	}
	m.helpHeight = lipgloss.Height(m.help.View(defaultEditFormKeys))
	return m
}

func (m *StateDetailModel) Init() tea.Cmd {
	return m.loadState()
}

func (m *StateDetailModel) loadState() tea.Cmd {
	return func() tea.Msg {
		switch m.stateType {
		case StateEmotion:
			data, err := m.api.GetEmotion(m.bot.BotID, m.group.GroupID)
			return StateLoadedMsg{Type: StateEmotion, Data: data, Error: err}
		case StateFlow:
			data, err := m.api.GetFlow(m.bot.BotID, m.group.GroupID)
			return StateLoadedMsg{Type: StateFlow, Data: data, Error: err}
		case StateVolition:
			data, err := m.api.GetVolition(m.bot.BotID, m.group.GroupID)
			return StateLoadedMsg{Type: StateVolition, Data: data, Error: err}
		}
		return StateLoadedMsg{Error: fmt.Errorf("unknown state type")}
	}
}

func (m *StateDetailModel) applyStateUpdate(msg StateUpdatedMsg) {
	switch msg.Type {
	case StateEmotion:
		if d, ok := msg.Data.(*api.EmotionRecord); ok {
			m.emotion = d
		}
	case StateFlow:
		if d, ok := msg.Data.(*api.FlowRecord); ok {
			m.flow = d
		}
	case StateVolition:
		if d, ok := msg.Data.(*api.VolitionRecord); ok {
			m.volition = d
		}
	}
}

func (m *StateDetailModel) formSize() (w, h int) {
	w = m.width - 4
	if w < 20 {
		w = 60
	}
	h = m.height - m.helpHeight - 1
	if h < 5 {
		h = 5
	}
	return w, h
}

func (m *StateDetailModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.editing {
		return m.updateEditing(msg)
	}
	return m.updateViewing(msg)
}

func (m *StateDetailModel) updateViewing(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.viewport.Width = msg.Width
		const footerGap = 1
		viewH := msg.Height - lipgloss.Height(m.help.View(m.keys)) - footerGap
		if viewH < 3 {
			viewH = 3
		}
		m.viewport.Height = viewH
		return m, nil

	case StateLoadedMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.applyStateUpdate(StateUpdatedMsg{Type: msg.Type, Data: msg.Data})
		return m, nil

	case StateUpdatedMsg:
		m.loading = false
		m.applyStateUpdate(msg)
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
		if key.Matches(msg, m.keys.Edit) {
			m.startEditing()
			if m.form != nil {
				return m, m.form.Init()
			}
			return m, nil
		}
		if key.Matches(msg, m.keys.Up) {
			m.viewport.LineUp(1)
			return m, nil
		}
		if key.Matches(msg, m.keys.Down) {
			m.viewport.LineDown(1)
			return m, nil
		}
	}

	var vcmd tea.Cmd
	m.viewport, vcmd = m.viewport.Update(msg)
	return m, vcmd
}

func (m *StateDetailModel) updateEditing(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.helpHeight = lipgloss.Height(m.help.View(defaultEditFormKeys))
		if m.form != nil {
			w, h := m.formSize()
			m.form.WithWidth(w).WithHeight(h)
		}
		return m, nil

	case tea.KeyMsg:
		if msg.Type == tea.KeyCtrlC {
			return m, tea.Quit
		}
		if msg.Type == tea.KeyEsc {
			m.editing = false
			m.form = nil
			return m, nil
		}

	case StateUpdatedMsg:
		m.loading = false
		m.editing = false
		m.form = nil
		m.applyStateUpdate(msg)
		return m, nil

	case error:
		m.loading = false
		m.editing = false
		m.form = nil
		m.err = msg
		return m, nil
	}

	if m.form != nil {
		var cmd tea.Cmd
		var newModel tea.Model
		newModel, cmd = m.form.Update(msg)
		m.form = newModel.(*huh.Form)

		if m.form.State == huh.StateCompleted {
			return m, m.submit()
		}
		if m.form.State == huh.StateAborted {
			m.editing = false
			m.form = nil
			return m, nil
		}
		return m, cmd
	}

	return m, nil
}

func (m *StateDetailModel) View() string {
	if m.loading {
		return "\n  " + style.Muted("Loading "+m.stateType.String()+" state...")
	}
	if m.err != nil {
		return "\n  " + style.ErrorText("Error: "+m.err.Error())
	}

	if m.editing && m.form != nil {
		return m.form.View() + "\n" + m.help.View(defaultEditFormKeys)
	}

	m.viewport.SetContent(m.buildContent())
	return m.viewport.View() + "\n" + m.help.View(m.keys)
}

// ── Content builder ──

func (m *StateDetailModel) buildContent() string {
	w := m.width
	if w < 60 {
		w = 80
	}
	panelW := w - 4
	innerW := panelW - 2

	var rows []string

	// Header
	rows = append(rows, fmt.Sprintf("Bot:  %s  %s    Group: %s  %s",
		style.Title(m.bot.BotName), style.Muted("("+m.bot.BotID+")"),
		style.InfoText(m.group.GroupName), style.Muted("("+m.group.GroupID+")"),
	))

	rows = append(rows, hline(innerW))
	rows = append(rows, sectionTitle(m.stateType.Title()+" State"))

	switch m.stateType {
	case StateEmotion:
		rows = append(rows, m.buildEmotionContent(innerW)...)
	case StateFlow:
		rows = append(rows, m.buildFlowContent(innerW)...)
	case StateVolition:
		rows = append(rows, m.buildVolitionContent(innerW)...)
	}

	content := strings.Join(rows, "\n")
	panel := lipgloss.NewStyle().
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderStrong).
		Padding(0, 1).
		Width(panelW).
		Render(content)

	return panel
}

func (m *StateDetailModel) buildEmotionContent(w int) []string {
	if m.emotion == nil {
		return []string{style.Muted("No emotion data available")}
	}
	e := m.emotion
	var rows []string

	rows = append(rows, fmt.Sprintf("Tendency: %s %s",
		lipgloss.NewStyle().Foreground(style.ChartCyan).Bold(true).Render(emotionName(e.EmotionalTendency)),
		style.Muted("("+e.EmotionalTendency+")"),
	))

	bw := w - 22
	rows = append(rows, padBar(bw, "Mood P", e.Mood.P, style.ChartBlue))
	rows = append(rows, padBar(bw, "Mood A", e.Mood.A, style.ChartAmber))
	rows = append(rows, padBar(bw, "Mood D", e.Mood.D, style.ChartViolet))

	rows = append(rows, "")
	rows = append(rows, lipgloss.NewStyle().Foreground(style.Secondary).Bold(true).Render("Behavior Profile"))
	rows = append(rows, fmt.Sprintf("  Emotion:        %s", style.InfoText(e.Behavior.Emotion)))
	rows = append(rows, fmt.Sprintf("  Tone:           %s", style.InfoText(e.Behavior.Tone)))
	rows = append(rows, fmt.Sprintf("  Aggressiveness: %s", style.InfoText(e.Behavior.Aggressiveness)))
	rows = append(rows, fmt.Sprintf("  Emoji Level:    %s", style.InfoText(e.Behavior.EmojiLevel)))

	return rows
}

func (m *StateDetailModel) buildFlowContent(w int) []string {
	if m.flow == nil {
		return []string{style.Muted("No flow data available")}
	}
	f := m.flow
	var rows []string

	fc := style.Info
	rows = append(rows, fmt.Sprintf("Flow Value: %s",
		lipgloss.NewStyle().Foreground(fc).Bold(true).Render(fmt.Sprintf("%.1f", f.FlowValue)),
	))
	rows = append(rows, renderBar(w-4, f.FlowValue, fc, style.Track))
	rows = append(rows, "")
	rows = append(rows, fmt.Sprintf("Current Topic: %s", style.InfoText(f.CurrentTopic)))

	return rows
}

func (m *StateDetailModel) buildVolitionContent(w int) []string {
	if m.volition == nil {
		return []string{style.Muted("No volition data available")}
	}
	v := m.volition
	var rows []string

	bw := w - 22
	rows = append(rows, metricBar(bw, "Stimulus", v.Stimulus, style.ChartBlue))
	rows = append(rows, metricBar(bw, "Fatigue", v.Fatigue, style.ChartViolet))

	return rows
}

// ── UI helpers ──

func sectionTitle(title string) string {
	return lipgloss.NewStyle().Foreground(style.Secondary).Bold(true).Render(title)
}

func hline(width int) string {
	return lipgloss.NewStyle().Foreground(style.BorderColor).Render(strings.Repeat("─", width))
}

func renderBar(width int, percent float64, filledColor, emptyColor lipgloss.TerminalColor) string {
	return components.ProgressBar(width, percent, filledColor, emptyColor)
}

func padBar(width int, name string, val float64, barColor lipgloss.TerminalColor) string {
	pct := (val + 4.0) / 8.0 * 100.0
	bar := renderBar(width, pct, barColor, style.Track)
	ns := lipgloss.NewStyle().Foreground(style.TextMuted).Width(10).Render(name)
	vs := lipgloss.NewStyle().Foreground(style.Info).Bold(true).Render(fmt.Sprintf("%.2f", val))
	return fmt.Sprintf("%s  %s  %s", ns, bar, vs)
}

func metricBar(width int, name string, val float64, barColor lipgloss.TerminalColor) string {
	bar := renderBar(width, val, barColor, style.Track)
	ns := lipgloss.NewStyle().Foreground(style.TextMuted).Width(10).Render(name)
	vs := lipgloss.NewStyle().Foreground(style.Info).Bold(true).Render(fmt.Sprintf("%.1f", val))
	return fmt.Sprintf("%s  %s  %s", ns, bar, vs)
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

func validateFloatRange(min, max float64) func(string) error {
	return func(s string) error {
		if s == "" {
			return fmt.Errorf("cannot be empty")
		}
		v, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return fmt.Errorf("must be a valid number")
		}
		if v < min || v > max {
			return fmt.Errorf("must be between %.1f and %.1f", min, max)
		}
		return nil
	}
}

// ── Editing ──

func (m *StateDetailModel) startEditing() {
	w, scrollH := m.formSize()

	switch m.stateType {
	case StateEmotion:
		m.startEmotionEdit(w, scrollH)
	case StateFlow:
		m.startFlowEdit(w, scrollH)
	case StateVolition:
		m.startVolitionEdit(w, scrollH)
	}

	if m.form != nil {
		m.editing = true
	}
}

func (m *StateDetailModel) startEmotionEdit(w, h int) {
	if m.emotion == nil {
		return
	}
	e := m.emotion
	m.moodP = fmt.Sprintf("%.2f", e.Mood.P)
	m.moodA = fmt.Sprintf("%.2f", e.Mood.A)
	m.moodD = fmt.Sprintf("%.2f", e.Mood.D)
	m.emotionalTendency = e.EmotionalTendency
	m.tone = e.Behavior.Tone
	m.aggressiveness = e.Behavior.Aggressiveness
	m.emojiLevel = e.Behavior.EmojiLevel

	m.form = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("moodP").Title("Mood P (Pleasure)").Value(&m.moodP).Validate(validateFloatRange(-4, 4)),
			huh.NewInput().Key("moodA").Title("Mood A (Arousal)").Value(&m.moodA).Validate(validateFloatRange(-4, 4)),
			huh.NewInput().Key("moodD").Title("Mood D (Dominance)").Value(&m.moodD).Validate(validateFloatRange(-4, 4)),
			huh.NewSelect[string]().Key("tendency").Title("Emotional Tendency").
				Options(
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
				).
				Value(&m.emotionalTendency),
			huh.NewSelect[string]().Key("tone").Title("Tone").
				Options(
					huh.NewOption("FRIENDLY", "FRIENDLY"),
					huh.NewOption("GENTLE", "GENTLE"),
					huh.NewOption("NEUTRAL", "NEUTRAL"),
					huh.NewOption("IRONIC", "IRONIC"),
					huh.NewOption("LOW_ENERGY", "LOW_ENERGY"),
				).
				Value(&m.tone),
			huh.NewSelect[string]().Key("aggressiveness").Title("Aggressiveness").
				Options(
					huh.NewOption("NONE", "NONE"),
					huh.NewOption("ABSTRACT_SARCASM", "ABSTRACT_SARCASM"),
					huh.NewOption("TEASING", "TEASING"),
				).
				Value(&m.aggressiveness),
			huh.NewSelect[string]().Key("emojiLevel").Title("Emoji Level").
				Options(
					huh.NewOption("NONE", "NONE"),
					huh.NewOption("LOW", "LOW"),
					huh.NewOption("MEDIUM", "MEDIUM"),
					huh.NewOption("HIGH", "HIGH"),
				).
				Value(&m.emojiLevel),
		).WithHeight(h),
	).WithWidth(w).WithShowHelp(false)
}

func (m *StateDetailModel) startFlowEdit(w, h int) {
	if m.flow == nil {
		return
	}
	f := m.flow
	m.flowValue = fmt.Sprintf("%.1f", f.FlowValue)
	m.currentTopic = f.CurrentTopic

	m.form = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("flowValue").Title("Flow Value (0-100)").Value(&m.flowValue).Validate(validateFloatRange(0, 100)),
			huh.NewText().Key("currentTopic").Title("Current Topic (multi-line)").Value(&m.currentTopic),
		).WithHeight(h),
	).WithWidth(w).WithShowHelp(false)
}

func (m *StateDetailModel) startVolitionEdit(w, h int) {
	if m.volition == nil {
		return
	}
	v := m.volition
	m.volitionStimulus = fmt.Sprintf("%.1f", v.Stimulus)
	m.volitionFatigue = fmt.Sprintf("%.1f", v.Fatigue)

	m.form = huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("stimulus").Title("Stimulus (0-100)").Value(&m.volitionStimulus).Validate(validateFloatRange(0, 100)),
			huh.NewInput().Key("fatigue").Title("Fatigue (0-100)").Value(&m.volitionFatigue).Validate(validateFloatRange(0, 100)),
		).WithHeight(h),
	).WithWidth(w).WithShowHelp(false)
}

func (m *StateDetailModel) submit() tea.Cmd {
	client := m.api
	botID := m.bot.BotID
	groupID := m.group.GroupID
	st := m.stateType

	return func() tea.Msg {
		switch st {
		case StateEmotion:
			p, _ := strconv.ParseFloat(m.moodP, 64)
			a, _ := strconv.ParseFloat(m.moodA, 64)
			d, _ := strconv.ParseFloat(m.moodD, 64)
			req := api.UpdateEmotionRequest{
				EmotionalTendency: m.emotionalTendency,
				Stimulus:          api.PAD{P: p, A: a, D: d},
				Emotion:           api.PAD{P: p, A: a, D: d},
				Mood:              api.PAD{P: p, A: a, D: d},
				Behavior: api.BehaviorProfile{
					Emotion:        m.emotionalTendency,
					Tone:           m.tone,
					Aggressiveness: m.aggressiveness,
					EmojiLevel:     m.emojiLevel,
				},
			}
			data, err := client.UpdateEmotion(botID, groupID, req)
			if err != nil {
				return err
			}
			return StateUpdatedMsg{Type: StateEmotion, Data: data}

		case StateFlow:
			fv, _ := strconv.ParseFloat(m.flowValue, 64)
			req := api.UpdateFlowRequest{
				FlowValue:    fv,
				CurrentTopic: m.currentTopic,
			}
			data, err := client.UpdateFlow(botID, groupID, req)
			if err != nil {
				return err
			}
			return StateUpdatedMsg{Type: StateFlow, Data: data}

		case StateVolition:
			stimulus, _ := strconv.ParseFloat(m.volitionStimulus, 64)
			fatigue, _ := strconv.ParseFloat(m.volitionFatigue, 64)
			req := api.UpdateVolitionRequest{
				Stimulus: stimulus,
				Fatigue:  fatigue,
			}
			data, err := client.UpdateVolition(botID, groupID, req)
			if err != nil {
				return err
			}
			return StateUpdatedMsg{Type: StateVolition, Data: data}
		}
		return fmt.Errorf("unknown state type")
	}
}
