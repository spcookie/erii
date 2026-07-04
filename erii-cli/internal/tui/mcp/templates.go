package mcp

import "encoding/json"

func NewTemplate(transport string) []byte {
	return NewTemplateWithName(transport, "example")
}

func NewTemplateWithName(transport, name string) []byte {
	if name == "" {
		name = "example"
	}
	var payload map[string]any
	switch transport {
	case "sse", "streamable_http", "websocket":
		payload = map[string]any{
			"enabled":   true,
			"transport": transport,
			"name":      name,
			"url":       "",
			"headers":   map[string]any{},
		}
	default:
		payload = map[string]any{
			"enabled":   true,
			"transport": "stdio",
			"name":      name,
			"command":   "",
			"args":      []any{},
			"env":       map[string]any{},
			"cwd":       ".",
		}
	}
	data, _ := json.MarshalIndent(payload, "", "  ")
	return append(data, '\n')
}
