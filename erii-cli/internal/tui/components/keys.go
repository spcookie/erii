package components

import "github.com/charmbracelet/bubbles/key"

// NavKeys defines key bindings for list-navigation screens.
type NavKeys struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Back  key.Binding
	Quit  key.Binding
	Help  key.Binding
}

func (k NavKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Help, k.Quit}
}

func (k NavKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down},
		{k.Enter, k.Back, k.Help, k.Quit},
	}
}

// DefaultNavKeys provides standard key bindings for TUI list-navigation screens.
var DefaultNavKeys = NavKeys{
	Up:    key.NewBinding(key.WithKeys("up", "k"), key.WithHelp("↑/k", "up")),
	Down:  key.NewBinding(key.WithKeys("down", "j"), key.WithHelp("↓/j", "down")),
	Enter: key.NewBinding(key.WithKeys("enter"), key.WithHelp("enter", "select")),
	Back:  key.NewBinding(key.WithKeys("esc", "backspace"), key.WithHelp("esc/backspace", "back")),
	Quit:  key.NewBinding(key.WithKeys("ctrl+c"), key.WithHelp("ctrl+c", "quit")),
	Help:  key.NewBinding(key.WithKeys("?"), key.WithHelp("?", "help")),
}
