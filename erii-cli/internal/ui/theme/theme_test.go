package theme

import (
	"reflect"
	"testing"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/lipgloss"
)

func TestLightPaletteMatchesVercelSpec(t *testing.T) {
	tests := map[string]struct {
		got  lipgloss.AdaptiveColor
		want string
	}{
		"background":   {Background, "#FAFAFA"},
		"surface":      {Surface, "#FFFFFF"},
		"surface alt":  {SurfaceAlt, "#F2F2F2"},
		"text":         {Text, "#171717"},
		"body":         {TextBody, "#4D4D4D"},
		"muted":        {TextMuted, "#8F8F8F"},
		"faint":        {TextFaint, "#A1A1A1"},
		"border":       {BorderColor, "#EBEBEB"},
		"selection":    {Selection, "#D3E5FF"},
		"accent":       {Accent, "#0070F3"},
		"accent deep":  {AccentDeep, "#0761D1"},
		"error":        {Error, "#EE0000"},
		"error deep":   {ErrorDeep, "#C50000"},
		"warning":      {Warning, "#F5A623"},
		"warning deep": {WarningDeep, "#AB570A"},
	}

	for name, test := range tests {
		if test.got.Light != test.want {
			t.Errorf("%s light color = %s, want %s", name, test.got.Light, test.want)
		}
	}
	if !reflect.DeepEqual(Success, Accent) {
		t.Errorf("success = %v, want accent %v", Success, Accent)
	}
}

func TestStyleDelegateDoesNotHighlightSelectedBackground(t *testing.T) {
	delegate := StyleDelegate(list.NewDefaultDelegate())

	if got := delegate.Styles.SelectedTitle.GetBackground(); got != (lipgloss.NoColor{}) {
		t.Fatalf("selected title background = %v, want no color", got)
	}
	if got := delegate.Styles.SelectedDesc.GetBackground(); got != (lipgloss.NoColor{}) {
		t.Fatalf("selected description background = %v, want no color", got)
	}
}
