package setup

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// ---- Data types ----

type Provider struct {
	Name    string `json:"name"`
	Desc    string `json:"desc"`
	BaseURL string `json:"base-url"`
}

type SetupFile struct {
	Providers []Provider `json:"providers"`
}

type SetupData struct {
	Providers    []Provider
	SelectedProv int
	APIKey       string
	BaseURL      string
	ModelMode    string
	AllModel     string
	LiteModel    string
	FlashModel   string
	ProModel     string

	EmbeddingEnabled  bool
	EmbeddingAPIKey   string
	EmbeddingURL      string
	EmbeddingProvider string
	SearchEnabled     bool
	SearchAPIKey      string
	SearchURL         string
	SearchProvider    string
	VisionEnabled     bool
	VisionAPIKey      string
	VisionURL         string
	VisionProvider    string
	BrowserEnabled    bool
	PlaywrightHost    string
	StatusHost        string
	ProxyEnabled      bool
	HTTPProxy         string
	SOCKSProxy        string

	BotWS    string
	BotToken string

	DebugGroupID       string
	EnableGroups       string
	MessageRedirectMap string
}

// ---- Steps ----

type Step int

const (
	StepProviderSelect Step = iota
	StepLLMConfig
	StepToolsMenu
	StepToolsEmbedding
	StepToolsSearch
	StepToolsVision
	StepToolsBrowser
	StepToolsProxy
	StepBot
	StepGroups
	StepDone
)

var nodeLabels = []string{"LLM Configuration", "Tools & Features", "Default Bot (erii)", "Groups"}

func (s Step) node() int {
	switch {
	case s <= StepLLMConfig:
		return 0
	case s <= StepToolsProxy:
		return 1
	case s <= StepBot:
		return 2
	default:
		return 3
	}
}

func (s Step) isMainStep() bool {
	switch s {
	case StepProviderSelect, StepToolsMenu, StepBot, StepGroups:
		return true
	default:
		return false
	}
}

func (s Step) prevMainStep() Step {
	switch s {
	case StepProviderSelect:
		return StepProviderSelect
	case StepLLMConfig:
		return StepProviderSelect
	case StepToolsMenu, StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy:
		return StepProviderSelect
	case StepBot:
		return StepToolsMenu
	case StepGroups:
		return StepBot
	default:
		return StepProviderSelect
	}
}

func providerToHoconKey(name string) string {
	switch strings.ToLower(name) {
	case "google":
		return "google"
	case "deepseek":
		return "deep-seek"
	case "minimax":
		return "minimax"
	default:
		return strings.ToLower(name)
	}
}

func providerToChoiceModel(name string) string {
	switch strings.ToUpper(name) {
	case "GOOGLE":
		return "GOOGLE"
	case "DEEPSEEK":
		return "DEEP_SEEK"
	case "MINIMAX":
		return "MINIMAX"
	default:
		return strings.ToUpper(name)
	}
}

// ---- Key bindings ----

type SetupKeyMap struct {
	Enter  key.Binding
	Nav    key.Binding
	Back   key.Binding
	Quit   key.Binding
	Help   key.Binding
	Scroll key.Binding
}

func (k SetupKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Enter, k.Nav, k.Back, k.Quit, k.Help}
}

func (k SetupKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Enter, k.Nav, k.Scroll},
		{k.Back, k.Quit, k.Help},
	}
}

