package components

import (
	"time"

	tea "github.com/charmbracelet/bubbletea"
)

// PopScreenMsg is sent by a screen to request the root navigator to pop it.
type PopScreenMsg struct{}

// RefreshMsg is sent by the root navigator after popping a screen,
// so the new top screen can reload its data if needed.
type RefreshMsg struct{}

// ClearMsg is used to clear notification messages after a delay.
type ClearMsg struct{}

// ClearAfter is a helper that returns a tea.Cmd that sleeps then sends ClearMsg.
func ClearAfter(d time.Duration) tea.Cmd {
	return tea.Tick(d, func(time.Time) tea.Msg { return ClearMsg{} })
}
