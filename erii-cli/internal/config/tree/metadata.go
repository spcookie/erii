package tree

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
)

// Metadata holds external configuration for enum, descriptions, and copy rules.
type Metadata struct {
	MainDesc          map[string]string              // __main__: path -> description
	MainEnum          map[string][]string            // __main__: path -> enum options
	PluginDesc        map[string]map[string]string   // __plugin__: pluginName -> item(path -> description)
	PluginOverallDesc map[string]string              // __plugin__: pluginName -> overall description
	PluginEnum        map[string]map[string][]string // __plugin__: pluginName -> (path -> enum options)
	PluginOverallEnum map[string]string              // __plugin__: pluginName -> overall description (unused for now)
	CopyMain          []string
	CopyPlugin        map[string][]string // pluginName -> patterns
}

// GlobalMetadata is populated at startup.
var GlobalMetadata = &Metadata{
	MainDesc:          make(map[string]string),
	MainEnum:          make(map[string][]string),
	PluginDesc:        make(map[string]map[string]string),
	PluginOverallDesc: make(map[string]string),
	PluginEnum:        make(map[string]map[string][]string),
	PluginOverallEnum: make(map[string]string),
	CopyMain:          []string{},
	CopyPlugin:        make(map[string][]string),
}

var metaDir string

// LoadMetadata loads enum.json, desc.json, copy.json from the given directory.
// Supports __main__ and __plugin__ top-level structure.
func LoadMetadata(confDir string) error {
	metaDir = confDir

	// Load enum.json
	loadEnumFile(filepath.Join(confDir, "enum.json"))

	// Load desc.json
	loadDescFile(filepath.Join(confDir, "desc.json"))

	// Load copy.json
	loadCopyFile(filepath.Join(confDir, "copy.json"))

	return nil
}

func loadEnumFile(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var raw map[string]any
	if json.Unmarshal(data, &raw) != nil {
		return
	}

	// Load __main__ enums
	if main, ok := raw["__main__"].(map[string]any); ok {
		for k, v := range main {
			if arr, ok := toStringArray(v); ok {
				GlobalMetadata.MainEnum[k] = arr
			}
		}
	}

	// Load __plugin__ enums
	if plugins, ok := raw["__plugin__"].(map[string]any); ok {
		for pName, pData := range plugins {
			if pm, ok := pData.(map[string]any); ok {
				// Load item enums
				if item, ok := pm["item"].(map[string]any); ok {
					if GlobalMetadata.PluginEnum[pName] == nil {
						GlobalMetadata.PluginEnum[pName] = make(map[string][]string)
					}
					for k, v := range item {
						if arr, ok := toStringArray(v); ok {
							GlobalMetadata.PluginEnum[pName][k] = arr
						}
					}
				}
			}
		}
	}
}

func loadDescFile(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var raw map[string]any
	if json.Unmarshal(data, &raw) != nil {
		return
	}

	// Load __main__ descriptions
	if main, ok := raw["__main__"].(map[string]any); ok {
		for k, v := range main {
			if s, ok := v.(string); ok {
				GlobalMetadata.MainDesc[k] = s
			}
		}
	}

	// Load __plugin__ descriptions
	if plugins, ok := raw["__plugin__"].(map[string]any); ok {
		for pName, pData := range plugins {
			if pm, ok := pData.(map[string]any); ok {
				// Load overall description
				if desc, ok := pm["description"].(string); ok {
					GlobalMetadata.PluginOverallDesc[pName] = desc
				}
				// Load item descriptions
				if item, ok := pm["item"].(map[string]any); ok {
					if GlobalMetadata.PluginDesc[pName] == nil {
						GlobalMetadata.PluginDesc[pName] = make(map[string]string)
					}
					for k, v := range item {
						if s, ok := v.(string); ok {
							GlobalMetadata.PluginDesc[pName][k] = s
						}
					}
				}
			}
		}
	}
}

