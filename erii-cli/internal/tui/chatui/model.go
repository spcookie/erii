package chatui

import (
	"fmt"
	"strings"
	"time"

	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/textarea"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// --- message types ---

type msgType int

const (
	msgUser msgType = iota
	msgBot
)

type chatMsg struct {
	typ       msgType
	content   string
	timestamp time.Time
	duration  time.Duration // bot reply duration from last user send (0 for user msgs)
}

// --- tea messages ---

type botResponseMsg string
type chatErrorMsg struct{ err error }
type historyLoadedMsg struct {
	entries []api.ChatHistoryEntry
	hasMore bool
}
type historyErrorMsg struct{ err error }

// --- key bindings ---

type chatKeyMap struct {
	Send   key.Binding
	Scroll key.Binding
	Back   key.Binding
	Quit   key.Binding
}

func (k chatKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Send, k.Scroll, k.Quit}
}
func (k chatKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Send, k.Quit}, {k.Scroll}}
}

var focusedKeys = chatKeyMap{
	Send:   key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "send")),
	Scroll: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "scroll")),
	Quit:   key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

type scrollKeyMap struct {
	Nav  key.Binding
	Type key.Binding
	Back key.Binding
	Quit key.Binding
}

func (k scrollKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Nav, k.Type, k.Back}
}
func (k scrollKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Nav, k.Type}, {k.Back, k.Quit}}
}

var blurredKeys = scrollKeyMap{
	Nav:  key.NewBinding(key.WithKeys("↑/↓/PgUp/PgDn"), key.WithHelp("↑/↓/PgUp/PgDn", "scroll")),
	Type: key.NewBinding(key.WithKeys("i / enter"), key.WithHelp("i/enter", "type")),
	Back: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "back")),
	Quit: key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

type errorKeyMap struct {
	Dismiss key.Binding
	Quit    key.Binding
}

func (k errorKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Dismiss, k.Quit}
}
func (k errorKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Dismiss, k.Quit}}
}

type sendingKeyMap struct {
	Cancel key.Binding
	Quit   key.Binding
}

func (k sendingKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Cancel, k.Quit}
}
func (k sendingKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Cancel, k.Quit}}
}

var sendingKeys = sendingKeyMap{
	Cancel: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "cancel")),
	Quit:   key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

