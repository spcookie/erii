package web

import (
	"log"
	"os/exec"
	"sync"
)

// Session manages the lifecycle of a single PTY-backed process.
// Only one process runs at a time. Starting a new process terminates the old one.
type Session struct {
	mu  sync.Mutex
	cmd *exec.Cmd
	pty *Pty
}

// Start launches an erii subcommand in a PTY. Kills any existing process first.
func (s *Session) Start(eriiBin string, subcmd string, args []string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.cleanup()

	cmdArgs := append([]string{subcmd}, args...)
	cmd, pt, err := StartPty(eriiBin, cmdArgs)
	if err != nil {
		return err
	}
	s.cmd = cmd
	s.pty = pt
	return nil
}

// Write sends data to PTY stdin.
func (s *Session) Write(data []byte) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.pty == nil {
		return 0, nil
	}
	return s.pty.Write(data)
}

// Read reads from PTY stdout. Returns 0, nil if no active PTY.
func (s *Session) Read(buf []byte) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.pty == nil {
		return 0, nil
	}
	return s.pty.Read(buf)
}

// Resize changes the PTY window size.
func (s *Session) Resize(rows, cols int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.pty != nil {
		if err := s.pty.Resize(rows, cols); err != nil {
			log.Printf("pty resize error: %v", err)
		}
	}
}

// Active returns whether a PTY process is currently running.
func (s *Session) Active() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.pty != nil
}

// Close terminates the PTY process and cleans up.
func (s *Session) Close() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.cleanup()
}

func (s *Session) cleanup() {
	if s.pty != nil {
		s.pty.Close()
		s.pty = nil
	}
	if s.cmd != nil && s.cmd.Process != nil {
		s.cmd.Process.Kill()
		s.cmd.Wait()
		s.cmd = nil
	}
}
