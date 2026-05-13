package tui

import (
	"log"

	"erii-cli/internal/config/tree"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/components"
	cfgcomp "erii-cli/internal/tui/components/config"
	"erii-cli/internal/tui/components/md"
	"erii-cli/internal/tui/components/plugin"

	tea "github.com/charmbracelet/bubbletea"
)

func Start() error {
	if _, err := tree.InitializePluginConfigs(path.PluginDir, path.PluginConfigDir, path.PluginSchemaDir); err != nil {
		log.Printf("warn: plugin config initialization failed: %v", err)
	}
	if err := tree.LoadMetadata(path.ConfMetaDir); err != nil {
		log.Printf("warn: metadata load failed: %v", err)
	}

	var root *RootModel

	// pushScreen is used by child components to push new screens.
	pushScreen := func(screen tea.Model) {
		root.pendingPush = screen
	}

	menu := components.NewMainMenuModel(func(index int) {
		switch index {
		case 0:
			parser := tree.DetectParser(path.EnvFile)
			browser := buildConfigBrowser(parser, path.EnvFile, "Env Config", pushScreen, "").
				WithEditable(true).
				WithNewItemFactory(func(title, desc string) tree.ConfigNode {
					return tree.NewLeaf(title, desc, tree.TypeString, "")
				})
			root.pendingPush = browser
		case 1:
			parser := tree.DetectParser(path.AppFile)
			root.pendingPush = buildConfigBrowser(parser, path.AppFile, "Application Config", pushScreen, "")
		case 2:
			root.pendingPush = plugin.NewBrowserModel(
				func(pluginName string, pluginPath string) {
					parser := tree.DetectParser(pluginPath)
					browser := buildConfigBrowser(parser, pluginPath, pluginName+" Config", pushScreen, pluginName)
					pushScreen(browser)
				},
				nil,
			)
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

func buildConfigBrowser(parser tree.Parser, filePath string, title string, pushScreen func(tea.Model), pluginName string) *cfgcomp.BrowserModel {
	rootNode, err := parser.Parse(filePath)
	if err != nil {
		rootNode = tree.NewBranch("root", "Configuration")
	}

	// Re-apply metadata with plugin context if pluginName is provided
	if pluginName != "" {
		tree.ApplyMetadataWithPlugin(rootNode, "root", pluginName)
	}

	return cfgcomp.NewBrowserModel(rootNode, title,
		func(leaf *tree.LeafNode, onSaveCb func() tea.Cmd) {
			pushScreen(cfgcomp.NewLeafEditorModel(leaf, onSaveCb))
		},
		func(r tree.ConfigNode) error {
			return parser.Save(filePath, r)
		},
	).WithPlugin(pluginName)
}
