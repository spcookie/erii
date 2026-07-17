package components

import (
	"strings"

	"github.com/charmbracelet/lipgloss"
)

const (
	progressFilled = "█"
	progressTrack  = "░"
)

// ProgressBar renders a continuous colored value over a lightweight track.
func ProgressBar(width int, percent float64, filledColor, trackColor lipgloss.TerminalColor) string {
	if width < 3 {
		width = 20
	}
	filled := int(float64(width) * percent / 100.0)
	filled = max(0, min(width, filled))

	value := lipgloss.NewStyle().
		Foreground(filledColor).
		Render(strings.Repeat(progressFilled, filled))
	track := lipgloss.NewStyle().
		Foreground(trackColor).
		Render(strings.Repeat(progressTrack, width-filled))
	return value + track
}
