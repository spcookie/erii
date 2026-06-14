package tree

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"

	"erii-cli/internal/path"

	"github.com/gurkankaymak/hocon"
)

// ConfReloadResult holds results for conf directory reload.
type ConfReloadResult struct {
	MetaResults   []FileMergeResult
	EnvResult     FileMergeResult
	AppConfResult FileMergeResult
	RulesResults  []FileMergeResult
	SoulsResults  []FileMergeResult
}

// FindUpdateConfDir returns the path to .update-conf directory (sibling of conf/).
// This directory is populated by erii-config/postinstall.js when configs already exist.
func FindUpdateConfDir() string {
	return filepath.Join(filepath.Dir(path.ConfDir), ".update-conf")
}

// ReloadMetaDir merges JSON files from srcMetaDir into dstMetaDir using deep merge.
func ReloadMetaDir(srcMetaDir, dstMetaDir string) ([]FileMergeResult, error) {
	if err := os.MkdirAll(dstMetaDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create meta dir: %w", err)
	}

	entries, err := os.ReadDir(srcMetaDir)
	if err != nil {
		return nil, fmt.Errorf("failed to read meta source dir: %w", err)
	}

	var results []FileMergeResult
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		// copy.json is an array (not an object), skip it — the app reads it as []string
		if entry.Name() == "copy.json" {
			continue
		}
		srcPath := filepath.Join(srcMetaDir, entry.Name())
		dstPath := filepath.Join(dstMetaDir, entry.Name())
		results = append(results, mergeJSONFile(srcPath, dstPath))
	}
	return results, nil
}

// ReloadConfDirs merges config files from srcConfDir into dstConfDir.
func ReloadConfDirs(srcConfDir, dstConfDir string) (*ConfReloadResult, error) {
	result := &ConfReloadResult{}

	if err := os.MkdirAll(dstConfDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create conf dir: %w", err)
	}

	result.AppConfResult = mergeHOCONFile(
		filepath.Join(srcConfDir, "application.conf"),
		filepath.Join(dstConfDir, "application.conf"),
	)

	result.EnvResult = mergeEnvFile(
		filepath.Join(srcConfDir, ".env.local"),
		filepath.Join(dstConfDir, ".env.local"),
	)

	if r, err := copyNewFilesRecursively(
		filepath.Join(srcConfDir, "rules"),
		filepath.Join(dstConfDir, "rules"),
	); err == nil {
		result.RulesResults = r
	}

	if r, err := copyNewFilesRecursively(
		filepath.Join(srcConfDir, "souls"),
		filepath.Join(dstConfDir, "souls"),
	); err == nil {
		result.SoulsResults = r
	}

	return result, nil
}

// extractFieldKey extracts the field key from an already-trimmed line like "key = value" or "key {".
func extractFieldKey(line string) string {
	inQuote := false
	for i := 0; i < len(line); i++ {
		ch := line[i]
		if ch == '"' && (i == 0 || line[i-1] != '\\') {
			inQuote = !inQuote
			continue
		}
		if inQuote {
			continue
		}
		if ch == '=' || ch == '{' {
			return strings.TrimSpace(line[:i])
		}
	}
	return ""
}

// extractBlockFieldLines extracts top-level fields from inside a HOCON block.
// The blockLines include the opening and closing braces.
func extractBlockFieldLines(blockLines []string) map[string][]string {
	fields := make(map[string][]string)
	if len(blockLines) < 2 {
		return fields
	}

	depth := 1 // Already inside the block after the opening line
	var currentKey string
	var currentLines []string

	for i := 1; i < len(blockLines); i++ {
		line := blockLines[i]
		open, close := countBracesOutsideStrings(line)
		newDepth := depth + open - close

		if depth == 1 && newDepth >= 1 {
			trimmed := strings.TrimSpace(line)
			if trimmed == "" || strings.HasPrefix(trimmed, "#") || trimmed == "}" {
				depth = newDepth
				continue
			}
			key := extractFieldKey(trimmed)
			if key != "" {
				if currentKey != "" {
					fields[currentKey] = currentLines
				}
				currentKey = key
				currentLines = []string{line}
			} else if currentKey != "" {
				currentLines = append(currentLines, line)
			}
		} else if currentKey != "" && newDepth > 0 {
			// Still inside the block; skip the final closing brace
			currentLines = append(currentLines, line)
		}

		depth = newDepth
	}

	if currentKey != "" {
		fields[currentKey] = currentLines
	}

	return fields
}