var DefaultSetupKeys = SetupKeyMap{
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "confirm"),
	),
	Nav: key.NewBinding(
		key.WithKeys("j", "k", "up", "down"),
		key.WithHelp("j/k/↑/↓", "navigate"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "back"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("ctrl+c", "quit"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Scroll: key.NewBinding(
		key.WithKeys("pgup", "pgdown", "ctrl+up", "ctrl+down"),
		key.WithHelp("pgup/pgdn", "scroll"),
	),
}

// ---- List item ----

type providerItem struct {
	name    string
	desc    string
	baseURL string
	index   int
}

func (i providerItem) Title() string       { return i.name }
func (i providerItem) Description() string { return fmt.Sprintf("%s  |  %s", i.desc, i.baseURL) }
func (i providerItem) FilterValue() string { return i.name }

// ---- Model ----

type Model struct {
	step Step
	data *SetupData

	width  int
	height int

	list    list.Model
	form    *huh.Form
	confirm *huh.Form

	help help.Model
	keys SetupKeyMap

	wrote    bool
	writeErr error
	quitting bool
}

func newModel(providers []Provider) Model {
	data := &SetupData{
		Providers: providers,
		BotWS:     "ws://127.0.0.1:3001",
		ModelMode: "separate",
	}

	items := make([]list.Item, len(providers))
	for i, p := range providers {
		items[i] = providerItem{name: p.Name, desc: p.Desc, baseURL: p.BaseURL, index: i}
	}

	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Title("Select LLM Provider")
	l.SetShowHelp(false)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)

	return Model{
		step: StepProviderSelect,
		data: data,
		list: l,
		help: help.New(),
		keys: DefaultSetupKeys,
	}
}

// ---- Entry point ----

func Start() error {
	configPath := filepath.Join(path.ConfMetaDir, "setup.json")
	data, err := os.ReadFile(configPath)
	if err != nil {
		return fmt.Errorf("cannot read setup.json (%s): %w", configPath, err)
	}

	var sf SetupFile
	if err := json.Unmarshal(data, &sf); err != nil {
		return fmt.Errorf("cannot parse setup.json: %w", err)
	}

	if len(sf.Providers) == 0 {
		return fmt.Errorf("no providers configured in setup.json")
	}

	m := newModel(sf.Providers)
	p := tea.NewProgram(m, tea.WithAltScreen())
	_, err = p.Run()
	return err
}

// ---- tea.Model ----

func (m Model) Init() tea.Cmd {
	return tea.SetWindowTitle("Erii Setup")
}

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width-4, msg.Height-12)
		m.help.Width = msg.Width

		if m.form != nil {
			contentHeight := msg.Height - 13
			if contentHeight < 10 {
				contentHeight = 10
			}
			m.form.WithWidth(msg.Width)
			m.form.WithHeight(contentHeight)
		}
		return m, nil

	case tea.KeyMsg:
		if m.step == StepDone {
			switch msg.String() {
			case "y", "Y":
				m.writeConfig()
				m.quitting = true
				return m, tea.Quit
			case "n", "N", "q":
				m.quitting = true
				return m, tea.Quit
			}
			if key.Matches(msg, m.keys.Quit) {
				m.quitting = true
				return m, tea.Quit
			}
			return m, nil
		}

		if key.Matches(msg, m.keys.Quit) {
			m.quitting = true
			return m, tea.Quit
		}

		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			return m, nil
		}

		if key.Matches(msg, m.keys.Back) {
			if m.step.isMainStep() && m.step > StepProviderSelect {
				m.step = m.step.prevMainStep()
				m.rebuildCurrentStep()
				return m, m.currentInitCmd()
			}
			if !m.step.isMainStep() {
				m.step = m.step.prevMainStep()
				m.rebuildCurrentStep()
				return m, m.currentInitCmd()
			}
			return m, nil
		}
	}

	switch m.step {
	case StepProviderSelect:
		return m.updateProviderSelect(msg)
	case StepLLMConfig:
		return m.updateForm(msg, StepToolsMenu)
	case StepToolsMenu:
		return m.updateForm(msg, m.nextEnabledTool())
	case StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy:
		return m.updateForm(msg, m.nextEnabledTool())
	case StepBot:
		return m.updateForm(msg, StepGroups)
	case StepGroups:
		return m.updateForm(msg, StepDone)
	}

	return m, nil
}

func (m Model) View() string {
	if m.width == 0 {
		return "Loading..."
	}

	if m.quitting {
		if m.wrote {
			return styleSuccess("Configuration saved to " + path.AppFile + "\n\nPress any key to exit...")
		}
		if m.writeErr != nil {
			return styleError("Failed to write config: " + m.writeErr.Error() + "\n\nPress any key to exit...")
		}
		return styleText("Setup cancelled.\n\nPress any key to exit...")
	}

	header := renderHeader(m.width)
	timeline := renderTimeline(m.step.node(), m.data, m.width)
	content := m.renderContent()
	footer := m.help.View(m.keys)

	return lipgloss.JoinVertical(lipgloss.Left,
		header,
		"",
		timeline,
		"",
		content,
		"",
		footer,
	)
}

// ---- Step rendering ----

