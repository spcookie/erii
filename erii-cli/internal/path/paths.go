package path

import (
	"os"
	"path/filepath"
)

var (
	ConfDir     string
	ConfMetaDir string
	EnvFile     string
	AppFile     string
	SoulsDir    string
	RulesDir    string
)

func init() {
	ConfDir = resolveConfDir()
	ConfMetaDir = resolveConfMetaDir()
	EnvFile = filepath.Join(ConfDir, ".env.local")
	AppFile = filepath.Join(ConfDir, "application.conf")
	SoulsDir = filepath.Join(ConfDir, "souls")
	RulesDir = filepath.Join(ConfDir, "rules")
}

func resolveConfMetaDir() string {
	// Try CWD first
	if cwd, err := os.Getwd(); err == nil {
		candidate := filepath.Join(cwd, ".conf")
		if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
			return candidate
		}
		// Walk up from CWD looking for .conf
		for dir := cwd; ; dir = filepath.Dir(dir) {
			candidate = filepath.Join(dir, ".conf")
			if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
				return candidate
			}
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
		}
	}

	// Try executable directory
	if ex, err := os.Executable(); err == nil {
		exDir := filepath.Dir(ex)
		for dir := exDir; ; dir = filepath.Dir(dir) {
			candidate := filepath.Join(dir, ".conf")
			if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
				return candidate
			}
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
		}
	}

	// Fallback to sibling of ConfDir
	return filepath.Join(filepath.Dir(ConfDir), ".conf")
}

func resolveConfDir() string {
	// Try CWD first
	if cwd, err := os.Getwd(); err == nil {
		candidate := filepath.Join(cwd, "conf")
		if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
			return candidate
		}

		// Walk up from CWD looking for conf
		for dir := cwd; ; dir = filepath.Dir(dir) {
			candidate = filepath.Join(dir, "conf")
			if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
				return candidate
			}
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
		}
	}

	// Try executable directory
	if ex, err := os.Executable(); err == nil {
		exDir := filepath.Dir(ex)
		for dir := exDir; ; dir = filepath.Dir(dir) {
			candidate := filepath.Join(dir, "conf")
			if stat, err := os.Stat(candidate); err == nil && stat.IsDir() {
				return candidate
			}
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
		}
	}

	// Fallback to CWD-relative
	if cwd, err := os.Getwd(); err == nil {
		return filepath.Join(cwd, "conf")
	}
	return "./conf"
}
