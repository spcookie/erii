package output

import (
	"strings"
	"testing"

	"erii-cli/internal/ui/theme"

	"github.com/charmbracelet/lipgloss"
	"github.com/muesli/termenv"
)

func TestRendererUsesAdaptiveVercelPalette(t *testing.T) {
	lipgloss.SetColorProfile(termenv.TrueColor)
	theme.Apply(theme.ModeDark)
	dark := Title("Erii")
	if !strings.Contains(dark, "38;2;237;237;237") {
		t.Fatalf("dark title did not use dark text token: %q", dark)
	}

	theme.Apply(theme.ModeLight)
	light := Title("Erii")
	if !strings.Contains(light, "38;2;23;23;23") {
		t.Fatalf("light title did not use light text token: %q", light)
	}
}

func TestErrorResultKeepsReadableStructure(t *testing.T) {
	got := ErrorResult("Config refresh", "Backend", assertError("offline"))
	for _, want := range []string{"Config refresh", "ERROR", "Backend", "offline"} {
		if !strings.Contains(got, want) {
			t.Fatalf("ErrorResult missing %q: %q", want, got)
		}
	}
}

func TestErrorResultIndentsWrappedMessage(t *testing.T) {
	got := ErrorResult("Plugin refresh", "Connection", assertError(strings.Repeat("x", 90)))
	if !strings.Contains(got, "\n                ") {
		t.Fatalf("wrapped error continuation is not indented: %q", got)
	}
}

type assertError string

func (e assertError) Error() string { return string(e) }
