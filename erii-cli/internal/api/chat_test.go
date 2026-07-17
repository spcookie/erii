package api

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestGetChatHistoryParsesImageMetadata(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"entries":[{"id":42,"sender":"user","content":"caption","timestamp":1,"messageType":"IMAGE","hasImage":true}],"hasMore":false}`))
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	result, err := client.GetChatHistory(0, 50)
	if err != nil {
		t.Fatalf("GetChatHistory returned error: %v", err)
	}
	if len(result.Entries) != 1 || result.Entries[0].MessageType != "IMAGE" || !result.Entries[0].HasImage {
		t.Fatalf("unexpected history response: %+v", result)
	}
}

func TestGetChatHistoryImageUsesHistoryImageEndpoint(t *testing.T) {
	var gotURI string
	want := []byte{0x89, 'P', 'N', 'G'}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotURI = r.RequestURI
		_, _ = w.Write(want)
	}))
	defer server.Close()

	client := &Client{baseURL: server.URL, http: server.Client()}
	got, err := client.GetChatHistoryImage(42)
	if err != nil {
		t.Fatalf("GetChatHistoryImage returned error: %v", err)
	}
	if gotURI != "/api/chat/history/42/image" {
		t.Fatalf("uri = %q, want /api/chat/history/42/image", gotURI)
	}
	if string(got) != string(want) {
		t.Fatalf("image bytes = %v, want %v", got, want)
	}
}
