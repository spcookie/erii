package logview

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"regexp"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/mattn/go-runewidth"
)

var (
	styleDate   = lipgloss.NewStyle().Foreground(lipgloss.Color("#8be9fd"))
	styleThread = lipgloss.NewStyle().Foreground(lipgloss.Color("#6272a4"))
	styleLogger = lipgloss.NewStyle().Foreground(lipgloss.Color("#ff79c6")).Bold(true)
	styleMsg    = lipgloss.NewStyle().Foreground(lipgloss.Color("#f8f8f2"))
	styleMuted  = lipgloss.NewStyle().Foreground(lipgloss.Color("#6272a4"))

	levelStyles = map[string]lipgloss.Style{
		"ERROR": lipgloss.NewStyle().Foreground(lipgloss.Color("#ff5555")).Bold(true),
		"WARN":  lipgloss.NewStyle().Foreground(lipgloss.Color("#f1fa8c")).Bold(true),
		"INFO":  lipgloss.NewStyle().Foreground(lipgloss.Color("#50fa7b")),
		"DEBUG": lipgloss.NewStyle().Foreground(lipgloss.Color("#8be9fd")),
		"TRACE": lipgloss.NewStyle().Foreground(lipgloss.Color("#6272a4")),
	}

	titleStyle = lipgloss.NewStyle().
			Bold(true).
			Foreground(lipgloss.Color("#ff79c6")).
			MarginBottom(1)

	// Logback pattern: %date{yyyy-MM-dd HH:mm:ss} %highlight(%-5level) %gray([%thread]) %boldMagenta(%logger{50}) %msg%n
	logLineRe = regexp.MustCompile(
		`^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+(\S+)\s+\[([^]]+)]\s+(\S+)\s+(.*)$`,
	)
)

type tickMsg struct{}

type logKeyMap struct {
	Up       key.Binding
	Down     key.Binding
	HalfUp   key.Binding
	HalfDown key.Binding
	Top      key.Binding
	Bottom   key.Binding
	Follow   key.Binding
	Quit     key.Binding
}

func (k logKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Follow, k.Bottom, k.Quit}
}

func (k logKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{{k.Up, k.Down, k.HalfDown, k.HalfUp}, {k.Top, k.Bottom, k.Follow, k.Quit}}
}

var keys = logKeyMap{
	Up:       key.NewBinding(key.WithKeys("up", "k"), key.WithHelp("↑/k", "up")),
	Down:     key.NewBinding(key.WithKeys("down", "j"), key.WithHelp("↓/j", "down")),
	HalfUp:   key.NewBinding(key.WithKeys("ctrl+u"), key.WithHelp("ctrl+u", "½ up")),
	HalfDown: key.NewBinding(key.WithKeys("ctrl+d"), key.WithHelp("ctrl+d", "½ down")),
	Top:      key.NewBinding(key.WithKeys("g", "home"), key.WithHelp("g/home", "top")),
	Bottom:   key.NewBinding(key.WithKeys("G", "end"), key.WithHelp("G/end", "bottom")),
	Follow:   key.NewBinding(key.WithKeys("f"), key.WithHelp("f", "follow")),
	Quit:     key.NewBinding(key.WithKeys("esc", "ctrl+c"), key.WithHelp("esc/ctrl+c", "quit")),
}

type model struct {
	viewport viewport.Model
	help     help.Model
	rawLines []string
	filePath string
	follow   bool
	readPos  int64
	ready    bool
	err      error
}

func (m model) Init() tea.Cmd {
	if m.follow {
		return doTick()
	}
	return nil
}

func doTick() tea.Cmd {
	return tea.Tick(200*time.Millisecond, func(t time.Time) tea.Msg {
		return tickMsg{}
	})
}

func (m *model) setContent(width int) {
	if width <= 0 {
		return
	}
	var wrapped []string
	for _, line := range m.rawLines {
		highlighted := highlightLine(line)
		wrapped = append(wrapped, wrapLine(highlighted, width)...)
	}
	m.viewport.SetContent(strings.Join(wrapped, "\n"))
}

