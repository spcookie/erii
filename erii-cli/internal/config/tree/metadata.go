package tree

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
)

// Metadata holds external configuration for enum, descriptions, and copy rules.
type Metadata struct {
	MainMeta   map[string]any            // __main__: flat map of path->value
	PluginMeta map[string]map[string]any // __plugin__: pluginName -> (path -> value)
	CopyMain   []string
	CopyPlugin map[string][]string // pluginName -> patterns
}

// GlobalMetadata is populated at startup.
var GlobalMetadata = &Metadata{
	MainMeta:   make(map[string]any),
	PluginMeta: make(map[string]map[string]any),
	CopyMain:   []string{},
	CopyPlugin: make(map[string][]string),
}

var metaDir string

// LoadMetadata loads enum.json, desc.json, copy.json from the given directory.
// Supports __main__ and __plugin__ top-level structure.
func LoadMetadata(confDir string) error {
	metaDir = confDir

	// Load enum.json
	if data, err := os.ReadFile(filepath.Join(confDir, "enum.json")); err == nil {
		var raw map[string]any
		if json.Unmarshal(data, &raw) == nil {
			if main, ok := raw["__main__"].(map[string]any); ok {
				GlobalMetadata.MainMeta = main
			}
			if plugins, ok := raw["__plugin__"].(map[string]any); ok {
				for pName, pData := range plugins {
					if pm, ok := pData.(map[string]any); ok {
						GlobalMetadata.PluginMeta[pName] = pm
					}
				}
			}
		}
	}

	// Load desc.json
	if data, err := os.ReadFile(filepath.Join(confDir, "desc.json")); err == nil {
		var raw map[string]any
		if json.Unmarshal(data, &raw) == nil {
			if main, ok := raw["__main__"].(map[string]any); ok {
				GlobalMetadata.MainMeta = main
			}
			if plugins, ok := raw["__plugin__"].(map[string]any); ok {
				for pName, pData := range plugins {
					if pm, ok := pData.(map[string]any); ok {
						GlobalMetadata.PluginMeta[pName] = pm
					}
				}
			}
		}
	}

	// Load copy.json
	if data, err := os.ReadFile(filepath.Join(confDir, "copy.json")); err == nil {
		var raw map[string]any
		if json.Unmarshal(data, &raw) == nil {
			if main, ok := raw["__main__"].([]any); ok {
				for _, v := range main {
					if s, ok := v.(string); ok {
						GlobalMetadata.CopyMain = append(GlobalMetadata.CopyMain, s)
					}
				}
			}
			if plugins, ok := raw["__plugin__"].(map[string]any); ok {
				for pName, pData := range plugins {
					if arr, ok := pData.([]any); ok {
						var patterns []string
						for _, v := range arr {
							if s, ok := v.(string); ok {
								patterns = append(patterns, s)
							}
						}
						GlobalMetadata.CopyPlugin[pName] = patterns
					}
				}
			}
		}
	}

	return nil
}

// SaveDesc updates the description for a path and persists desc.json.
func SaveDesc(path, desc string) error {
	if GlobalMetadata == nil {
		GlobalMetadata = &Metadata{MainMeta: make(map[string]any)}
	}
	if strings.HasPrefix(path, "root.") {
		path = path[5:]
	}
	GlobalMetadata.MainMeta[path] = desc
	if metaDir == "" {
		return nil
	}

	descMap := map[string]any{
		"__main__": GlobalMetadata.MainMeta,
	}
	if len(GlobalMetadata.PluginMeta) > 0 {
		descMap["__plugin__"] = GlobalMetadata.PluginMeta
	}
	data, err := json.MarshalIndent(descMap, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(metaDir, "desc.json"), data, 0644)
}

