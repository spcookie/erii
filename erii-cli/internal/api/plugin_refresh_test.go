package api

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRefreshPluginsUsesAllPluginsEndpoint(t *testing.T) {
	var gotURI string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotURI = r.RequestURI
		_, _ = w.Write([]byte(`{"status":"ok","message":"plugins refreshed","refreshedPlugins":["demo"],"loadedExtensions":1,"failedPlugins":{}}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	if _, err := client.RefreshPlugins(""); err != nil {
		t.Fatalf("RefreshPlugins returned error: %v", err)
	}
	if gotURI != "/api/plugins/refresh" {
		t.Fatalf("uri = %q, want /api/plugins/refresh", gotURI)
	}
}

func TestRefreshPluginsUsesSpecificPluginEndpoint(t *testing.T) {
	var gotURI string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotURI = r.RequestURI
		_, _ = w.Write([]byte(`{"status":"ok","message":"plugin refreshed","requestedPluginId":"demo/plugin","refreshedPlugins":["demo/plugin"],"loadedExtensions":1,"failedPlugins":{}}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	if _, err := client.RefreshPlugins("demo/plugin"); err != nil {
		t.Fatalf("RefreshPlugins returned error: %v", err)
	}
	if gotURI != "/api/plugins/demo%2Fplugin/refresh" {
		t.Fatalf("uri = %q, want /api/plugins/demo%%2Fplugin/refresh", gotURI)
	}
}

func TestRefreshPluginsParsesErrorResponseBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"status":"error","message":"plugin refresh completed with failures","refreshedPlugins":["demo"],"loadedExtensions":1,"failedPlugins":{"demo":"boom"}}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	result, err := client.RefreshPlugins("")
	if err != nil {
		t.Fatalf("RefreshPlugins returned error: %v", err)
	}
	if result.HTTPStatus != http.StatusInternalServerError {
		t.Fatalf("HTTPStatus = %d, want %d", result.HTTPStatus, http.StatusInternalServerError)
	}
	if result.Status != "error" {
		t.Fatalf("Status = %q, want error", result.Status)
	}
	if result.FailedPlugins["demo"] != "boom" {
		t.Fatalf("failed plugin reason = %q, want boom", result.FailedPlugins["demo"])
	}
}

func TestRefreshConfigParsesResponseBody(t *testing.T) {
	var gotURI string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotURI = r.RequestURI
		_, _ = w.Write([]byte(`{"status":"ok","message":"config refreshed","reloaded":{"config":true,"roles":3,"rules":7,"mcp":2},"bots":{"added":["bot-a"],"removed":["bot-b","bot-c"],"reconnected":["bot-d","bot-e","bot-f"],"roleUpdated":["bot-g","bot-h","bot-i","bot-j"],"failed":["bot-k","bot-l","bot-m","bot-n","bot-o"]}}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	result, err := client.RefreshConfig()
	if err != nil {
		t.Fatalf("RefreshConfig returned error: %v", err)
	}
	if gotURI != "/api/config/refresh" {
		t.Fatalf("uri = %q, want /api/config/refresh", gotURI)
	}
	if result.Reloaded.Roles != 3 || result.Reloaded.Rules != 7 || result.Reloaded.MCP != 2 {
		t.Fatalf("reloaded = %+v, want roles/rules/mcp 3/7/2", result.Reloaded)
	}
	if result.Bots.Added.Count() != 1 || result.Bots.Removed.Count() != 2 || result.Bots.Reconnected.Count() != 3 || result.Bots.RoleUpdated.Count() != 4 || result.Bots.Failed.Count() != 5 {
		t.Fatalf("bots = %+v, want counts 1/2/3/4/5", result.Bots)
	}
	if result.Bots.Reconnected[0] != "bot-d" {
		t.Fatalf("first reconnected bot = %q, want bot-d", result.Bots.Reconnected[0])
	}
}

func TestRefreshConfigAcceptsLegacyBotCounts(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"status":"ok","message":"config refreshed","reloaded":{"config":true,"roles":3,"rules":7,"mcp":2},"bots":{"added":1,"removed":2,"reconnected":3,"roleUpdated":4,"failed":5}}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	result, err := client.RefreshConfig()
	if err != nil {
		t.Fatalf("RefreshConfig returned error: %v", err)
	}
	if result.Bots.Added.Count() != 1 || result.Bots.Failed.Count() != 5 {
		t.Fatalf("bots = %+v, want legacy counts accepted", result.Bots)
	}
}
