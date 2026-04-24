package tui

import (
	"erii-cli/internal/config/tree"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/components"
	cfgcomp "erii-cli/internal/tui/components/config"
	"erii-cli/internal/tui/components/md"
	"erii-cli/internal/tui/components/plugin"

	tea "github.com/charmbracelet/bubbletea"
)

func Start() error {
	_ = tree.LoadMetadata(path.ConfMetaDir)

	var root *RootModel

	// pushScreen is used by child components to push new screens.
	pushScreen := func(screen tea.Model) {
		root.pendingPush = screen
	}

	menu := components.NewMainMenuModel(func(index int) {
		switch index {
		case 0:
			parser := tree.DetectParser(path.EnvFile)
			root.pendingPush = buildConfigBrowser(parser, path.EnvFile, "Env Config", pushScreen)
		case 1:
			parser := tree.DetectParser(path.AppFile)
			root.pendingPush = buildConfigBrowser(parser, path.AppFile, "Application Config", pushScreen)
		case 2:
			root.pendingPush = plugin.NewBrowserModel()
		case 3:
			root.pendingPush = md.NewBrowserModel(path.SoulsDir, "Souls")
		case 4:
			root.pendingPush = md.NewBrowserModel(path.RulesDir, "Rules")
		}
	})

	root = NewRootModel(menu)
	p := tea.NewProgram(root, tea.WithAltScreen())
	_, err := p.Run()
	return err
}

func buildConfigBrowser(parser tree.Parser, filePath string, title string, pushScreen func(tea.Model)) tea.Model {
	rootNode, err := parser.Parse(filePath)
	if err != nil {
		rootNode = tree.NewBranch("root", "Configuration")
	}

	return cfgcomp.NewBrowserModel(rootNode, title,
		func(leaf *tree.LeafNode, onSave func() tea.Cmd) {
			pushScreen(cfgcomp.NewLeafEditorModel(leaf, onSave))
		},
		func(r tree.ConfigNode) error {
			return parser.Save(filePath, r)
		},
	)
}
