package config

import (
	"fmt"
	"os"
	"regexp"
	"strconv"
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
	flat := flattenHOCON(string(data))
	cfg := &AppConfig{OneBot: OneBotConfig{Bots: make(map[string]BotConfig)}}

	cfg.LLM.ChoiceModel = unquote(flat["llm.choice-model"])

	for _, name := range []string{"google", "deep-seek", "minimax"} {
		p := LLMProvider{Name: name}
		p.APIKey = unquote(flat["llm."+name+".api-key"])
		p.BaseURL = unquote(flat["llm."+name+".base-url"])
		p.Models = loadModels(flat, "llm."+name+".models")
		cfg.LLM.Providers = append(cfg.LLM.Providers, p)
	}

	cfg.Embedding.Provider = unquote(flat["embedding.provider"])
	cfg.Embedding.APIKey = unquote(flat["embedding.api-key"])
	cfg.Search.Provider = unquote(flat["search.provider"])
	cfg.Search.APIKey = unquote(flat["search.api-key"])
	cfg.Browser.PlaywrightHost = unquote(flat["browser.playwright-host"])
	cfg.Browser.StatusHost = unquote(flat["browser.status-host"])
	cfg.Proxy.HTTP = unquote(flat["proxy.http"])
	cfg.Proxy.SOCKS = unquote(flat["proxy.socks"])

	cfg.OneBot.Bots = loadOneBotBots(flat, string(data))
	cfg.Groups = loadGroups(flat)

	return cfg, nil
}

func flattenHOCON(data string) map[string]string {
	result := make(map[string]string)
	stack := []string{}
	lines := strings.Split(data, "\n")

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}

		// Strip inline comments
		if idx := strings.IndexByte(trimmed, '#'); idx >= 0 {
			trimmed = strings.TrimSpace(trimmed[:idx])
		}
		if trimmed == "" {
			continue
		}

		// Block end
		if trimmed == "}" {
			if len(stack) > 0 {
				stack = stack[:len(stack)-1]
			}
			continue
		}

		// Block start: key {  or  key = {
		if strings.HasSuffix(trimmed, "{") {
			key := strings.TrimSpace(strings.TrimSuffix(trimmed, "{"))
			key = strings.TrimSuffix(key, "=")
			key = strings.TrimSpace(key)
			stack = append(stack, key)
			continue
		}

		// key = value
		if idx := strings.IndexByte(trimmed, '='); idx != -1 {
			key := strings.TrimSpace(trimmed[:idx])
			val := strings.TrimSpace(trimmed[idx+1:])
			path := strings.Join(append(stack, key), ".")
			result[path] = val
		}
	}
	return result
}

func unquote(s string) string {
	s = strings.TrimSpace(s)
	if strings.HasPrefix(s, "${") {
		return ""
	}
	s = strings.TrimPrefix(s, `"`)
	s = strings.TrimSuffix(s, `"`)
	return s
}

func loadModels(flat map[string]string, prefix string) map[string]string {
	models := make(map[string]string)
	for k, v := range flat {
		if strings.HasPrefix(k, prefix+".") {
			modelKey := strings.TrimPrefix(k, prefix+".")
			models[modelKey] = unquote(v)
		}
	}
	return models
}

func loadOneBotBots(flat map[string]string, raw string) map[string]BotConfig {
	bots := make(map[string]BotConfig)
	// Find all keys under onebot.bots
	for k, v := range flat {
		if !strings.HasPrefix(k, "onebot.bots.") {
			continue
		}
		rest := strings.TrimPrefix(k, "onebot.bots.")
		parts := strings.SplitN(rest, ".", 2)
		if len(parts) < 2 {
			continue
		}
		botName := parts[0]
		field := parts[1]
		bot, ok := bots[botName]
		if !ok {
			bot = BotConfig{Groups: make(map[int64]GroupSettings)}
		}
		switch field {
		case "ws":
			bot.WS = unquote(v)
		case "token":
			bot.Token = unquote(v)
		case "role-id":
			bot.RoleID = unquote(v)
		}
		bots[botName] = bot
	}
	if len(bots) == 0 {
		// Fallback: try regex parse from raw text
		return parseOneBotBotsRaw(raw)
	}
	return bots
}

