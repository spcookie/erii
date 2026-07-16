package setup

import (
	"fmt"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// ---- Unified step layout ----

func renderStepPage(title, content string) string {
	var b strings.Builder
	b.WriteString(style.Title(title))
	b.WriteString("\n\n")
	b.WriteString(content)
	return b.String()
}

type tabItem struct {
	Label  string
	Active bool
}

func renderFormStep(title string, form *huh.Form, validationMessage string, tabs []tabItem) string {
	if form == nil {
		return renderStepPage(title, "")
	}
	parts := []string{style.Title(title)}
	if len(tabs) > 0 {
		parts = append(parts, renderTabs(tabs))
	}
	if validationMessage != "" {
		parts = append(parts, renderValidationMessage(validationMessage))
	}
	parts = append(parts, form.View())
	return strings.Join(parts, "\n\n")
}

func renderTabs(tabs []tabItem) string {
	parts := make([]string, 0, len(tabs))
	for _, tab := range tabs {
		label := " " + tab.Label + " "
		if tab.Active {
			parts = append(parts, lipgloss.NewStyle().
				Foreground(style.SurfaceAlt).
				Background(style.Primary).
				Bold(true).
				Render(label))
		} else {
			parts = append(parts, lipgloss.NewStyle().
				Foreground(style.TextMuted).
				Border(lipgloss.NormalBorder(), false, false, true, false).
				BorderForeground(style.BorderColor).
				Render(label))
		}
	}
	return lipgloss.JoinHorizontal(lipgloss.Top, parts...)
}

func renderValidationMessage(message string) string {
	return lipgloss.NewStyle().
		Foreground(style.Error).
		Border(lipgloss.RoundedBorder()).
		BorderForeground(style.Error).
		Padding(0, 1).
		Render("Validation failed: " + message)
}

// ---- Timeline ----

func renderTimeline(currentNode int, data *SetupData) string {
	var b strings.Builder

	for i, label := range nodeLabels {
		status := getNodeStatus(i, currentNode)
		icon := nodeIcon(status)
		suffix := nodeSuffix(i, status, data)
		connector := nodeConnector(i, len(nodeLabels))

		iconStyled := styleNodeIcon(icon, status)
		labelStyled := styleNodeLabel(label, status)
		suffixStyled := styleNodeSuffix(suffix)

		line := fmt.Sprintf("  %s %s %s", iconStyled, labelStyled, suffixStyled)
		b.WriteString(line)
		b.WriteString("\n")

		if connector != "" {
			b.WriteString("  ")
			b.WriteString(style.StepPending.Render(connector))
			b.WriteString("\n")
		}
	}

	return b.String()
}

type nodeStatus int

const (
	nodeDone nodeStatus = iota
	nodeCurrent
	nodePending
)

func getNodeStatus(idx int, currentNode int) nodeStatus {
	if idx < currentNode {
		return nodeDone
	}
	if idx == currentNode {
		return nodeCurrent
	}
	return nodePending
}

func nodeIcon(status nodeStatus) string {
	switch status {
	case nodeDone:
		return "●"
	case nodeCurrent:
		return "◉"
	default:
		return "○"
	}
}

func nodeConnector(idx int, total int) string {
	if idx < total-1 {
		return "│"
	}
	return ""
}

func nodeSuffix(idx int, status nodeStatus, data *SetupData) string {
	if status != nodeDone {
		return ""
	}
	switch idx {
	case 0:
		if data.SelectedProv < len(data.Providers) {
			return "→ " + data.Providers[data.SelectedProv].Name
		}
	case 1:
		parts := []string{}
		if data.EmbeddingEnabled {
			parts = append(parts, "embedding")
		}
		if data.SearchEnabled {
			parts = append(parts, "search")
		}
		if data.VisionEnabled {
			parts = append(parts, "vision")
		}
		if data.BrowserEnabled {
			parts = append(parts, "browser")
		}
		if data.ProxyEnabled {
			parts = append(parts, "proxy")
		}
		if len(parts) > 0 {
			return "→ " + strings.Join(parts, ", ")
		}
		return "→ (skipped)"
	case 2:
		if data.BotWS != "" {
			return "→ " + data.BotWS
		}
	case 3:
		if data.EnableGroups != "" {
			return "→ " + data.EnableGroups
		}
	}
	return ""
}

func styleNodeIcon(icon string, status nodeStatus) string {
	switch status {
	case nodeDone:
		return style.StepDone.Render(icon)
	case nodeCurrent:
		return style.StepCurrent.Render(icon)
	default:
		return style.StepPending.Render(icon)
	}
}

func styleNodeLabel(label string, status nodeStatus) string {
	switch status {
	case nodeDone:
		return style.StepDone.Render(label)
	case nodeCurrent:
		return style.StepCurrent.Render(label)
	default:
		return style.StepPending.Render(label)
	}
}

func styleNodeSuffix(suffix string) string {
	if suffix == "" {
		return ""
	}
	return lipgloss.NewStyle().Foreground(style.TextMuted).Render(suffix)
}

// ---- Huh theme (matches style.StyleDelegate color scheme) ----

func huhTheme() *huh.Theme {
	t := huh.ThemeBase()

	// Tighter field spacing
	t.FieldSeparator = lipgloss.NewStyle().SetString("\n\n")

	// Focused field: purple titles (matching list desc), green interactive elements (matching list title)
	t.Focused.Base = t.Focused.Base.BorderForeground(style.Primary)
	t.Focused.Title = t.Focused.Title.Foreground(style.Secondary).Bold(true).MarginBottom(0)
	t.Focused.Description = t.Focused.Description.Foreground(style.TextMuted)
	t.Focused.SelectSelector = t.Focused.SelectSelector.Foreground(style.Secondary)
	t.Focused.NextIndicator = t.Focused.NextIndicator.Foreground(style.Secondary)
	t.Focused.PrevIndicator = t.Focused.PrevIndicator.Foreground(style.Secondary)
	t.Focused.Option = t.Focused.Option.Foreground(style.Text)
	t.Focused.MultiSelectSelector = t.Focused.MultiSelectSelector.Foreground(style.Secondary)
	t.Focused.SelectedOption = t.Focused.SelectedOption.Foreground(style.Primary)
	t.Focused.SelectedPrefix = t.Focused.SelectedPrefix.Foreground(style.Primary)
	t.Focused.UnselectedOption = t.Focused.UnselectedOption.Foreground(style.Text)
	t.Focused.UnselectedPrefix = t.Focused.UnselectedPrefix.Foreground(style.TextMuted)
	t.Focused.FocusedButton = t.Focused.FocusedButton.Foreground(style.SurfaceAlt).Background(style.Primary).Bold(true)
	t.Focused.BlurredButton = t.Focused.BlurredButton.Foreground(style.TextMuted).Background(style.Surface)
	t.Focused.TextInput.Cursor = t.Focused.TextInput.Cursor.Foreground(style.Primary)
	t.Focused.TextInput.Placeholder = t.Focused.TextInput.Placeholder.Foreground(style.TextMuted)
	t.Focused.TextInput.Prompt = t.Focused.TextInput.Prompt.Foreground(style.Primary)
	t.Focused.TextInput.Text = t.Focused.TextInput.Text.Foreground(style.Text)
	t.Focused.ErrorIndicator = t.Focused.ErrorIndicator.Foreground(style.Error)
	t.Focused.ErrorMessage = t.Focused.ErrorMessage.Foreground(style.Error)

	// Blurred fields: hidden border, muted text
	t.Blurred = t.Focused
	t.Blurred.Base = t.Blurred.Base.BorderStyle(lipgloss.HiddenBorder())
	t.Blurred.NextIndicator = lipgloss.NewStyle()
	t.Blurred.PrevIndicator = lipgloss.NewStyle()
	t.Blurred.TextInput.Prompt = t.Blurred.TextInput.Prompt.Foreground(style.TextMuted)
	t.Blurred.TextInput.Text = t.Blurred.TextInput.Text.Foreground(style.Text)

	// Group title — align with style.Title (Primary + Bold)
	t.Group.Title = lipgloss.NewStyle().Foreground(style.Primary).Bold(true)
	t.Group.Description = lipgloss.NewStyle().Foreground(style.TextMuted)

	return t
}
