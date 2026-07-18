package chatui

import (
	"image/color"
	"os"
	"strconv"
	"strings"

	"github.com/NimbleMarkets/ntcharts/v2/picture"
)

const (
	defaultHistoryImageMaxCols = 40
	defaultHistoryImageMaxRows = 12
	historyImageMaxCols        = defaultHistoryImageMaxCols
	historyImageMaxRows        = defaultHistoryImageMaxRows
)

type ImageMode string

const (
	ImageModeGlyph ImageMode = "glyph"
	ImageModeKitty ImageMode = "kitty"
	ImageModeIterm ImageMode = "iterm"
	ImageModeAuto  ImageMode = "auto"
)

type ImageConfig struct {
	MaxCols    int
	MaxRows    int
	Fit        picture.FitMode
	Background color.Color
	Mode       ImageMode
}

func DefaultImageConfig() ImageConfig {
	return ImageConfig{
		MaxCols:    defaultHistoryImageMaxCols,
		MaxRows:    defaultHistoryImageMaxRows,
		Fit:        picture.FitContain,
		Background: color.Transparent,
		Mode:       ImageModeGlyph,
	}
}

func LoadImageConfigFromEnv() ImageConfig {
	cfg := DefaultImageConfig()
	cfg = cfg.WithMaxCols(parsePositiveInt(os.Getenv("ERII_CHAT_IMAGE_MAX_COLS"), cfg.MaxCols))
	cfg = cfg.WithMaxRows(parsePositiveInt(os.Getenv("ERII_CHAT_IMAGE_MAX_ROWS"), cfg.MaxRows))
	cfg = cfg.WithFit(os.Getenv("ERII_CHAT_IMAGE_FIT"))
	cfg = cfg.WithBackground(os.Getenv("ERII_CHAT_IMAGE_BACKGROUND"))
	cfg = cfg.WithMode(os.Getenv("ERII_CHAT_IMAGE_MODE"))
	return cfg
}

func (cfg ImageConfig) WithMaxCols(value int) ImageConfig {
	if value > 0 {
		cfg.MaxCols = value
	}
	return cfg
}

func (cfg ImageConfig) WithMaxRows(value int) ImageConfig {
	if value > 0 {
		cfg.MaxRows = value
	}
	return cfg
}

func (cfg ImageConfig) WithFit(value string) ImageConfig {
	cfg.Fit = parseImageFit(value, cfg.Fit)
	return cfg
}

func (cfg ImageConfig) WithBackground(value string) ImageConfig {
	cfg.Background = parseImageBackground(value, cfg.Background)
	return cfg
}

func (cfg ImageConfig) WithMode(value string) ImageConfig {
	cfg.Mode = parseImageMode(value, cfg.Mode)
	return cfg
}

func parsePositiveInt(value string, fallback int) int {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}

func parseImageFit(value string, fallback picture.FitMode) picture.FitMode {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "", "contain":
		return fallback
	case "fill":
		return picture.FitFill
	case "cover":
		return picture.FitCover
	default:
		return fallback
	}
}

func parseImageMode(value string, fallback ImageMode) ImageMode {
	normalized := ImageMode(strings.ToLower(strings.TrimSpace(value)))
	switch normalized {
	case ImageModeGlyph, ImageModeKitty, ImageModeIterm, ImageModeAuto:
		return normalized
	case "iterm2":
		return ImageModeIterm
	default:
		return fallback
	}
}

func resolveImageMode(mode ImageMode) ImageMode {
	if mode == ImageModeIterm {
		return ImageModeGlyph
	}
	if mode != ImageModeAuto {
		return mode
	}
	if isKittyLikeTerminal() || picture.KittySupported() == picture.KittyCapabilitySupported {
		return ImageModeKitty
	}
	return ImageModeGlyph
}

func isKittyLikeTerminal() bool {
	if os.Getenv("KITTY_WINDOW_ID") != "" || os.Getenv("WEZTERM_EXECUTABLE") != "" || os.Getenv("GHOSTTY_RESOURCES_DIR") != "" {
		return true
	}
	term := strings.ToLower(os.Getenv("TERM"))
	termProgram := strings.ToLower(os.Getenv("TERM_PROGRAM"))
	return strings.Contains(term, "kitty") || strings.Contains(termProgram, "wezterm") || strings.Contains(termProgram, "ghostty")
}

func parseImageBackground(value string, fallback color.Color) color.Color {
	value = strings.ToLower(strings.TrimSpace(value))
	switch value {
	case "":
		return fallback
	case "transparent", "none":
		return color.Transparent
	case "black":
		return color.Black
	case "white":
		return color.White
	}

	if strings.HasPrefix(value, "#") && len(value) == 7 {
		r, errR := strconv.ParseUint(value[1:3], 16, 8)
		g, errG := strconv.ParseUint(value[3:5], 16, 8)
		b, errB := strconv.ParseUint(value[5:7], 16, 8)
		if errR == nil && errG == nil && errB == nil {
			return color.RGBA{R: uint8(r), G: uint8(g), B: uint8(b), A: 255}
		}
	}
	return fallback
}