func extractValue(block, key string) string {
	re := regexp.MustCompile(fmt.Sprintf(`%s\s*=\s*"?([^"\n]+)"?`, regexp.QuoteMeta(key)))
	matches := re.FindStringSubmatch(block)
	if len(matches) >= 2 {
		return strings.TrimSpace(matches[1])
	}
	return ""
}

func parseOneBotBotsRaw(data string) map[string]BotConfig {
	bots := make(map[string]BotConfig)
	re := regexp.MustCompile(`(?m)^\s*(\w+)\s*=\s*\{`)
	matches := re.FindAllStringSubmatchIndex(data, -1)
	for _, m := range matches {
		name := data[m[2]:m[3]]
		start := m[1]
		depth := 1
		end := start
		for i := start; i < len(data) && depth > 0; i++ {
			if data[i] == '{' {
				depth++
			} else if data[i] == '}' {
				depth--
				if depth == 0 {
					end = i
				}
			}
		}
		block := data[start:end]
		bot := BotConfig{
			WS:     extractValue(block, "ws"),
			Token:  extractValue(block, "token"),
			RoleID: extractValue(block, "role-id"),
			Groups: make(map[int64]GroupSettings),
		}
		bots[name] = bot
	}
	return bots
}

func loadGroups(flat map[string]string) GroupsConfig {
	g := GroupsConfig{}
	if v := flat["groups.debug-group-id"]; v != "" && v != "null" {
		if id, err := strconv.ParseInt(unquote(v), 10, 64); err == nil {
			g.DebugGroupID = &id
		}
	}
	g.EnableGroups = parseIntArray(flat["groups.enable-groups"])
	if v := flat["groups.message-redirect-map"]; v != "" {
		// Parse array format: ["a", "b"] or [a, b]
		g.MessageRedirectMap = parseStringArray(v)
	}
	return g
}

func parseIntArray(s string) []int64 {
	s = strings.TrimSpace(s)
	s = strings.TrimPrefix(s, "[")
	s = strings.TrimSuffix(s, "]")
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	var result []int64
	for _, p := range parts {
		p = strings.TrimSpace(p)
		p = strings.TrimPrefix(p, `"`)
		p = strings.TrimSuffix(p, `"`)
		if p == "" {
			continue
		}
		if v, err := strconv.ParseInt(p, 10, 64); err == nil {
			result = append(result, v)
		}
	}
	return result
}

func parseStringArray(s string) []string {
	s = strings.TrimSpace(s)
	s = strings.TrimPrefix(s, "[")
	s = strings.TrimSuffix(s, "]")
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	var result []string
	for _, p := range parts {
		p = strings.TrimSpace(p)
		p = strings.TrimPrefix(p, `"`)
		p = strings.TrimSuffix(p, `"`)
		if p == "" {
			continue
		}
		result = append(result, p)
	}
	return result
}

func SaveApp(path string, cfg *AppConfig) error {
	data, err := os.ReadFile(path)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	s := string(data)

	if cfg.LLM.ChoiceModel != "" {
		s = setString(s, "choice-model", quote(cfg.LLM.ChoiceModel))
	}
	for _, p := range cfg.LLM.Providers {
		if p.BaseURL != "" {
			s = setProviderField(s, p.Name, "base-url", quote(p.BaseURL))
		}
		for tier, model := range p.Models {
			s = setProviderModel(s, p.Name, tier, quote(model))
		}
	}

	if cfg.Embedding.Provider != "" {
		s = setString(s, "provider", quote(cfg.Embedding.Provider))
	}
	if cfg.Embedding.APIKey != "" {
		s = setString(s, "api-key", quote(cfg.Embedding.APIKey))
	}
	if cfg.Search.Provider != "" {
		s = setString(s, "provider", quote(cfg.Search.Provider))
	}
	if cfg.Search.APIKey != "" {
		s = setString(s, "api-key", quote(cfg.Search.APIKey))
	}
	if cfg.Browser.PlaywrightHost != "" {
		s = setString(s, "playwright-host", quote(cfg.Browser.PlaywrightHost))
	}
	if cfg.Browser.StatusHost != "" {
		s = setString(s, "status-host", quote(cfg.Browser.StatusHost))
	}
	if cfg.Proxy.HTTP != "" {
		s = setString(s, "http", quote(cfg.Proxy.HTTP))
	}
	if cfg.Proxy.SOCKS != "" {
		s = setString(s, "socks", quote(cfg.Proxy.SOCKS))
	}

	s = setOneBotBots(s, cfg.OneBot.Bots)

	return os.WriteFile(path, []byte(s), 0644)
}

