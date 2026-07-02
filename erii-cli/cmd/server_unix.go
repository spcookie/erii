//go:build !windows

package cmd

import (
	"os/exec"
	"syscall"

	"golang.org/x/sys/unix"
)

func isProcessRunning(pid int) bool {
	if pid <= 0 {
		return false
	}
	return unix.Kill(pid, 0) == nil
}

func killProcess(pid int) error {
	return unix.Kill(pid, unix.SIGTERM)
}

func configureDaemonProcess(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setsid: true,
	}
}
