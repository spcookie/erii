package style

import "github.com/charmbracelet/lipgloss"

// Adaptive color palette (auto-switches between dark/light modes)
var (
	Background  = lipgloss.AdaptiveColor{Dark: "#282A36", Light: "#F8F8F2"}
	Surface     = lipgloss.AdaptiveColor{Dark: "#44475A", Light: "#E8E8EE"}
	SurfaceAlt  = lipgloss.AdaptiveColor{Dark: "#383846", Light: "#D0D0DC"}
	Primary     = lipgloss.AdaptiveColor{Dark: "#50FA7B", Light: "#2E7D32"}
	Secondary   = lipgloss.AdaptiveColor{Dark: "#BD93F9", Light: "#7B1FA2"}
	Accent      = lipgloss.AdaptiveColor{Dark: "#F1FA8C", Light: "#F9A825"}
	Text        = lipgloss.AdaptiveColor{Dark: "#F8F8F2", Light: "#212121"}
	TextMuted   = lipgloss.AdaptiveColor{Dark: "#6272A4", Light: "#90A4AE"}
	BorderColor = lipgloss.AdaptiveColor{Dark: "#6272A4", Light: "#B0BEC5"}
	Error       = lipgloss.AdaptiveColor{Dark: "#FF5555", Light: "#D32F2F"}
	Success     = lipgloss.AdaptiveColor{Dark: "#50FA7B", Light: "#388E3C"}
	Warning     = lipgloss.AdaptiveColor{Dark: "#FFB86C", Light: "#F57C00"}
	Info        = lipgloss.AdaptiveColor{Dark: "#8BE9FD", Light: "#0288D1"}
)