// GetEnum returns enum options for a dot-separated path, checking plugin context first.
func GetEnum(pluginName, path string) []string {
	// Check plugin-specific metadata first
	if pluginName != "" {
		if pm, ok := GlobalMetadata.PluginMeta[pluginName]; ok {
			if opts, ok := pm[path]; ok {
				if arr, ok := opts.([]any); ok {
					result := make([]string, 0, len(arr))
					for _, v := range arr {
						if s, ok := v.(string); ok {
							result = append(result, s)
						}
					}
					return result
				}
			}
		}
		// Check with root. prefix stripped
		if strings.HasPrefix(path, "root.") {
			if pm, ok := GlobalMetadata.PluginMeta[pluginName]; ok {
				if opts, ok := pm[path[5:]]; ok {
					if arr, ok := opts.([]any); ok {
						result := make([]string, 0, len(arr))
						for _, v := range arr {
							if s, ok := v.(string); ok {
								result = append(result, s)
							}
						}
						return result
					}
				}
			}
		}
	}

	// Check main metadata
	if opts, ok := GlobalMetadata.MainMeta[path]; ok {
		if arr, ok := opts.([]any); ok {
			result := make([]string, 0, len(arr))
			for _, v := range arr {
				if s, ok := v.(string); ok {
					result = append(result, s)
				}
			}
			return result
		}
	}
	if strings.HasPrefix(path, "root.") {
		if opts, ok := GlobalMetadata.MainMeta[path[5:]]; ok {
			if arr, ok := opts.([]any); ok {
				result := make([]string, 0, len(arr))
				for _, v := range arr {
					if s, ok := v.(string); ok {
						result = append(result, s)
					}
				}
				return result
			}
		}
	}
	return matchWildcardEnum(path)
}

func matchWildcardEnum(path string) []string {
	parts := strings.Split(path, ".")
	for pattern, opts := range GlobalMetadata.MainMeta {
		if matchWildcardPattern(pattern, parts) {
			if arr, ok := opts.([]any); ok {
				result := make([]string, 0, len(arr))
				for _, v := range arr {
					if s, ok := v.(string); ok {
						result = append(result, s)
					}
				}
				return result
			}
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, opts := range GlobalMetadata.MainMeta {
			if matchWildcardPattern(pattern, parts) {
				if arr, ok := opts.([]any); ok {
					result := make([]string, 0, len(arr))
					for _, v := range arr {
						if s, ok := v.(string); ok {
							result = append(result, s)
						}
					}
					return result
				}
			}
		}
	}
	return nil
}

// GetDesc returns a description override for a dot-separated path.
func GetDesc(pluginName, path string) string {
	// Check plugin-specific metadata first
	if pluginName != "" {
		if pm, ok := GlobalMetadata.PluginMeta[pluginName]; ok {
			if d, ok := pm[path].(string); ok {
				return d
			}
		}
		if strings.HasPrefix(path, "root.") {
			if pm, ok := GlobalMetadata.PluginMeta[pluginName]; ok {
				if d, ok := pm[path[5:]].(string); ok {
					return d
				}
			}
		}
	}

	// Check main metadata
	if d, ok := GlobalMetadata.MainMeta[path].(string); ok {
		return d
	}
	if strings.HasPrefix(path, "root.") {
		if d, ok := GlobalMetadata.MainMeta[path[5:]].(string); ok {
			return d
		}
	}
	return matchWildcardDesc(path)
}

func matchWildcardDesc(path string) string {
	parts := strings.Split(path, ".")
	for pattern, d := range GlobalMetadata.MainMeta {
		if matchWildcardPattern(pattern, parts) {
			if s, ok := d.(string); ok {
				return s
			}
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, d := range GlobalMetadata.MainMeta {
			if matchWildcardPattern(pattern, parts) {
				if s, ok := d.(string); ok {
					return s
				}
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
func CanCopy(pluginName, path string) bool {
	// Check plugin-specific patterns first
	if pluginName != "" {
		if patterns, ok := GlobalMetadata.CopyPlugin[pluginName]; ok {
			for _, p := range patterns {
				if matchCopyPattern(p, path) {
					return true
				}
			}
		}
	}

	// Check main patterns
	for _, p := range GlobalMetadata.CopyMain {
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
