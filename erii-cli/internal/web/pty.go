package web

import (
	"os"
	"os/exec"

	"github.com/creack/pty"
)

// Pty wraps a pseudo-terminal file and the associated command.
type Pty struct {
	cmd *exec.Cmd
	f   *os.File
}

// StartPty creates a PTY and starts the given command in it with default terminal size.
func StartPty(command string, args []string) (*exec.Cmd, *Pty, error) {
	cmd := exec.Command(command, args...)
	f, err := pty.StartWithSize(cmd, &pty.Winsize{Rows: 40, Cols: 120})
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
