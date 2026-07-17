package theme

import (
	"bytes"
	"strings"
	"testing"

	"github.com/charmbracelet/lipgloss"
	"github.com/muesli/termenv"
)

func TestResolveThemePrecedence(t *testing.T) {
	tests := []struct {
		name        string
		flag        string
		flagChanged bool
		env         string
		want        Mode
	}{
		{name: "default", flag: "auto", want: ModeAuto},
		{name: "environment", flag: "auto", env: "LIGHT", want: ModeLight},
		{name: "flag wins", flag: "dark", flagChanged: true, env: "light", want: ModeDark},
		{name: "explicit auto wins", flag: "auto", flagChanged: true, env: "dark", want: ModeAuto},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Resolve(tt.flag, tt.flagChanged, tt.env)
			if err != nil {
				t.Fatal(err)
			}
			if got != tt.want {
				t.Fatalf("Resolve() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestResolveRejectsInvalidTheme(t *testing.T) {
	if _, err := Resolve("neon", true, ""); err == nil {
		t.Fatal("expected invalid theme error")
	}
}

func TestApplyExplicitTheme(t *testing.T) {
	if got := Apply(ModeDark); got != ModeDark || !lipgloss.HasDarkBackground() {
		t.Fatalf("dark theme was not applied: resolved=%q dark=%t", got, lipgloss.HasDarkBackground())
	}
	if got := Apply(ModeLight); got != ModeLight || lipgloss.HasDarkBackground() {
		t.Fatalf("light theme was not applied: resolved=%q dark=%t", got, lipgloss.HasDarkBackground())
	}
}

func TestRedirectedOutputDisablesANSI(t *testing.T) {
	lipgloss.SetColorProfile(termenv.TrueColor)
	var out bytes.Buffer
	ConfigureColorOutput(&out)
	if rendered := Title("Erii"); strings.Contains(rendered, "\x1b[") {
		t.Fatalf("redirected output contains ANSI: %q", rendered)
	}
}

func TestNoColorDisablesANSI(t *testing.T) {
	t.Setenv("NO_COLOR", "1")
	lipgloss.SetColorProfile(termenv.TrueColor)
	ConfigureColorOutput(nil)
	if rendered := ErrorText("failed"); strings.Contains(rendered, "\x1b[") {
		t.Fatalf("NO_COLOR output contains ANSI: %q", rendered)
	}
}
