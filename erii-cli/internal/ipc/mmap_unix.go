//go:build linux || darwin

package ipc

import (
	"fmt"

	"golang.org/x/sys/unix"
)

// doMmap 内存映射文件 (Linux / macOS)
func doMmap(fd int, size int) ([]byte, error) {
	data, err := unix.Mmap(fd, 0, size, unix.PROT_READ|unix.PROT_WRITE, unix.MAP_SHARED)
	if err != nil {
		return nil, fmt.Errorf("unix mmap failed: %w", err)
	}
	return data, nil
}

// doMunmap 解除内存映射 (Linux / macOS)
func doMunmap(data []byte) error {
	return unix.Munmap(data)
}
