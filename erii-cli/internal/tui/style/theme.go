package style

import (
	"fmt"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/lipgloss"
)

// Theme aggregates all style components.
type Theme struct{}

// StepBar renders a step indicator like "1 ── 2 ── 3".
func (t *Theme) StepBar(current, total int, label string) string {
	steps := make([]string, total)
	for i := 0; i < total; i++ {
		num := fmt.Sprintf(" %d ", i+1)
		if i < current {
			steps[i] = StepDone.Render(num)
		} else if i == current {
			steps[i] = StepCurrent.Render(num)
		} else {
			steps[i] = StepPending.Render(num)
		}
		if i < total-1 {
			sep := lipgloss.NewStyle().Foreground(TextMuted).Render(" ── ")
			steps[i] += sep
		}
	}
	bar := lipgloss.JoinHorizontal(lipgloss.Top, steps...)
	return bar + "\n" + StepLabel.Render(label)
}

// TwoColumn renders two bordered panels side-by-side.
func (t *Theme) TwoColumn(left, right string, width int) string {
	return TwoColumn(left, right, width)
}

// StyleDelegate applies standard styling to a list delegate.
func StyleDelegate(delegate list.DefaultDelegate) list.DefaultDelegate {
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(Primary)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.Foreground(Secondary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(TextMuted)
	return delegate
}
