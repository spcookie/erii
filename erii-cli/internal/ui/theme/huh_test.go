package theme

import (
	"reflect"
	"testing"

	"github.com/charmbracelet/lipgloss"
)

func TestHuhThemeUsesTransparentFieldBackgrounds(t *testing.T) {
	theme := HuhTheme()
	styles := map[string]lipgloss.Style{
		"form":               theme.Form.Base,
		"group":              theme.Group.Base,
		"focused base":       theme.Focused.Base,
		"focused card":       theme.Focused.Card,
		"focused option":     theme.Focused.Option,
		"selected option":    theme.Focused.SelectedOption,
		"focused button":     theme.Focused.FocusedButton,
		"blurred button":     theme.Focused.BlurredButton,
		"text input":         theme.Focused.TextInput.Text,
		"blurred base":       theme.Blurred.Base,
		"blurred card":       theme.Blurred.Card,
		"blurred text input": theme.Blurred.TextInput.Text,
	}

	for name, s := range styles {
		if got := s.GetBackground(); got != (lipgloss.NoColor{}) {
			t.Errorf("%s background should be transparent, got %v", name, got)
		}
	}
}

func TestDestructiveHuhThemeUsesErrorEmphasis(t *testing.T) {
	theme := DestructiveHuhTheme()
	styles := map[string]lipgloss.Style{
		"focused title":  theme.Focused.Title,
		"focused button": theme.Focused.FocusedButton,
		"blurred title":  theme.Blurred.Title,
		"blurred button": theme.Blurred.FocusedButton,
	}
	for name, s := range styles {
		if got := s.GetForeground(); !reflect.DeepEqual(got, Error) {
			t.Errorf("%s foreground = %v, want %v", name, got, Error)
		}
	}
}

func TestHuhThemeUsesTextHierarchy(t *testing.T) {
	theme := HuhTheme()
	tests := map[string]struct {
		got  lipgloss.TerminalColor
		want lipgloss.AdaptiveColor
	}{
		"description":       {theme.Focused.Description.GetForeground(), TextBody},
		"group description": {theme.Group.Description.GetForeground(), TextBody},
		"placeholder":       {theme.Focused.TextInput.Placeholder.GetForeground(), TextFaint},
		"blurred button":    {theme.Focused.BlurredButton.GetForeground(), TextFaint},
	}
	for name, test := range tests {
		if !reflect.DeepEqual(test.got, test.want) {
			t.Errorf("%s foreground = %v, want %v", name, test.got, test.want)
		}
	}
}
