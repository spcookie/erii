//go:build windows

package ipc

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

// doMmap 内存映射文件 (Windows)
func doMmap(fd int, size int) ([]byte, error) {
	low := uint32(size)
	high := uint32(size >> 32)

	h, err := windows.CreateFileMapping(
		windows.Handle(fd),
		nil,
		windows.PAGE_READWRITE,
		high,
		low,
		nil,
	)
	if err != nil {
		return nil, fmt.Errorf("create file mapping failed: %w", err)
	}

	addr, err := windows.MapViewOfFile(
		h,
		0xF001F, // FILE_MAP_ALL_ACCESS
		0,
		0,
		uintptr(size),
	)
	if err != nil {
		_ = windows.CloseHandle(h)
		return nil, fmt.Errorf("map view of file failed: %w", err)
	}

	// 保存 handle 以便 munmap 时关闭
	mmapHandle = h

	// 将指针转换为 []byte
	data := unsafe.Slice((*byte)(unsafe.Pointer(addr)), size)
	return data, nil
}

// mmapHandle 保存当前映射句柄，用于 munmap 时释放
var mmapHandle windows.Handle

// doMunmap 解除内存映射 (Windows)
func doMunmap(data []byte) error {
	_ = windows.UnmapViewOfFile(uintptr(unsafe.Pointer(unsafe.SliceData(data))))
	if mmapHandle != 0 {
		_ = windows.CloseHandle(mmapHandle)
		mmapHandle = 0
	}
	return nil
}
