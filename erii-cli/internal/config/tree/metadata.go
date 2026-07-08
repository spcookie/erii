package tree

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"

	"erii-cli/internal/path"
)

// Metadata holds external configuration for enum, descriptions, and copy rules.
type Metadata struct {
	MainDesc          map[string]string              // main config: path -> description
	MainEnum          map[string][]string            // main config: path -> enum options
	PluginDesc        map[string]map[string]string   // plugin config: pluginName -> item(path -> description)
	PluginOverallDesc map[string]string              // plugin config: pluginName -> overall description
	PluginEnum        map[string]map[string][]string // plugin config: pluginName -> (path -> enum options)
	PluginOverallEnum map[string]string              // plugin config: pluginName -> overall description (unused for now)
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

// LoadMetadata loads main metadata (flat format) and plugin schemas.
func LoadMetadata(confDir string) error {
	metaDir = confDir

	// Load main metadata (flat format)
	loadMainDescFile(filepath.Join(confDir, "desc.json"))
	loadMainEnumFile(filepath.Join(confDir, "enum.json"))
	loadMainCopyFile(filepath.Join(confDir, "copy.json"))
	loadMainValueFile(filepath.Join(confDir, "value.json"))

	// Load plugin schemas from schema directory
	if path.PluginSchemaDir != "" {
		loadPluginSchemas(path.PluginSchemaDir)
	}

	return nil
}

func loadJSONFile[T any](path string) (T, bool) {
	var result T
	data, err := os.ReadFile(path)
	if err != nil {
		return result, false
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return result, false
	}
	return result, true
}

func loadMainDescFile(path string) {
	if raw, ok := loadJSONFile[map[string]string](path); ok {
		for k, v := range raw {
			GlobalMetadata.MainDesc[k] = v
		}
	}
}

func loadMainEnumFile(path string) {
	if raw, ok := loadJSONFile[map[string][]string](path); ok {
		for k, v := range raw {
			GlobalMetadata.MainEnum[k] = v
		}
	}
}

func loadMainCopyFile(path string) {
	if raw, ok := loadJSONFile[[]string](path); ok {
		GlobalMetadata.CopyMain = raw
	}
}

func loadMainValueFile(path string) {
	if raw, ok := loadJSONFile[map[string]any](path); ok {
		for k, v := range raw {
			if vc := parseValueConfig(v); vc != nil {
				GlobalValueConfig.Main[k] = vc
			}
		}
	}
}

func loadPluginSchemas(schemaDir string) {
	entries, err := os.ReadDir(schemaDir)
	if err != nil {
		return
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		pluginName := strings.TrimSuffix(entry.Name(), ".json")
		schemaPath := filepath.Join(schemaDir, entry.Name())
		loadPluginSchema(pluginName, schemaPath)
	}
}

func loadPluginSchema(pluginName, schemaPath string) {
	data, err := os.ReadFile(schemaPath)
	if err != nil {
		return
	}

	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return
	}

	// __desc__
	if descRaw, ok := raw["__desc__"].(map[string]any); ok {
		if GlobalMetadata.PluginDesc[pluginName] == nil {
			GlobalMetadata.PluginDesc[pluginName] = make(map[string]string)
		}
		for k, v := range descRaw {
			if s, ok := v.(string); ok {
				if k == "__overall__" {
					GlobalMetadata.PluginOverallDesc[pluginName] = s
				} else {
					GlobalMetadata.PluginDesc[pluginName][k] = s
				}
			}
		}
	}

	// __enum__
	if enumRaw, ok := raw["__enum__"].(map[string]any); ok {
		if GlobalMetadata.PluginEnum[pluginName] == nil {
			GlobalMetadata.PluginEnum[pluginName] = make(map[string][]string)
		}
		for k, v := range enumRaw {
			if arr, ok := toStringArray(v); ok {
				GlobalMetadata.PluginEnum[pluginName][k] = arr
			}
		}
	}

	// __copy__
	if copyRaw, ok := raw["__copy__"].([]any); ok {
		var patterns []string
		for _, v := range copyRaw {
			if s, ok := v.(string); ok {
				patterns = append(patterns, s)
			}
		}
		GlobalMetadata.CopyPlugin[pluginName] = patterns
	}

	// __value__
	if valueRaw, ok := raw["__value__"].(map[string]any); ok {
		if GlobalValueConfig.Plugin[pluginName] == nil {
			GlobalValueConfig.Plugin[pluginName] = make(map[string]*ValueConfig)
		}
		for k, v := range valueRaw {
			if vc := parseValueConfig(v); vc != nil {
				GlobalValueConfig.Plugin[pluginName][k] = vc
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

// SaveDesc updates the description for a path and persists desc.json (flat format).
func SaveDesc(nodePath, desc string) error {
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
	if strings.HasPrefix(nodePath, "root.") {
		nodePath = nodePath[5:]
	}
	GlobalMetadata.MainDesc[nodePath] = desc
	if metaDir == "" {
		return nil
	}

	data, err := json.MarshalIndent(GlobalMetadata.MainDesc, "", "  ")
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

// IsObjectContext checks whether the given dot-separated path is inside an object-typed
// config node (as defined by value.json). Walk up the path's ancestors looking for a
// ValueConfig with Type == "object".
func IsObjectContext(pluginName, path string) bool {
	for p := path; p != ""; {
		vc := GetValueConfig(pluginName, p)
		if vc != nil && vc.Type == "object" {
			return true
		}
		lastDot := strings.LastIndex(p, ".")
		if lastDot < 0 {
			break
		}
		p = p[:lastDot]
	}
	return false
}

// CanModify reports whether the given path allows modification (add/copy/rename/delete/desc-edit).
// True when: explicit editable (caller handles), CanCopy matches, or path is inside an object-typed context.
func CanModify(pluginName, path string) bool {
	return CanCopy(pluginName, path) || IsObjectContext(pluginName, path)
}
