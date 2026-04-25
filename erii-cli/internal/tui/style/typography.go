package style

import "github.com/charmbracelet/lipgloss"

// Text styles
var (
	TitleStyle = lipgloss.NewStyle().
			Foreground(Primary).
			Bold(true)

	SubtitleStyle = lipgloss.NewStyle().
			Foreground(Secondary)

	MutedStyle = lipgloss.NewStyle().
			Foreground(TextMuted)

	ErrorStyle = lipgloss.NewStyle().
			Foreground(Error).
			Bold(true)

	SuccessStyle = lipgloss.NewStyle().
			Foreground(Success)

	WarningStyle = lipgloss.NewStyle().
			Foreground(Warning)

	InfoStyle = lipgloss.NewStyle().
			Foreground(Info)
)

// Standalone text helpers

func Title(s string) string       { return TitleStyle.Render(s) }
func Subtitle(s string) string    { return SubtitleStyle.Render(s) }
func Muted(s string) string       { return MutedStyle.Render(s) }
func ErrorText(s string) string   { return ErrorStyle.Render(s) }
func SuccessText(s string) string { return SuccessStyle.Render(s) }
func WarningText(s string) string { return WarningStyle.Render(s) }
func InfoText(s string) string    { return InfoStyle.Render(s) }
