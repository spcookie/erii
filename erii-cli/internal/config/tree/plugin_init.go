package tree

import (
	"archive/zip"
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// InitializePluginConfigs performs plugin initialization on CLI startup:
// unzips plugin archives if needed and copies plugin.json/schema.json to config dirs.
func InitializePluginConfigs(pluginDir, pluginConfigDir, pluginSchemaDir string) error {
	if err := os.MkdirAll(pluginConfigDir, 0755); err != nil {
		return fmt.Errorf("failed to create plugin-config dir: %w", err)
	}
	if err := os.MkdirAll(pluginSchemaDir, 0755); err != nil {
		return fmt.Errorf("failed to create plugin-config/schema dir: %w", err)
	}

	entries, err := os.ReadDir(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to read plugin dir: %w", err)
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasSuffix(name, ".zip") {
			continue
		}

		zipPath := filepath.Join(pluginDir, name)
		baseName := strings.TrimSuffix(name, ".zip")
		extractDir := filepath.Join(pluginDir, baseName)

		// 1. Check if already extracted; if not, unzip
		if _, err := os.Stat(extractDir); os.IsNotExist(err) {
			if err := unzip(zipPath, extractDir); err != nil {
				continue
			}
		}

		// 2. Read plugin.id from plugin.properties
		propsPath := filepath.Join(extractDir, "plugin.properties")
		pluginID, err := readPluginID(propsPath)
		if err != nil {
			continue
		}

		// 3. Copy plugin.json if not exists
		srcPluginJson := filepath.Join(extractDir, "classes", "plugin.json")
		dstPluginJson := filepath.Join(pluginConfigDir, pluginID+".json")
		_ = copyFileIfNotExists(srcPluginJson, dstPluginJson)

		// 4. Copy schema.json if not exists (silently skip if source missing)
		srcSchemaJson := filepath.Join(extractDir, "classes", "schema.json")
		dstSchemaJson := filepath.Join(pluginSchemaDir, pluginID+".json")
		_ = copyFileIfNotExists(srcSchemaJson, dstSchemaJson)
	}

	return nil
}

// unzip extracts a zip archive to the destination directory with zip-slip protection.
func unzip(src, dest string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer r.Close()

	if err := os.MkdirAll(dest, 0755); err != nil {
		return err
	}

	for _, f := range r.File {
		fpath := filepath.Join(dest, f.Name)

		// Zip-slip protection
		if !strings.HasPrefix(fpath, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path in zip: %s", f.Name)
		}

		if f.FileInfo().IsDir() {
			os.MkdirAll(fpath, f.Mode())
			continue
		}

		if err := os.MkdirAll(filepath.Dir(fpath), 0755); err != nil {
			return err
		}

		outFile, err := os.OpenFile(fpath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
		if err != nil {
			return err
		}
		defer outFile.Close()

		rc, err := f.Open()
		if err != nil {
			return err
		}
		defer rc.Close()

		if _, err := io.Copy(outFile, rc); err != nil {
			return err
		}
	}
	return nil
}

// readPluginID reads the plugin.id value from a plugin.properties file.
func readPluginID(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "plugin.id=") {
			return strings.TrimPrefix(line, "plugin.id="), nil
		}
	}
	if err := scanner.Err(); err != nil {
		return "", err
	}
	return "", fmt.Errorf("plugin.id not found in %s", path)
}

// copyFileIfNotExists copies src to dst only if dst does not already exist.
// Returns nil if src does not exist or dst already exists.
func copyFileIfNotExists(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	defer srcFile.Close()

	dstFile, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0644)
	if err != nil {
		if os.IsExist(err) {
			return nil
		}
		return err
	}
	defer dstFile.Close()

	_, err = io.Copy(dstFile, srcFile)
	return err
}
