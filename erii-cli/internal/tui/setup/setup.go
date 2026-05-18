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
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

// ---- Data types ----

type Provider struct {
	Name    string `json:"name"`
	Key     string `json:"key"`
	Desc    string `json:"desc"`
	BaseURL string `json:"base-url"`
}

type ToolProvider struct {
	Name  string `json:"name"`
	URL   string `json:"url"`
	Model string `json:"model"`
}

type LLMDefaults struct {
	BaseURL    string `json:"base-url"`
	ModelMode  string `json:"model-mode"`
	LiteModel  string `json:"lite-model"`
	FlashModel string `json:"flash-model"`
	ProModel   string `json:"pro-model"`
	AllModel   string `json:"all-model"`
}

type BotDefaults struct {
	WS    string `json:"ws"`
	Token string `json:"token"`
}

type GroupsDefaults struct {
	DebugGroupID       string `json:"debug-group-id"`
	EnableGroups       string `json:"enable-groups"`
	MessageRedirectMap string `json:"message-redirect-map"`
}

type BrowserDefaults struct {
	Download      bool   `json:"download"`
	PlaywrightURL string `json:"playwright-url"`
	StatusHost    string `json:"status-host"`
}

type ProxyDefaults struct {
	HTTP  string `json:"http"`
	SOCKS string `json:"socks"`
}

type DefaultsConfig struct {
	LLM     map[string]LLMDefaults `json:"llm"`
	Bot     BotDefaults            `json:"bot"`
	Groups  GroupsDefaults         `json:"groups"`
	Browser BrowserDefaults        `json:"browser"`
	Proxy   ProxyDefaults          `json:"proxy"`
}

type ToolProvidersConfig struct {
	Embedding []ToolProvider `json:"embedding"`
	Search    []ToolProvider `json:"search"`
	Vision    []ToolProvider `json:"vision"`
	Browser   []ToolProvider `json:"browser"`
}

type SetupFile struct {
	Providers     []Provider          `json:"providers"`
	Defaults      DefaultsConfig      `json:"defaults"`
	ToolProviders ToolProvidersConfig `json:"tool-providers"`
}

type SetupData struct {
	Providers    []Provider
	SelectedProv int
	LLMDefaults  map[string]LLMDefaults
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
	EmbeddingModel    string
	SearchEnabled     bool
	SearchAPIKey      string
	SearchURL         string
	SearchProvider    string
	VisionEnabled     bool
	VisionAPIKey      string
	VisionURL         string
	VisionProvider    string
	BrowserEnabled    bool
	BrowserDownload   bool
	PlaywrightURL     string
	StatusHost        string
	ProxyEnabled      bool
	HTTPProxy         string
	SOCKSProxy        string

	BotWS     string
	BotToken  string
	BotAdmins string

	DebugGroupID       string
	EnableGroups       string
	MessageRedirectMap string

	ToolProviders ToolProvidersConfig
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
	case StepToolsMenu:
		return StepProviderSelect
	case StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy:
		return StepToolsMenu
	case StepBot:
		return StepToolsMenu
	case StepGroups:
		return StepBot
	default:
		return StepProviderSelect
	}
}

func hoconKeyToChoiceModel(key string) string {
	return strings.ReplaceAll(strings.ToUpper(key), "-", "_")
}

// ---- Key bindings ----

type SetupKeyMap struct {
	Enter key.Binding
	Nav   key.Binding
	Back  key.Binding
	Quit  key.Binding
	Help  key.Binding
}

func (k SetupKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Enter, k.Nav, k.Back, k.Quit, k.Help}
}

func (k SetupKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Enter, k.Nav},
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
}

var FormKeys = SetupKeyMap{
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "confirm"),
	),
	Nav: key.NewBinding(
		key.WithKeys("tab", "shift+tab"),
		key.WithHelp("tab/shift+tab", "navigate"),
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
}