var errorKeys = errorKeyMap{
	Dismiss: key.NewBinding(key.WithKeys("r"), key.WithHelp("r", "dismiss")),
	Quit:    key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

// --- Model ---

type Model struct {
	client               *api.Client
	wsConn               *api.ChatWSConn
	roleName             string
	messages             []chatMsg
	viewport             viewport.Model
	textarea             textarea.Model
	spinner              spinner.Model
	help                 help.Model
	sending              bool
	sendCancelled        bool
	err                  error
	width                int
	height               int
	focused              bool
	BackToRole           bool
	historyEarliestId    int64
	hasMoreHistory       bool
	loadingHistory       bool
	initialHistoryLoaded bool
	lastSendTime         time.Time
	wsMsgCh              chan tea.Msg
}

func initialModel(client *api.Client, wsConn *api.ChatWSConn, roleName string, wsMsgCh chan tea.Msg) *Model {
	ta := textarea.New()
	ta.Placeholder = "Type a message..."
	ta.ShowLineNumbers = false
	ta.SetHeight(3)
	ta.CharLimit = 2000
	ta.KeyMap.InsertNewline = key.NewBinding(key.WithKeys("ctrl+j"), key.WithHelp("ctrl+j", "insert newline"))
	ta.FocusedStyle.CursorLine = lipgloss.NewStyle().
		Foreground(style.Primary)
	ta.BlurredStyle.CursorLine = lipgloss.NewStyle().
		Foreground(style.TextMuted)
	ta.Focus()

	vp := viewport.New(0, 0)
	vp.KeyMap = viewport.KeyMap{}

	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(style.Accent)

	return &Model{
		client:         client,
		wsConn:         wsConn,
		roleName:       roleName,
		messages:       make([]chatMsg, 0),
		viewport:       vp,
		textarea:       ta,
		spinner:        s,
		help:           help.New(),
		focused:        true,
		loadingHistory: true,
		wsMsgCh:        wsMsgCh,
	}
}

func (m *Model) Init() tea.Cmd {
	return tea.Batch(textarea.Blink, m.spinner.Tick, m.wsListen())
}

func (m *Model) wsListen() tea.Cmd {
	return func() tea.Msg {
		return <-m.wsMsgCh
	}
}

func (m *Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.recalcSizes()
		if !m.initialHistoryLoaded {
			m.initialHistoryLoaded = true
			return m, func() tea.Msg {
				resp, err := m.client.GetChatHistory(0, 50)
				if err != nil {
					return historyErrorMsg{err}
				}
				return historyLoadedMsg{entries: resp.Entries, hasMore: resp.HasMore}
			}
		}
		return m, nil

	case spinner.TickMsg:
		var cmd tea.Cmd
		m.spinner, cmd = m.spinner.Update(msg)
		if m.sending {
			m.viewport.SetContent(m.renderMessages())
			m.viewport.GotoBottom()
		}
		return m, cmd

	case tea.KeyMsg:
		if msg.String() == "ctrl+c" {
			return m, tea.Quit
		}

		if m.sending {
			if msg.String() == "esc" {
				m.sendCancelled = true
				m.sending = false
				m.textarea.Focus()
				m.recalcSizes()
				return m, textarea.Blink
			}
			return m, nil
		}

		// error state: limited keys
		if m.err != nil {
			switch msg.String() {
			case "r", "R":
				m.err = nil
				m.recalcSizes()
				return m, textarea.Blink
			}
			return m, nil
		}

		// ESC behavior
		if msg.String() == "esc" {
			if m.focused {
				m.textarea.Blur()
				m.focused = false
				return m, nil
			}
			m.BackToRole = true
			return m, tea.Quit
		}

		// --- blurred mode: scroll viewport ---
		if !m.focused {
			switch msg.String() {
			case "i", "enter":
				m.textarea.Focus()
				m.focused = true
				return m, textarea.Blink
			case "up", "k":
				m.viewport.LineUp(1)
				return m, m.checkHistoryTrigger()
			case "down", "j":
				m.viewport.LineDown(1)
				return m, nil
			case "pgup":
				m.viewport.ViewUp()
				return m, m.checkHistoryTrigger()
			case "pgdown":
				m.viewport.ViewDown()
				return m, nil
			case "home":
				m.viewport.GotoTop()
				return m, m.checkHistoryTrigger()
			case "end":
				m.viewport.GotoBottom()
				return m, nil
			default:
				m.textarea.Focus()
				m.focused = true
				var cmd tea.Cmd
				m.textarea, cmd = m.textarea.Update(msg)
				return m, cmd
			}
		}

		// --- focused mode ---
		switch msg.String() {
		case "enter":
			text := strings.TrimSpace(m.textarea.Value())
			if text == "" {
				return m, nil
			}
			now := time.Now()
			m.lastSendTime = now
			m.messages = append(m.messages, chatMsg{typ: msgUser, content: text, timestamp: now})
			m.textarea.Reset()
			m.textarea.Blur()
			m.sending = true
			m.err = nil
			m.recalcSizes()
			if err := m.wsConn.SendMessage(text); err != nil {
				m.sending = false
				m.textarea.Focus()
				m.err = err
				m.recalcSizes()
				return m, textarea.Blink
			}
			return m, m.wsListen()

		default:
			var cmd tea.Cmd
			m.textarea, cmd = m.textarea.Update(msg)
			return m, cmd
		}

	case botResponseMsg:
		var cmds []tea.Cmd
		m, cmds = m.finishSending()
		now := time.Now()
		dur := now.Sub(m.lastSendTime)
		m.messages = append(m.messages, chatMsg{typ: msgBot, content: string(msg), timestamp: now, duration: dur})
		m.recalcSizes()
		return m, tea.Batch(append(cmds, textarea.Blink, m.wsListen())...)

	case chatErrorMsg:
		var cmds []tea.Cmd
		m, cmds = m.finishSending()
		m.err = msg.err
		m.recalcSizes()
		return m, tea.Batch(append(cmds, textarea.Blink, m.wsListen())...)

	case historyLoadedMsg:
		return m.handleHistoryLoaded(msg)

	case historyErrorMsg:
		m.loadingHistory = false
		m.recalcSizes()
		return m, nil
	}

	return m, nil
}

