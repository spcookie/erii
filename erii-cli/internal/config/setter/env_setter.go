package setter

import (
	"bufio"
	"fmt"
	"os"
	"strings"
)

// EnvSet sets a key=value in an .env file, preserving comments and existing structure.
func EnvSet(filePath, key, rawValue string) error {
	value := strings.TrimSpace(rawValue)

	// Read existing file
	var lines []string
	var found bool
	if data, err := os.ReadFile(filePath); err == nil {
		scanner := bufio.NewScanner(strings.NewReader(string(data)))
		for scanner.Scan() {
			line := scanner.Text()
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
			existingKey := strings.TrimSpace(trimmed[:idx])
			if existingKey == key {
				lines = append(lines, fmt.Sprintf("%s=%s", key, value))
				found = true
			} else {
				lines = append(lines, line)
			}
		}
	}

	// Append new key if not found
	if !found {
		if len(lines) > 0 && lines[len(lines)-1] != "" {
			lines = append(lines, "")
		}
		lines = append(lines, fmt.Sprintf("%s=%s", key, value))
	}

	return os.WriteFile(filePath, []byte(strings.Join(lines, "\n")+"\n"), 0644)
}

// EnvGet retrieves the value for a key from an .env file.
func EnvGet(filePath, key string) (string, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return "", fmt.Errorf("key not found: %s", key)
		}
		return "", fmt.Errorf("read env: %w", err)
	}
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx == -1 {
			continue
		}
		if strings.TrimSpace(line[:idx]) == key {
			return strings.TrimSpace(line[idx+1:]), nil
		}
	}
	return "", fmt.Errorf("key not found: %s", key)
}
