package md

import (
	"strings"
	"testing"

	style "erii-cli/internal/ui/theme"

	"github.com/charmbracelet/lipgloss"
	"github.com/muesli/termenv"
)

func TestMarkdownRendererUsesVercelTheme(t *testing.T) {
	lipgloss.SetColorProfile(termenv.TrueColor)
	style.Apply(style.ModeDark)
	renderer, err := createRenderer(80)
	if err != nil {
		t.Fatal(err)
	}
	output, err := renderer.Render("# Heading\n\n[Link](https://example.com) and `code`.")
	if err != nil {
		t.Fatal(err)
	}
	for _, ansiColor := range []string{"38;2;50;145;255", "38;2;80;227;194"} {
		if !strings.Contains(output, ansiColor) {
			t.Fatalf("markdown output missing themed ANSI color %q: %q", ansiColor, output)
		}
	}
}
