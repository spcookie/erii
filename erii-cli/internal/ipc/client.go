package ipc

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"syscall"

	"erii-cli/internal/path"
)

const (
	SIZE        = 64 * 1024 // 64KB
	DefaultSock = ".conf/erii.sock"
)

type ServerConfig struct {
	Type     string `json:"type"`
	Port     int    `json:"port"`
	Username string `json:"username"`
	Password string `json:"password"`
}

func ReadConfig() (*ServerConfig, error) {
	var filePath string
	if dirPath := os.Getenv("ERII_IPC_PATH"); dirPath != "" {
		filePath = filepath.Join(dirPath, DefaultSock)
	} else {
		filePath = filepath.Join(path.ConfMetaDir, "erii.sock")
	}

	// 创建父目录
	if err := os.MkdirAll(filepath.Dir(filePath), 0755); err != nil {
		return nil, fmt.Errorf("failed to create IPC directory: %w", err)
	}

	// Open file (don't truncate - Kotlin owns this file)
	file, err := os.OpenFile(filePath, os.O_RDWR, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to open IPC file: %w", err)
	}
	defer func(file *os.File) {
		_ = file.Close()
	}(file)

	// Ensure file is large enough
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if info.Size() < SIZE {
		if err := file.Truncate(SIZE); err != nil {
			return nil, fmt.Errorf("failed to truncate IPC file: %w", err)
		}
	}

	// mmap the file
	data, err := syscall.Mmap(int(file.Fd()), 0, SIZE, syscall.PROT_READ|syscall.PROT_WRITE, syscall.MAP_SHARED)
	if err != nil {
		return nil, fmt.Errorf("failed to mmap: %w", err)
	}
	defer func(b []byte) {
		_ = syscall.Munmap(b)
	}(data)

	// Read config length (first 4 bytes) - use big endian to match Kotlin MappedByteBuffer
	configLen := binary.BigEndian.Uint32(data[0:4])

	if configLen == 0 {
		return nil, fmt.Errorf("config length is 0 - data not written yet")
	}
	if configLen > SIZE-4 {
		return nil, fmt.Errorf("invalid config length: %d", configLen)
	}

	// Read JSON config data
	configData := data[4 : 4+configLen]

	var config ServerConfig
	if err := json.Unmarshal(configData, &config); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	if config.Type != "config" {
		return nil, fmt.Errorf("unexpected message type: %s", config.Type)
	}

	return &config, nil
}