var DoneKeys = SetupKeyMap{
	Enter: key.NewBinding(
		key.WithKeys("y"),
		key.WithHelp("y", "confirm"),
	),
	Nav: key.NewBinding(
		key.WithKeys("n"),
		key.WithHelp("n", "cancel"),
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
}

func (m Model) currentKeys() SetupKeyMap {
	if m.step == StepProviderSelect || m.step == StepToolsMenu {
		return DefaultSetupKeys
	}
	if m.step == StepDone {
		return DoneKeys
	}
	return FormKeys
}

// ---- List item ----

type providerItem struct {
	name    string
	key     string
	desc    string
	baseURL string
	index   int
}

func (i providerItem) Title() string       { return i.name }
func (i providerItem) Description() string { return fmt.Sprintf("%s  |  %s", i.desc, i.baseURL) }
func (i providerItem) FilterValue() string { return i.name }

type toolItem struct {
	name   string
	desc   string
	step   Step
	isDone bool
}

func (i toolItem) Title() string { return i.name }
func (i toolItem) Description() string {
	if i.isDone {
		return ""
	}
	return i.desc
}
func (i toolItem) FilterValue() string { return i.name }

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

	lastEmbeddingProvider string
	lastSearchProvider    string
	lastVisionProvider    string
	vp                    viewport.Model
}

func newModel(providers []Provider, defaults DefaultsConfig, toolProviders ToolProvidersConfig) Model {
	data := &SetupData{
		Providers:          providers,
		LLMDefaults:        defaults.LLM,
		BotWS:              defaults.Bot.WS,
		BotToken:           defaults.Bot.Token,
		BrowserDownload:    defaults.Browser.Download,
		PlaywrightURL:      defaults.Browser.PlaywrightURL,
		StatusHost:         defaults.Browser.StatusHost,
		HTTPProxy:          defaults.Proxy.HTTP,
		SOCKSProxy:         defaults.Proxy.SOCKS,
		DebugGroupID:       defaults.Groups.DebugGroupID,
		EnableGroups:       defaults.Groups.EnableGroups,
		MessageRedirectMap: defaults.Groups.MessageRedirectMap,
		ToolProviders:      toolProviders,
	}

	m := Model{
		step: StepProviderSelect,
		data: data,
		help: help.New(),
		keys: DefaultSetupKeys,
		vp:   viewport.New(0, 0),
	}
	m.buildProviderList()
	return m
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

	m := newModel(sf.Providers, sf.Defaults, sf.ToolProviders)
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
		m.syncSizes()
		footerHeight := lipgloss.Height(m.help.View(m.currentKeys()))
		vpHeight := msg.Height - footerHeight
		if vpHeight < 3 {
			vpHeight = 3
		}
		m.vp.Width = msg.Width
		m.vp.Height = vpHeight
		m.syncViewportContent()
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
			m.syncViewportContent()

			var cmd tea.Cmd
			m.vp, cmd = m.vp.Update(msg)
			return m, cmd
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
			if (m.step.isMainStep() && m.step > StepProviderSelect) || !m.step.isMainStep() {
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
		return m.updateToolsMenu(msg)
	case StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy:
		return m.updateForm(msg, StepToolsMenu)
	case StepBot:
		return m.updateForm(msg, StepGroups)
	case StepGroups:
		return m.updateForm(msg, StepDone)
	case StepDone:
		// All interactions handled in tea.KeyMsg block above
	default:
		panic("unhandled default case")
	}

	return m, nil
}

func (m Model) View() string {
	if m.width == 0 {
		return "Loading..."
	}

	if m.quitting {
		if m.wrote {
			return style.SuccessText("Configuration saved.\n\nPress any key to exit...")
		}
		if m.writeErr != nil {
			return style.ErrorText("Failed to write config: " + m.writeErr.Error() + "\n\nPress any key to exit...")
		}
		return lipgloss.NewStyle().Foreground(style.Text).Render("Setup cancelled.\n\nPress any key to exit...")
	}

	footer := m.help.View(m.currentKeys())

	return lipgloss.JoinVertical(lipgloss.Left,
		m.vp.View(),
		"",
		footer,
	)
}

// ---- Step rendering ----

