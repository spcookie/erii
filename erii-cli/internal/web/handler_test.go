package web

import (
	"reflect"
	"testing"
)

func TestWSHandlerCommandArgsIncludesGlobalPathFlags(t *testing.T) {
	h := &WSHandler{
		ConfDir:     "./.uesugi/conf",
		MetaConfDir: "./.uesugi/.conf",
		EriiDir:     "./.uesugi/.erii",
		PluginDir:   "./erii-plugins/build/plugins",
		OptsPath:    "./.uesugi/opts",
	}

	got := h.commandArgs([]string{"refresh"})
	want := []string{
		"--conf-dir", "./.uesugi/conf",
		"--meta-conf-dir", "./.uesugi/.conf",
		"--erii-dir", "./.uesugi/.erii",
		"--plugin-dir", "./erii-plugins/build/plugins",
		"--opts-path", "./.uesugi/opts",
		"refresh",
	}

	if !reflect.DeepEqual(got, want) {
		t.Fatalf("commandArgs() = %#v, want %#v", got, want)
	}
}

func TestWSHandlerCommandArgsUsesResolvedBrowserThemeForAuto(t *testing.T) {
	h := &WSHandler{Theme: "auto"}
	got := h.commandArgs([]string{"refresh"}, "light")
	want := []string{"--theme", "light", "refresh"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("commandArgs() = %#v, want %#v", got, want)
	}
}

func TestWSHandlerCommandArgsRejectsUnknownBrowserTheme(t *testing.T) {
	h := &WSHandler{Theme: "auto"}
	got := h.commandArgs([]string{"refresh"}, "neon")
	want := []string{"refresh"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("commandArgs() = %#v, want %#v", got, want)
	}
}

func TestWSHandlerExplicitThemeOverridesBrowserTheme(t *testing.T) {
	h := &WSHandler{Theme: "dark"}
	got := h.commandArgs([]string{"refresh"}, "light")
	want := []string{"--theme", "dark", "refresh"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("commandArgs() = %#v, want %#v", got, want)
	}
}
