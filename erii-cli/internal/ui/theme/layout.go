package theme

import "github.com/charmbracelet/lipgloss"

const ErrorCardWidth = 52

var (
	BorderedPanel = lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(BorderStrong).
			Padding(1, 2)
	PanelTitle  = lipgloss.NewStyle().Foreground(Text).Bold(true).MarginBottom(1)
	ListTitle   = lipgloss.NewStyle().Foreground(Text).Bold(true).MarginBottom(1)
	HelpText    = lipgloss.NewStyle().Foreground(TextMuted).MarginTop(1)
	StepDone    = lipgloss.NewStyle().Foreground(Success)
	StepCurrent = lipgloss.NewStyle().Foreground(Accent).Bold(true)
	StepPending = lipgloss.NewStyle().Foreground(TextMuted)
	StepLabel   = lipgloss.NewStyle().Foreground(Text).MarginTop(1)
)

func TwoColumn(left, right string, width int) string {
	leftW := width/2 - 1
	rightW := width - leftW - 1
	l := BorderedPanel.Width(leftW).Render(left)
	r := BorderedPanel.Width(rightW).Render(right)
	return lipgloss.JoinHorizontal(lipgloss.Top, l, r)
}