func (m *Model) handleHistoryLoaded(msg historyLoadedMsg) (tea.Model, tea.Cmd) {
	m.loadingHistory = false
	if len(msg.entries) == 0 {
		m.recalcSizes()
		return m, nil
	}

	var histMsgs []chatMsg
	for _, e := range msg.entries {
		typ := msgBot
		if e.Sender == "user" {
			typ = msgUser
		}
		histMsgs = append(histMsgs, chatMsg{
			typ:       typ,
			content:   e.Content,
			timestamp: time.UnixMilli(e.Timestamp),
		})
	}

	isInitial := len(m.messages) == 0
	savedYOffset := m.viewport.YOffset
	oldLines := strings.Count(m.renderMessages(), "\n")
	m.messages = append(histMsgs, m.messages...)
	m.hasMoreHistory = msg.hasMore
	if len(msg.entries) > 0 {
		m.historyEarliestId = msg.entries[0].ID
	}
	newContent := m.renderMessages()
	newLines := strings.Count(newContent, "\n")
	m.viewport.SetContent(newContent)
	if isInitial {
		m.viewport.GotoBottom()
	} else {
		m.viewport.YOffset = savedYOffset + (newLines - oldLines)
	}

	return m, nil
}

func (m *Model) checkHistoryTrigger() tea.Cmd {
	if m.viewport.AtTop() && m.hasMoreHistory && !m.loadingHistory {
		m.loadingHistory = true
		earliest := m.historyEarliestId
		return func() tea.Msg {
			resp, err := m.client.GetChatHistory(earliest, 50)
			if err != nil {
				return historyErrorMsg{err}
			}
			return historyLoadedMsg{entries: resp.Entries, hasMore: resp.HasMore}
		}
	}
	return nil
}

func (m *Model) finishSending() (*Model, []tea.Cmd) {
	var cmds []tea.Cmd
	if m.sendCancelled {
		m.sendCancelled = false
		cmds = append(cmds, m.wsListen())
		return m, cmds
	}
	m.sending = false
	m.textarea.Focus()
	return m, cmds
}

func (m *Model) recalcSizes() {
	if m.width == 0 || m.height == 0 {
		return
	}
	headerH := 1

	// footer: textarea + optional status line + help
	textareaH := lipgloss.Height(m.textarea.View())
	statusH := 0
	if m.sending || m.err != nil || !m.focused {
		statusH = 1
	}
	helpH := lipgloss.Height(m.helpView())
	footerH := textareaH + statusH + helpH

	m.viewport.Width = m.width - 2
	m.viewport.Height = m.height - headerH - footerH
	if m.viewport.Height < 2 {
		m.viewport.Height = 2
	}
	m.textarea.SetWidth(m.width - 4)
	m.viewport.SetContent(m.renderMessages())
	m.viewport.GotoBottom()
}

func (m *Model) helpView() string {
	switch {
	case m.err != nil:
		return m.help.View(errorKeys)
	case m.sending:
		return m.help.View(sendingKeys)
	case !m.focused:
		return m.help.View(blurredKeys)
	default:
		return m.help.View(focusedKeys)
	}
}

func (m *Model) View() string {
	title := lipgloss.NewStyle().
		Foreground(style.Primary).
		Bold(true).
		Padding(0, 1).
		Render(m.roleName + " Chat")

	titleBar := lipgloss.NewStyle().
		BorderStyle(lipgloss.NormalBorder()).
		BorderBottom(true).
		BorderForeground(style.BorderColor).
		Width(m.width - 2).
		Render(title)

	chatArea := m.viewport.View()

	textareaView := m.textarea.View()

	var statusLine string
	if m.err != nil {
		statusLine = lipgloss.NewStyle().
			Foreground(style.Error).
			Padding(0, 1).
			Render("✗ " + components.FriendlyErrorMessage(m.err))
	}

	var inputArea string
	if statusLine != "" {
		inputArea = lipgloss.JoinVertical(lipgloss.Top, textareaView, statusLine)
	} else {
		inputArea = textareaView
	}

	return lipgloss.JoinVertical(lipgloss.Top,
		titleBar,
		chatArea,
		"",
		inputArea,
		m.helpView(),
	)
}

// --- message rendering ---

