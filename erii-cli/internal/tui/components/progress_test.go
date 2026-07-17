package components

import (
	"strings"
	"testing"

	"github.com/charmbracelet/lipgloss"
)

func TestProgressBarUsesContinuousTrack(t *testing.T) {
	bar := ProgressBar(10, 40, lipgloss.Color("2"), lipgloss.Color("8"))
	plain := lipgloss.NewStyle().UnsetForeground().Render(bar)

	if strings.ContainsAny(bar, "■□━─") {
		t.Fatalf("progress bar contains a legacy or line glyph: %q", bar)
	}
	if !strings.Contains(bar, progressFilled) || !strings.Contains(bar, progressTrack) {
		t.Fatalf("progress bar does not contain both continuous segments: %q", bar)
	}
	if lipgloss.Width(plain) != 10 {
		t.Fatalf("progress bar width = %d, want 10", lipgloss.Width(plain))
	}
}

func TestProgressBarClampsPercent(t *testing.T) {
	for _, percent := range []float64{-20, 120} {
		bar := ProgressBar(8, percent, lipgloss.Color("2"), lipgloss.Color("8"))
		if lipgloss.Width(bar) != 8 {
			t.Fatalf("ProgressBar(8, %v) width = %d, want 8", percent, lipgloss.Width(bar))
		}
	}
}
