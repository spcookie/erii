package chatui

import (
	"bytes"
	"image"
	"image/color"
	"image/png"
	"strings"
	"testing"
)

func TestHistoryImageSizePreservesBounds(t *testing.T) {
	for _, tc := range []struct {
		name          string
		width, height int
		available     int
	}{
		{name: "landscape", width: 800, height: 400, available: 80},
		{name: "portrait", width: 400, height: 1200, available: 80},
		{name: "narrow terminal", width: 800, height: 400, available: 18},
	} {
		t.Run(tc.name, func(t *testing.T) {
			cols, rows := historyImageSize(tc.width, tc.height, tc.available)
			if cols < 1 || cols > historyImageMaxCols || cols > tc.available {
				t.Fatalf("cols = %d", cols)
			}
			if rows < 1 || rows > historyImageMaxRows {
				t.Fatalf("rows = %d", rows)
			}
		})
	}
}

func TestRenderHistoryImageUsesGlyphOutput(t *testing.T) {
	data := encodedTestImage(t)

	rendered, err := renderHistoryImage(data, 24)
	if err != nil {
		t.Fatalf("renderHistoryImage returned error: %v", err)
	}
	if rendered.content == "" || rendered.cols > 24 || rendered.rows > historyImageMaxRows {
		t.Fatalf("unexpected render: %+v", rendered)
	}
	if strings.Contains(rendered.content, "\x1b_G") {
		t.Fatal("glyph renderer emitted a Kitty graphics sequence")
	}
}

func TestRenderHistoryImageKittyModeOptIn(t *testing.T) {
	data := encodedTestImage(t)

	rendered, err := renderHistoryImageWithConfig(data, 24, ImageConfig{
		MaxCols: historyImageMaxCols,
		MaxRows: historyImageMaxRows,
		Mode:    ImageModeKitty,
	})
	if err != nil {
		t.Fatalf("renderHistoryImageWithConfig returned error: %v", err)
	}
	if !strings.Contains(rendered.content, "\x1b_G") {
		t.Fatal("kitty renderer did not emit a Kitty graphics sequence")
	}
}

func TestRenderHistoryImageItermModeFallsBackToGlyph(t *testing.T) {
	data := encodedTestImage(t)

	rendered, err := renderHistoryImageWithConfig(data, 24, ImageConfig{
		MaxCols: historyImageMaxCols,
		MaxRows: historyImageMaxRows,
		Mode:    ImageModeIterm,
	})
	if err != nil {
		t.Fatalf("renderHistoryImageWithConfig returned error: %v", err)
	}
	if strings.Contains(rendered.content, "\x1b]1337;File=") {
		t.Fatal("iterm mode should fall back instead of emitting unsupported inline image sequences")
	}
	if strings.Contains(rendered.content, "\x1b_G") {
		t.Fatal("iterm fallback emitted a Kitty graphics sequence")
	}
}

func TestRenderHistoryImageAutoFallsBackToGlyphInIterm(t *testing.T) {
	t.Setenv("TERM_PROGRAM", "iTerm.app")
	data := encodedTestImage(t)

	rendered, err := renderHistoryImageWithConfig(data, 24, ImageConfig{
		MaxCols: historyImageMaxCols,
		MaxRows: historyImageMaxRows,
		Mode:    ImageModeAuto,
	})
	if err != nil {
		t.Fatalf("renderHistoryImageWithConfig returned error: %v", err)
	}
	if strings.Contains(rendered.content, "\x1b]1337;File=") || strings.Contains(rendered.content, "\x1b_G") {
		t.Fatal("auto renderer should use ntcharts glyph fallback in iTerm")
	}
}

func TestRenderHistoryImageAutoFallsBackToGlyphWithItermSessionID(t *testing.T) {
	t.Setenv("ITERM_SESSION_ID", "w0t0p0:12345678-1234-1234-1234-123456789abc")
	data := encodedTestImage(t)

	rendered, err := renderHistoryImageWithConfig(data, 24, ImageConfig{
		MaxCols: historyImageMaxCols,
		MaxRows: historyImageMaxRows,
		Mode:    ImageModeAuto,
	})
	if err != nil {
		t.Fatalf("renderHistoryImageWithConfig returned error: %v", err)
	}
	if strings.Contains(rendered.content, "\x1b]1337;File=") || strings.Contains(rendered.content, "\x1b_G") {
		t.Fatal("auto renderer should use ntcharts glyph fallback with ITERM_SESSION_ID")
	}
}

func encodedTestImage(t *testing.T) []byte {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, 8, 4))
	for y := 0; y < 4; y++ {
		for x := 0; x < 8; x++ {
			img.Set(x, y, color.RGBA{R: uint8(x * 20), G: uint8(y * 40), B: 180, A: 255})
		}
	}
	var data bytes.Buffer
	if err := png.Encode(&data, img); err != nil {
		t.Fatal(err)
	}
	return data.Bytes()
}