func (m Model) stepTitle() string {
	switch m.step {
	case StepProviderSelect:
		return "LLM Configuration"
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
	case StepProviderSelect, StepToolsMenu:
		return renderStepPage(m.stepTitle(), m.list.View())
	case StepLLMConfig, StepToolsEmbedding, StepToolsSearch, StepToolsVision, StepToolsBrowser, StepToolsProxy, StepBot, StepGroups:
		return renderFormStep(m.stepTitle(), m.form)
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
		b.WriteString(fmt.Sprintf("  Embedding: provider=%s model=%s\n", d.EmbeddingProvider, d.EmbeddingModel))
	}
	if d.SearchEnabled {
		b.WriteString(fmt.Sprintf("  Search: provider=%s\n", d.SearchProvider))
	}
	if d.VisionEnabled {
		b.WriteString(fmt.Sprintf("  Vision: provider=%s\n", d.VisionProvider))
	}
	if d.BrowserEnabled {
		b.WriteString(fmt.Sprintf("  Browser: download=%v playwright-url=%s\n", d.BrowserDownload, d.PlaywrightURL))
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
	if d.BotAdmins != "" {
		b.WriteString(fmt.Sprintf("  Admins: %s\n", d.BotAdmins))
	}
	b.WriteString("\n")

	b.WriteString(style.Subtitle("Groups"))
	b.WriteString("\n")
	b.WriteString(fmt.Sprintf("  Debug Group: %s\n", d.DebugGroupID))
	b.WriteString(fmt.Sprintf("  Enabled Groups: %s\n", d.EnableGroups))
	b.WriteString(fmt.Sprintf("  Message Redirect: %s\n", d.MessageRedirectMap))

	b.WriteString("\n")
	b.WriteString(style.Title("Save configuration? (Y/n)"))

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
				if item.baseURL != "" {
					m.data.BaseURL = item.baseURL
				}
				// Populate model defaults for the selected provider
				if cfg, ok := m.data.LLMDefaults[item.key]; ok {
					m.data.ModelMode = cfg.ModelMode
					m.data.LiteModel = cfg.LiteModel
					m.data.FlashModel = cfg.FlashModel
					m.data.ProModel = cfg.ProModel
					m.data.AllModel = cfg.AllModel
				}
				m.step = StepLLMConfig
				m.rebuildCurrentStep()
				return m, m.currentInitCmd()
			}
		}
	}
	m.syncViewportContent()
	return m, cmd
}

func (m *Model) updateToolsMenu(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(msg)

	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "enter":
			if item, ok := m.list.SelectedItem().(toolItem); ok {
				if item.isDone {
					m.step = StepBot
					m.rebuildCurrentStep()
					return m, m.currentInitCmd()
				}
				m.setToolEnabled(item.step)
				m.step = item.step
				m.rebuildCurrentStep()
				return m, m.currentInitCmd()
			}
		}
	}
	m.syncViewportContent()
	return m, cmd
}

func (m *Model) setToolEnabled(step Step) {
	switch step {
	case StepToolsEmbedding:
		m.data.EmbeddingEnabled = true
	case StepToolsSearch:
		m.data.SearchEnabled = true
	case StepToolsVision:
		m.data.VisionEnabled = true
	case StepToolsBrowser:
		m.data.BrowserEnabled = true
	case StepToolsProxy:
		m.data.ProxyEnabled = true
	default:
		panic("unhandled default case")
	}
}

