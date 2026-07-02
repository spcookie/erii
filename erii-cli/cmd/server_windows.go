//go:build windows

package cmd

import (
	"fmt"
	"io"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
)

func isProcessRunning(pid int) bool {
	if pid <= 0 {
		return false
	}
	cmd := exec.Command("tasklist", "/FI", fmt.Sprintf("PID eq %d", pid))
	out, err := cmd.Output()
	if err != nil {
		return false
	}
	return strings.Contains(string(out), strconv.Itoa(pid))
}

func killProcess(pid int) error {
	cmd := exec.Command("taskkill", "/T", "/F", "/PID", strconv.Itoa(pid))
	cmd.Stdout = io.Discard
	cmd.Stderr = io.Discard
	return cmd.Run()
}

func configureDaemonProcess(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x00000200, // CREATE_NEW_PROCESS_GROUP
	}
}