func (m Model) stepTitle() string {
	switch m.step {
	case StepLLMConfig:
		return "LLM Configuration"
	case StepToolsMenu:
		return "Tools & Features"
	case StepToolsEmbedding:
		return "Embedding Configuration"
	case StepToolsSearch:
		return "Search Configuration"
	case StepToolsVision:
		return "Vision Configuration"
	case StepToolsBrowser:
		return "Browser Configuration"
	case StepToolsProxy:
		return "Proxy Configuration"
	case StepBot:
		return "Default Bot (erii)"
	case StepGroups:
		return "Groups"
	default:
		return ""
	}
}

func (m Model) renderContent() string {
	switch m.step {
	case StepProviderSelect:
		return m.list.View()
	case StepLLMConfig, StepToolsMenu, StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy, StepBot, StepGroups:
		var b strings.Builder
		if t := m.stepTitle(); t != "" {
			b.WriteString(style.Title(t))
			b.WriteString("\n\n")
		}
		if m.form != nil {
			b.WriteString(m.form.View())
		}
		return b.String()
	case StepDone:
		return m.renderSummary()
	}
	return ""
}

// ---- Summary ----

func (m Model) renderSummary() string {
	var b strings.Builder
	b.WriteString(style.Title("Configuration Summary"))
	b.WriteString("\n\n")

	d := m.data
	prov := d.Providers[d.SelectedProv]

	b.WriteString(style.Subtitle("LLM"))
	b.WriteString("\n")
	b.WriteString(fmt.Sprintf("  Provider: %s\n", prov.Name))
	b.WriteString(fmt.Sprintf("  Base URL: %s\n", d.BaseURL))
	if d.ModelMode == "all" {
		b.WriteString(fmt.Sprintf("  Model (all tiers): %s\n", d.AllModel))
	} else {
		b.WriteString(fmt.Sprintf("  Lite: %s\n", d.LiteModel))
		b.WriteString(fmt.Sprintf("  Flash: %s\n", d.FlashModel))
		b.WriteString(fmt.Sprintf("  Pro: %s\n", d.ProModel))
	}
	b.WriteString("\n")

	b.WriteString(style.Subtitle("Tools & Features"))
	b.WriteString("\n")
	if d.EmbeddingEnabled {
		b.WriteString(fmt.Sprintf("  Embedding: provider=%s\n", d.EmbeddingProvider))
	}
	if d.SearchEnabled {
		b.WriteString(fmt.Sprintf("  Search: provider=%s\n", d.SearchProvider))
	}
	if d.VisionEnabled {
		b.WriteString(fmt.Sprintf("  Vision: provider=%s\n", d.VisionProvider))
	}
	if d.BrowserEnabled {
		b.WriteString(fmt.Sprintf("  Browser: playwright-host=%s\n", d.PlaywrightHost))
	}
	if d.ProxyEnabled {
		b.WriteString(fmt.Sprintf("  Proxy: http=%s socks=%s\n", d.HTTPProxy, d.SOCKSProxy))
	}
	if !d.EmbeddingEnabled && !d.SearchEnabled && !d.VisionEnabled && !d.BrowserEnabled && !d.ProxyEnabled {
		b.WriteString(style.Muted("  (not configured)\n"))
	}
	b.WriteString("\n")

	b.WriteString(style.Subtitle("Bot"))
	b.WriteString("\n")
	b.WriteString(fmt.Sprintf("  WS: %s\n", d.BotWS))
	b.WriteString(fmt.Sprintf("  Token: %s\n", maskString(d.BotToken)))
	b.WriteString("\n")

	b.WriteString(style.Subtitle("Groups"))
	b.WriteString("\n")
	b.WriteString(fmt.Sprintf("  Debug Group: %s\n", d.DebugGroupID))
	b.WriteString(fmt.Sprintf("  Enabled Groups: %s\n", d.EnableGroups))
	b.WriteString(fmt.Sprintf("  Message Redirect: %s\n", d.MessageRedirectMap))

	b.WriteString("\n")
	b.WriteString(style.Title("Write configuration to " + path.AppFile + "? (Y/n)"))

	return b.String()
}

// ---- Update helpers ----

func (m *Model) updateProviderSelect(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)

	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "enter":
			if item, ok := m.list.SelectedItem().(providerItem); ok {
				m.data.SelectedProv = item.index
				m.data.BaseURL = item.baseURL
				m.step = StepLLMConfig
				m.rebuildCurrentStep()
				return m, m.currentInitCmd()
			}
		}
	}
	return m, cmd
}

