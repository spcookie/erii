package chatui

import (
	"encoding/base64"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"
	"erii-cli/internal/tui/components/md"
	style "erii-cli/internal/ui/theme"

	"charm.land/glamour/v2"
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
	id                 int64
	typ                msgType
	content            string
	markdownContent    string
	timestamp          time.Time
	duration           time.Duration // bot reply duration from last user send (0 for user msgs)
	hasImage           bool
	imageState         historyImageState
	imageBytes         []byte
	imageContent       string
	imageAvailableCols int
	imageRendering     bool
}

type historyImageState int

const (
	historyImageNone historyImageState = iota
	historyImageLoading
	historyImageReady
	historyImageError
)

// --- tea messages ---

type botResponseMsg struct {
	requestID string
	response  string
}
type chatErrorMsg struct {
	err          error
	disconnected bool
	requestID    string
}
type chatReconnectMsg struct {
	conn *api.ChatWSConn
	err  error
}
type historyLoadedMsg struct {
	entries []api.ChatHistoryEntry
	hasMore bool
}
type historyErrorMsg struct{ err error }
type historyImageRenderedMsg struct {
	id            int64
	data          []byte
	rendered      historyImageRender
	availableCols int
	refresh       bool
	err           error
}

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

type connectionErrorKeyMap struct {
	Dismiss key.Binding
	Retry   key.Binding
	Quit    key.Binding
}

func (k connectionErrorKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Dismiss, k.Retry, k.Quit}
}
func (k connectionErrorKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Dismiss, k.Retry, k.Quit}}
}

type disconnectedKeyMap struct {
	Retry key.Binding
	Back  key.Binding
	Quit  key.Binding
}

func (k disconnectedKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Retry, k.Back, k.Quit}
}
func (k disconnectedKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Retry, k.Back, k.Quit}}
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
	Dismiss: key.NewBinding(key.WithKeys("esc", "r"), key.WithHelp("esc/r", "dismiss")),
	Quit:    key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

var connectionErrorKeys = connectionErrorKeyMap{
	Dismiss: key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "dismiss")),
	Retry:   key.NewBinding(key.WithKeys("r"), key.WithHelp("r", "retry")),
	Quit:    key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
}

var disconnectedKeys = disconnectedKeyMap{
	Retry: key.NewBinding(key.WithKeys("r"), key.WithHelp("r", "reconnect")),
	Back:  key.NewBinding(key.WithKeys("esc"), key.WithHelp("esc", "back")),
	Quit:  key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
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
	imageWorkers         chan struct{}
	wsDisconnected       bool
	reconnecting         bool
	dismissOnReconnect   bool
	requestSequence      int64
	liveImageSequence    int64
	activeRequestID      string
	imageConfig          ImageConfig
	mdRenderer           *glamour.TermRenderer
	mdRendererWidth      int
}

