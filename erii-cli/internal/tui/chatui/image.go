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

const (
	historyImageMaxCols = 40
	historyImageMaxRows = 12
)

type historyImageRender struct {
	content string
	cols    int
	rows    int
}

func renderHistoryImage(data []byte, availableCols int) (historyImageRender, error) {
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return historyImageRender{}, fmt.Errorf("decode image: %w", err)
	}

	cols, rows := historyImageSize(img.Bounds().Dx(), img.Bounds().Dy(), availableCols)
	model := picture.NewWithConfig(picture.Config{Fit: picture.FitContain})
	_ = model.SetImage(img)
	_ = model.SetSize(cols, rows)
	content := model.String()
	if content == "" {
		return historyImageRender{}, fmt.Errorf("render image: empty output")
	}
	if strings.Contains(content, "\x1b_G") {
		return historyImageRender{}, fmt.Errorf("render image: unexpected kitty sequence")
	}
	return historyImageRender{content: content, cols: cols, rows: rows}, nil
}

func historyImageSize(pixelWidth, pixelHeight, availableCols int) (int, int) {
	cols := min(historyImageMaxCols, max(1, availableCols))
	if pixelWidth <= 0 || pixelHeight <= 0 {
		return cols, 1
	}

	// A terminal cell is approximately twice as tall as it is wide.
	rows := int(math.Ceil(float64(cols*pixelHeight) / float64(pixelWidth*2)))
	if rows > historyImageMaxRows {
		rows = historyImageMaxRows
		cols = int(math.Ceil(float64(rows*pixelWidth*2) / float64(pixelHeight)))
		cols = min(cols, min(historyImageMaxCols, max(1, availableCols)))
	}
	return max(1, cols), max(1, rows)
}
