package theme

import "github.com/charmbracelet/lipgloss"

// Palette follows Vercel's neutral hierarchy. Blue is reserved for focus and
// information; semantic colors are used only for status and data.
var (
	Background   = lipgloss.AdaptiveColor{Dark: "#000000", Light: "#FFFFFF"}
	Surface      = lipgloss.AdaptiveColor{Dark: "#161616", Light: "#F7F7F7"}
	SurfaceAlt   = lipgloss.AdaptiveColor{Dark: "#242424", Light: "#ECECEC"}
	Text         = lipgloss.AdaptiveColor{Dark: "#EDEDED", Light: "#171717"}
	TextMuted    = lipgloss.AdaptiveColor{Dark: "#A1A1A1", Light: "#666666"}
	BorderColor  = lipgloss.AdaptiveColor{Dark: "#484848", Light: "#C7C7C7"}
	BorderStrong = lipgloss.AdaptiveColor{Dark: "#707070", Light: "#8A8A8A"}
	Track        = lipgloss.AdaptiveColor{Dark: "#383838", Light: "#D4D4D4"}
	Selection    = lipgloss.AdaptiveColor{Dark: "#102A43", Light: "#D6EBFF"}
	ErrorSurface = lipgloss.AdaptiveColor{Dark: "#351418", Light: "#FFE8EC"}
	Accent       = lipgloss.AdaptiveColor{Dark: "#3291FF", Light: "#0070F3"}
	Success      = lipgloss.AdaptiveColor{Dark: "#46A758", Light: "#1A7F37"}
	Warning      = lipgloss.AdaptiveColor{Dark: "#F5A623", Light: "#B86E00"}
	Error        = lipgloss.AdaptiveColor{Dark: "#E5484D", Light: "#D70022"}

	// Compatibility names keep component code semantic while the palette is
	// migrated away from the previous primary/secondary color scheme.
	Primary   = Text
	Secondary = Accent
	Info      = Accent

	ChartBlue   = Accent
	ChartCyan   = lipgloss.AdaptiveColor{Dark: "#50E3C2", Light: "#0F766E"}
	ChartViolet = lipgloss.AdaptiveColor{Dark: "#8B5CF6", Light: "#6D28D9"}
	ChartAmber  = Warning
)

// HeatScale is the shared neutral-to-blue intensity scale used by heatmaps.
var HeatScale = []lipgloss.TerminalColor{
	lipgloss.AdaptiveColor{Dark: "#383838", Light: "#D4D4D4"},
	lipgloss.AdaptiveColor{Dark: "#10233F", Light: "#EAF4FF"},
	lipgloss.AdaptiveColor{Dark: "#12345A", Light: "#D6EBFF"},
	lipgloss.AdaptiveColor{Dark: "#174777", Light: "#B9DDFF"},
	lipgloss.AdaptiveColor{Dark: "#1C5FA0", Light: "#8EC8FF"},
	lipgloss.AdaptiveColor{Dark: "#2377C8", Light: "#5EAEFF"},
	lipgloss.AdaptiveColor{Dark: "#2A88E5", Light: "#3291FF"},
	Accent,
	lipgloss.AdaptiveColor{Dark: "#79B8FF", Light: "#0058C7"},
}

func AdaptiveHex(color lipgloss.AdaptiveColor) string {
	if lipgloss.HasDarkBackground() {
		return color.Dark
	}
	return color.Light
}