func (m model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.help.Width = msg.Width - 2
		headerHeight := 3
		footerHeight := lipgloss.Height(m.help.View(keys)) + 1
		m.viewport.Width = msg.Width - 2
		m.viewport.Height = msg.Height - headerHeight - footerHeight
		m.setContent(m.viewport.Width)
		if !m.ready {
			m.ready = true
			m.viewport.GotoBottom()
		}
		return m, nil

	case tickMsg:
		if !m.follow {
			return m, nil
		}
		newLines, err := m.readNewLines()
		if err != nil || len(newLines) == 0 {
			return m, doTick()
		}
		atBottom := m.viewport.ScrollPercent() >= 0.99
		m.rawLines = append(m.rawLines, newLines...)
		if len(m.rawLines) > 10000 {
			m.rawLines = m.rawLines[len(m.rawLines)-10000:]
		}
		m.setContent(m.viewport.Width)
		if atBottom {
			m.viewport.GotoBottom()
		}
		return m, doTick()

	case tea.KeyMsg:
		switch msg.String() {
		case "esc", "ctrl+c":
			return m, tea.Quit
		case "f":
			m.follow = !m.follow
			if m.follow {
				m.viewport.GotoBottom()
				return m, doTick()
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.viewport, cmd = m.viewport.Update(msg)
	return m, cmd
}

func (m model) View() string {
	if m.err != nil {
		return lipgloss.NewStyle().
			Foreground(lipgloss.Color("#ff5555")).
			Render(fmt.Sprintf("Error: %v\n\nPress esc to quit.", m.err))
	}

	followStatus := "ON"
	if !m.follow {
		followStatus = "OFF"
	}
	title := titleStyle.Render(fmt.Sprintf("Log: %s  [follow: %s]", m.filePath, followStatus))

	return lipgloss.JoinVertical(lipgloss.Left,
		title,
		m.viewport.View(),
		m.help.View(keys),
	)
}

func highlightLine(line string) string {
	matches := logLineRe.FindStringSubmatch(line)
	if matches == nil {
		return styleMuted.Render(line)
	}

	date := matches[1]
	levelRaw := strings.TrimSpace(matches[2])
	thread := matches[3]
	logger := matches[4]
	msg := matches[5]

	levelStyle, ok := levelStyles[levelRaw]
	if !ok {
		levelStyle = styleMsg
	}

	return styleDate.Render(date) + " " +
		levelStyle.Render(fmt.Sprintf("%-5s", levelRaw)) + " " +
		styleThread.Render("["+thread+"]") + " " +
		styleLogger.Render(logger) + " " +
		styleMsg.Render(msg)
}

func wrapLine(line string, width int) []string {
	if width <= 0 || lipgloss.Width(line) <= width {
		return []string{line}
	}

	var wrapped []string
	remaining := line
	for lipgloss.Width(remaining) > width {
		cut := 0
		w := 0
		inEscape := false
		for i, r := range remaining {
			if r == '\x1b' {
				inEscape = true
			}
			if inEscape {
				if r == 'm' {
					inEscape = false
				}
				continue
			}
			rw := runewidth.RuneWidth(r)
			if w+rw > width {
				break
			}
			w += rw
			cut = i + len(string(r))
		}
		if cut == 0 {
			cut = 1
		}
		wrapped = append(wrapped, remaining[:cut])
		remaining = remaining[cut:]
	}
	if remaining != "" {
		wrapped = append(wrapped, remaining)
	}
	return wrapped
}

func (m *model) readNewLines() ([]string, error) {
	info, err := os.Stat(m.filePath)
	if err != nil {
		return nil, err
	}

	if info.Size() < m.readPos {
		m.readPos = 0
	}
	if info.Size() <= m.readPos {
		return nil, nil
	}

	f, err := os.Open(m.filePath)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	_, err = f.Seek(m.readPos, io.SeekStart)
	if err != nil {
		m.readPos = 0
		return nil, err
	}

	remaining := info.Size() - m.readPos
	buf := make([]byte, remaining)
	n, err := io.ReadFull(f, buf)
	if err != nil && err != io.EOF && err != io.ErrUnexpectedEOF {
		return nil, err
	}

	m.readPos += int64(n)

	text := string(buf[:n])
	if text == "" {
		return nil, nil
	}

	lines := strings.Split(text, "\n")
	if len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	return lines, nil
}

func tailLines(filePath string, numLines int) ([]string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var lines []string
	scanner := bufio.NewScanner(file)

	if numLines > 0 {
		buf := make([]string, numLines)
		idx := 0
		count := 0
		for scanner.Scan() {
			buf[idx] = scanner.Text()
			idx = (idx + 1) % numLines
			count++
		}
		if err := scanner.Err(); err != nil {
			return nil, err
		}
		if count <= numLines {
			lines = make([]string, count)
			copy(lines, buf[:count])
		} else {
			lines = make([]string, numLines)
			for i := 0; i < numLines; i++ {
				lines[i] = buf[(idx+i)%numLines]
			}
		}
	} else {
		for scanner.Scan() {
			lines = append(lines, scanner.Text())
		}
		if err := scanner.Err(); err != nil {
			return nil, err
		}
	}

	return lines, nil
}

func Start(logPath string, numLines int, follow bool) error {
	lines, err := tailLines(logPath, numLines)
	if err != nil {
		if follow {
			lines = nil
		} else {
			lines = []string{fmt.Sprintf("Failed to read log file: %v", err)}
		}
	}

	vp := viewport.New(0, 0)

	m := &model{
		viewport: vp,
		help:     help.New(),
		rawLines: lines,
		filePath: logPath,
		follow:   follow,
		readPos:  0,
		err:      nil,
	}
	if err != nil && !follow {
		m.err = err
	}

	if follow {
		if info, statErr := os.Stat(logPath); statErr == nil {
			m.readPos = info.Size()
		}
	}

	p := tea.NewProgram(m, tea.WithAltScreen())
	if _, runErr := p.Run(); runErr != nil {
		return fmt.Errorf("failed to run log viewer: %w", runErr)
	}
	return nil
}
