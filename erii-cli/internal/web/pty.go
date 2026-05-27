package web

import (
	"os"
	"os/exec"
	"strings"

	"github.com/creack/pty"
)

// Pty wraps a pseudo-terminal file and the associated command.
type Pty struct {
	cmd *exec.Cmd
	f   *os.File
}

// StartPty creates a PTY and starts the given command in it with the given terminal size.
func StartPty(command string, args []string, rows int, cols int) (*exec.Cmd, *Pty, error) {
	if rows <= 0 {
		rows = 40
	}
	if cols <= 0 {
		cols = 120
	}
	cmd := exec.Command(command, args...)
	cmd.Env = os.Environ()
	// Ensure proper terminal encoding for TUI programs
	if !envHas(cmd.Env, "TERM") {
		cmd.Env = append(cmd.Env, "TERM=xterm-256color")
	}
	if !envHas(cmd.Env, "LANG") {
		cmd.Env = append(cmd.Env, "LANG=en_US.UTF-8")
	}
	if !envHas(cmd.Env, "LC_ALL") {
		cmd.Env = append(cmd.Env, "LC_ALL=en_US.UTF-8")
	}
	f, err := pty.StartWithSize(cmd, &pty.Winsize{Rows: uint16(rows), Cols: uint16(cols)})
	if err != nil {
		return nil, nil, err
	}
	return cmd, &Pty{cmd: cmd, f: f}, nil
}

// Read reads from the PTY stdout.
func (p *Pty) Read(buf []byte) (int, error) {
	return p.f.Read(buf)
}

// Write writes to the PTY stdin.
func (p *Pty) Write(data []byte) (int, error) {
	return p.f.Write(data)
}

// Resize changes the PTY window size.
func (p *Pty) Resize(rows, cols int) error {
	return pty.Setsize(p.f, &pty.Winsize{Rows: uint16(rows), Cols: uint16(cols)})
}

// Close closes the PTY file descriptor. Call cmd.Wait() before Close to get exit code.
func (p *Pty) Close() error {
	return p.f.Close()
}

func envHas(env []string, key string) bool {
	prefix := key + "="
	for _, e := range env {
		if strings.HasPrefix(e, prefix) {
			return true
		}
	}
	return false
}
