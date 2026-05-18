package setup

import (
	"bufio"
	"fmt"
	"os"
	"regexp"
	"strings"
)

// modifyConfig reads the existing application.conf, updates configured values in-place,
// and writes back. Unconfigured keys are left unchanged.
func modifyConfig(d *SetupData, filePath string) error {
	f, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("cannot open config: %w", err)
	}

	var lines []string
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		lines = append(lines, scanner.Text())
	}
	f.Close()

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("cannot read config: %w", err)
	}

	ctx := pathStack{}
	prov := d.Providers[d.SelectedProv]
	provKey := prov.Key
	choiceModel := hoconKeyToChoiceModel(prov.Key)

	for i := 0; i < len(lines); i++ {
		line := lines[i]
		trimmed := strings.TrimSpace(line)

		if isBlockStart(trimmed) && !strings.ContainsRune(trimmed, '}') {
			blockName := trimmed
			blockName = strings.TrimSuffix(blockName, " = {")
			blockName = strings.TrimSuffix(blockName, " ={")
			blockName = strings.TrimSuffix(blockName, " {")
			blockName = strings.TrimSpace(blockName)
			ctx.push(blockName)
		}

		if isBlockEnd(trimmed) && len(ctx) > 0 {
			ctx.pop()
		}

		// llm.choice-provider
		if ctx.match("llm") && isKey(trimmed, "choice-provider") {
			lines[i] = replaceHoconValue(line, choiceModel)
		}

		// Provider-specific llm blocks
		if ctx.match("llm", provKey) {
			if isKey(trimmed, "api-key") && d.APIKey != "" {
				lines[i] = replaceHoconValue(line, d.APIKey)
			}
			if isKey(trimmed, "base-url") && d.BaseURL != "" {
				lines[i] = replaceHoconValue(line, d.BaseURL)
			}
		}

		// Provider models
		if ctx.match("llm", provKey, "models") {
			if d.ModelMode == "all" && d.AllModel != "" {
				if isKey(trimmed, "lite") {
					lines[i] = replaceHoconValue(line, d.AllModel)
				}
				if isKey(trimmed, "flash") {
					lines[i] = replaceHoconValue(line, d.AllModel)
				}
				if isKey(trimmed, "pro") {
					lines[i] = replaceHoconValue(line, d.AllModel)
				}
			} else {
				if isKey(trimmed, "lite") && d.LiteModel != "" {
					lines[i] = replaceHoconValue(line, d.LiteModel)
				}
				if isKey(trimmed, "flash") && d.FlashModel != "" {
					lines[i] = replaceHoconValue(line, d.FlashModel)
				}
				if isKey(trimmed, "pro") && d.ProModel != "" {
					lines[i] = replaceHoconValue(line, d.ProModel)
				}
			}
		}

		// embedding
		if d.EmbeddingEnabled {
			if ctx.match("embedding") {
				if isKey(trimmed, "api-key") && d.EmbeddingAPIKey != "" {
					lines[i] = replaceHoconValue(line, d.EmbeddingAPIKey)
				}
				if isKey(trimmed, "url") && d.EmbeddingURL != "" {
					lines[i] = replaceHoconValue(line, d.EmbeddingURL)
				}
				if isKey(trimmed, "provider") && d.EmbeddingProvider != "" {
					lines[i] = replaceHoconValue(line, d.EmbeddingProvider)
				}
				if isKey(trimmed, "model") && d.EmbeddingModel != "" {
					lines[i] = replaceHoconValue(line, d.EmbeddingModel)
				}
			}
		}

		// search
		if d.SearchEnabled {
			if ctx.match("search") {
				if isKey(trimmed, "api-key") && d.SearchAPIKey != "" {
					lines[i] = replaceHoconValue(line, d.SearchAPIKey)
				}
				if isKey(trimmed, "url") && d.SearchURL != "" {
					lines[i] = replaceHoconValue(line, d.SearchURL)
				}
				if isKey(trimmed, "provider") && d.SearchProvider != "" {
					lines[i] = replaceHoconValue(line, d.SearchProvider)
				}
			}
		}

		// vision
		if d.VisionEnabled {
			if ctx.match("vision") {
				if isKey(trimmed, "api-key") && d.VisionAPIKey != "" {
					lines[i] = replaceHoconValue(line, d.VisionAPIKey)
				}
				if isKey(trimmed, "url") && d.VisionURL != "" {
					lines[i] = replaceHoconValue(line, d.VisionURL)
				}
				if isKey(trimmed, "provider") && d.VisionProvider != "" {
					lines[i] = replaceHoconValue(line, d.VisionProvider)
				}
			}
		}

		// browser
		if d.BrowserEnabled {
			if ctx.match("browser") {
				if isKey(trimmed, "download") {
					lines[i] = replaceHoconValue(line, fmt.Sprintf("%v", d.BrowserDownload))
				}
				if isKey(trimmed, "playwright-url") && d.PlaywrightURL != "" {
					lines[i] = replaceHoconValue(line, d.PlaywrightURL)
				}
				if isKey(trimmed, "status-host") && d.StatusHost != "" {
					lines[i] = replaceHoconValue(line, d.StatusHost)
				}
			}
		}

		// proxy
		if d.ProxyEnabled {
			if ctx.match("proxy") {
				if isKey(trimmed, "http") && d.HTTPProxy != "" {
					lines[i] = replaceHoconValue(line, d.HTTPProxy)
				}
				if isKey(trimmed, "socks") && d.SOCKSProxy != "" {
					lines[i] = replaceHoconValue(line, d.SOCKSProxy)
				}
			}
		}

		// onebot.bots.erii
		if ctx.match("onebot", "bots", "erii") {
			if isKey(trimmed, "ws") && d.BotWS != "" {
				lines[i] = replaceHoconValue(line, d.BotWS)
			}
			if isKey(trimmed, "token") && d.BotToken != "" {
				lines[i] = replaceHoconValue(line, d.BotToken)
			}
		}

		// groups
		if ctx.match("groups") {
			if isKey(trimmed, "debug-group-id") {
				if d.DebugGroupID != "" {
					lines[i] = replaceHoconValue(line, d.DebugGroupID)
				} else {
					lines[i] = replaceHoconValue(line, "null")
				}
			}
			if isKey(trimmed, "enable-groups") {
				if d.EnableGroups != "" {
					lines[i] = replaceHoconArray(line, d.EnableGroups)
				}
			}
			if isKey(trimmed, "message-redirect-map") {
				if d.MessageRedirectMap != "" {
					lines[i] = replaceHoconArray(line, d.MessageRedirectMap)
				}
			}
		}
	}

	out, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("cannot write config: %w", err)
	}
	defer out.Close()

	for _, line := range lines {
		fmt.Fprintln(out, line)
	}
	return nil
}