// mergeHOCONFile merges src HOCON into dst. New top-level blocks are appended.
// New fields inside existing blocks are inserted into those blocks (before closing }).
func mergeHOCONFile(srcPath, dstPath string) FileMergeResult {
	srcData, err := os.ReadFile(srcPath)
	if err != nil {
		return FileMergeResult{Action: "source_missing"}
	}

	srcKeys, err := topLevelHOCONKeys(string(srcData))
	if err != nil {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("parse source hocon: %w", err)}
	}

	var dstKeys map[string]bool
	var dstData []byte
	if data, err := os.ReadFile(dstPath); err == nil {
		dstData = data
		dstKeys, _ = topLevelHOCONKeys(string(dstData))
	} else if !os.IsNotExist(err) {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("read destination: %w", err)}
	}

	// Find new top-level keys in src
	var newKeys []string
	for k := range srcKeys {
		if !dstKeys[k] {
			newKeys = append(newKeys, k)
		}
	}

	// Find new fields inside existing blocks
	srcBlocks := extractHOCONBlocks(string(srcData))
	dstBlocks := extractHOCONBlocks(string(dstData))

	dstFieldKeysMap := make(map[string]map[string]bool, len(dstBlocks))
	for _, b := range dstBlocks {
		fields := extractBlockFieldLines(b.lines)
		keys := make(map[string]bool, len(fields))
		for k := range fields {
			keys[k] = true
		}
		dstFieldKeysMap[b.key] = keys
	}

	// Collect new nested fields by parent block: blockKey -> new field lines
	newFieldsByBlock := make(map[string][]string)
	var addedKeys []string
	addedKeys = append(addedKeys, newKeys...)

	for _, srcBlock := range srcBlocks {
		if !dstKeys[srcBlock.key] {
			continue
		}
		dstFieldKeys, ok := dstFieldKeysMap[srcBlock.key]
		if !ok {
			continue
		}
		srcFieldLines := extractBlockFieldLines(srcBlock.lines)
		for fieldKey, fieldLines := range srcFieldLines {
			if !dstFieldKeys[fieldKey] {
				newFieldsByBlock[srcBlock.key] = append(newFieldsByBlock[srcBlock.key], fieldLines...)
				addedKeys = append(addedKeys, srcBlock.key+"."+fieldKey)
			}
		}
	}

	// If nothing new
	if len(newKeys) == 0 && len(newFieldsByBlock) == 0 {
		if len(dstKeys) == 0 {
			return FileMergeResult{Action: "source_missing"}
		}
		return FileMergeResult{Action: "skipped"}
	}

	// If dst doesn't exist, just copy src verbatim
	if len(dstKeys) == 0 {
		if err := os.WriteFile(dstPath, srcData, 0644); err != nil {
			return FileMergeResult{Action: "error", Error: fmt.Errorf("write destination: %w", err)}
		}
		return FileMergeResult{Action: "created", AddedKeys: newKeys}
	}

	// Start with dst content; insert new nested fields into existing blocks
	content := string(dstData)
	for _, dstBlock := range dstBlocks {
		newFieldLines, hasNew := newFieldsByBlock[dstBlock.key]
		if !hasNew {
			continue
		}
		// Insert new fields before the closing } of the block
		oldBlock := strings.Join(dstBlock.lines, "\n")
		closingBrace := dstBlock.lines[len(dstBlock.lines)-1]
		innerLines := make([]string, 0, len(dstBlock.lines)+len(newFieldLines)+1)
		innerLines = append(innerLines, dstBlock.lines[:len(dstBlock.lines)-1]...)
		innerLines = append(innerLines, "")
		innerLines = append(innerLines, newFieldLines...)
		innerLines = append(innerLines, closingBrace)
		newBlock := strings.Join(innerLines, "\n")
		content = strings.Replace(content, oldBlock, newBlock, 1)
	}

	// Append new top-level blocks
	if len(newKeys) > 0 {
		var toAppend strings.Builder
		for _, block := range srcBlocks {
			if slices.Contains(newKeys, block.key) {
				for _, line := range block.lines {
					toAppend.WriteString(line)
					toAppend.WriteByte('\n')
				}
				toAppend.WriteByte('\n')
			}
		}
		content = strings.TrimRight(content, "\n") + "\n\n" + strings.TrimRight(toAppend.String(), "\n") + "\n"
	}

	if err := os.WriteFile(dstPath, []byte(content), 0644); err != nil {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("write destination: %w", err)}
	}

	return FileMergeResult{Action: "merged", AddedKeys: addedKeys}
}

