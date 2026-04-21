package tui

import (
	"erii-cli/internal/tui/components"

	tea "github.com/charmbracelet/bubbletea"
)

func Start() error {
	var root *RootModel
	menu := components.NewMenuModel(func(index int) {
		switch index {
		case 0:
			root.pendingPush = components.NewEnvEditorModel(root.Width(), root.Height())
		case 1:
			root.pendingPush = components.NewAppSubmenuModel(root.Width(), root.Height(), func(sub int) {
				switch sub {
				case 0:
					root.pendingPush = components.NewLLMWizardModel(root.Width(), root.Height())
				case 1:
					root.pendingPush = components.NewEmbeddingFormModel(root.Width(), root.Height())
				case 2:
					root.pendingPush = components.NewSearchFormModel(root.Width(), root.Height())
				case 3:
					root.pendingPush = components.NewBrowserFormModel(root.Width(), root.Height())
				case 4:
					root.pendingPush = components.NewProxyFormModel(root.Width(), root.Height())
				case 5:
					root.pendingPush = components.NewOneBotListModel(root.Width(), root.Height())
				case 6:
					root.pendingPush = components.NewGroupsFormModel(root.Width(), root.Height())
				}
			})
		case 2:
			root.pendingPush = components.NewSoulsListModel(root.Width(), root.Height())
		case 3:
			root.pendingPush = components.NewRulesListModel(root.Width(), root.Height())
		}
	})

	root = NewRootModel(menu)
	p := tea.NewProgram(root, tea.WithAltScreen())
	_, err := p.Run()
	return err
}