func setString(data, fieldPath, value string) string {
	escaped := regexp.QuoteMeta(fieldPath)
	bareRe := regexp.MustCompile(`(?m)` + escaped + `\s*=\s*[^\n]+`)
	if bareRe.MatchString(data) {
		return bareRe.ReplaceAllString(data, fieldPath+" = "+value)
	}
	return data
}

// setProviderField replaces a field inside a provider block (e.g. llm.google.base-url).
func setProviderField(data, provider, field, value string) string {
	providerRe := regexp.MustCompile(`(?m)^\s*` + regexp.QuoteMeta(provider) + `\s*\{`)
	loc := providerRe.FindStringIndex(data)
	if loc == nil {
		return data
	}
	start := loc[1]
	depth := 1
	end := start
	for i := start; i < len(data) && depth > 0; i++ {
		if data[i] == '{' {
			depth++
		} else if data[i] == '}' {
			depth--
			if depth == 0 {
				end = i
			}
		}
	}
	block := data[start:end]
	fieldRe := regexp.MustCompile(`(?m)^(\s*` + regexp.QuoteMeta(field) + `\s*=\s*)[^\n]+`)
	if fieldRe.MatchString(block) {
		newBlock := fieldRe.ReplaceAllString(block, `${1}`+value)
		return data[:start] + newBlock + data[end:]
	}
	return data
}

// setProviderModel replaces a tier inside a provider's models block.
func setProviderModel(data, provider, tier, value string) string {
	providerRe := regexp.MustCompile(`(?m)^\s*` + regexp.QuoteMeta(provider) + `\s*\{`)
	loc := providerRe.FindStringIndex(data)
	if loc == nil {
		return data
	}
	start := loc[1]
	depth := 1
	end := start
	for i := start; i < len(data) && depth > 0; i++ {
		if data[i] == '{' {
			depth++
		} else if data[i] == '}' {
			depth--
			if depth == 0 {
				end = i
			}
		}
	}
	block := data[start:end]

	modelsRe := regexp.MustCompile(`(?m)^\s*models\s*\{`)
	modelsLoc := modelsRe.FindStringIndex(block)
	if modelsLoc == nil {
		return data
	}
	modelsStart := modelsLoc[1]
	modelsDepth := 1
	modelsEnd := modelsStart
	for i := modelsStart; i < len(block) && modelsDepth > 0; i++ {
		if block[i] == '{' {
			modelsDepth++
		} else if block[i] == '}' {
			modelsDepth--
			if modelsDepth == 0 {
				modelsEnd = i
			}
		}
	}
	modelsBlock := block[modelsStart:modelsEnd]

	tierRe := regexp.MustCompile(`(?m)^(\s*` + regexp.QuoteMeta(tier) + `\s*=\s*)[^\n]+`)
	if tierRe.MatchString(modelsBlock) {
		newModelsBlock := tierRe.ReplaceAllString(modelsBlock, `${1}`+value)
		newBlock := block[:modelsStart] + newModelsBlock + block[modelsEnd:]
		return data[:start] + newBlock + data[end:]
	}
	return data
}

func quote(s string) string {
	return fmt.Sprintf(`"%s"`, s)
}

func findMatchingBrace(data string, start int) int {
	depth := 1
	for i := start; i < len(data) && depth > 0; i++ {
		if data[i] == '{' {
			depth++
		} else if data[i] == '}' {
			depth--
			if depth == 0 {
				return i
			}
		}
	}
	return len(data)
}

