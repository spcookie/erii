package style

import (
	"fmt"

	"github.com/charmbracelet/lipgloss"
)

var (
	Background  = lipgloss.Color("#282A36")
	Surface     = lipgloss.Color("#44475A")
	SurfaceAlt  = lipgloss.Color("#383846")
	Primary     = lipgloss.Color("#50FA7B")
	Secondary   = lipgloss.Color("#BD93F9")
	Accent      = lipgloss.Color("#F1FA8C")
	Text        = lipgloss.Color("#F8F8F2")
	TextMuted   = lipgloss.Color("#6272A4")
	BorderColor = lipgloss.Color("#6272A4")
	Error       = lipgloss.Color("#FF5555")
	Success     = lipgloss.Color("#50FA7B")
)

type Theme struct {
	BorderedPanel lipgloss.Style
	PanelTitle    lipgloss.Style
	StepDone      lipgloss.Style
	StepCurrent   lipgloss.Style
	StepPending   lipgloss.Style
	StepLabel     lipgloss.Style
	ListTitle     lipgloss.Style
	HelpText      lipgloss.Style
	ErrorText     lipgloss.Style
}

func NewTheme() *Theme {
	return &Theme{
		BorderedPanel: lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(BorderColor).
			Padding(1, 2),
		PanelTitle: lipgloss.NewStyle().
			Foreground(Secondary).
			Bold(true).
			MarginBottom(1),
		StepDone: lipgloss.NewStyle().
			Foreground(Success),
		StepCurrent: lipgloss.NewStyle().
			Foreground(Accent).
			Bold(true),
		StepPending: lipgloss.NewStyle().
			Foreground(TextMuted),
		StepLabel: lipgloss.NewStyle().
			Foreground(Secondary).
			MarginTop(1),
		ListTitle: lipgloss.NewStyle().
			Foreground(Primary).
			Bold(true).
			MarginBottom(1),
		HelpText: lipgloss.NewStyle().
			Foreground(TextMuted).
			MarginTop(1),
		ErrorText: lipgloss.NewStyle().
			Foreground(Error).
			Bold(true),
	}
}

func (t *Theme) StepBar(current, total int, label string) string {
	steps := make([]string, total)
	for i := 0; i < total; i++ {
		num := fmt.Sprintf(" %d ", i+1)
		if i < current {
			steps[i] = t.StepDone.Render(num)
		} else if i == current {
			steps[i] = t.StepCurrent.Render(num)
		} else {
			steps[i] = t.StepPending.Render(num)
		}
		if i < total-1 {
			sep := lipgloss.NewStyle().Foreground(TextMuted).Render(" ── ")
			steps[i] += sep
		}
	}
	bar := lipgloss.JoinHorizontal(lipgloss.Top, steps...)
	return bar + "\n" + t.StepLabel.Render(label)
}

func (t *Theme) TwoColumn(left, right string, width int) string {
	leftW := width/2 - 1
	rightW := width - leftW - 1
	l := t.BorderedPanel.Width(leftW).Render(left)
	r := t.BorderedPanel.Width(rightW).Render(right)
	return lipgloss.JoinHorizontal(lipgloss.Top, l, r)
}

func Title(s string) string {
	return lipgloss.NewStyle().
		Foreground(Primary).
		Bold(true).
		Render(s)
}

func Subtitle(s string) string {
	return lipgloss.NewStyle().
		Foreground(Secondary).
		Render(s)
}

func Muted(s string) string {
	return lipgloss.NewStyle().
		Foreground(TextMuted).
		Render(s)
}
