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

// ValueConfig holds type, nullable, and default value for a config node.
type ValueConfig struct {
	Type     string // "string", "number", "boolean", "array", "object"
	Nullable bool
	Default  any
}

// ValueConfigStore holds all value configurations.
type ValueConfigStore struct {
	Main   map[string]*ValueConfig
	Plugin map[string]map[string]*ValueConfig
}

// GlobalValueConfig is populated at startup.
var GlobalValueConfig = &ValueConfigStore{
	Main:   make(map[string]*ValueConfig),
	Plugin: make(map[string]map[string]*ValueConfig),
}

var metaDir string

// LoadMetadata loads enum.json, desc.json, copy.json, value.json from the given directory.
// Supports __main__ and __plugin__ top-level structure.
func LoadMetadata(confDir string) error {
	metaDir = confDir

	// Load enum.json
	loadEnumFile(filepath.Join(confDir, "enum.json"))

	// Load desc.json
	loadDescFile(filepath.Join(confDir, "desc.json"))

	// Load copy.json
	loadCopyFile(filepath.Join(confDir, "copy.json"))

	// Load value.json
	loadValueFile(filepath.Join(confDir, "value.json"))

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

func loadValueFile(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var raw map[string]any
	if json.Unmarshal(data, &raw) != nil {
		return
	}

	// Load __main__ value configs
	if main, ok := raw["__main__"].(map[string]any); ok {
		for k, v := range main {
			if vc := parseValueConfig(v); vc != nil {
				GlobalValueConfig.Main[k] = vc
			}
		}
	}

	// Load __plugin__ value configs
	if plugins, ok := raw["__plugin__"].(map[string]any); ok {
		for pName, pData := range plugins {
			if GlobalValueConfig.Plugin[pName] == nil {
				GlobalValueConfig.Plugin[pName] = make(map[string]*ValueConfig)
			}
			if pm, ok := pData.(map[string]any); ok {
				// Check for "item" wrapper first
				if item, ok := pm["item"].(map[string]any); ok {
					for k, v := range item {
						if vc := parseValueConfig(v); vc != nil {
							GlobalValueConfig.Plugin[pName][k] = vc
						}
					}
				} else {
					// No "item" wrapper - direct key-value pairs
					for k, v := range pm {
						if vc := parseValueConfig(v); vc != nil {
							GlobalValueConfig.Plugin[pName][k] = vc
						}
					}
				}
			}
		}
	}
}

func parseValueConfig(v any) *ValueConfig {
	m, ok := v.(map[string]any)
	if !ok {
		return nil
	}
	vc := &ValueConfig{}
	if t, ok := m["type"].(string); ok {
		vc.Type = t
	}
	if nullable, ok := m["nullable"].(bool); ok {
		vc.Nullable = nullable
	}
	if def, ok := m["default"]; ok {
		vc.Default = def
	}
	return vc
}

// lookup performs common config lookup: plugin map -> main map -> wildcard fallback.
// The wildcardFn is called when no exact match is found in either plugin or main maps.
func lookup[T any](
	pluginMap map[string]map[string]T,
	mainMap map[string]T,
	pluginName, path string,
	wildcardFn func(string) T,
) T {
	tryMap := func(m map[string]map[string]T, name, p string) (T, bool) {
		if m == nil {
			return *new(T), false
		}
		if pm, ok := m[name]; ok {
			if v, ok := pm[p]; ok {
				return v, true
			}
		}
		return *new(T), false
	}

	// Check plugin-specific metadata first
	if pluginName != "" {
		if v, ok := tryMap(pluginMap, pluginName, path); ok {
			return v
		}
		if strings.HasPrefix(path, "root.") {
			if v, ok := tryMap(pluginMap, pluginName, path[5:]); ok {
				return v
			}
		}
	}

	// Check main metadata
	if v, ok := mainMap[path]; ok {
		return v
	}
	if strings.HasPrefix(path, "root.") {
		path = path[5:]
		if v, ok := mainMap[path]; ok {
			return v
		}
	}
	return wildcardFn(path)
}

// GetValueConfig returns a value config for a dot-separated path, checking plugin context first.
func GetValueConfig(pluginName, path string) *ValueConfig {
	return lookup(GlobalValueConfig.Plugin, GlobalValueConfig.Main, pluginName, path, matchWildcardValueConfig)
}

func matchWildcardValueConfig(path string) *ValueConfig {
	parts := strings.Split(path, ".")
	for pattern, vc := range GlobalValueConfig.Main {
		if matchWildcardPattern(pattern, parts) {
			return vc
		}
	}
	if strings.HasPrefix(path, "root.") {
		parts = strings.Split(path[5:], ".")
		for pattern, vc := range GlobalValueConfig.Main {
			if matchWildcardPattern(pattern, parts) {
				return vc
			}
		}
	}
	return nil
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
	return lookup(GlobalMetadata.PluginEnum, GlobalMetadata.MainEnum, pluginName, path, matchWildcardEnum)
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
	return lookup(GlobalMetadata.PluginDesc, GlobalMetadata.MainDesc, pluginName, path, matchWildcardDesc)
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
	pathParts := strings.Split(path, ".")

	// Check plugin-specific patterns first
	if pluginName != "" {
		if patterns, ok := GlobalMetadata.CopyPlugin[pluginName]; ok {
			for _, p := range patterns {
				if matchCopyPattern(p, pathParts) {
					return true
				}
			}
		}
	}

	// Check main patterns
	for _, p := range GlobalMetadata.CopyMain {
		if matchCopyPattern(p, pathParts) {
			return true
		}
	}
	return false
}

// matchCopyPattern supports simple wildcard: "onebot.*" matches "onebot.bots" etc.
// Pattern matches if it is a suffix of the path.
func matchCopyPattern(pattern string, pathParts []string) bool {
	pp := strings.Split(pattern, ".")
	if len(pp) > len(pathParts) {
		return false
	}
	offset := len(pathParts) - len(pp)
	for i := range pp {
		if pp[i] == "*" {
			continue
		}
		if pp[i] != pathParts[offset+i] {
			return false
		}
	}
	return true
}
