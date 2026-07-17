package theme

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"

	"github.com/charmbracelet/lipgloss"
	"github.com/mattn/go-isatty"
	"github.com/muesli/termenv"
)

type Mode string

const (
	ModeAuto  Mode = "auto"
	ModeDark  Mode = "dark"
	ModeLight Mode = "light"
)

var (
	modeMu        sync.RWMutex
	requestedMode = ModeAuto
	resolvedMode  = ModeAuto
)

// Resolve applies the public precedence: an explicitly set flag, ERII_THEME,
// then auto. Invalid input is never silently ignored.
func Resolve(flagValue string, flagChanged bool, envValue string) (Mode, error) {
	value := string(ModeAuto)
	if flagChanged {
		value = flagValue
	} else if strings.TrimSpace(envValue) != "" {
		value = envValue
	}

	mode := Mode(strings.ToLower(strings.TrimSpace(value)))
	switch mode {
	case ModeAuto, ModeDark, ModeLight:
		return mode, nil
	default:
		return "", fmt.Errorf("invalid theme %q: expected auto, dark, or light", value)
	}
}

// Apply freezes the theme for the lifetime of the command.
func Apply(mode Mode) Mode {
	resolved := mode
	switch mode {
	case ModeDark:
		lipgloss.SetHasDarkBackground(true)
	case ModeLight:
		lipgloss.SetHasDarkBackground(false)
	default:
		if lipgloss.HasDarkBackground() {
			resolved = ModeDark
		} else {
			resolved = ModeLight
		}
		lipgloss.SetHasDarkBackground(resolved == ModeDark)
	}

	modeMu.Lock()
	requestedMode = mode
	resolvedMode = resolved
	modeMu.Unlock()
	return resolved
}

func Configure(flagValue string, flagChanged bool, envValue string) (Mode, error) {
	mode, err := Resolve(flagValue, flagChanged, envValue)
	if err != nil {
		return "", err
	}
	return Apply(mode), nil
}

func Requested() Mode {
	modeMu.RLock()
	defer modeMu.RUnlock()
	return requestedMode
}

func Resolved() Mode {
	modeMu.RLock()
	defer modeMu.RUnlock()
	return resolvedMode
}

// ConfigureColorOutput disables ANSI for NO_COLOR and redirected output.
func ConfigureColorOutput(w io.Writer) {
	if os.Getenv("NO_COLOR") != "" || !isTerminalWriter(w) {
		lipgloss.SetColorProfile(termenv.Ascii)
		return
	}
	colorTerm := strings.ToLower(os.Getenv("COLORTERM"))
	if strings.Contains(colorTerm, "truecolor") || strings.Contains(colorTerm, "24bit") {
		lipgloss.SetColorProfile(termenv.TrueColor)
		return
	}
	lipgloss.SetColorProfile(termenv.ANSI256)
}

func isTerminalWriter(w io.Writer) bool {
	f, ok := w.(*os.File)
	if !ok {
		return false
	}
	fd := f.Fd()
	return isatty.IsTerminal(fd) || isatty.IsCygwinTerminal(fd)
}