func (m *Model) updateForm(msg tea.Msg, nextStep Step) (tea.Model, tea.Cmd) {
	if m.form == nil {
		return m, nil
	}

	f, cmd := m.form.Update(msg)
	if f2, ok := f.(*huh.Form); ok {
		m.form = f2
	}

	if m.form.State == huh.StateCompleted {
		m.collectFormData()
		m.step = nextStep
		m.rebuildCurrentStep()
		return m, m.currentInitCmd()
	}

	if m.form.State == huh.StateAborted {
		m.step = m.step.prevMainStep()
		m.rebuildCurrentStep()
		return m, m.currentInitCmd()
	}

	return m, cmd
}

func (m *Model) collectFormData() {
	// Data is bound via pointers in form builders — nothing to extract here.
}

// ---- Form builders ----

func (m *Model) rebuildCurrentStep() {
	switch m.step {
	case StepLLMConfig:
		m.form = buildLLMForm(m.data)
	case StepToolsMenu:
		m.form = buildToolsMenuForm(m.data)
	case StepToolsEmbedding:
		m.form = buildEmbeddingForm(m.data)
	case StepToolsSearch:
		m.form = buildSearchForm(m.data)
	case StepToolsVision:
		m.form = buildVisionForm(m.data)
	case StepToolsBrowser:
		m.form = buildBrowserForm(m.data)
	case StepToolsProxy:
		m.form = buildProxyForm(m.data)
	case StepBot:
		m.form = buildBotForm(m.data)
	case StepGroups:
		m.form = buildGroupsForm(m.data)
	default:
		m.form = nil
	}
	if m.form != nil && m.width > 0 {
		contentHeight := m.height - 13
		if contentHeight < 10 {
			contentHeight = 10
		}
		m.form.WithWidth(m.width)
		m.form.WithHeight(contentHeight)
	}
}

func (m *Model) currentInitCmd() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *Model) nextEnabledTool() Step {
	d := m.data
	switch m.step {
	case StepToolsMenu:
		if d.EmbeddingEnabled {
			return StepToolsEmbedding
		}
		fallthrough
	case StepToolsEmbedding:
		if d.SearchEnabled {
			return StepToolsSearch
		}
		fallthrough
	case StepToolsSearch:
		if d.VisionEnabled {
			return StepToolsVision
		}
		fallthrough
	case StepToolsVision:
		if d.BrowserEnabled {
			return StepToolsBrowser
		}
		fallthrough
	case StepToolsBrowser:
		if d.ProxyEnabled {
			return StepToolsProxy
		}
		fallthrough
	default:
		return StepBot
	}
}

// ---- Form builders ----