func initialModel(client *api.Client, wsConn *api.ChatWSConn, roleName string, wsMsgCh chan tea.Msg) *Model {
	ta := textarea.New()
	ta.Placeholder = "Type a message..."
	ta.ShowLineNumbers = false
	ta.SetHeight(3)
	ta.CharLimit = 2000
	ta.KeyMap.InsertNewline = key.NewBinding(key.WithKeys("ctrl+j"), key.WithHelp("ctrl+j", "insert newline"))
	configureEnabledTextarea(&ta)
	ta.Cursor.Style = lipgloss.NewStyle().Foreground(style.Accent)
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
		imageWorkers:   make(chan struct{}, 3),
		imageConfig:    DefaultImageConfig(),
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
		wasAtBottom := m.viewport.AtBottom()
		savedYOffset := m.viewport.YOffset
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.recalcSizes()
		if !wasAtBottom {
			m.viewport.YOffset = min(savedYOffset, m.maxViewportYOffset())
		}
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
		return m, m.queueHistoryImageRenders()

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
				m.sending = false
				m.activeRequestID = ""
				m.enableInput()
				m.recalcSizes()
				return m, textarea.Blink
			}
			return m, nil
		}

		// error state: limited keys
		if m.err != nil {
			if m.wsDisconnected {
				switch msg.String() {
				case "esc":
					m.err = nil
					m.dismissOnReconnect = m.reconnecting
					if m.reconnecting {
						m.disableInput("Reconnecting...", style.Info)
					} else {
						m.disableInput("Chat disconnected - press r to reconnect", style.Warning)
					}
					m.recalcSizes()
					return m, nil
				case "r", "R":
					m.err = nil
					m.dismissOnReconnect = true
					m.disableInput("Reconnecting...", style.Info)
					m.recalcSizes()
					if !m.reconnecting {
						m.reconnecting = true
						return m, m.reconnectChatCmd()
					}
					return m, nil
				}
				return m, nil
			}
			switch msg.String() {
			case "esc", "r", "R":
				m.err = nil
				m.enableInput()
				m.recalcSizes()
				return m, textarea.Blink
			}
			return m, nil
		}

		if m.wsDisconnected {
			switch msg.String() {
			case "r", "R":
				if !m.reconnecting {
					m.reconnecting = true
					m.dismissOnReconnect = true
					m.disableInput("Reconnecting...", style.Info)
					m.recalcSizes()
					return m, m.reconnectChatCmd()
				}
				return m, nil
			case "esc":
				m.BackToRole = true
				return m, tea.Quit
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
			m.requestSequence++
			requestID := fmt.Sprintf("cli-%d", m.requestSequence)
			m.lastSendTime = now
			m.activeRequestID = requestID
			m.messages = append(m.messages, chatMsg{typ: msgUser, content: text, timestamp: now})
			m.textarea.Reset()
			m.sending = true
			m.err = nil
			m.disableInput("Waiting for response...", style.Info)
			m.recalcSizes()
			if err := m.wsConn.SendMessage(requestID, text); err != nil {
				m.sending = false
				m.activeRequestID = ""
				m.err = err
				m.disableInput("Dismiss error to continue", style.Error)
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
		matchesActiveRequest := msg.requestID == "" || msg.requestID == m.activeRequestID
		if matchesActiveRequest {
			m, cmds = m.finishSending()
			m.activeRequestID = ""
		}
		now := time.Now()
		var dur time.Duration
		if matchesActiveRequest {
			dur = now.Sub(m.lastSendTime)
		}
		content, imageSource, hasImage := extractCQImage(msg.response)
		content, mdContent, _ := extractCQMarkdown(content)
		reply := chatMsg{typ: msgBot, content: content, markdownContent: mdContent, timestamp: now, duration: dur}
		if hasImage {
			m.liveImageSequence++
			reply.id = -m.liveImageSequence
			reply.hasImage = true
			reply.imageState = historyImageLoading
		}
		m.messages = append(m.messages, reply)
		m.recalcSizes()
		cmds = append(cmds, textarea.Blink, m.wsListen())
		if hasImage {
			cmds = append(cmds, m.loadLiveImageCmd(reply.id, imageSource, m.historyImageAvailableCols()))
		}
		return m, tea.Batch(cmds...)

	case chatErrorMsg:
		if !msg.disconnected && msg.requestID != "" && msg.requestID != m.activeRequestID {
			return m, m.wsListen()
		}
		var cmds []tea.Cmd
		m, cmds = m.finishSending()
		m.activeRequestID = ""
		m.err = msg.err
		m.wsDisconnected = msg.disconnected
		if msg.disconnected {
			m.disableInput("Press r to retry or esc to dismiss", style.Error)
		} else {
			m.disableInput("Dismiss error to continue", style.Error)
		}
		m.recalcSizes()
		if msg.disconnected && !m.reconnecting {
			m.reconnecting = true
			cmds = append(cmds, m.reconnectChatCmd())
		} else if !msg.disconnected {
			cmds = append(cmds, m.wsListen())
		}
		return m, tea.Batch(cmds...)

	case chatReconnectMsg:
		m.reconnecting = false
		if msg.err != nil {
			m.wsDisconnected = true
			m.err = fmt.Errorf("chat reconnect failed: %w", msg.err)
			m.disableInput("Press r to retry or esc to dismiss", style.Error)
			m.recalcSizes()
			return m, nil
		}

		m.wsConn = msg.conn
		m.wsDisconnected = false
		startChatWSReader(m.wsConn, m.wsMsgCh)
		if m.dismissOnReconnect || m.err == nil {
			m.dismissOnReconnect = false
			m.err = nil
			m.enableInput()
			m.recalcSizes()
			return m, tea.Batch(textarea.Blink, m.wsListen())
		}
		return m, m.wsListen()

	case historyLoadedMsg:
		return m.handleHistoryLoaded(msg)

	case historyErrorMsg:
		m.loadingHistory = false
		m.recalcSizes()
		return m, nil

	case historyImageRenderedMsg:
		return m.handleHistoryImageRendered(msg)
	}

	var cmd tea.Cmd
	m.textarea, cmd = m.textarea.Update(msg)
	return m, cmd
}

func (m *Model) reconnectChatCmd() tea.Cmd {
	client := m.client
	return func() tea.Msg {
		conn, err := api.NewChatWSConn(client.BaseURL(), client.Username(), client.Password())
		return chatReconnectMsg{conn: conn, err: err}
	}
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
		imageState := historyImageNone
		if e.HasImage {
			imageState = historyImageLoading
		}
		content := cleanHistoryImagePlaceholder(e.Content, e.HasImage)
		mdContent := ""
		if typ == msgBot && content != "" {
			mdContent = content
		}
		histMsgs = append(histMsgs, chatMsg{
			id:              e.ID,
			typ:             typ,
			content:         content,
			markdownContent: mdContent,
			timestamp:       time.UnixMilli(e.Timestamp),
			hasImage:        e.HasImage,
			imageState:      imageState,
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

	return m, m.queueHistoryImageLoads(histMsgs)
}

func (m *Model) queueHistoryImageLoads(messages []chatMsg) tea.Cmd {
	availableCols := m.historyImageAvailableCols()
	var cmds []tea.Cmd
	for _, msg := range messages {
		if !msg.hasImage {
			continue
		}
		cmds = append(cmds, m.loadHistoryImageCmd(msg.id, availableCols))
	}
	return tea.Batch(cmds...)
}

func (m *Model) loadHistoryImageCmd(historyID int64, availableCols int) tea.Cmd {
	client := m.client
	workers := m.imageWorkers
	cfg := m.imageConfig
	return func() tea.Msg {
		workers <- struct{}{}
		defer func() { <-workers }()

		data, err := client.GetChatHistoryImage(historyID)
		if err != nil {
			return historyImageRenderedMsg{id: historyID, availableCols: availableCols, err: err}
		}
		rendered, err := renderHistoryImageWithConfig(data, availableCols, cfg)
		return historyImageRenderedMsg{
			id:            historyID,
			data:          data,
			rendered:      rendered,
			availableCols: availableCols,
			err:           err,
		}
	}
}

func (m *Model) loadLiveImageCmd(messageID int64, imageSource string, availableCols int) tea.Cmd {
	workers := m.imageWorkers
	cfg := m.imageConfig
	return func() tea.Msg {
		workers <- struct{}{}
		defer func() { <-workers }()

		data, err := loadLiveImageBytes(imageSource)
		if err != nil {
			return historyImageRenderedMsg{id: messageID, availableCols: availableCols, err: err}
		}
		rendered, err := renderHistoryImageWithConfig(data, availableCols, cfg)
		return historyImageRenderedMsg{
			id:            messageID,
			data:          data,
			rendered:      rendered,
			availableCols: availableCols,
			err:           err,
		}
	}
}

func loadLiveImageBytes(imageSource string) ([]byte, error) {
	switch {
	case strings.HasPrefix(imageSource, "base64://"):
		return base64.StdEncoding.DecodeString(strings.TrimPrefix(imageSource, "base64://"))
	case strings.HasPrefix(imageSource, "data:image/"):
		_, encoded, ok := strings.Cut(imageSource, "base64,")
		if !ok {
			return nil, fmt.Errorf("decode image data url: missing base64 payload")
		}
		return base64.StdEncoding.DecodeString(encoded)
	default:
		client := http.Client{Timeout: 15 * time.Second}
		resp, err := client.Get(imageSource)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			return nil, fmt.Errorf("download image: http %d", resp.StatusCode)
		}
		return io.ReadAll(io.LimitReader(resp.Body, 10<<20))
	}
}

func (m *Model) renderCachedHistoryImageCmd(historyID int64, data []byte, availableCols int) tea.Cmd {
	workers := m.imageWorkers
	cfg := m.imageConfig
	return func() tea.Msg {
		workers <- struct{}{}
		defer func() { <-workers }()

		rendered, err := renderHistoryImageWithConfig(data, availableCols, cfg)
		return historyImageRenderedMsg{
			id:            historyID,
			rendered:      rendered,
			availableCols: availableCols,
			refresh:       true,
			err:           err,
		}
	}
}

func (m *Model) queueHistoryImageRenders() tea.Cmd {
	availableCols := m.historyImageAvailableCols()
	var cmds []tea.Cmd
	for i := range m.messages {
		msg := &m.messages[i]
		if msg.imageState != historyImageReady || len(msg.imageBytes) == 0 ||
			msg.imageAvailableCols == availableCols || msg.imageRendering {
			continue
		}
		msg.imageRendering = true
		cmds = append(cmds, m.renderCachedHistoryImageCmd(msg.id, msg.imageBytes, availableCols))
	}
	return tea.Batch(cmds...)
}

func (m *Model) handleHistoryImageRendered(result historyImageRenderedMsg) (tea.Model, tea.Cmd) {
	index := -1
	for i := range m.messages {
		if m.messages[i].id == result.id {
			index = i
			break
		}
	}
	if index < 0 {
		return m, nil
	}

	oldContent := m.renderMessages()
	oldHeight := lipgloss.Height(oldContent)
	oldStartLine := m.messageStartLine(index)
	savedYOffset := m.viewport.YOffset
	wasAtBottom := m.viewport.AtBottom()

	msg := &m.messages[index]
	msg.imageRendering = false
	if result.err != nil {
		if !result.refresh {
			msg.imageState = historyImageError
		}
	} else {
		msg.imageState = historyImageReady
		if len(result.data) > 0 {
			msg.imageBytes = result.data
		}
		msg.imageContent = result.rendered.content
		msg.imageAvailableCols = result.availableCols
	}

	newContent := m.renderMessages()
	m.viewport.SetContent(newContent)
	if wasAtBottom {
		m.viewport.GotoBottom()
	} else {
		newYOffset := savedYOffset
		if oldStartLine < savedYOffset {
			newYOffset += lipgloss.Height(newContent) - oldHeight
		}
		m.viewport.YOffset = min(max(0, newYOffset), m.maxViewportYOffset())
	}

	if result.err == nil && result.availableCols != m.historyImageAvailableCols() && len(msg.imageBytes) > 0 {
		msg.imageRendering = true
		return m, m.renderCachedHistoryImageCmd(msg.id, msg.imageBytes, m.historyImageAvailableCols())
	}
	return m, nil
}

func (m *Model) historyImageAvailableCols() int {
	return min(max(1, m.imageConfig.MaxCols), max(1, m.viewport.Width-6))
}

func (m *Model) maxViewportYOffset() int {
	return max(0, lipgloss.Height(m.renderMessages())-m.viewport.Height)
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
	m.sending = false
	cmds = append(cmds, m.enableInput())
	return m, cmds
}

func configureEnabledTextarea(ta *textarea.Model) {
	ta.Placeholder = "Type a message..."
	ta.FocusedStyle.CursorLine = lipgloss.NewStyle().Foreground(style.Primary)
	ta.FocusedStyle.Prompt = lipgloss.NewStyle().Foreground(style.Accent)
	ta.FocusedStyle.Placeholder = lipgloss.NewStyle().Foreground(style.TextMuted)
	ta.FocusedStyle.Text = lipgloss.NewStyle().Foreground(style.Text)
	ta.BlurredStyle.CursorLine = lipgloss.NewStyle().Foreground(style.TextMuted)
	ta.BlurredStyle.Prompt = lipgloss.NewStyle().Foreground(style.BorderStrong)
	ta.BlurredStyle.Placeholder = lipgloss.NewStyle().Foreground(style.TextMuted)
	ta.BlurredStyle.Text = lipgloss.NewStyle().Foreground(style.TextMuted)
}

func (m *Model) enableInput() tea.Cmd {
	configureEnabledTextarea(&m.textarea)
	if m.focused {
		return m.textarea.Focus()
	}
	m.textarea.Blur()
	return nil
}

func (m *Model) disableInput(placeholder string, tone lipgloss.TerminalColor) {
	m.textarea.Placeholder = placeholder
	m.textarea.BlurredStyle.CursorLine = lipgloss.NewStyle().Foreground(style.TextMuted)
	m.textarea.BlurredStyle.Prompt = lipgloss.NewStyle().Foreground(tone)
	m.textarea.BlurredStyle.Placeholder = lipgloss.NewStyle().Foreground(style.TextMuted).Italic(true)
	m.textarea.BlurredStyle.Text = lipgloss.NewStyle().Foreground(style.TextMuted)
	m.textarea.Blur()
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

	msgWidth := m.viewport.Width - 6
	if msgWidth < 20 {
		msgWidth = 20
	}
	if m.mdRenderer == nil || m.mdRendererWidth != msgWidth {
		m.mdRenderer, _ = createChatRenderer(msgWidth)
		m.mdRendererWidth = msgWidth
	}

	m.viewport.SetContent(m.renderMessages())
	m.viewport.GotoBottom()
}

func (m *Model) helpView() string {
	switch {
	case m.err != nil && m.wsDisconnected:
		return m.help.View(connectionErrorKeys)
	case m.err != nil:
		return m.help.View(errorKeys)
	case m.wsDisconnected:
		return m.help.View(disconnectedKeys)
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
		statusLine = renderChatError(m.err)
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

func renderChatError(err error) string {
	return lipgloss.NewStyle().
		Foreground(style.Error).
		BorderStyle(lipgloss.ThickBorder()).
		BorderLeft(true).
		BorderForeground(style.Error).
		PaddingLeft(1).
		Render("Error: " + components.FriendlyErrorMessage(err))
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
		lines = append(lines, m.renderMessageLines(msg, maxWidth, msgWidth)...)
	}

	if m.sending {
		lines = append(lines, "")
		lines = append(lines, thinkingBubble(m.spinner.View()))
	}

	return strings.Join(lines, "\n")
}

func (m *Model) renderMessageLines(msg chatMsg, maxWidth, msgWidth int) []string {
	lines := []string{""}
	if msg.typ == msgUser {
		lines = append(lines, userLabel(maxWidth, msg.timestamp))
	} else {
		lines = append(lines, botLabel(m.roleName, msg.timestamp, msg.duration))
	}

	if msg.markdownContent != "" && m.mdRenderer != nil {
		rendered, err := m.mdRenderer.Render(msg.markdownContent)
		if err == nil {
			trimmed := strings.TrimSpace(rendered)
			for _, line := range strings.Split(trimmed, "\n") {
				line = strings.TrimRight(line, " ")
				messageStyle := lipgloss.NewStyle().PaddingLeft(2)
				lines = append(lines, messageStyle.Render(line))
			}
		}
	} else if msg.content != "" {
		wrapped := wrapText(msg.content, msgWidth)
		for _, line := range strings.Split(wrapped, "\n") {
			messageStyle := lipgloss.NewStyle().Foreground(style.Text)
			if msg.typ == msgUser {
				messageStyle = messageStyle.Align(lipgloss.Right).Width(maxWidth)
			} else {
				messageStyle = messageStyle.PaddingLeft(2)
			}
			lines = append(lines, messageStyle.Render(line))
		}
	}

	if msg.hasImage {
		lines = append(lines, renderHistoryImageBlock(msg, maxWidth))
	}
	return lines
}

func renderHistoryImageBlock(msg chatMsg, maxWidth int) string {
	var block string
	switch msg.imageState {
	case historyImageReady:
		block = msg.imageContent
	case historyImageError:
		block = lipgloss.NewStyle().
			Foreground(style.Warning).
			Render("Image unavailable")
	default:
		block = lipgloss.NewStyle().
			Foreground(style.TextMuted).
			Italic(true).
			Render("Loading image...")
	}

	if msg.typ == msgUser {
		return lipgloss.NewStyle().
			Align(lipgloss.Right).
			Width(maxWidth).
			Render(block)
	}
	return lipgloss.NewStyle().PaddingLeft(2).Render(block)
}

func (m *Model) messageStartLine(index int) int {
	maxWidth := m.viewport.Width - 2
	msgWidth := maxWidth - 4
	if msgWidth < 20 {
		msgWidth = 20
	}

	var lines []string
	if m.loadingHistory {
		lines = append(lines, "Loading history...", "")
	}
	for i := 0; i < index && i < len(m.messages); i++ {
		lines = append(lines, m.renderMessageLines(m.messages[i], maxWidth, msgWidth)...)
	}
	if len(lines) == 0 {
		return 0
	}
	return lipgloss.Height(strings.Join(lines, "\n"))
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

func cleanHistoryImagePlaceholder(text string, hasImage bool) string {
	if !hasImage {
		return text
	}
	parts := strings.Split(text, "[图片]")
	cleaned := make([]string, 0, len(parts))
	for _, part := range parts {
		if part = strings.TrimSpace(part); part != "" {
			cleaned = append(cleaned, part)
		}
	}
	return strings.Join(cleaned, " ")
}

func extractCQImage(text string) (string, string, bool) {
	const prefix = "[CQ:image"
	start := strings.Index(text, prefix)
	if start < 0 {
		return text, "", false
	}
	endRel := strings.Index(text[start:], "]")
	if endRel < 0 {
		return text, "", false
	}
	end := start + endRel
	rawAttrs := strings.TrimPrefix(text[start+len(prefix):end], ",")
	attrs := parseCQAttributes(rawAttrs)
	imageSource := attrs["url"]
	if imageSource == "" {
		imageSource = attrs["file"]
	}
	if !isSupportedLiveImageSource(imageSource) {
		return text, "", false
	}

	cleaned := strings.Join(strings.Fields(strings.TrimSpace(text[:start]+" "+text[end+1:])), " ")
	return cleaned, imageSource, true
}

func parseCQAttributes(raw string) map[string]string {
	attrs := make(map[string]string)
	for _, part := range splitCQAttributeParts(raw) {
		key, value, ok := strings.Cut(part, "=")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		if key == "" {
			continue
		}
		attrs[key] = unescapeCQValue(strings.TrimSpace(value))
	}
	return attrs
}

func splitCQAttributeParts(raw string) []string {
	var parts []string
	for _, part := range strings.Split(raw, ",") {
		if len(parts) == 0 || looksLikeCQAttributeStart(part) {
			parts = append(parts, part)
			continue
		}
		parts[len(parts)-1] += "," + part
	}
	return parts
}

func looksLikeCQAttributeStart(part string) bool {
	key, _, ok := strings.Cut(part, "=")
	if !ok {
		return false
	}
	key = strings.TrimSpace(key)
	if key == "" || len(key) > 32 {
		return false
	}
	for _, r := range key {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '_' || r == '-' {
			continue
		}
		return false
	}
	return true
}

func unescapeCQValue(value string) string {
	return strings.NewReplacer(
		"&#44;", ",",
		"&#91;", "[",
		"&#93;", "]",
		"&amp;", "&",
	).Replace(value)
}

func isSupportedLiveImageSource(value string) bool {
	return isHTTPImageURL(value) || strings.HasPrefix(value, "base64://") || strings.HasPrefix(value, "data:image/")
}

func isHTTPImageURL(value string) bool {
	parsed, err := url.Parse(value)
	if err != nil {
		return false
	}
	return (parsed.Scheme == "http" || parsed.Scheme == "https") && parsed.Host != ""
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

func extractCQMarkdown(text string) (string, string, bool) {
	const prefix = "[CQ:markdown"
	start := strings.Index(text, prefix)
	if start < 0 {
		return text, "", false
	}
	endRel := strings.Index(text[start:], "]")
	if endRel < 0 {
		return text, "", false
	}
	end := start + endRel
	rawAttrs := strings.TrimPrefix(text[start+len(prefix):end], ",")
	attrs := parseCQAttributes(rawAttrs)
	mdContent := attrs["content"]
	if mdContent == "" {
		return text, "", false
	}
	cleaned := strings.Join(strings.Fields(strings.TrimSpace(text[:start]+" "+text[end+1:])), " ")
	return cleaned, mdContent, true
}

func createChatRenderer(width int) (*glamour.TermRenderer, error) {
	ww := width
	if ww < 20 {
		ww = 20
	}
	return glamour.NewTermRenderer(
		glamour.WithStyles(md.VercelMarkdownStyle()),
		glamour.WithWordWrap(ww),
	)
}
