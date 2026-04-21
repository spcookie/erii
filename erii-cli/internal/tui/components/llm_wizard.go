package components

import (
	"os"
	"strings"

	"erii-cli/internal/config"
	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

var providerDefaults = map[string]struct {
	baseURL string
	models  map[string]string
}{
	"google": {
		baseURL: "https://generativelanguage.googleapis.com",
		models:  map[string]string{"lite": "gemini-2.0-flash-lite", "flash": "gemini-2.0-flash", "pro": "gemini-2.5-pro"},
	},
	"deep-seek": {
		baseURL: "https://api.deepseek.com",
		models:  map[string]string{"all": "deepseek-chat"},
	},
	"minimax": {
		baseURL: "https://api.minimaxi.com",
		models:  map[string]string{"lite": "MiniMax-M2.5", "flash": "MiniMax-M2.5", "pro": "MiniMax-M2.7"},
	},
}

type providerItem struct {
	name string
}

func (i providerItem) Title() string       { return i.name }
func (i providerItem) Description() string { return providerDefaults[i.name].baseURL }
func (i providerItem) FilterValue() string { return i.name }

type LLMWizardModel struct {
	step     int
	provider string
	apiKey   string
	baseURL  string
	models   map[string]string

	providerList list.Model
	apiKeyInput  textinput.Model
	urlInput     textinput.Model

	width    int
	height   int
	template string
}

func NewLLMWizardModel(w, h int) *LLMWizardModel {
	items := []list.Item{
		providerItem{"google"},
		providerItem{"deep-seek"},
		providerItem{"minimax"},
	}
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(style.Primary)
	delegate.Styles.NormalTitle = delegate.Styles.NormalTitle.Foreground(style.Text)
	l := list.New(items, delegate, 0, 0)
	l.Title = style.Subtitle("Select Provider")
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(false)

	aki := textinput.New()
	aki.Placeholder = "API Key"
	aki.EchoMode = textinput.EchoPassword

	uri := textinput.New()
	uri.Placeholder = "Base URL (optional)"

	tmpl, _ := os.ReadFile(path.AppTemplate)

	return &LLMWizardModel{
		providerList: l,
		apiKeyInput:  aki,
		urlInput:     uri,
		width:        w,
		height:       h,
		template:     string(tmpl),
		models:       make(map[string]string),
	}
}

func (m *LLMWizardModel) Init() tea.Cmd { return nil }

func (m *LLMWizardModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyBackspace:
			if m.step > 0 {
				m.step--
				return m, nil
			}
		case tea.KeyEnter:
			switch m.step {
			case 0:
				if item, ok := m.providerList.SelectedItem().(providerItem); ok {
					m.provider = item.name
					if defs, ok := providerDefaults[m.provider]; ok {
						m.baseURL = defs.baseURL
						m.urlInput.SetValue(defs.baseURL)
						m.models = make(map[string]string)
						for k, v := range defs.models {
							m.models[k] = v
						}
					}
					m.step = 1
					return m, m.apiKeyInput.Focus()
				}
			case 1:
				m.apiKey = m.apiKeyInput.Value()
				m.step = 2
				return m, m.urlInput.Focus()
			case 2:
				if v := m.urlInput.Value(); v != "" {
					m.baseURL = v
				}
				m.step = 3
				m.save()
				return m, nil
			}
		}
	}

	switch m.step {
	case 0:
		var cmd tea.Cmd
		m.providerList, cmd = m.providerList.Update(msg)
		return m, cmd
	case 1:
		var cmd tea.Cmd
		m.apiKeyInput, cmd = m.apiKeyInput.Update(msg)
		return m, cmd
	case 2:
		var cmd tea.Cmd
		m.urlInput, cmd = m.urlInput.Update(msg)
		return m, cmd
	}
	return m, nil
}

func (m *LLMWizardModel) View() string {
	theme := style.NewTheme()
	labels := []string{"Select Provider", "API Key", "Base URL", "Done"}
	bar := theme.StepBar(m.step, 4, labels[m.step])

	var left string
	switch m.step {
	case 0:
		m.providerList.SetSize(m.width/2-4, m.height-8)
		left = m.providerList.View()
	case 1:
		left = style.Subtitle("Enter API Key for "+strings.ToUpper(m.provider)) + "\n\n" + m.apiKeyInput.View()
	case 2:
		left = style.Subtitle("Base URL (optional)") + "\n\n" + m.urlInput.View() + "\n\n" + style.Muted("Press Enter to keep default or type custom URL")
	case 3:
		left = lipgloss.NewStyle().Foreground(style.Success).Render("✓ LLM configuration saved!")
	}

	preview := m.template
	if m.provider != "" {
		preview = highlightSection(preview, "llm."+m.provider)
	}
	right := style.Title("Template Preview") + "\n" + style.Muted(preview)

	return bar + "\n\n" + theme.TwoColumn(left, right, m.width)
}

func (m *LLMWizardModel) save() {
	cfg, _ := config.LoadApp(path.AppFile)
	found := false
	for i := range cfg.LLM.Providers {
		if cfg.LLM.Providers[i].Name == m.provider {
			cfg.LLM.Providers[i].APIKey = m.apiKey
			cfg.LLM.Providers[i].BaseURL = m.baseURL
			cfg.LLM.Providers[i].Models = m.models
			found = true
			break
		}
	}
	if !found {
		cfg.LLM.Providers = append(cfg.LLM.Providers, config.LLMProvider{
			Name: m.provider, APIKey: m.apiKey, BaseURL: m.baseURL, Models: m.models,
		})
	}
	config.SaveApp(path.AppFile, cfg)
}

func highlightSection(text, section string) string {
	lines := strings.Split(text, "\n")
	var out []string
	inSection := false
	braceCount := 0
	for _, line := range lines {
		if strings.Contains(line, section) {
			inSection = true
		}
		if inSection {
			out = append(out, lipgloss.NewStyle().Foreground(style.Primary).Render(line))
			braceCount += strings.Count(line, "{")
			braceCount -= strings.Count(line, "}")
			if braceCount <= 0 && strings.Contains(line, "}") {
				inSection = false
			}
		} else {
			out = append(out, line)
		}
	}
	return strings.Join(out, "\n")
}
