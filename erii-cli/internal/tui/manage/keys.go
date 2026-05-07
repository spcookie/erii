package manage

import "github.com/charmbracelet/bubbles/key"

type tableKeys struct {
	Up         key.Binding
	Down       key.Binding
	PageUp     key.Binding
	PageDown   key.Binding
	Enter      key.Binding
	Search     key.Binding
	Select     key.Binding
	Delete     key.Binding
	BatchDel   key.Binding
	New        key.Binding
	Refresh    key.Binding
	Back       key.Binding
	Help       key.Binding
	Quit       key.Binding
	Sort1      key.Binding
	Sort2      key.Binding
	Sort3      key.Binding
	Sort4      key.Binding
	SortToggle key.Binding
	Preview    key.Binding
}

func (k tableKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Enter, k.Search, k.Preview, k.Back, k.Help, k.Quit}
}

func (k tableKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.PageUp, k.PageDown, k.Enter, k.Search},
		{k.Select, k.Delete, k.BatchDel, k.New, k.Refresh, k.Preview},
		{k.Sort1, k.Sort2, k.Sort3, k.Sort4, k.SortToggle, k.Back, k.Help, k.Quit},
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
	PageUp: key.NewBinding(
		key.WithKeys("pgup", "b"),
		key.WithHelp("pgup/b", "page up"),
	),
	PageDown: key.NewBinding(
		key.WithKeys("pgdown", "f"),
		key.WithHelp("pgdown/f", "page down"),
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
	Sort1: key.NewBinding(
		key.WithKeys("1"),
		key.WithHelp("1", "sort col 1"),
	),
	Sort2: key.NewBinding(
		key.WithKeys("2"),
		key.WithHelp("2", "sort col 2"),
	),
	Sort3: key.NewBinding(
		key.WithKeys("3"),
		key.WithHelp("3", "sort col 3"),
	),
	Sort4: key.NewBinding(
		key.WithKeys("4"),
		key.WithHelp("4", "sort col 4"),
	),
	SortToggle: key.NewBinding(
		key.WithKeys("s"),
		key.WithHelp("s", "toggle sort"),
	),
	Preview: key.NewBinding(
		key.WithKeys("v"),
		key.WithHelp("v", "preview"),
	),
}

type editFormKeys struct {
	Next   key.Binding
	Prev   key.Binding
	Submit key.Binding
	Cancel key.Binding
	Quit   key.Binding
}

func (k editFormKeys) ShortHelp() []key.Binding {
	return []key.Binding{k.Next, k.Prev, k.Submit, k.Cancel, k.Quit}
}

func (k editFormKeys) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Next, k.Prev},
		{k.Submit, k.Cancel, k.Quit},
	}
}

var defaultEditFormKeys = editFormKeys{
	Next: key.NewBinding(
		key.WithKeys("tab", "down"),
		key.WithHelp("tab/\xe2\x86\x93", "next field"),
	),
	Prev: key.NewBinding(
		key.WithKeys("shift+tab", "up"),
		key.WithHelp("shift+tab/\xe2\x86\x91", "prev field"),
	),
	Submit: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "submit"),
	),
	Cancel: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "cancel"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
}
