package chatui

import (
	"bytes"
	"fmt"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"math"
	"strings"

	"github.com/NimbleMarkets/ntcharts/v2/picture"
	_ "golang.org/x/image/webp"
)

type historyImageRender struct {
	content string
	cols    int
	rows    int
}

func renderHistoryImage(data []byte, availableCols int) (historyImageRender, error) {
	return renderHistoryImageWithConfig(data, availableCols, DefaultImageConfig())
}

func renderHistoryImageWithConfig(data []byte, availableCols int, cfg ImageConfig) (historyImageRender, error) {
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return historyImageRender{}, fmt.Errorf("decode image: %w", err)
	}

	cols, rows := historyImageSizeWithConfig(img.Bounds().Dx(), img.Bounds().Dy(), availableCols, cfg)
	model := picture.NewWithConfig(picture.Config{
		Fit:        cfg.Fit,
		Background: cfg.Background,
	})
	_ = model.SetSize(cols, rows)
	resolvedMode := resolveImageMode(cfg.Mode)
	content := renderPictureContent(model, img, cols, rows, resolvedMode)
	if content == "" {
		return historyImageRender{}, fmt.Errorf("render image: empty output")
	}
	if resolvedMode != ImageModeKitty && strings.Contains(content, "\x1b_G") {
		return historyImageRender{}, fmt.Errorf("render image: unexpected kitty sequence")
	}
	return historyImageRender{content: content, cols: cols, rows: rows}, nil
}

func renderPictureContent(model picture.Model, img image.Image, cols, rows int, mode ImageMode) string {
	resolvedMode := resolveImageMode(mode)
	switch resolvedMode {
	case ImageModeKitty:
		previous := picture.KittySupported()
		if previous != picture.KittyCapabilitySupported {
			picture.ForceKittyCapability(picture.KittyCapabilitySupported)
			defer picture.ForceKittyCapability(previous)
		}
		_ = model.Toggle()
		if cmd := model.SetImage(img); cmd != nil {
			if frame, ok := cmd().(picture.KittyFrameMsg); ok && frame.APC != "" && frame.Grid != "" {
				return frame.APC + frame.Grid
			}
		}
	}

	_ = model.SetImage(img)
	return model.String()
}

func historyImageSize(pixelWidth, pixelHeight, availableCols int) (int, int) {
	return historyImageSizeWithConfig(pixelWidth, pixelHeight, availableCols, DefaultImageConfig())
}

func historyImageSizeWithConfig(pixelWidth, pixelHeight, availableCols int, cfg ImageConfig) (int, int) {
	maxCols := max(1, cfg.MaxCols)
	maxRows := max(1, cfg.MaxRows)
	cols := min(maxCols, max(1, availableCols))
	if pixelWidth <= 0 || pixelHeight <= 0 {
		return cols, 1
	}

	// A terminal cell is approximately twice as tall as it is wide.
	rows := int(math.Ceil(float64(cols*pixelHeight) / float64(pixelWidth*2)))
	if rows > maxRows {
		rows = maxRows
		cols = int(math.Ceil(float64(rows*pixelWidth*2) / float64(pixelHeight)))
		cols = min(cols, min(maxCols, max(1, availableCols)))
	}
	return max(1, cols), max(1, rows)
}
