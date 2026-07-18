package chatui

import (
	"bytes"
	"encoding/base64"
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

	"github.com/NimbleMarkets/ntcharts/v2/picture"
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

func TestExtractCQImageFindsURLAndRemovesSegment(t *testing.T) {
	cleaned, imageURL, ok := extractCQImage("look [CQ:image,file=https://example.com/fallback.jpg,url=https://example.com/cat.jpg] here")
	if !ok {
		t.Fatal("expected CQ image to be detected")
	}
	if cleaned != "look here" {
		t.Fatalf("cleaned content = %q", cleaned)
	}
	if imageURL != "https://example.com/cat.jpg" {
		t.Fatalf("image URL = %q", imageURL)
	}
}

func TestExtractCQImageFallsBackToFileURL(t *testing.T) {
	cleaned, imageURL, ok := extractCQImage("[CQ:image,file=https://example.com/fallback.jpg]")
	if !ok {
		t.Fatal("expected CQ image to be detected")
	}
	if cleaned != "" {
		t.Fatalf("cleaned content = %q", cleaned)
	}
	if imageURL != "https://example.com/fallback.jpg" {
		t.Fatalf("image URL = %q", imageURL)
	}
}

func TestExtractCQImageAcceptsBase64File(t *testing.T) {
	source := "base64://" + base64.StdEncoding.EncodeToString(testPNG(t))
	cleaned, imageSource, ok := extractCQImage("seedream [CQ:image,file=" + source + "]")
	if !ok {
		t.Fatal("expected base64 CQ image to be detected")
	}
	if cleaned != "seedream" {
		t.Fatalf("cleaned content = %q", cleaned)
	}
	if imageSource != source {
		t.Fatalf("image source was not preserved")
	}
}

func TestExtractCQImageAcceptsDataURLFile(t *testing.T) {
	source := "data:image/png;base64," + base64.StdEncoding.EncodeToString(testPNG(t))
	cleaned, imageSource, ok := extractCQImage("seedream [CQ:image,file=" + source + "]")
	if !ok {
		t.Fatal("expected data URL CQ image to be detected")
	}
	if cleaned != "seedream" {
		t.Fatalf("cleaned content = %q", cleaned)
	}
	if imageSource != source {
		t.Fatalf("image source was not preserved")
	}
}

func TestParseCQImageRefDetectsHistoryImage(t *testing.T) {
	ref := parseCQImageRef("caption [CQ:image,file=erii-history://42,historyId=42]")
	if !ref.found {
		t.Fatal("expected history CQ image to be detected")
	}
	if ref.cleaned != "caption" {
		t.Fatalf("cleaned content = %q", ref.cleaned)
	}
	if ref.historyID != 42 {
		t.Fatalf("history id = %d, want 42", ref.historyID)
	}
	if ref.supported {
		t.Fatal("history image source should not be treated as a live image source")
	}
}

func TestExtractCQImageIgnoresHistoryImageForLiveRendering(t *testing.T) {
	text := "caption [CQ:image,file=erii-history://42,historyId=42]"
	cleaned, imageSource, ok := extractCQImage(text)
	if ok {
		t.Fatal("history image should not be accepted as a live image")
	}
	if cleaned != text {
		t.Fatalf("cleaned content = %q, want original", cleaned)
	}
	if imageSource != "" {
		t.Fatalf("image source = %q, want empty", imageSource)
	}
}

func TestHistoryLoadedUsesCQParsing(t *testing.T) {
	m := initialModel(nil, nil, "Erii", nil)
	m.viewport.Width = 80
	m.viewport.Height = 20

	updated, _ := m.handleHistoryLoaded(historyLoadedMsg{
		entries: []api.ChatHistoryEntry{{
			ID:        42,
			Sender:    "bot",
			Content:   "caption [CQ:image,file=erii-history://42,historyId=42]",
			Timestamp: time.Now().UnixMilli(),
			HasImage:  true,
		}},
	})
	next := updated.(*Model)
	if len(next.messages) != 1 {
		t.Fatalf("messages length = %d, want 1", len(next.messages))
	}
	msg := next.messages[0]
	if msg.content != "caption" {
		t.Fatalf("message content = %q", msg.content)
	}
	if !msg.hasImage {
		t.Fatal("expected history message to have image")
	}
	if msg.imageState != historyImageLoading {
		t.Fatalf("image state = %v, want loading", msg.imageState)
	}
}

func TestBotResponseWithCQImageQueuesLiveRender(t *testing.T) {
	m := initialModel(nil, nil, "Erii", make(chan tea.Msg))
	m.viewport.Width = 80
	m.viewport.Height = 20

	updated, cmd := m.Update(botResponseMsg{
		response: "cat [CQ:image,file=https://example.com/fallback.jpg,url=https://example.com/cat.jpg]",
	})
	next := updated.(*Model)
	if len(next.messages) != 1 {
		t.Fatalf("messages length = %d, want 1", len(next.messages))
	}
	message := next.messages[0]
	if !message.hasImage {
		t.Fatal("expected live bot message to have image")
	}
	if message.id >= 0 {
		t.Fatalf("live image message id = %d, want negative id", message.id)
	}
	if message.content != "cat" {
		t.Fatalf("message content = %q", message.content)
	}
	if message.imageState != historyImageLoading {
		t.Fatalf("image state = %v, want loading", message.imageState)
	}
	if cmd == nil {
		t.Fatal("expected live image render command")
	}
}

func TestLoadLiveImageCmdRendersBase64Image(t *testing.T) {
	source := "base64://" + base64.StdEncoding.EncodeToString(testPNG(t))
	m := initialModel(nil, nil, "Erii", make(chan tea.Msg))
	msg := m.loadLiveImageCmd(-1, source, 20)()
	result, ok := msg.(historyImageRenderedMsg)
	if !ok {
		t.Fatalf("message type = %T, want historyImageRenderedMsg", msg)
	}
	if result.err != nil {
		t.Fatalf("render error = %v", result.err)
	}
	if len(result.data) == 0 {
		t.Fatal("expected image data")
	}
	if result.rendered.content == "" {
		t.Fatal("expected rendered image content")
	}
}

func TestLoadLiveImageCmdRendersImage(t *testing.T) {
	imageData := testPNG(t)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(imageData)
	}))
	defer server.Close()

	m := initialModel(nil, nil, "Erii", make(chan tea.Msg))
	msg := m.loadLiveImageCmd(-1, server.URL, 20)()
	result, ok := msg.(historyImageRenderedMsg)
	if !ok {
		t.Fatalf("message type = %T, want historyImageRenderedMsg", msg)
	}
	if result.err != nil {
		t.Fatalf("render error = %v", result.err)
	}
	if result.id != -1 {
		t.Fatalf("message id = %d, want -1", result.id)
	}
	if len(result.data) == 0 {
		t.Fatal("expected image data")
	}
	if result.rendered.content == "" {
		t.Fatal("expected rendered image content")
	}
}