func loadCopyFile(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var raw map[string]any
	if json.Unmarshal(data, &raw) != nil {
		return
	}

	// Load __main__ copy patterns
	if main, ok := raw["__main__"].([]any); ok {
		for _, v := range main {
			if s, ok := v.(string); ok {
				GlobalMetadata.CopyMain = append(GlobalMetadata.CopyMain, s)
			}
		}
	}

	// Load __plugin__ copy patterns
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

func toStringArray(v any) ([]string, bool) {
	arr, ok := v.([]any)
	if !ok {
		return nil, false
	}
	result := make([]string, 0, len(arr))
	for _, a := range arr {
		if s, ok := a.(string); ok {
			result = append(result, s)
		} else {
			return nil, false
		}
	}
	return result, true
}

// SaveDesc updates the description for a path and persists desc.json.
func SaveDesc(path, desc string) error {
	if GlobalMetadata == nil {
		GlobalMetadata = &Metadata{
			MainDesc:          make(map[string]string),
			MainEnum:          make(map[string][]string),
			PluginDesc:        make(map[string]map[string]string),
			PluginOverallDesc: make(map[string]string),
			PluginEnum:        make(map[string]map[string][]string),
			PluginOverallEnum: make(map[string]string),
			CopyMain:          []string{},
			CopyPlugin:        make(map[string][]string),
		}
	}
	if strings.HasPrefix(path, "root.") {
		path = path[5:]
	}
	GlobalMetadata.MainDesc[path] = desc
	if metaDir == "" {
		return nil
	}

	descMap := map[string]any{
		"__main__": GlobalMetadata.MainDesc,
	}
	if len(GlobalMetadata.PluginDesc) > 0 || len(GlobalMetadata.PluginOverallDesc) > 0 {
		pluginMap := make(map[string]any)
		for pName, itemDesc := range GlobalMetadata.PluginDesc {
			if _, ok := pluginMap[pName]; !ok {
				if overallDesc, exists := GlobalMetadata.PluginOverallDesc[pName]; exists {
					pluginMap[pName] = map[string]any{
						"description": overallDesc,
						"item":        itemDesc,
					}
				} else {
					pluginMap[pName] = map[string]any{
						"item": itemDesc,
					}
				}
			}
		}
		descMap["__plugin__"] = pluginMap
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
		if pm, ok := GlobalMetadata.PluginEnum[pluginName]; ok {
			if opts, ok := pm[path]; ok {
				return opts
			}
		}
		// Check with root. prefix stripped
		if strings.HasPrefix(path, "root.") {
			path = path[5:]
			if pm, ok := GlobalMetadata.PluginEnum[pluginName]; ok {
				if opts, ok := pm[path]; ok {
					return opts
				}
			}
		}
	}

	// Check main metadata
	if opts, ok := GlobalMetadata.MainEnum[path]; ok {
		return opts
	}
	if strings.HasPrefix(path, "root.") {
		path = path[5:]
		if opts, ok := GlobalMetadata.MainEnum[path]; ok {
			return opts
		}
	}
	return matchWildcardEnum(path)
}

func matchWildcardEnum(path string) []string {
	parts := strings.Split(path, ".")
	for pattern, opts := range GlobalMetadata.MainEnum {
		if matchWildcardPattern(pattern, parts) {
			return opts
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, opts := range GlobalMetadata.MainEnum {
			if matchWildcardPattern(pattern, parts) {
				return opts
			}
		}
	}
	return nil
}

// GetDesc returns a description override for a dot-separated path.
func GetDesc(pluginName, path string) string {
	// Check plugin-specific metadata first
	if pluginName != "" {
		if pm, ok := GlobalMetadata.PluginDesc[pluginName]; ok {
			if d, ok := pm[path]; ok {
				return d
			}
		}
		if strings.HasPrefix(path, "root.") {
			stripped := path[5:]
			if pm, ok := GlobalMetadata.PluginDesc[pluginName]; ok {
				if d, ok := pm[stripped]; ok {
					return d
				}
			}
		}
	}

	// Check main metadata
	if d, ok := GlobalMetadata.MainDesc[path]; ok {
		return d
	}
	if strings.HasPrefix(path, "root.") {
		path = path[5:]
		if d, ok := GlobalMetadata.MainDesc[path]; ok {
			return d
		}
	}
	return matchWildcardDesc(path)
}

func matchWildcardDesc(path string) string {
	parts := strings.Split(path, ".")
	for pattern, d := range GlobalMetadata.MainDesc {
		if matchWildcardPattern(pattern, parts) {
			return d
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, d := range GlobalMetadata.MainDesc {
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
