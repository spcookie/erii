package setter

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"

	"github.com/gurkankaymak/hocon"
)

// ParseJSONValue parses a CLI string into a JSON-compatible value.
// Supports: boolean, null, number, array (comma-separated), substitution, unquoted string.
func ParseJSONValue(raw string) (any, error) {
	raw = strings.TrimSpace(raw)

	switch {
	case raw == "null":
		return nil, nil
	case isBoolTrue(raw):
		return true, nil
	case isBoolFalse(raw):
		return false, nil
	case len(raw) >= 2 && raw[0] == '[' && raw[len(raw)-1] == ']':
		return parseJSONArray(raw)
	case len(raw) >= 2 && raw[0] == '{' && raw[len(raw)-1] == '}':
		var m map[string]any
		if err := json.Unmarshal([]byte(raw), &m); err != nil {
			return nil, fmt.Errorf("invalid JSON object: %w", err)
		}
		return m, nil
	case len(raw) >= 2 && raw[0] == '"' && raw[len(raw)-1] == '"':
		return raw[1 : len(raw)-1], nil
	case isInt(raw):
		n, _ := strconv.ParseInt(raw, 10, 64)
		return float64(n), nil
	case isFloat(raw):
		n, _ := strconv.ParseFloat(raw, 64)
		return n, nil
	default:
		return raw, nil
	}
}

func parseJSONArray(raw string) ([]any, error) {
	inner := strings.TrimSpace(raw[1 : len(raw)-1])
	if inner == "" {
		return []any{}, nil
	}
	parts := splitComma(inner)
	var arr []any
	for _, p := range parts {
		p = strings.TrimSpace(p)
		v, err := ParseJSONValue(p)
		if err != nil {
			return nil, err
		}
		arr = append(arr, v)
	}
	return arr, nil
}

func splitComma(s string) []string {
	var parts []string
	depth := 0
	start := 0
	for i, ch := range s {
		switch ch {
		case '[', '{':
			depth++
		case ']', '}':
			depth--
		case ',':
			if depth == 0 {
				parts = append(parts, s[start:i])
				start = i + 1
			}
		}
	}
	parts = append(parts, s[start:])
	return parts
}

func isBoolTrue(s string) bool {
	switch strings.ToLower(s) {
	case "true", "yes", "on":
		return true
	}
	return false
}

func isBoolFalse(s string) bool {
	switch strings.ToLower(s) {
	case "false", "no", "off":
		return true
	}
	return false
}

func isInt(s string) bool {
	_, err := strconv.ParseInt(s, 10, 64)
	return err == nil
}

func isFloat(s string) bool {
	_, err := strconv.ParseFloat(s, 64)
	return err == nil && strings.ContainsAny(s, ".eE")
}

// ParseHOCONValue parses a CLI string into a hocon.Value.
func ParseHOCONValue(raw string) (hocon.Value, error) {
	raw = strings.TrimSpace(raw)

	switch {
	case raw == "null":
		return hocon.Null(raw), nil
	case isBoolTrue(raw):
		return hocon.Boolean(true), nil
	case isBoolFalse(raw):
		return hocon.Boolean(false), nil
	case len(raw) >= 2 && raw[0] == '[' && raw[len(raw)-1] == ']':
		return parseHOCONArray(raw)
	case len(raw) >= 2 && raw[0] == '"' && raw[len(raw)-1] == '"':
		return hocon.String(raw[1 : len(raw)-1]), nil
	case isSubstitution(raw):
		return parseHOCONSubstitution(raw), nil
	case isInt(raw):
		n, _ := strconv.Atoi(raw)
		return hocon.Int(n), nil
	case isFloat(raw):
		n, _ := strconv.ParseFloat(raw, 64)
		return hocon.Float64(n), nil
	default:
		return hocon.String(raw), nil
	}
}

func parseHOCONArray(raw string) (hocon.Array, error) {
	inner := strings.TrimSpace(raw[1 : len(raw)-1])
	if inner == "" {
		return hocon.Array{}, nil
	}
	parts := splitComma(inner)
	var arr hocon.Array
	for _, p := range parts {
		p = strings.TrimSpace(p)
		v, err := ParseHOCONValue(p)
		if err != nil {
			return nil, err
		}
		arr = append(arr, v)
	}
	return arr, nil
}

// subMarker prefixes hocon.String values that represent substitution references.
// go-hocon cannot construct *hocon.Substitution externally (unexported fields),
// and ParseString eagerly resolves substitutions (setting optional ones to nil).
// We store a marked string instead; writeHOCONValue checks for the marker and
// writes the raw ${VAR} / ${?VAR} unquoted.
const subMarker = "\x00HOCON_SUB:"

func parseHOCONSubstitution(raw string) hocon.Value {
	return hocon.String(subMarker + raw)
}

func isSubstitution(s string) bool {
	return strings.HasPrefix(s, "${") && strings.HasSuffix(s, "}")
}