func TestLoadImageConfigFromEnv(t *testing.T) {
	t.Setenv("ERII_CHAT_IMAGE_MAX_COLS", "64")
	t.Setenv("ERII_CHAT_IMAGE_MAX_ROWS", "18")
	t.Setenv("ERII_CHAT_IMAGE_FIT", "cover")
	t.Setenv("ERII_CHAT_IMAGE_BACKGROUND", "#101820")
	t.Setenv("ERII_CHAT_IMAGE_MODE", "auto")

	cfg := LoadImageConfigFromEnv()
	if cfg.MaxCols != 64 {
		t.Fatalf("MaxCols = %d, want 64", cfg.MaxCols)
	}
	if cfg.MaxRows != 18 {
		t.Fatalf("MaxRows = %d, want 18", cfg.MaxRows)
	}
	if cfg.Fit != picture.FitCover {
		t.Fatalf("Fit = %v, want cover", cfg.Fit)
	}
	if cfg.Mode != ImageModeAuto {
		t.Fatalf("Mode = %q, want auto", cfg.Mode)
	}
	if got := color.RGBAModel.Convert(cfg.Background).(color.RGBA); got != (color.RGBA{R: 0x10, G: 0x18, B: 0x20, A: 0xff}) {
		t.Fatalf("Background = %#v", got)
	}
}

func TestLoadImageConfigAcceptsItermAlias(t *testing.T) {
	t.Setenv("ERII_CHAT_IMAGE_MODE", "iterm2")

	cfg := LoadImageConfigFromEnv()
	if cfg.Mode != ImageModeIterm {
		t.Fatalf("Mode = %q, want iterm", cfg.Mode)
	}
}

func TestHistoryImageSizeUsesConfigLimits(t *testing.T) {
	cols, rows := historyImageSizeWithConfig(160, 90, 120, ImageConfig{
		MaxCols: 64,
		MaxRows: 18,
	})
	if cols > 64 {
		t.Fatalf("cols = %d, want <= 64", cols)
	}
	if rows > 18 {
		t.Fatalf("rows = %d, want <= 18", rows)
	}
	if cols <= 40 {
		t.Fatalf("cols = %d, want config to allow wider than default", cols)
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
