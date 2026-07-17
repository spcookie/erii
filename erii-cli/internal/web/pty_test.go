package web

import (
	"reflect"
	"testing"
)

func TestRemoveEnvForBrowserTerminal(t *testing.T) {
	env := []string{
		"PATH=/usr/bin",
		"NO_COLOR=1",
		"TERM=dumb",
		"COLORTERM=",
		"LANG=en_US.UTF-8",
	}
	got := removeEnv(env, "NO_COLOR", "TERM", "COLORTERM")
	want := []string{"PATH=/usr/bin", "LANG=en_US.UTF-8"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("removeEnv() = %#v, want %#v", got, want)
	}
}

func TestBrowserTerminalEnvEnablesWebGlyphs(t *testing.T) {
	env := browserTerminalEnv([]string{
		"PATH=/usr/bin",
		"NO_COLOR=1",
		"TERM=dumb",
		"COLORTERM=",
		"ERII_WEB=0",
	})
	for _, want := range []string{
		"PATH=/usr/bin",
		"TERM=xterm-256color",
		"COLORTERM=truecolor",
		"ERII_WEB=1",
		"LANG=en_US.UTF-8",
		"LC_ALL=en_US.UTF-8",
	} {
		found := false
		for _, value := range env {
			if value == want {
				found = true
				break
			}
		}
		if !found {
			t.Fatalf("browser environment missing %q: %#v", want, env)
		}
	}
}