// subPattern matches ${?VAR} or ${VAR} substitution references.
var subPattern = regexp.MustCompile(`\$\{(\??\w+)}`)

// encodeSubstitutions replaces ${VAR} / ${?VAR} with safe marker strings
// that go-hocon will treat as regular strings.
func encodeSubstitutions(input string) string {
	return subPattern.ReplaceAllStringFunc(input, func(match string) string {
		return fmt.Sprintf(`"__SUB_%s__"`, match)
	})
}

// topLevelHOCONKeys parses HOCON content and returns the set of top-level keys.
func topLevelHOCONKeys(content string) (map[string]bool, error) {
	// Strip comments and encode substitutions before parsing
	clean := stripHOCONComments(content)
	encoded := encodeSubstitutions(clean)
	cfg, err := hocon.ParseString(encoded)
	if err != nil {
		return nil, err
	}
	root, ok := cfg.GetRoot().(hocon.Object)
	if !ok {
		return nil, fmt.Errorf("root is not an object")
	}
	keys := make(map[string]bool, len(root))
	for k := range root {
		keys[k] = true
	}
	return keys, nil
}

// hoconBlock represents a top-level block in a HOCON file.
type hoconBlock struct {
	key   string
	lines []string
}

// extractHOCONBlocks splits HOCON text into top-level blocks by tracking brace depth.
func extractHOCONBlocks(content string) []hoconBlock {
	lines := strings.Split(content, "\n")
	var blocks []hoconBlock
	var current []string
	depth := 0
	inBlock := false

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)

		if !inBlock && (trimmed == "" || strings.HasPrefix(trimmed, "#")) {
			continue
		}

		open, close := countBracesOutsideStrings(line)

		if !inBlock {
			current = []string{line}
			inBlock = true
			depth += open - close
		} else {
			current = append(current, line)
			depth += open - close
		}

		if depth <= 0 && inBlock {
			key := extractKeyFromLines(current)
			if key != "" {
				blocks = append(blocks, hoconBlock{key: key, lines: current})
			}
			inBlock = false
			current = nil
			depth = 0
		}
	}

	return blocks
}

// countBracesOutsideStrings counts { and } outside of double-quoted strings.
func countBracesOutsideStrings(s string) (open, close int) {
	inStr := false
	for i := 0; i < len(s); i++ {
		ch := s[i]
		if ch == '"' {
			backslashes := 0
			for j := i - 1; j >= 0 && s[j] == '\\'; j-- {
				backslashes++
			}
			if backslashes%2 == 0 {
				inStr = !inStr
			}
			continue
		}
		if inStr {
			continue
		}
		if ch == '{' {
			open++
		} else if ch == '}' {
			close++
		}
	}
	return
}

// extractKeyFromLines extracts the key from the first line of a HOCON block.
func extractKeyFromLines(lines []string) string {
	if len(lines) == 0 {
		return ""
	}
	first := strings.TrimSpace(lines[0])
	key := extractFieldKey(first)
	if key == "" {
		return first
	}
	return key
}

