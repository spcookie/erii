package manage

import (
	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/lipgloss"
	"github.com/mattn/go-runewidth"
)

func DataTableStyles() table.Styles {
	styles := table.DefaultStyles()
	styles.Header = TableHeaderStyle.Copy().
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderStrong).
		BorderBottom(true)
	styles.Cell = TableRowStyle.Copy()
	styles.Selected = TableSelectedRowStyle.Copy()
	return styles
}

var (
	TableHeaderStyle = lipgloss.NewStyle().
				Foreground(style.Accent).
				Bold(true).
				Padding(0, 1)

	TableRowStyle = lipgloss.NewStyle().
			Padding(0, 1)

	TableSelectedRowStyle = lipgloss.NewStyle().
				Background(style.Selection).
				Foreground(style.Accent).
				Bold(true)

	TableAltRowStyle = lipgloss.NewStyle().
				Padding(0, 1)

	StatusBarStyle = lipgloss.NewStyle().
			Background(style.Surface).
			Foreground(style.Text).
			Border(lipgloss.NormalBorder(), true, false, false, false).
			BorderForeground(style.BorderColor).
			Padding(0, 1)

	SearchActiveStyle = lipgloss.NewStyle().
				Foreground(style.Accent).
				Bold(true)

	TitleBarStyle = lipgloss.NewStyle().
			Foreground(style.Accent).
			Border(lipgloss.NormalBorder(), false, false, true, false).
			BorderForeground(style.BorderColor).
			Bold(true).
			Padding(0, 1)

	SearchBarStyle = lipgloss.NewStyle().
			Foreground(style.Text).
			Border(lipgloss.NormalBorder(), false, false, true, false).
			BorderForeground(style.Accent).
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
