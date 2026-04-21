package config

import (
	"bufio"
	"fmt"
	"os"
	"strings"
)

type EnvConfig struct {
	Vars map[string]string
}

func LoadEnv(path string) (*EnvConfig, error) {
	cfg := &EnvConfig{Vars: make(map[string]string)}
	f, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return cfg, nil
		}
		return nil, err
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx == -1 {
			continue
		}
		key := strings.TrimSpace(line[:idx])
		val := strings.TrimSpace(line[idx+1:])
		cfg.Vars[key] = val
	}
	return cfg, scanner.Err()
}

func SaveEnv(path string, cfg *EnvConfig) error {
	var lines []string
	var written = make(map[string]bool)

	if data, err := os.ReadFile(path); err == nil {
		for _, line := range strings.Split(string(data), "\n") {
			trimmed := strings.TrimSpace(line)
			if trimmed == "" || strings.HasPrefix(trimmed, "#") {
				lines = append(lines, line)
				continue
			}
			idx := strings.IndexByte(trimmed, '=')
			if idx == -1 {
				lines = append(lines, line)
				continue
			}
			key := strings.TrimSpace(trimmed[:idx])
			if val, ok := cfg.Vars[key]; ok {
				lines = append(lines, fmt.Sprintf("%s=%s", key, val))
				written[key] = true
			} else {
				lines = append(lines, line)
			}
		}
	}

	for k, v := range cfg.Vars {
		if !written[k] {
			lines = append(lines, fmt.Sprintf("%s=%s", k, v))
		}
	}

	return os.WriteFile(path, []byte(strings.Join(lines, "\n")+"\n"), 0644)
}
