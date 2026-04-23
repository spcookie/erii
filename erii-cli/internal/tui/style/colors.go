package style

import "github.com/charmbracelet/lipgloss"

// Color palette (Dracula-like)
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
	Warning     = lipgloss.Color("#FFB86C")
	Info        = lipgloss.Color("#8BE9FD")
)

// Adaptive colors based on background.
func LightDark(isDark bool, dark, light lipgloss.Color) lipgloss.Color {
	if isDark {
		return dark
	}
	return light
}
