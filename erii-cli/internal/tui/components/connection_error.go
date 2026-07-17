package components

import (
	"strings"

	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/key"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

var (
	errCardStyle = lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(style.BorderStrong).
			Padding(1, 3).
			Width(style.ErrorCardWidth)

	errTitleStyle = lipgloss.NewStyle().
			Foreground(style.Error).
			Bold(true)
)

type connectionErrorKeys struct {
	Retry key.Binding
	Quit  key.Binding
}

func (k connectionErrorKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Retry, k.Quit}
}

func (k connectionErrorKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Retry, k.Quit}}
}

var defaultConnectionErrorKeys = connectionErrorKeys{
	Retry: key.NewBinding(
		key.WithKeys("r"),
		key.WithHelp("r", "retry"),
	),
	Quit: key.NewBinding(
		key.WithKeys("q", "ctrl+c"),
		key.WithHelp("q/ctrl+c", "quit"),
	),
}

type ConnectionErrorModel struct {
	errMsg      string
	width       int
	height      int
	keys        connectionErrorKeys
	shouldRetry bool
}

func NewConnectionErrorModel(err error) *ConnectionErrorModel {
	return &ConnectionErrorModel{
		errMsg: FriendlyErrorMessage(err),
		keys:   defaultConnectionErrorKeys,
	}
}

func (m *ConnectionErrorModel) ShouldRetry() bool {
	return m.shouldRetry
}

func (m *ConnectionErrorModel) Init() tea.Cmd {
	return nil
}

func (m *ConnectionErrorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}
		if key.Matches(msg, m.keys.Retry) {
			m.shouldRetry = true
			return m, tea.Quit
		}
	}

	return m, nil
}

func (m *ConnectionErrorModel) View() string {
	if m.width == 0 || m.height == 0 {
		return ""
	}
	return RenderErrorCard(m.width, m.height, m.errMsg, "press r to retry    press q to quit")
}

func RenderErrorCard(width, height int, errMsg, hint string) string {
	var content []string
	content = append(content, errTitleStyle.Render("Cannot Connect to Erii Service"))
	content = append(content, "")
	content = append(content, style.Muted("Reason: ")+style.ErrorText(errMsg))
	content = append(content, "")
	content = append(content, style.Muted("Suggestions:"))
	content = append(content, style.Muted("  1. Make sure the Erii service is running"))
	content = append(content, style.Muted("  2. Check if the service port is occupied"))
	content = append(content, style.Muted("  3. Verify the IPC config path is correct"))
	content = append(content, "")
	content = append(content, style.Muted(hint))

	card := errCardStyle.Render(strings.Join(content, "\n"))
	return lipgloss.Place(width, height, lipgloss.Center, lipgloss.Center, card)
}

func FriendlyErrorMessage(err error) string {
	if err == nil {
		return "Unknown error"
	}
	msg := err.Error()
	msgLower := strings.ToLower(msg)

	switch {
	case strings.Contains(msgLower, "config length is 0"):
		return "Erii service has not finished initializing"
	case strings.Contains(msgLower, "failed to open ipc file"):
		return "Erii service is not running (IPC file not found)"
	case strings.Contains(msgLower, "connection refused"):
		return "Erii API service is not started or port not listening"
	case strings.Contains(msgLower, "timeout") || strings.Contains(msgLower, "timed out"):
		return "Connection timed out, please check network or service status"
	case strings.Contains(msgLower, "no such host") || strings.Contains(msgLower, "dns"):
		return "Unable to resolve service address"
	case strings.Contains(msgLower, "http 401") || strings.Contains(msgLower, "unauthorized"):
		return "Authentication failed, please check username and password"
	case strings.Contains(msgLower, "http 403"):
		return "Access denied, insufficient permissions"
	case strings.Contains(msgLower, "http 404"):
		return "Requested endpoint does not exist"
	case strings.Contains(msgLower, "http 500") || strings.Contains(msgLower, "http 502") || strings.Contains(msgLower, "http 503"):
		return "Erii service internal error"
	default:
		return msg
	}
}
