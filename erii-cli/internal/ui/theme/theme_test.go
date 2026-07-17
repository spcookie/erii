package theme

import (
	"testing"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/lipgloss"
)

func TestStyleDelegateDoesNotHighlightSelectedBackground(t *testing.T) {
	delegate := StyleDelegate(list.NewDefaultDelegate())

	if got := delegate.Styles.SelectedTitle.GetBackground(); got != (lipgloss.NoColor{}) {
		t.Fatalf("selected title background = %v, want no color", got)
	}
	if got := delegate.Styles.SelectedDesc.GetBackground(); got != (lipgloss.NoColor{}) {
		t.Fatalf("selected description background = %v, want no color", got)
	}
}
