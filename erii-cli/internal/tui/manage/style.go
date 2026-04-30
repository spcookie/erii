package manage

import (
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/lipgloss"
	"github.com/mattn/go-runewidth"
)

var (
	TableHeaderStyle = lipgloss.NewStyle().
		Background(style.SurfaceAlt).
		Foreground(style.Primary).
		Bold(true).
		Padding(0, 1)

	TableRowStyle = lipgloss.NewStyle().
		Foreground(style.Text).
		Padding(0, 1)

	TableSelectedRowStyle = lipgloss.NewStyle().
		Background(style.Surface).
		Foreground(style.Primary).
		Bold(true).
		Padding(0, 1)

	TableAltRowStyle = lipgloss.NewStyle().
		Foreground(style.Text).
		Background(style.SurfaceAlt).
		Padding(0, 1)

	StatusBarStyle = lipgloss.NewStyle().
		Background(style.SurfaceAlt).
		Foreground(style.TextMuted).
		Padding(0, 1)

	SearchActiveStyle = lipgloss.NewStyle().
		Foreground(style.Accent).
		Bold(true)

	TitleBarStyle = lipgloss.NewStyle().
		Background(style.SurfaceAlt).
		Foreground(style.Primary).
		Bold(true).
		Padding(0, 1)
)

func CheckboxChecked() string {
	return lipgloss.NewStyle().Foreground(style.Success).Render("[x]")
}

func CheckboxUnchecked() string {
	return lipgloss.NewStyle().Foreground(style.TextMuted).Render("[ ]")
}

func TruncateMiddle(s string, maxWidth int) string {
	if runewidth.StringWidth(s) <= maxWidth {
		return s
	}
	if maxWidth <= 3 {
		return string([]rune(s)[:maxWidth])
	}
	half := (maxWidth - 3) / 2
	runes := []rune(s)
	leftEnd := 0
	w := 0
	for i, r := range runes {
		rw := runewidth.RuneWidth(r)
		if w+rw > half {
			leftEnd = i
			break
		}
		w += rw
	}
	rightStart := len(runes)
	w = 0
	for i := len(runes) - 1; i >= 0; i-- {
		rw := runewidth.RuneWidth(runes[i])
		if w+rw > half {
			rightStart = i + 1
			break
		}
		w += rw
	}
	return string(runes[:leftEnd]) + "..." + string(runes[rightStart:])
}

func TruncateEnd(s string, maxWidth int) string {
	if runewidth.StringWidth(s) <= maxWidth {
		return s
	}
	if maxWidth <= 3 {
		return string([]rune(s)[:maxWidth])
	}
	runes := []rune(s)
	w := 0
	for i, r := range runes {
		rw := runewidth.RuneWidth(r)
		if w+rw > maxWidth-3 {
			return string(runes[:i]) + "..."
		}
		w += rw
	}
	return s
}