func (m *Model) updateForm(msg tea.Msg, nextStep Step) (tea.Model, tea.Cmd) {
	if m.form == nil {
		return m, nil
	}

	f, cmd := m.form.Update(msg)
	if f2, ok := f.(*huh.Form); ok {
		m.form = f2
	}

	if m.syncProviderURL() {
		m.rebuildCurrentStep()
		return m, m.currentInitCmd()
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

	m.syncViewportContent()
	return m, cmd
}

func (m *Model) collectFormData() {
	// Data is bound via pointers in form builders — nothing to extract here.
}

func (m *Model) syncProviderURL() bool {
	switch m.step {
	case StepToolsEmbedding:
		if m.syncToolURL(m.data.ToolProviders.Embedding, &m.lastEmbeddingProvider, &m.data.EmbeddingProvider, &m.data.EmbeddingURL) {
			m.data.EmbeddingModel = defaultToolModel(m.data.ToolProviders.Embedding, m.data.EmbeddingProvider)
			return true
		}
	case StepToolsSearch:
		return m.syncToolURL(m.data.ToolProviders.Search, &m.lastSearchProvider, &m.data.SearchProvider, &m.data.SearchURL)
	case StepToolsVision:
		return m.syncToolURL(m.data.ToolProviders.Vision, &m.lastVisionProvider, &m.data.VisionProvider, &m.data.VisionURL)
	}
	return false
}

func (m *Model) syncToolURL(providers []ToolProvider, lastProv, curProv, curURL *string) bool {
	if *curProv == *lastProv {
		return false
	}
	lastDefault := ""
	if *lastProv != "" {
		lastDefault = defaultToolURL(providers, *lastProv)
	}
	shouldRebuild := false
	if *curURL == "" || *curURL == lastDefault {
		*curURL = defaultToolURL(providers, *curProv)
		shouldRebuild = true
	}
	*lastProv = *curProv
	return shouldRebuild
}

// ---- Form builders ----

func (m *Model) buildProviderList() {
	items := make([]list.Item, len(m.data.Providers))
	for i, p := range m.data.Providers {
		items[i] = providerItem{name: p.Name, key: p.Key, desc: p.Desc, baseURL: p.BaseURL, index: i}
	}
	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New(items, delegate, 0, 0)
	l.SetShowTitle(false)
	l.SetShowHelp(false)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	m.list = l
}

func (m *Model) buildToolsList() {
	d := m.data
	items := []list.Item{
		toolItem{name: "Done →", isDone: true},
		toolItem{name: "Embedding", desc: d.toolStatus(StepToolsEmbedding), step: StepToolsEmbedding},
		toolItem{name: "Search", desc: d.toolStatus(StepToolsSearch), step: StepToolsSearch},
		toolItem{name: "Vision", desc: d.toolStatus(StepToolsVision), step: StepToolsVision},
		toolItem{name: "Browser", desc: d.toolStatus(StepToolsBrowser), step: StepToolsBrowser},
		toolItem{name: "Proxy", desc: d.toolStatus(StepToolsProxy), step: StepToolsProxy},
	}

	delegate := style.StyleDelegate(list.NewDefaultDelegate())
	l := list.New(items, delegate, 0, 0)
	l.SetShowTitle(false)
	l.SetShowHelp(false)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)
	m.list = l
}

func (d *SetupData) toolStatus(step Step) string {
	switch step {
	case StepToolsEmbedding:
		if d.EmbeddingEnabled && d.EmbeddingAPIKey != "" {
			return "configured"
		}
	case StepToolsSearch:
		if d.SearchEnabled && d.SearchAPIKey != "" {
			return "configured"
		}
	case StepToolsVision:
		if d.VisionEnabled && d.VisionAPIKey != "" {
			return "configured"
		}
	case StepToolsBrowser:
		if d.BrowserEnabled && (d.PlaywrightURL != "" || d.BrowserDownload) {
			return "configured"
		}
	case StepToolsProxy:
		if d.ProxyEnabled && (d.HTTPProxy != "" || d.SOCKSProxy != "") {
			return "configured"
		}
	}
	return "not configured"
}

func (m *Model) syncViewportContent() {
	timeline := renderTimeline(m.step.node(), m.data)
	content := m.renderContent()
	body := lipgloss.JoinVertical(lipgloss.Left, timeline, content)
	m.vp.SetContent(body)
}

func (m *Model) rebuildCurrentStep() {
	m.vp.GotoTop()
	m.keys = m.currentKeys()
	switch m.step {
	case StepProviderSelect:
		m.form = nil
		m.buildProviderList()
	case StepLLMConfig:
		m.form = buildLLMForm(m.data)
	case StepToolsMenu:
		m.form = nil
		m.buildToolsList()
	case StepToolsEmbedding:
		m.lastEmbeddingProvider = m.data.EmbeddingProvider
		m.form = buildEmbeddingForm(m.data)
	case StepToolsSearch:
		m.lastSearchProvider = m.data.SearchProvider
		m.form = buildSearchForm(m.data)
	case StepToolsVision:
		m.lastVisionProvider = m.data.VisionProvider
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
	m.syncSizes()
	m.syncViewportContent()
}

func (m *Model) syncSizes() {
	if m.width == 0 || m.height == 0 {
		return
	}
	m.help.Width = m.width

	listHeight := m.height - 12
	if m.step == StepProviderSelect || m.step == StepToolsMenu {
		// renderStepPage adds title + "\n\n" = 3 extra lines
		listHeight = m.height - 13
	}
	if listHeight < 5 {
		listHeight = 5
	}
	m.list.SetSize(m.width-4, listHeight)

	if m.form != nil {
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
