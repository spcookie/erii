package style

import "github.com/charmbracelet/lipgloss"

// ErrorCardWidth is the fixed width of error notification cards.
const ErrorCardWidth = 52

// Layout styles
var (
	BorderedPanel = lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(BorderColor).
			Padding(1, 2)

	PanelTitle = lipgloss.NewStyle().
			Foreground(Secondary).
			Bold(true).
			MarginBottom(1)

	ListTitle = lipgloss.NewStyle().
			Foreground(Primary).
			Bold(true).
			MarginBottom(1)

	HelpText = lipgloss.NewStyle().
			Foreground(TextMuted).
			MarginTop(1)

	StepDone = lipgloss.NewStyle().
			Foreground(Success)

	StepCurrent = lipgloss.NewStyle().
			Foreground(Accent).
			Bold(true)

	StepPending = lipgloss.NewStyle().
			Foreground(TextMuted)

	StepLabel = lipgloss.NewStyle().
			Foreground(Secondary).
			MarginTop(1)
)

// TwoColumn renders two bordered panels side-by-side.
func TwoColumn(left, right string, width int) string {
	leftW := width/2 - 1
	rightW := width - leftW - 1
	l := BorderedPanel.Width(leftW).Render(left)
	r := BorderedPanel.Width(rightW).Render(right)
	return lipgloss.JoinHorizontal(lipgloss.Top, l, r)
}
