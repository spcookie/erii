//go:build windows

package ipc

import (
	"fmt"
	"sync"
	"unsafe"

	"golang.org/x/sys/windows"
)

// mmapHandles 保存所有映射句柄，用于 munmap 时释放 (key=数据指针)
var mmapHandles sync.Map

func doMmap(fd int, size int) ([]byte, error) {
	// Windows 32-bit 不支持 >4GB 文件，这里统一用 uint64 避免 shift 未定义行为
	size64 := uint64(size)
	low := uint32(size64)
	high := uint32(size64 >> 32)

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
		windows.FILE_MAP_WRITE,
		0,
		0,
		uintptr(size),
	)
	if err != nil {
		_ = windows.CloseHandle(h)
		return nil, fmt.Errorf("map view of file failed: %w", err)
	}

	// 用数据指针做 key 保存句柄，支持多并发映射
	mmapHandles.Store(addr, h)

	// 将指针转换为 []byte
	data := unsafe.Slice((*byte)(unsafe.Pointer(addr)), size)
	return data, nil
}

// doMunmap 解除内存映射 (Windows)
func doMunmap(data []byte) error {
	if len(data) == 0 {
		return nil
	}
	ptr := uintptr(unsafe.Pointer(unsafe.SliceData(data)))
	_ = windows.UnmapViewOfFile(ptr)
	if h, ok := mmapHandles.LoadAndDelete(ptr); ok {
		_ = windows.CloseHandle(h.(windows.Handle))
	}
	return nil
}