func setOneBotBots(data string, bots map[string]BotConfig) string {
	onebotRe := regexp.MustCompile(`(?m)^\s*onebot\s*\{`)
	loc := onebotRe.FindStringIndex(data)
	if loc == nil {
		return data
	}
	obStart := loc[1]
	obEnd := findMatchingBrace(data, obStart)
	obBlock := data[obStart:obEnd]

	botsRe := regexp.MustCompile(`(?m)^\s*bots\s*=\s*\{`)
	botsLoc := botsRe.FindStringIndex(obBlock)
	if botsLoc == nil {
		return data
	}
	bbStart := botsLoc[1]
	bbEnd := findMatchingBrace(obBlock, bbStart)
	bbBlock := obBlock[bbStart:bbEnd]

	// Remove bots no longer in config
	type delRange struct{ start, end int }
	var toDelete []delRange
	existingBotRe := regexp.MustCompile(`(?m)^\s*(\w+)\s*=\s*\{`)
	for _, match := range existingBotRe.FindAllStringSubmatchIndex(bbBlock, -1) {
		name := bbBlock[match[2]:match[3]]
		if _, ok := bots[name]; !ok {
			bEnd := findMatchingBrace(bbBlock, match[1])
			toDelete = append(toDelete, delRange{match[0], bEnd + 1})
		}
	}
	for i := len(toDelete) - 1; i >= 0; i-- {
		bbBlock = bbBlock[:toDelete[i].start] + bbBlock[toDelete[i].end:]
	}

	for name, bot := range bots {
		botRe := regexp.MustCompile(`(?m)^\s*` + regexp.QuoteMeta(name) + `\s*=\s*\{`)
		botLoc := botRe.FindStringIndex(bbBlock)
		if botLoc == nil {
			newBot := formatBotBlock(name, bot)
			insertPos := len(bbBlock) - 1
			for insertPos > 0 && (bbBlock[insertPos] == '}' || bbBlock[insertPos] == ' ' || bbBlock[insertPos] == '\t' || bbBlock[insertPos] == '\n') {
				insertPos--
			}
			insertPos++
			bbBlock = bbBlock[:insertPos] + newBot + bbBlock[insertPos:]
		} else {
			bStart := botLoc[1]
			bEnd := findMatchingBrace(bbBlock, bStart)
			oldBlock := bbBlock[bStart:bEnd]
			newBlock := replaceBotFields(oldBlock, bot)
			bbBlock = bbBlock[:bStart] + newBlock + bbBlock[bEnd:]
		}
	}

	obBlock = obBlock[:bbStart] + bbBlock + obBlock[bbEnd:]
	return data[:obStart] + obBlock + data[obEnd:]
}

func formatBotBlock(name string, bot BotConfig) string {
	var b strings.Builder
	b.WriteString("\n    " + name + " = {\n")
	if bot.WS != "" {
		b.WriteString("      ws = " + quote(bot.WS) + "\n")
	}
	if bot.Token != "" {
		b.WriteString("      token = " + quote(bot.Token) + "\n")
	}
	if bot.RoleID != "" {
		b.WriteString("      role-id = " + quote(bot.RoleID) + "\n")
	}
	b.WriteString("    }")
	return b.String()
}

func replaceBotFields(block string, bot BotConfig) string {
	if bot.WS != "" {
		block = setFieldInBlock(block, "ws", quote(bot.WS))
	}
	if bot.Token != "" {
		block = setFieldInBlock(block, "token", quote(bot.Token))
	}
	if bot.RoleID != "" {
		block = setFieldInBlock(block, "role-id", quote(bot.RoleID))
	}
	return block
}

func setFieldInBlock(block, field, value string) string {
	re := regexp.MustCompile(`(?m)^(\s*` + regexp.QuoteMeta(field) + `\s*=\s*)[^\n]+`)
	if re.MatchString(block) {
		return re.ReplaceAllString(block, `${1}`+value)
	}
	lastBrace := strings.LastIndex(block, "}")
	if lastBrace >= 0 {
		return block[:lastBrace] + "      " + field + " = " + value + "\n" + block[lastBrace:]
	}
	return block + "      " + field + " = " + value + "\n"
}
