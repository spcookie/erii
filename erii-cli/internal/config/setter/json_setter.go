package setter

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

// JSONSet sets a value at keyPath in a JSON file, creating intermediate objects as needed.
func JSONSet(filePath, keyPath, rawValue string) error {
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			data = []byte("{}")
		} else {
			return fmt.Errorf("read json: %w", err)
		}
	}

	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		m = make(map[string]any)
	}

	v, err := ParseJSONValue(rawValue)
	if err != nil {
		return fmt.Errorf("parse value: %w", err)
	}

	keys := strings.Split(keyPath, ".")
	setJSONPath(m, keys, v)

	output, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal json: %w", err)
	}
	output = append(output, '\n')
	return os.WriteFile(filePath, output, 0644)
}

// JSONGet retrieves the value at keyPath from a JSON file.
func JSONGet(filePath, keyPath string) (string, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return "", fmt.Errorf("read json: %w", err)
	}

	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		return "", fmt.Errorf("parse json: %w", err)
	}

	v := findJSONPath(m, strings.Split(keyPath, "."))
	if v == nil {
		return "", fmt.Errorf("key not found: %s", keyPath)
	}
	return formatJSONValue(v), nil
}

func setJSONPath(m map[string]any, keys []string, v any) {
	for i := 0; i < len(keys)-1; i++ {
		existing, ok := m[keys[i]]
		if ok {
			if child, isMap := existing.(map[string]any); isMap {
				m = child
				continue
			}
		}
		newObj := make(map[string]any)
		m[keys[i]] = newObj
		m = newObj
	}
	m[keys[len(keys)-1]] = v
}

func findJSONPath(m map[string]any, keys []string) any {
	for i := 0; i < len(keys)-1; i++ {
		child, ok := m[keys[i]]
		if !ok {
			return nil
		}
		childMap, ok := child.(map[string]any)
		if !ok {
			return nil
		}
		m = childMap
	}
	return m[keys[len(keys)-1]]
}

func formatJSONValue(v any) string {
	switch val := v.(type) {
	case string:
		return val
	case bool:
		return fmt.Sprintf("%v", val)
	case float64:
		return fmt.Sprintf("%v", val)
	case nil:
		return "null"
	case map[string]any:
		b, _ := json.Marshal(val)
		return string(b)
	case []any:
		b, _ := json.Marshal(val)
		return string(b)
	default:
		return fmt.Sprintf("%v", v)
	}
}