// ---- Lightweight HOCON path tracking ----

type pathStack []string

func (p *pathStack) push(name string) {
	*p = append(*p, name)
}

func (p *pathStack) pop() {
	if len(*p) > 0 {
		*p = (*p)[:len(*p)-1]
	}
}

func (p pathStack) match(path ...string) bool {
	if len(p) < len(path) {
		return false
	}
	for i, seg := range path {
		if p[i] != seg {
			return false
		}
	}
	return true
}

// ---- Helpers ----

var (
	blockStartRe = regexp.MustCompile(`^\s*[\w\-.*]+\s*(=\s*)?\{`)
	blockEndRe   = regexp.MustCompile(`^\s*}\s*$`)
	keyValueRe   = regexp.MustCompile(`^(\s*[\w\-.]+\s*=\s*)(.+)$`)
	arrayRe      = regexp.MustCompile(`^(\s*[\w\-.]+\s*=\s*)\[.*?\].*$`)
)

func isBlockStart(line string) bool {
	return blockStartRe.MatchString(line)
}

func isBlockEnd(line string) bool {
	return blockEndRe.MatchString(line)
}

func isKey(line string, key string) bool {
	trimmed := strings.TrimSpace(line)
	return strings.HasPrefix(trimmed, key+" ") || strings.HasPrefix(trimmed, key+"=")
}

func replaceHoconValue(line string, value string) string {
	matches := keyValueRe.FindStringSubmatch(line)
	if matches == nil {
		return line
	}
	// Preserve quoting if original value was quoted
	origValue := strings.TrimSpace(matches[2])
	if strings.HasPrefix(origValue, "\"") || strings.HasPrefix(origValue, "${") {
		return matches[1] + "\"" + value + "\""
	}
	return matches[1] + value
}

func replaceHoconArray(line string, csv string) string {
	matches := arrayRe.FindStringSubmatch(line)
	if matches == nil {
		return line
	}
	parts := strings.Split(csv, ",")
	elems := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			elems = append(elems, p)
		}
	}
	return fmt.Sprintf("%s[%s]", matches[1], strings.Join(elems, ", "))
}