// stripHOCONComments removes # and // comments from HOCON text.
func stripHOCONComments(input string) string {
	var out strings.Builder
	out.Grow(len(input))
	inString := false
	inTripleQuote := false

	for i := 0; i < len(input); i++ {
		ch := input[i]

		if !inString && !inTripleQuote && i+2 < len(input) && input[i] == '"' && input[i+1] == '"' && input[i+2] == '"' {
			inTripleQuote = true
			out.WriteString("\"\"\"")
			i += 2
			continue
		}
		if inTripleQuote {
			out.WriteByte(ch)
			if i+2 < len(input) && input[i] == '"' && input[i+1] == '"' && input[i+2] == '"' {
				inTripleQuote = false
				i += 2
				out.WriteString("\"\"")
			}
			continue
		}

		if ch == '"' && !inString {
			inString = true
			out.WriteByte(ch)
			continue
		}
		if inString {
			out.WriteByte(ch)
			if ch == '"' && (i == 0 || input[i-1] != '\\') {
				inString = false
			}
			continue
		}

		if ch == '#' || (ch == '/' && i+1 < len(input) && input[i+1] == '/') {
			for i < len(input) && input[i] != '\n' {
				i++
			}
			if i < len(input) {
				out.WriteByte('\n')
			}
			continue
		}

		out.WriteByte(ch)
	}
	return out.String()
}

// mergeEnvFile merges src .env into dst by appending variables that don't exist in dst.
func mergeEnvFile(srcPath, dstPath string) FileMergeResult {
	srcData, err := os.ReadFile(srcPath)
	if err != nil {
		return FileMergeResult{Action: "source_missing"}
	}

	var dstKeys map[string]bool
	if dstData, err := os.ReadFile(dstPath); err == nil {
		dstKeys = parseEnvKeys(string(dstData))
	} else if !os.IsNotExist(err) {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("read destination: %w", err)}
	}

	// If dst doesn't exist, just copy
	if len(dstKeys) == 0 {
		if err := os.WriteFile(dstPath, srcData, 0644); err != nil {
			return FileMergeResult{Action: "error", Error: fmt.Errorf("write destination: %w", err)}
		}
		return FileMergeResult{Action: "created"}
	}

	srcLines := strings.Split(string(srcData), "\n")
	var toAdd []string
	for _, line := range srcLines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		m := strings.IndexByte(trimmed, '=')
		if m < 0 {
			continue
		}
		key := strings.TrimSpace(trimmed[:m])
		if !dstKeys[key] {
			toAdd = append(toAdd, line)
		}
	}

	if len(toAdd) == 0 {
		return FileMergeResult{Action: "skipped"}
	}

	f, err := os.OpenFile(dstPath, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("open destination for append: %w", err)}
	}
	defer f.Close()

	if _, err := f.WriteString("\n" + strings.Join(toAdd, "\n") + "\n"); err != nil {
		return FileMergeResult{Action: "error", Error: fmt.Errorf("append to destination: %w", err)}
	}
	return FileMergeResult{Action: "merged", AddedKeys: toAdd}
}

// parseEnvKeys extracts variable names from .env content.
func parseEnvKeys(content string) map[string]bool {
	keys := make(map[string]bool)
	for _, line := range strings.Split(content, "\n") {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		m := strings.IndexByte(trimmed, '=')
		if m < 0 {
			continue
		}
		key := strings.TrimSpace(trimmed[:m])
		if key != "" {
			keys[key] = true
		}
	}
	return keys
}

// copyNewFilesRecursively copies files from srcDir to dstDir that don't already exist in dstDir.
// Returns empty results if srcDir does not exist.
func copyNewFilesRecursively(srcDir, dstDir string) ([]FileMergeResult, error) {
	if _, err := os.Stat(srcDir); os.IsNotExist(err) {
		return nil, nil
	}

	var results []FileMergeResult

	err := filepath.WalkDir(srcDir, func(srcPath string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(srcDir, srcPath)
		if err != nil {
			return err
		}
		dstPath := filepath.Join(dstDir, rel)

		if d.IsDir() {
			if err := os.MkdirAll(dstPath, 0755); err != nil {
				return fmt.Errorf("mkdir %s: %w", dstPath, err)
			}
			return nil
		}

		if _, err := os.Stat(dstPath); os.IsNotExist(err) {
			data, err := os.ReadFile(srcPath)
			if err != nil {
				return fmt.Errorf("read %s: %w", srcPath, err)
			}
			if err := os.WriteFile(dstPath, data, 0644); err != nil {
				return fmt.Errorf("write %s: %w", dstPath, err)
			}
			results = append(results, FileMergeResult{Action: "created", AddedKeys: []string{rel}})
		} else {
			results = append(results, FileMergeResult{Action: "skipped"})
		}
		return nil
	})

	return results, err
}
