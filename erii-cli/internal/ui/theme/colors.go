package theme

import "github.com/charmbracelet/lipgloss"

// Palette follows Vercel's neutral hierarchy. Blue is reserved for focus and
// information; semantic colors are used only for status and data.
var (
	Background   = lipgloss.AdaptiveColor{Dark: "#000000", Light: "#FAFAFA"}
	Surface      = lipgloss.AdaptiveColor{Dark: "#111111", Light: "#FFFFFF"}
	SurfaceAlt   = lipgloss.AdaptiveColor{Dark: "#1F1F1F", Light: "#F2F2F2"}
	Text         = lipgloss.AdaptiveColor{Dark: "#EDEDED", Light: "#171717"}
	TextBody     = lipgloss.AdaptiveColor{Dark: "#A1A1A1", Light: "#4D4D4D"}
	TextMuted    = lipgloss.AdaptiveColor{Dark: "#737373", Light: "#8F8F8F"}
	TextFaint    = lipgloss.AdaptiveColor{Dark: "#525252", Light: "#A1A1A1"}
	BorderColor  = lipgloss.AdaptiveColor{Dark: "#333333", Light: "#EBEBEB"}
	BorderStrong = lipgloss.AdaptiveColor{Dark: "#484848", Light: "#EBEBEB"}
	Track        = lipgloss.AdaptiveColor{Dark: "#333333", Light: "#F2F2F2"}
	Selection    = lipgloss.AdaptiveColor{Dark: "#102A43", Light: "#D3E5FF"}
	ErrorSurface = lipgloss.AdaptiveColor{Dark: "#351418", Light: "#FFE8EC"}
	Accent       = lipgloss.AdaptiveColor{Dark: "#3291FF", Light: "#0070F3"}
	AccentDeep   = lipgloss.AdaptiveColor{Dark: "#0070F3", Light: "#0761D1"}
	Success      = Accent
	Warning      = lipgloss.AdaptiveColor{Dark: "#F5A623", Light: "#F5A623"}
	WarningDeep  = lipgloss.AdaptiveColor{Dark: "#AB570A", Light: "#AB570A"}
	Error        = lipgloss.AdaptiveColor{Dark: "#EE0000", Light: "#EE0000"}
	ErrorDeep    = lipgloss.AdaptiveColor{Dark: "#C50000", Light: "#C50000"}

	// Compatibility names keep component code semantic while the palette is
	// migrated away from the previous primary/secondary color scheme.
	Primary   = Text
	Secondary = Accent
	Info      = Accent

	ChartBlue    = Accent
	ChartCyan    = lipgloss.AdaptiveColor{Dark: "#50E3C2", Light: "#50E3C2"}
	ChartViolet  = lipgloss.AdaptiveColor{Dark: "#8B5CF6", Light: "#7928CA"}
	ChartPink    = lipgloss.AdaptiveColor{Dark: "#FF0080", Light: "#FF0080"}
	ChartMagenta = lipgloss.AdaptiveColor{Dark: "#EB367F", Light: "#EB367F"}
	ChartAmber   = Warning
)

// HeatScale is the shared neutral-to-blue intensity scale used by heatmaps.
var HeatScale = []lipgloss.TerminalColor{
	Track,
	lipgloss.AdaptiveColor{Dark: "#10233F", Light: "#EAF4FF"},
	Selection,
	lipgloss.AdaptiveColor{Dark: "#174777", Light: "#B9DDFF"},
	lipgloss.AdaptiveColor{Dark: "#1C5FA0", Light: "#8EC8FF"},
	lipgloss.AdaptiveColor{Dark: "#2377C8", Light: "#5EAEFF"},
	lipgloss.AdaptiveColor{Dark: "#2A88E5", Light: "#3291FF"},
	Accent,
	lipgloss.AdaptiveColor{Dark: "#79B8FF", Light: "#0761D1"},
}

func AdaptiveHex(color lipgloss.AdaptiveColor) string {
	if lipgloss.HasDarkBackground() {
		return color.Dark
	}
	return color.Light
}
