package ipc

import (
	"encoding/binary"
	"fmt"

	"os"
	"path/filepath"

	"github.com/vmihailenco/msgpack/v5"

	"erii-cli/internal/path"
)

const (
	SIZE = 64 * 1024 // 64KB
)

type ServerConfig struct {
	Type     string `msgpack:"type" json:"type"`
	Port     int    `msgpack:"port" json:"port"`
	Username string `msgpack:"username" json:"username"`
	Password string `msgpack:"password" json:"password"`
}

func ReadConfig() (*ServerConfig, error) {
	return ReadConfigFromDir(path.EriiDir)
}

// ReadConfigFromDir reads the core connection config from a specific Erii
// runtime directory. This keeps callers with explicit --erii-dir values from
// accidentally using the process-global path.
func ReadConfigFromDir(eriiDir string) (*ServerConfig, error) {
	filePath := filepath.Join(eriiDir, "erii.sock")

	// 创建父目录
	if err := os.MkdirAll(filepath.Dir(filePath), 0755); err != nil {
		return nil, fmt.Errorf("failed to create IPC directory: %w", err)
	}

	// Open file (don't truncate - Kotlin owns this file)
	file, err := os.OpenFile(filePath, os.O_RDWR, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to open IPC file: %w", err)
	}

	// Ensure file is large enough
	info, err := file.Stat()
	if err != nil {
		err := file.Close()
		if err != nil {
			return nil, err
		}
		return nil, err
	}
	if info.Size() < SIZE {
		if err := file.Truncate(SIZE); err != nil {
			err := file.Close()
			if err != nil {
				return nil, err
			}
			return nil, fmt.Errorf("failed to truncate IPC file: %w", err)
		}
	}

	// mmap the file - 平台特定实现
	data, err := doMmap(int(file.Fd()), SIZE)
	if err != nil {
		err := file.Close()
		if err != nil {
			return nil, err
		}
		return nil, fmt.Errorf("failed to mmap: %w", err)
	}
	defer func() {
		_ = doMunmap(data)
		err := file.Close()
		if err != nil {
			return
		}
	}()

	return decodeConfig(data)
}

// ReadConfigSnapshotFromDir reads the IPC file without resizing or mapping it.
// Status polling uses this so it cannot modify a file while core is writing it.
func ReadConfigSnapshotFromDir(eriiDir string) (*ServerConfig, error) {
	data, err := os.ReadFile(filepath.Join(eriiDir, "erii.sock"))
	if err != nil {
		return nil, fmt.Errorf("failed to open IPC file: %w", err)
	}
	return decodeConfig(data)
}

func decodeConfig(data []byte) (*ServerConfig, error) {
	if len(data) < 4 {
		return nil, fmt.Errorf("IPC data is not ready")
	}

	// Read config length (first 4 bytes) - use big endian to match Kotlin MappedByteBuffer.
	configLen := binary.BigEndian.Uint32(data[0:4])

	if configLen == 0 {
		return nil, fmt.Errorf("config length is 0 - data not written yet")
	}
	if configLen > uint32(len(data)-4) {
		return nil, fmt.Errorf("invalid config length: %d", configLen)
	}

	// Read MessagePack config data
	configData := data[4 : 4+configLen]

	var config ServerConfig
	if err := msgpack.Unmarshal(configData, &config); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	if config.Type != "config" {
		return nil, fmt.Errorf("unexpected message type: %s", config.Type)
	}

	return &config, nil
}