func (m *Model) renderMessages() string {
	if len(m.messages) == 0 && !m.sending && !m.loadingHistory {
		return lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Italic(true).
			Padding(0, 2).
			Render("Start a conversation with " + m.roleName + "...")
	}

	maxWidth := m.viewport.Width - 2
	msgWidth := maxWidth - 4
	if msgWidth < 20 {
		msgWidth = 20
	}

	var lines []string
	if m.loadingHistory {
		lines = append(lines, lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Italic(true).
			Padding(0, 1).
			Render("Loading history..."),
			"")
	}
	for _, msg := range m.messages {
		lines = append(lines, "")
		wrapped := wrapText(msg.content, msgWidth)
		if msg.typ == msgUser {
			lines = append(lines, userLabel(maxWidth, msg.timestamp))
			for _, line := range strings.Split(wrapped, "\n") {
				lines = append(lines, lipgloss.NewStyle().
					Foreground(style.Text).
					Align(lipgloss.Right).
					Width(maxWidth).
					Render(line))
			}
		} else {
			lines = append(lines, botLabel(m.roleName, msg.timestamp, msg.duration))
			for _, line := range strings.Split(wrapped, "\n") {
				lines = append(lines, lipgloss.NewStyle().
					Foreground(style.Text).
					PaddingLeft(2).
					Render(line))
			}
		}
	}

	if m.sending {
		lines = append(lines, "")
		lines = append(lines, thinkingBubble(m.spinner.View()))
	}

	return strings.Join(lines, "\n")
}

func thinkingBubble(spinnerView string) string {
	return lipgloss.NewStyle().
		PaddingLeft(2).
		Render(spinnerView + " " + lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Italic(true).
			Render("Thinking..."))
}

func userLabel(width int, t time.Time) string {
	timeStr := lipgloss.NewStyle().
		Foreground(style.TextMuted).
		Italic(true).
		Render(formatTimeMsg(t))
	label := lipgloss.NewStyle().
		Foreground(style.Info).
		Bold(true).
		Render("You")
	return lipgloss.NewStyle().
		Align(lipgloss.Right).
		Width(width).
		Render(timeStr + "  " + label)
}

func botLabel(name string, t time.Time, dur time.Duration) string {
	nameStyle := lipgloss.NewStyle().
		Foreground(style.Primary).
		Bold(true).
		Render(name)
	timeStr := lipgloss.NewStyle().
		Foreground(style.TextMuted).
		Italic(true).
		Render(formatTimeMsg(t))
	meta := nameStyle + "  " + timeStr
	if dur > 0 {
		meta += "  " + lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Render("• "+formatDuration(dur))
	}
	return meta
}

func formatTimeMsg(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	now := time.Now()
	diff := now.Sub(t)
	switch {
	case diff < time.Minute:
		return "just now"
	case diff < time.Hour:
		mins := int(diff.Minutes())
		if mins == 1 {
			return "1 min before"
		}
		return fmt.Sprintf("%d mins before", mins)
	case isToday(t):
		return t.Format("15:04:05")
	case isYesterday(t):
		return "Yesterday"
	default:
		return t.Format("2006/01/02 15:04:05")
	}
}

func isToday(t time.Time) bool {
	now := time.Now()
	ty, tm, td := t.Date()
	ny, nm, nd := now.Date()
	return ty == ny && tm == nm && td == nd
}

func isYesterday(t time.Time) bool {
	now := time.Now()
	yy, ym, yd := now.AddDate(0, 0, -1).Date()
	ty, tm, td := t.Date()
	return ty == yy && tm == ym && td == yd
}

func formatDuration(d time.Duration) string {
	if d < time.Second {
		ms := d.Milliseconds()
		if ms < 100 {
			return fmt.Sprintf("%dms", ms)
		}
		return fmt.Sprintf("%.1fs", d.Seconds())
	}
	if d < time.Minute {
		sec := int(d.Seconds())
		return fmt.Sprintf("%ds", sec)
	}
	mins := int(d.Minutes())
	secs := int(d.Seconds()) % 60
	if secs == 0 {
		return fmt.Sprintf("%dm", mins)
	}
	return fmt.Sprintf("%dm%ds", mins, secs)
}

func wrapText(text string, width int) string {
	if width <= 0 {
		return text
	}
	var result []string
	for _, paragraph := range strings.Split(text, "\n") {
		if len(paragraph) == 0 {
			result = append(result, "")
			continue
		}
		for len(paragraph) > width {
			split := width
			for split > 0 && paragraph[split] != ' ' {
				split--
			}
			if split == 0 {
				split = width
			}
			result = append(result, paragraph[:split])
			paragraph = strings.TrimLeft(paragraph[split:], " \t")
		}
		if len(paragraph) > 0 {
			result = append(result, paragraph)
		}
	}
	return strings.Join(result, "\n")
}