func buildLLMForm(d *SetupData) *huh.Form {
	provName := d.Providers[d.SelectedProv].Name
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("API Key").
				Value(&d.APIKey).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter API key (leave empty for env var)"),
		).Title("LLM — "+provName+" (1/3) — Authentication").WithShowHelp(false),
		huh.NewGroup(
			huh.NewInput().
				Title("Base URL").
				Value(&d.BaseURL).
				Placeholder(d.BaseURL),
			huh.NewSelect[string]().
				Title("Model configuration mode").
				Options(
					huh.NewOption("Configure separately (lite / flash / pro)", "separate"),
					huh.NewOption("Use one model for all tiers", "all"),
				).
				Value(&d.ModelMode),
		).Title("LLM — "+provName+" (2/3) — Endpoint").WithShowHelp(false),
		huh.NewGroup(
			huh.NewInput().
				Title("Model (all tiers)").
				Value(&d.AllModel).
				Placeholder("e.g. deepseek-chat (used for lite/flash/pro when 'one model' selected)"),
			huh.NewInput().
				Title("Lite Model").
				Value(&d.LiteModel).
				Placeholder("e.g. gemini-2.0-flash-lite"),
			huh.NewInput().
				Title("Flash Model").
				Value(&d.FlashModel).
				Placeholder("e.g. gemini-2.0-flash"),
			huh.NewInput().
				Title("Pro Model").
				Value(&d.ProModel).
				Placeholder("e.g. gemini-2.5-pro"),
		).Title("LLM — "+provName+" (3/3) — Models").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildToolsMenuForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewSelect[bool]().
				Title("Embedding").
				Description("Configure embedding service").
				Options(huh.NewOption("Enable", true), huh.NewOption("Skip", false)).
				Value(&d.EmbeddingEnabled),
			huh.NewSelect[bool]().
				Title("Search").
				Description("Configure search service").
				Options(huh.NewOption("Enable", true), huh.NewOption("Skip", false)).
				Value(&d.SearchEnabled),
			huh.NewSelect[bool]().
				Title("Vision").
				Description("Configure vision service").
				Options(huh.NewOption("Enable", true), huh.NewOption("Skip", false)).
				Value(&d.VisionEnabled),
		).Title("Tools & Features (1/2) — AI Services").WithShowHelp(false),
		huh.NewGroup(
			huh.NewSelect[bool]().
				Title("Browser").
				Description("Configure browser automation").
				Options(huh.NewOption("Enable", true), huh.NewOption("Skip", false)).
				Value(&d.BrowserEnabled),
			huh.NewSelect[bool]().
				Title("Proxy").
				Description("Configure proxy").
				Options(huh.NewOption("Enable", true), huh.NewOption("Skip", false)).
				Value(&d.ProxyEnabled),
		).Title("Tools & Features (2/2) — Infrastructure").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildEmbeddingForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("API Key").Value(&d.EmbeddingAPIKey).EchoMode(huh.EchoModePassword).Placeholder("Enter API key"),
			huh.NewInput().Title("URL").Value(&d.EmbeddingURL).Placeholder("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"),
			huh.NewInput().Title("Provider").Value(&d.EmbeddingProvider).Placeholder("bytedance"),
		).Title("Embedding Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildSearchForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("API Key").Value(&d.SearchAPIKey).EchoMode(huh.EchoModePassword).Placeholder("Enter API key"),
			huh.NewInput().Title("URL").Value(&d.SearchURL).Placeholder("https://api.exa.ai/search"),
			huh.NewInput().Title("Provider").Value(&d.SearchProvider).Placeholder("exa"),
		).Title("Search Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildVisionForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("API Key").Value(&d.VisionAPIKey).EchoMode(huh.EchoModePassword).Placeholder("Enter API key"),
			huh.NewInput().Title("URL").Value(&d.VisionURL).Placeholder("https://api.minimaxi.com/v1/coding_plan/vlm"),
			huh.NewInput().Title("Provider").Value(&d.VisionProvider).Placeholder("minimax"),
		).Title("Vision Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildBrowserForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("Playwright Host").Value(&d.PlaywrightHost).Placeholder("ws://127.0.0.1:13001"),
			huh.NewInput().Title("Status Host").Value(&d.StatusHost).Placeholder("http://127.0.0.1:13002"),
		).Title("Browser Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildProxyForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("HTTP Proxy").Value(&d.HTTPProxy).Placeholder("http://proxy:8080"),
			huh.NewInput().Title("SOCKS Proxy").Value(&d.SOCKSProxy).Placeholder("socks5://proxy:1080"),
		).Title("Proxy Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildBotForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("WebSocket Address").
				Value(&d.BotWS).
				Placeholder("ws://127.0.0.1:3001"),
			huh.NewInput().
				Title("Token").
				Value(&d.BotToken).
				EchoMode(huh.EchoModePassword).
				Placeholder("Enter NapCat token"),
		).Title("Default Bot (erii) Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

func buildGroupsForm(d *SetupData) *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("Debug Group ID").Value(&d.DebugGroupID).Placeholder("Leave empty to disable"),
			huh.NewInput().Title("Enabled Groups (comma-separated)").Value(&d.EnableGroups).Placeholder("474270623,1053148332"),
			huh.NewInput().Title("Message Redirect Map (comma-separated)").Value(&d.MessageRedirectMap).Placeholder("format: source:target"),
		).Title("Groups Configuration").WithShowHelp(false),
	).WithTheme(huhTheme())
}

// ---- Config write ----

func (m *Model) writeConfig() {
	m.writeErr = modifyConfig(m.data, path.AppFile)
	m.wrote = (m.writeErr == nil)
}

// ---- Helpers ----

func maskString(s string) string {
	if s == "" {
		return "(empty)"
	}
	if len(s) <= 4 {
		return "****"
	}
	return s[:2] + "****" + s[len(s)-2:]
}

func styleText(s string) string    { return lipgloss.NewStyle().Foreground(style.Text).Render(s) }
func styleSuccess(s string) string { return style.SuccessText(s) }
func styleError(s string) string   { return style.ErrorText(s) }
