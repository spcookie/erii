package mcp

import (
	"encoding/json"
	"testing"
)

func TestStdioTemplateShape(t *testing.T) {
	raw := NewTemplate("stdio")

	var parsed map[string]any
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("stdio template is invalid JSON: %v", err)
	}

	if parsed["enabled"] != true {
		t.Fatalf("enabled = %v, want true", parsed["enabled"])
	}
	if parsed["transport"] != "stdio" {
		t.Fatalf("transport = %v, want stdio", parsed["transport"])
	}
	if _, ok := parsed["command"].(string); !ok {
		t.Fatalf("command missing or not string: %#v", parsed["command"])
	}
	if _, ok := parsed["args"].([]any); !ok {
		t.Fatalf("args missing or not array: %#v", parsed["args"])
	}
	if _, ok := parsed["env"].(map[string]any); !ok {
		t.Fatalf("env missing or not object: %#v", parsed["env"])
	}
	if _, ok := parsed["cwd"].(string); !ok {
		t.Fatalf("cwd missing or not string: %#v", parsed["cwd"])
	}
}

func TestSseTemplateShape(t *testing.T) {
	raw := NewTemplate("sse")

	var parsed map[string]any
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("sse template is invalid JSON: %v", err)
	}

	if parsed["enabled"] != true {
		t.Fatalf("enabled = %v, want true", parsed["enabled"])
	}
	if parsed["transport"] != "sse" {
		t.Fatalf("transport = %v, want sse", parsed["transport"])
	}
	if _, ok := parsed["url"].(string); !ok {
		t.Fatalf("url missing or not string: %#v", parsed["url"])
	}
	if _, ok := parsed["headers"].(map[string]any); !ok {
		t.Fatalf("headers missing or not object: %#v", parsed["headers"])
	}
}

func TestStreamableHTTPTemplateShape(t *testing.T) {
	raw := NewTemplate("streamable_http")
	assertRemoteTemplateShape(t, raw, "streamable_http")
}

func TestWebSocketTemplateShape(t *testing.T) {
	raw := NewTemplate("websocket")
	assertRemoteTemplateShape(t, raw, "websocket")
}

func TestTemplateUsesProvidedName(t *testing.T) {
	raw := NewTemplateWithName("stdio", "test")

	var parsed map[string]any
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("template is invalid JSON: %v", err)
	}

	if parsed["name"] != "test" {
		t.Fatalf("name = %v, want test", parsed["name"])
	}
}

func assertRemoteTemplateShape(t *testing.T, raw []byte, transport string) {
	t.Helper()

	var parsed map[string]any
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("%s template is invalid JSON: %v", transport, err)
	}

	if parsed["enabled"] != true {
		t.Fatalf("enabled = %v, want true", parsed["enabled"])
	}
	if parsed["transport"] != transport {
		t.Fatalf("transport = %v, want %s", parsed["transport"], transport)
	}
	if _, ok := parsed["url"].(string); !ok {
		t.Fatalf("url missing or not string: %#v", parsed["url"])
	}
	if _, ok := parsed["headers"].(map[string]any); !ok {
		t.Fatalf("headers missing or not object: %#v", parsed["headers"])
	}
}
