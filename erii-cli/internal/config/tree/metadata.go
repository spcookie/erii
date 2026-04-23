package tree

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
)

// Metadata holds external configuration for enum, descriptions, and copy rules.
type Metadata struct {
	EnumMap      map[string][]string
	DescMap      map[string]string
	CopyPatterns []string
}

// GlobalMetadata is populated at startup.
var GlobalMetadata = &Metadata{
	EnumMap:      make(map[string][]string),
	DescMap:      make(map[string]string),
	CopyPatterns: []string{},
}

// LoadMetadata loads enum.json, desc.json, copy.json from the given directory.
func LoadMetadata(confDir string) error {
	if data, err := os.ReadFile(filepath.Join(confDir, "enum.json")); err == nil {
		_ = json.Unmarshal(data, &GlobalMetadata.EnumMap)
	}
	if data, err := os.ReadFile(filepath.Join(confDir, "desc.json")); err == nil {
		_ = json.Unmarshal(data, &GlobalMetadata.DescMap)
	}
	if data, err := os.ReadFile(filepath.Join(confDir, "copy.json")); err == nil {
		_ = json.Unmarshal(data, &GlobalMetadata.CopyPatterns)
	}
	return nil
}

// GetEnum returns enum options for a dot-separated path.
func GetEnum(path string) []string {
	if GlobalMetadata == nil {
		return nil
	}
	if opts, ok := GlobalMetadata.EnumMap[path]; ok {
		return opts
	}
	if strings.HasPrefix(path, "root.") {
		if opts, ok := GlobalMetadata.EnumMap[path[5:]]; ok {
			return opts
		}
	}
	return matchWildcardEnum(path)
}

func matchWildcardEnum(path string) []string {
	parts := strings.Split(path, ".")
	for pattern, opts := range GlobalMetadata.EnumMap {
		if matchWildcardPattern(pattern, parts) {
			return opts
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, opts := range GlobalMetadata.EnumMap {
			if matchWildcardPattern(pattern, parts) {
				return opts
			}
		}
	}
	return nil
}

// GetDesc returns a description override for a dot-separated path.
func GetDesc(path string) string {
	if GlobalMetadata == nil {
		return ""
	}
	if d, ok := GlobalMetadata.DescMap[path]; ok {
		return d
	}
	if strings.HasPrefix(path, "root.") {
		if d, ok := GlobalMetadata.DescMap[path[5:]]; ok {
			return d
		}
	}
	return matchWildcardDesc(path)
}

func matchWildcardDesc(path string) string {
	parts := strings.Split(path, ".")
	for pattern, d := range GlobalMetadata.DescMap {
		if matchWildcardPattern(pattern, parts) {
			return d
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, d := range GlobalMetadata.DescMap {
			if matchWildcardPattern(pattern, parts) {
				return d
			}
		}
	}
	return ""
}

// matchWildcardPattern checks if pattern (with "*" wildcards) matches pathParts.
func matchWildcardPattern(pattern string, pathParts []string) bool {
	pp := strings.Split(pattern, ".")
	if len(pp) != len(pathParts) {
		return false
	}
	for i := range pp {
		if pp[i] == "*" {
			continue
		}
		if pp[i] != pathParts[i] {
			return false
		}
	}
	return true
}

// CanCopy checks if the given dot-separated path allows copying/new items.
func CanCopy(path string) bool {
	if GlobalMetadata == nil {
		return false
	}
	for _, p := range GlobalMetadata.CopyPatterns {
		if matchCopyPattern(p, path) {
			return true
		}
	}
	return false
}

// matchCopyPattern supports simple wildcard: "onebot.*" matches "onebot.bots" etc.
// Pattern matches if it is a suffix of the path.
func matchCopyPattern(pattern, path string) bool {
	if pattern == path {
		return true
	}
	pp := strings.Split(pattern, ".")
	vp := strings.Split(path, ".")
	if len(pp) > len(vp) {
		return false
	}
	offset := len(vp) - len(pp)
	for i := range pp {
		if pp[i] == "*" {
			continue
		}
		if pp[i] != vp[offset+i] {
			return false
		}
	}
	return true
}
