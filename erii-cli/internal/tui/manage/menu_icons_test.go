package manage

import (
	"strings"
	"testing"

	"github.com/charmbracelet/bubbles/list"
)

func TestManageMenuUsesNerdGlyphsOnlyInWeb(t *testing.T) {
	webItems := manageMenuItems(true)
	nativeItems := manageMenuItems(false)
	if len(webItems) != len(nativeItems) || len(webItems) != 8 {
		t.Fatalf("unexpected menu item counts: web=%d native=%d", len(webItems), len(nativeItems))
	}

	labels := []string{"Memory", "User Profiles", "Summaries", "Memes", "Vocabulary", "Messages", "State", "Cron Tasks"}
	assertMenuIconModes(t, webItems, nativeItems, labels)
}

func TestNestedManageMenusUseNerdGlyphsOnlyInWeb(t *testing.T) {
	tests := []struct {
		name   string
		web    []interface{ Title() string }
		native []interface{ Title() string }
		labels []string
	}{
		{
			name:   "memory",
			web:    memoryMenuTitles(true),
			native: memoryMenuTitles(false),
			labels: []string{"Record", "Vector", "Graph"},
		},
		{
			name:   "messages",
			web:    messageMenuTitles(true),
			native: messageMenuTitles(false),
			labels: []string{"History", "Resources"},
		},
		{
			name:   "state",
			web:    stateMenuTitles(true),
			native: stateMenuTitles(false),
			labels: []string{"Emotion", "Flow", "Volition"},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			assertTitleIconModes(t, test.web, test.native, test.labels)
		})
	}
}

func assertMenuIconModes(t *testing.T, webItems, nativeItems []list.Item, labels []string) {
	t.Helper()
	web := make([]interface{ Title() string }, len(webItems))
	native := make([]interface{ Title() string }, len(nativeItems))
	for i := range webItems {
		web[i] = webItems[i].(interface{ Title() string })
		native[i] = nativeItems[i].(interface{ Title() string })
	}
	assertTitleIconModes(t, web, native, labels)
}

func assertTitleIconModes(t *testing.T, webItems, nativeItems []interface{ Title() string }, labels []string) {
	t.Helper()
	if len(webItems) != len(nativeItems) || len(webItems) != len(labels) {
		t.Fatalf("unexpected item counts: web=%d native=%d labels=%d", len(webItems), len(nativeItems), len(labels))
	}
	for i, label := range labels {
		webTitle := webItems[i].Title()
		nativeTitle := nativeItems[i].Title()
		if !containsPrivateUseGlyph(webTitle) {
			t.Errorf("web title %q does not contain a Nerd glyph", webTitle)
		}
		if containsPrivateUseGlyph(nativeTitle) {
			t.Errorf("native title %q unexpectedly requires a Nerd Font", nativeTitle)
		}
		if !strings.HasSuffix(webTitle, label) || !strings.HasSuffix(nativeTitle, label) {
			t.Errorf("menu labels differ: web=%q native=%q", webTitle, nativeTitle)
		}
	}
}

func memoryMenuTitles(web bool) []interface{ Title() string } {
	items := memoryMenuItems(web)
	result := make([]interface{ Title() string }, len(items))
	for i, item := range items {
		result[i] = item.(memoryMenuItem)
	}
	return result
}

func messageMenuTitles(web bool) []interface{ Title() string } {
	items := messageMenuItems(web)
	result := make([]interface{ Title() string }, len(items))
	for i, item := range items {
		result[i] = item.(messageMenuItem)
	}
	return result
}

func stateMenuTitles(web bool) []interface{ Title() string } {
	items := stateMenuItems(web)
	result := make([]interface{ Title() string }, len(items))
	for i, item := range items {
		result[i] = item.(stateMenuItem)
	}
	return result
}

func containsPrivateUseGlyph(value string) bool {
	for _, r := range value {
		if r >= '\ue000' && r <= '\uf8ff' {
			return true
		}
	}
	return false
}
