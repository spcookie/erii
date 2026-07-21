package theme

import (
	"fmt"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/lipgloss"
)

type Theme struct{}

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
			steps[i] += lipgloss.NewStyle().Foreground(TextMuted).Render(" -- ")
		}
	}
	return lipgloss.JoinHorizontal(lipgloss.Top, steps...) + "\n" + StepLabel.Render(label)
}

func (t *Theme) TwoColumn(left, right string, width int) string {
	return TwoColumn(left, right, width)
}

func StyleDelegate(delegate list.DefaultDelegate) list.DefaultDelegate {
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.
		Foreground(Accent).
		BorderForeground(Accent).
		Bold(true)
	delegate.Styles.SelectedDesc = delegate.Styles.SelectedDesc.
		Foreground(TextBody).
		BorderForeground(Accent)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(Text)
	delegate.Styles.NormalDesc = delegate.Styles.NormalDesc.Foreground(TextBody)
	return delegate
}
