package config

import (
	"fmt"
	"os"
	"regexp"
	"strings"
)

type LLMProvider struct {
	Name    string
	APIKey  string
	BaseURL string
	Models  map[string]string
}

type LLMConfig struct {
	ChoiceModel string
	Providers   []LLMProvider
}

type EmbeddingConfig struct {
	Provider string
	APIKey   string
}

type SearchConfig struct {
	Provider string
	APIKey   string
}

type BrowserConfig struct {
	PlaywrightHost string
	StatusHost     string
}

type ProxyConfig struct {
	HTTP  string
	SOCKS string
}

type GroupSettings struct {
	Admins []int64
	Desire int
}

type BotConfig struct {
	Name            string
	WS              string
	Token           string
	RoleID          string
	Groups          map[int64]GroupSettings
	EnabledPlugins  []string
	DisabledPlugins []string
}

type OneBotConfig struct {
	Bots map[string]BotConfig
}

type GroupsConfig struct {
	DebugGroupID       *int64
	EnableGroups       []int64
	MessageRedirectMap []string
}

type AppConfig struct {
	LLM       LLMConfig
	Embedding EmbeddingConfig
	Search    SearchConfig
	Browser   BrowserConfig
	Proxy     ProxyConfig
	OneBot    OneBotConfig
	Groups    GroupsConfig
}

func LoadApp(path string) (*AppConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return &AppConfig{OneBot: OneBotConfig{Bots: make(map[string]BotConfig)}}, nil
		}
		return nil, err
	}
	s := string(data)
	cfg := &AppConfig{OneBot: OneBotConfig{Bots: make(map[string]BotConfig)}}

	cfg.LLM.ChoiceModel = getString(s, "llm.choice-model")

	for _, name := range []string{"google", "deep-seek", "minimax"} {
		p := LLMProvider{Name: name}
		p.APIKey = getString(s, fmt.Sprintf("llm.%s.api-key", name))
		p.BaseURL = getString(s, fmt.Sprintf("llm.%s.base-url", name))
		p.Models = make(map[string]string)
		// models block parsing is simplified
		cfg.LLM.Providers = append(cfg.LLM.Providers, p)
	}

	cfg.Embedding.Provider = getString(s, "embedding.provider")
	cfg.Embedding.APIKey = getString(s, "embedding.api-key")
	cfg.Search.Provider = getString(s, "search.provider")
	cfg.Search.APIKey = getString(s, "search.api-key")
	cfg.Browser.PlaywrightHost = getString(s, "browser.playwright-host")
	cfg.Browser.StatusHost = getString(s, "browser.status-host")
	cfg.Proxy.HTTP = getString(s, "proxy.http")
	cfg.Proxy.SOCKS = getString(s, "proxy.socks")

	// TODO: parse onebot bots and groups

	return cfg, nil
}

func SaveApp(path string, cfg *AppConfig) error {
	data, err := os.ReadFile(path)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	s := string(data)

	if cfg.LLM.ChoiceModel != "" {
		s = setString(s, "llm.choice-model", quote(cfg.LLM.ChoiceModel))
	}
	for _, p := range cfg.LLM.Providers {
		if p.APIKey != "" {
			s = setString(s, fmt.Sprintf("llm.%s.api-key", p.Name), quote(p.APIKey))
		}
		if p.BaseURL != "" {
			s = setString(s, fmt.Sprintf("llm.%s.base-url", p.Name), quote(p.BaseURL))
		}
	}

	if cfg.Embedding.Provider != "" {
		s = setString(s, "embedding.provider", quote(cfg.Embedding.Provider))
	}
	if cfg.Embedding.APIKey != "" {
		s = setString(s, "embedding.api-key", quote(cfg.Embedding.APIKey))
	}
	if cfg.Search.Provider != "" {
		s = setString(s, "search.provider", quote(cfg.Search.Provider))
	}
	if cfg.Search.APIKey != "" {
		s = setString(s, "search.api-key", quote(cfg.Search.APIKey))
	}
	if cfg.Browser.PlaywrightHost != "" {
		s = setString(s, "browser.playwright-host", quote(cfg.Browser.PlaywrightHost))
	}
	if cfg.Browser.StatusHost != "" {
		s = setString(s, "browser.status-host", quote(cfg.Browser.StatusHost))
	}
	if cfg.Proxy.HTTP != "" {
		s = setString(s, "proxy.http", quote(cfg.Proxy.HTTP))
	}
	if cfg.Proxy.SOCKS != "" {
		s = setString(s, "proxy.socks", quote(cfg.Proxy.SOCKS))
	}

	return os.WriteFile(path, []byte(s), 0644)
}

func getString(data, fieldPath string) string {
	escaped := regexp.QuoteMeta(fieldPath)
	re := regexp.MustCompile(`(?m)` + escaped + `\s*=\s*"?([^"\n]+)"?`)
	matches := re.FindStringSubmatch(data)
	if len(matches) < 2 {
		return ""
	}
	v := strings.TrimSpace(matches[1])
	if strings.HasPrefix(v, "${") {
		return ""
	}
	return v
}

func setString(data, fieldPath, value string) string {
	escaped := regexp.QuoteMeta(fieldPath)
	bareRe := regexp.MustCompile(`(?m)` + escaped + `\s*=\s*[^\n]+`)
	if bareRe.MatchString(data) {
		return bareRe.ReplaceAllString(data, fieldPath+" = "+value)
	}
	return data
}

func quote(s string) string {
	return fmt.Sprintf("\"%s\"", s)
}
