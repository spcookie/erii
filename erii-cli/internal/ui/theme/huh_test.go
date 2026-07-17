package theme

import (
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
