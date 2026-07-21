package theme

import (
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// HuhTheme maps forms onto the same neutral hierarchy as lists and tables.
func HuhTheme() *huh.Theme {
	t := huh.ThemeBase()
	t.Form.Base = t.Form.Base.UnsetBackground()
	t.Group.Base = t.Group.Base.UnsetBackground()
	t.FieldSeparator = lipgloss.NewStyle().SetString("\n\n")

	t.Focused = transparentFieldStyles(t.Focused)
	t.Focused.Base = t.Focused.Base.BorderForeground(Accent)
	t.Focused.Card = t.Focused.Base
	t.Focused.Title = t.Focused.Title.Foreground(Text).Bold(true).MarginBottom(0)
	t.Focused.Description = t.Focused.Description.Foreground(TextBody)
	t.Focused.SelectSelector = t.Focused.SelectSelector.Foreground(Accent)
	t.Focused.NextIndicator = t.Focused.NextIndicator.Foreground(Accent)
	t.Focused.PrevIndicator = t.Focused.PrevIndicator.Foreground(Accent)
	t.Focused.Option = t.Focused.Option.Foreground(Text)
	t.Focused.MultiSelectSelector = t.Focused.MultiSelectSelector.Foreground(Accent)
	t.Focused.SelectedOption = t.Focused.SelectedOption.Foreground(Accent)
	t.Focused.SelectedPrefix = t.Focused.SelectedPrefix.Foreground(Accent)
	t.Focused.UnselectedOption = t.Focused.UnselectedOption.Foreground(Text)
	t.Focused.UnselectedPrefix = t.Focused.UnselectedPrefix.Foreground(TextMuted)
	t.Focused.FocusedButton = t.Focused.FocusedButton.Foreground(Accent).Bold(true)
	t.Focused.BlurredButton = t.Focused.BlurredButton.Foreground(TextFaint)
	t.Focused.TextInput.Cursor = t.Focused.TextInput.Cursor.Foreground(Accent)
	t.Focused.TextInput.Placeholder = t.Focused.TextInput.Placeholder.Foreground(TextFaint)
	t.Focused.TextInput.Prompt = t.Focused.TextInput.Prompt.Foreground(Accent)
	t.Focused.TextInput.Text = t.Focused.TextInput.Text.Foreground(Text)
	t.Focused.ErrorIndicator = t.Focused.ErrorIndicator.Foreground(Error)
	t.Focused.ErrorMessage = t.Focused.ErrorMessage.Foreground(Error)

	t.Blurred = t.Focused
	t.Blurred.Base = t.Blurred.Base.BorderStyle(lipgloss.HiddenBorder())
	t.Blurred.Card = t.Blurred.Base
	t.Blurred.NextIndicator = lipgloss.NewStyle()
	t.Blurred.PrevIndicator = lipgloss.NewStyle()
	t.Blurred.TextInput.Prompt = t.Blurred.TextInput.Prompt.Foreground(TextMuted)
	t.Blurred.TextInput.Text = t.Blurred.TextInput.Text.Foreground(Text)

	t.Group.Title = lipgloss.NewStyle().Foreground(Text).Bold(true)
	t.Group.Description = lipgloss.NewStyle().Foreground(TextBody)
	return t
}

// DestructiveHuhTheme applies the shared form palette with red emphasis for
// destructive actions such as delete confirmations.
func DestructiveHuhTheme() *huh.Theme {
	t := HuhTheme()
	t.Focused.Title = t.Focused.Title.Foreground(Error)
	t.Focused.FocusedButton = t.Focused.FocusedButton.Foreground(Error)
	t.Blurred.Title = t.Blurred.Title.Foreground(Error)
	t.Blurred.FocusedButton = t.Blurred.FocusedButton.Foreground(Error)
	return t
}

func transparentFieldStyles(s huh.FieldStyles) huh.FieldStyles {
	s.Base = s.Base.UnsetBackground()
	s.Title = s.Title.UnsetBackground()
	s.Description = s.Description.UnsetBackground()
	s.ErrorIndicator = s.ErrorIndicator.UnsetBackground()
	s.ErrorMessage = s.ErrorMessage.UnsetBackground()
	s.SelectSelector = s.SelectSelector.UnsetBackground()
	s.Option = s.Option.UnsetBackground()
	s.NextIndicator = s.NextIndicator.UnsetBackground()
	s.PrevIndicator = s.PrevIndicator.UnsetBackground()
	s.Directory = s.Directory.UnsetBackground()
	s.File = s.File.UnsetBackground()
	s.MultiSelectSelector = s.MultiSelectSelector.UnsetBackground()
	s.SelectedOption = s.SelectedOption.UnsetBackground()
	s.SelectedPrefix = s.SelectedPrefix.UnsetBackground()
	s.UnselectedOption = s.UnselectedOption.UnsetBackground()
	s.UnselectedPrefix = s.UnselectedPrefix.UnsetBackground()
	s.TextInput.Cursor = s.TextInput.Cursor.UnsetBackground()
	s.TextInput.CursorText = s.TextInput.CursorText.UnsetBackground()
	s.TextInput.Placeholder = s.TextInput.Placeholder.UnsetBackground()
	s.TextInput.Prompt = s.TextInput.Prompt.UnsetBackground()
	s.TextInput.Text = s.TextInput.Text.UnsetBackground()
	s.FocusedButton = s.FocusedButton.UnsetBackground()
	s.BlurredButton = s.BlurredButton.UnsetBackground()
	s.Card = s.Card.UnsetBackground()
	s.NoteTitle = s.NoteTitle.UnsetBackground()
	s.Next = s.Next.UnsetBackground()
	return s
}
