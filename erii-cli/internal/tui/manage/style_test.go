package manage

import (
	"strings"
	"testing"

	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/x/ansi"
)

func TestDataTableStylesUseTransparentCanvas(t *testing.T) {
	styles := DataTableStyles()
	for name, color := range map[string]lipgloss.TerminalColor{
		"header background": styles.Header.GetBackground(),
		"cell background":   styles.Cell.GetBackground(),
		"title background":  TitleBarStyle.GetBackground(),
	} {
		if color != (lipgloss.NoColor{}) {
			t.Errorf("%s should be transparent, got %v", name, color)
		}
	}
	if _, ok := styles.Selected.GetBackground().(lipgloss.AdaptiveColor); !ok {
		t.Errorf("selected row should use the adaptive focus background, got %T", styles.Selected.GetBackground())
	}
	if _, ok := StatusBarStyle.GetBackground().(lipgloss.AdaptiveColor); !ok {
		t.Errorf("status bar should use an adaptive surface background, got %T", StatusBarStyle.GetBackground())
	}
	if got := styles.Cell.GetForeground(); got != (lipgloss.NoColor{}) {
		t.Errorf("cells must inherit foreground so the selected row style is not reset, got %v", got)
	}
}

func TestDataTableSelectedRowKeepsColumnAlignment(t *testing.T) {
	model := table.New(
		table.WithColumns([]table.Column{
			{Title: "", Width: 3},
			{Title: "ID", Width: 6},
			{Title: "Content", Width: 12},
		}),
		table.WithRows([]table.Row{
			{"[ ]", "14114", "normal"},
			{"[ ]", "14113", "focused"},
		}),
		table.WithHeight(3),
		table.WithStyles(DataTableStyles()),
	)
	model.SetCursor(1)

	var normal, focused string
	for _, line := range strings.Split(model.View(), "\n") {
		plain := ansi.Strip(line)
		switch {
		case strings.Contains(plain, "normal"):
			normal = line
		case strings.Contains(plain, "focused"):
			focused = line
		}
	}
	if normal == "" || focused == "" {
		t.Fatalf("expected both data rows in rendered table: %q", ansi.Strip(model.View()))
	}
	if got, want := ansi.StringWidth(focused), ansi.StringWidth(normal); got != want {
		t.Fatalf("focused row width = %d, normal row width = %d", got, want)
	}
	if got, want := strings.Index(ansi.Strip(focused), "[ ]"), strings.Index(ansi.Strip(normal), "[ ]"); got != want {
		t.Fatalf("focused checkbox starts at %d, normal checkbox starts at %d", got, want)
	}
}
