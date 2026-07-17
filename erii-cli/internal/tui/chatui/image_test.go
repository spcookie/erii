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

	rendered, err := renderHistoryImage(data.Bytes(), 24)
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
