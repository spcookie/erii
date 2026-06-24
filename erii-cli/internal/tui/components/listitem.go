package components

import "github.com/charmbracelet/bubbles/list"

// ListItem is a general-purpose list.Item implementation with a title and description.
type ListItem struct {
	TitleText string
	DescText  string
}

func (i ListItem) Title() string       { return i.TitleText }
func (i ListItem) Description() string { return i.DescText }
func (i ListItem) FilterValue() string { return i.TitleText }

// SetListItems converts a slice of arbitrary type to []list.Item using the provided mapper.
func SetListItems[T any](data []T, mapper func(T) list.Item) []list.Item {
	items := make([]list.Item, len(data))
	for i, v := range data {
		items[i] = mapper(v)
	}
	return items
}
