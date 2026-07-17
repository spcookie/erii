package chatui

import (
	"bytes"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"erii-cli/internal/api"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

func TestCleanHistoryImagePlaceholderPreservesCaption(t *testing.T) {
	if got := cleanHistoryImagePlaceholder("look [图片] here", true); got != "look here" {
		t.Fatalf("cleaned content = %q", got)
	}
	if got := cleanHistoryImagePlaceholder("literal [图片]", false); got != "literal [图片]" {
		t.Fatalf("non-image content changed to %q", got)
	}
}

func TestHistoryImageLoadingLimitsConcurrency(t *testing.T) {
	imageData := testPNG(t)
	release := make(chan struct{})
	started := make(chan struct{}, 8)
	var active atomic.Int32
	var peak atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		current := active.Add(1)
		defer active.Add(-1)
		for {
			previous := peak.Load()
			if current <= previous || peak.CompareAndSwap(previous, current) {
				break
			}
		}
		started <- struct{}{}
		<-release
		_, _ = w.Write(imageData)
	}))
	defer server.Close()

	client := api.NewClient(testServerPort(t, server.URL), "", "")
	m := initialModel(client, nil, "Erii", make(chan tea.Msg))
	done := make(chan struct{}, 6)
	for id := int64(1); id <= 6; id++ {
		cmd := m.loadHistoryImageCmd(id, 20)
		go func() {
			_ = cmd()
			done <- struct{}{}
		}()
	}

	for i := 0; i < 3; i++ {
		select {
		case <-started:
		case <-time.After(2 * time.Second):
			t.Fatal("timed out waiting for the first three image requests")
		}
	}
	select {
	case <-started:
		t.Fatal("more than three image requests ran concurrently")
	case <-time.After(100 * time.Millisecond):
	}
	close(release)
	for i := 0; i < 6; i++ {
		select {
		case <-done:
		case <-time.After(2 * time.Second):
			t.Fatal("timed out waiting for image requests to finish")
		}
	}
	if got := peak.Load(); got != 3 {
		t.Fatalf("peak concurrent requests = %d, want 3", got)
	}
}

func TestHistoryImageLoadPreservesScrollAnchor(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.viewport.Width = 50
	m.viewport.Height = 4
	m.loadingHistory = false
	m.messages = []chatMsg{{
		id:         1,
		typ:        msgBot,
		hasImage:   true,
		imageState: historyImageLoading,
	}}
	for id := int64(2); id <= 12; id++ {
		m.messages = append(m.messages, chatMsg{id: id, typ: msgBot, content: "message"})
	}
	m.viewport.SetContent(m.renderMessages())
	m.viewport.YOffset = 5
	oldOffset := m.viewport.YOffset
	oldHeight := lipgloss.Height(m.renderMessages())

	m.handleHistoryImageRendered(historyImageRenderedMsg{
		id:            1,
		rendered:      historyImageRender{content: "one\ntwo\nthree", cols: 10, rows: 3},
		availableCols: m.historyImageAvailableCols(),
	})

	delta := lipgloss.Height(m.renderMessages()) - oldHeight
	if got, want := m.viewport.YOffset, oldOffset+delta; got != want {
		t.Fatalf("viewport offset = %d, want %d", got, want)
	}
}

func testPNG(t *testing.T) []byte {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, 8, 4))
	for y := 0; y < 4; y++ {
		for x := 0; x < 8; x++ {
			img.Set(x, y, color.RGBA{R: 40, G: uint8(x * 20), B: uint8(y * 40), A: 255})
		}
	}
	var data bytes.Buffer
	if err := png.Encode(&data, img); err != nil {
		t.Fatal(err)
	}
	return data.Bytes()
}

func testServerPort(t *testing.T, rawURL string) int {
	t.Helper()
	parsed, err := url.Parse(rawURL)
	if err != nil {
		t.Fatal(err)
	}
	port, err := strconv.Atoi(parsed.Port())
	if err != nil {
		t.Fatal(err)
	}
	return port
}
