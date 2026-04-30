package manage

import "github.com/charmbracelet/bubbles/key"

type tableKeys struct {
	Up       key.Binding
	Down     key.Binding
	Enter    key.Binding
	Search   key.Binding
	Select   key.Binding
	Delete   key.Binding
	BatchDel key.Binding
	New      key.Binding
	Refresh  key.Binding
	Back     key.Binding
	Help     key.Binding
	Quit     key.Binding
}

func (k tableKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Search, k.Back, k.Help, k.Quit}
}

func (k tableKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Enter, k.Search},
		{k.Select, k.Delete, k.BatchDel, k.New},
		{k.Refresh, k.Back, k.Help, k.Quit},
	}
}

var defaultTableKeys = tableKeys{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("\xe2\x86\x91/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("\xe2\x86\x93/j", "down"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "edit"),
	),
	Search: key.NewBinding(
		key.WithKeys("/"),
		key.WithHelp("/", "search"),
	),
	Select: key.NewBinding(
		key.WithKeys(" "),
		key.WithHelp("space", "select"),
	),
	Delete: key.NewBinding(
		key.WithKeys("d"),
		key.WithHelp("d", "delete"),
	),
	BatchDel: key.NewBinding(
		key.WithKeys("D"),
		key.WithHelp("D", "batch delete"),
	),
	New: key.NewBinding(
		key.WithKeys("n"),
		key.WithHelp("n", "new"),
	),
	Refresh: key.NewBinding(
		key.WithKeys("r"),
		key.WithHelp("r", "refresh"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc", "back"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
}
