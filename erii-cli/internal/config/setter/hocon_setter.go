package setter

import (
	"fmt"
	"os"
	"regexp"
	"strings"

	"github.com/gurkankaymak/hocon"
)

// HoconSet sets a value at keyPath in a HOCON file.
// Uses text-based editing to preserve original formatting and key order.
// Substitution references (${VAR} / ${?VAR}) are preserved.
func HoconSet(filePath, keyPath, rawValue string) error {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read hocon: %w", err)
	}

	v, err := ParseHOCONValue(rawValue)
	if err != nil {
		return fmt.Errorf("parse value: %w", err)
	}

	text := string(data)
	keys := strings.Split(keyPath, ".")

	result, err := setInHOCONText(text, keys, v)
	if err != nil {
		return fmt.Errorf("set %s: %w", keyPath, err)
	}

	return os.WriteFile(filePath, []byte(result), 0644)
}

// HoconGet retrieves the value at keyPath from a HOCON file.
func HoconGet(filePath, keyPath string) (string, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return "", fmt.Errorf("read hocon: %w", err)
	}

	clean := stripComments(string(data))
	encoded, substs := encodeSubstitutions(clean)

	conf, err := hocon.ParseString(encoded)
	if err != nil {
		return "", fmt.Errorf("parse hocon: %w", err)
	}

	obj, ok := conf.GetRoot().(hocon.Object)
	if !ok {
		return "", fmt.Errorf("root is not an object")
	}

	value := findHOCONPath(obj, strings.Split(keyPath, "."))
	if value == nil {
		return "", fmt.Errorf("key not found: %s", keyPath)
	}
	result := formatHOCONValue(value)
	result = decodeSubstitutions(result, substs)
	return result, nil
}

// --- Text-based HOCON editing (preserves formatting, key order, comments) ---

// setInHOCONText finds keyPath in the HOCON text and replaces its value with v.
func setInHOCONText(text string, keys []string, v hocon.Value) (string, error) {
	scan := &hoconTextScanner{text: text}
	span, err := scan.findKey(keys)
	if err != nil {
		return "", err
	}

	newVal := formatHOCONReplacement(v, span)
	result := text[:span.start] + newVal + text[span.end:]
	return result, nil
}

// valueSpan describes the byte range of a value in the original text.
type valueSpan struct {
	start, end int    // byte offsets in original text
	indent     string // whitespace before the key line
	isBlock    bool   // value is a multi-line { } or [ ] block
}

// hoconTextScanner scans HOCON text to locate a key path.
type hoconTextScanner struct {
	text string
	pos  int
}

// findKey locates the value span for a dot-separated key path.
func (s *hoconTextScanner) findKey(keys []string) (valueSpan, error) {
	// pathStack tracks keys of open blocks as we navigate.
	// depth from brace counting is authoritative; indentation is a hint.
	pathStack := []string{}
	braceDepth := 0
	keyIdx := 0

	lines := strings.SplitAfter(s.text, "\n")
	lineStart := 0 // byte offset of current line

	for _, line := range lines {
		if line == "" {
			continue
		}
		trimmed := strings.TrimSpace(line)
		lineEnd := lineStart + len(line)

		if trimmed == "" {
			lineStart = lineEnd
			continue
		}

		// Track closing braces before processing the line content.
		closeBraces := countLeadingCloseBraces(trimmed)
		for i := 0; i < closeBraces; i++ {
			braceDepth--
			if len(pathStack) > braceDepth && len(pathStack) > 0 {
				pathStack = pathStack[:len(pathStack)-1]
			}
		}
		if closeBraces > 0 {
			trimmed = strings.TrimSpace(trimmed[closeBraces:])
		}

		if trimmed == "" || trimmed == "{" {
			// Opening brace after key — already counted by isBlock logic below.
			// Or a standalone { line (rare).
			if trimmed == "{" {
				braceDepth++
			}
			lineStart = lineEnd
			continue
		}

		key, _, isBlock := parseHOCONLineKey(trimmed)

		// Calculate depth from indentation to sync pathStack
		lineIndent := countLeadingSpaces(line)
		indentDepth := lineIndent / 2

		// Sync path: pop keys if we're at a shallower depth than the path stack
		for indentDepth < len(pathStack) {
			pathStack = pathStack[:len(pathStack)-1]
		}

		if key == "" {
			lineStart = lineEnd
			continue
		}

		// Build the full path for this line's key
		fullPath := append(append([]string{}, pathStack...), key)

		if isBlock {
			// This key opens a block.
			pathStack = append(pathStack, key)

			// Check if this key is part of our target path.
			if keyIdx < len(keys) && key == keys[keyIdx] {
				if keyIdx == len(keys)-1 {
					// The target key itself opens a block — replace the entire block.
					return s.blockSpan(line, lineStart, lineEnd), nil
				}
				keyIdx++
			}
			braceDepth++
		} else {
			// Leaf key — check if it matches the target.
			if len(fullPath) == len(keys) && matchPath(fullPath, keys) {
				return s.leafSpan(line, lineStart, lineEnd), nil
			}
		}

		lineStart = lineEnd
	}

	return valueSpan{}, fmt.Errorf("key not found: %s", strings.Join(keys, "."))
}

// blockSpan returns the value span for a key whose value is a { } or [ ] block.
func (s *hoconTextScanner) blockSpan(line string, lineStart, lineEnd int) valueSpan {
	eqIdx := findEqualsInLine(line)
	// Find the opening brace/bracket — may be after "=" or directly after key.
	valStart := lineStart + eqIdx + 1
	if eqIdx < 0 {
		valStart = lineStart + findBlockOpen(line)
	}
	for valStart < lineEnd && (line[valStart-lineStart] == ' ' || line[valStart-lineStart] == '\t') {
		valStart++
	}

	openCh := line[valStart-lineStart]
	closeCh := byte('}')
	if openCh == '[' {
		closeCh = ']'
	}

	valEnd := findMatchingClose(s.text, valStart, openCh, closeCh)

	return valueSpan{
		start:   valStart,
		end:     valEnd,
		indent:  strings.Repeat(" ", countLeadingSpaces(line)),
		isBlock: true,
	}
}

// leafSpan returns the value span for a key with an inline (single-line) value.
func (s *hoconTextScanner) leafSpan(line string, lineStart, lineEnd int) valueSpan {
	eqIdx := findEqualsInLine(line)
	valStart := lineStart + eqIdx + 1
	for valStart < lineEnd && (line[valStart-lineStart] == ' ' || line[valStart-lineStart] == '\t') {
		valStart++
	}

	// Value ends at end of line or before an inline comment.
	// We need to find # or // that is not inside a string.
	valEnd := lineEnd
	rest := line[valStart-lineStart:]
	inStr := false
	for i := 0; i < len(rest); i++ {
		if rest[i] == '"' && (i == 0 || rest[i-1] != '\\') {
			inStr = !inStr
		}
		if !inStr {
			if rest[i] == '#' || (rest[i] == '/' && i+1 < len(rest) && rest[i+1] == '/') {
				valEnd = valStart + i
				break
			}
		}
	}
	// Trim trailing whitespace from the value end
	for valEnd > valStart && (s.text[valEnd-1] == ' ' || s.text[valEnd-1] == '\t') {
		valEnd--
	}
	// Don't include trailing newline
	if valEnd > valStart && s.text[valEnd-1] == '\n' {
		valEnd--
	}
	if valEnd > valStart && s.text[valEnd-1] == '\r' {
		valEnd--
	}

	return valueSpan{
		start:   valStart,
		end:     valEnd,
		indent:  strings.Repeat(" ", countLeadingSpaces(line)),
		isBlock: false,
	}
}

// formatHOCONReplacement formats a hocon.Value for text replacement.
// For simple values, returns a single-line string.
// For objects/arrays replacing a non-block span, returns inline format.
// For objects/arrays replacing a block span, returns multi-line format.
func formatHOCONReplacement(v hocon.Value, span valueSpan) string {
	switch val := v.(type) {
	case hocon.Object:
		if span.isBlock {
			return formatObjectBlock(val, span.indent)
		}
		return val.String()
	case hocon.Array:
		return formatArrayInline(val)
	default:
		return formatScalarInline(v)
	}
}

func formatScalarInline(v hocon.Value) string {
	if v == nil {
		return "null"
	}
	switch val := v.(type) {
	case hocon.String:
		s := string(val)
		if after, ok := strings.CutPrefix(s, subMarker); ok {
			return after
		}
		if s == "" {
			return `""`
		}
		if needsHOCONQuote(s) {
			return fmt.Sprintf(`"%s"`, s)
		}
		return s
	case hocon.Int:
		return fmt.Sprintf("%d", int(val))
	case hocon.Boolean:
		return fmt.Sprintf("%v", bool(val))
	case hocon.Float32:
		return fmt.Sprintf("%v", float32(val))
	case hocon.Float64:
		return fmt.Sprintf("%v", float64(val))
	case hocon.Null:
		return "null"
	default:
		return v.String()
	}
}

func formatObjectBlock(obj hocon.Object, baseIndent string) string {
	if len(obj) == 0 {
		return "{}"
	}
	var b strings.Builder
	b.WriteString("{\n")
	childIndent := baseIndent + "  "
	for key, value := range obj {
		if value == nil {
			continue
		}
		b.WriteString(childIndent)
		writeHOCONKey(&b, key)
		b.WriteString(formatScalarInline(value))
		b.WriteByte('\n')
	}
	b.WriteString(baseIndent)
	b.WriteString("}")
	return b.String()
}

func formatArrayInline(arr hocon.Array) string {
	if len(arr) == 0 {
		return "[]"
	}
	var b strings.Builder
	b.WriteByte('[')
	for i, item := range arr {
		if i > 0 {
			b.WriteString(", ")
		}
		switch val := item.(type) {
		case hocon.String:
			b.WriteByte('"')
			b.WriteString(string(val))
			b.WriteByte('"')
		default:
			b.WriteString(item.String())
		}
	}
	b.WriteByte(']')
	return b.String()
}

// --- Line parsing helpers ---

// parseHOCONLineKey extracts the key from a HOCON line and whether it opens a block.
// Handles both "key = {" and "key {" syntax.
// Returns (key, rest_after_equals, is_block_open).
func parseHOCONLineKey(trimmed string) (key string, rest string, isBlock bool) {
	eqIdx := findEqualsInLine(trimmed)
	if eqIdx >= 0 {
		keyPart := strings.TrimSpace(trimmed[:eqIdx])
		key = unquoteHOCONKey(keyPart)
		valPart := strings.TrimSpace(trimmed[eqIdx+1:])
		isBlock = strings.HasPrefix(valPart, "{") || strings.HasPrefix(valPart, "[")
		return key, valPart, isBlock
	}

	// No = sign — handle "key {" or "key [" syntax.
	blockIdx := findBlockOpen(trimmed)
	if blockIdx >= 0 {
		keyPart := strings.TrimSpace(trimmed[:blockIdx])
		key = unquoteHOCONKey(keyPart)
		rest = strings.TrimSpace(trimmed[blockIdx:])
		return key, rest, true
	}

	return "", "", false
}

// findBlockOpen finds the first '{' or '[' outside a quoted string.
func findBlockOpen(s string) int {
	inStr := false
	for i := 0; i < len(s); i++ {
		ch := s[i]
		if ch == '"' && (i == 0 || s[i-1] != '\\') {
			inStr = !inStr
		}
		if !inStr && (ch == '{' || ch == '[') {
			return i
		}
	}
	return -1
}

// findEqualsInLine finds the index of the first '=' outside of a quoted string.
func findEqualsInLine(line string) int {
	inStr := false
	for i := 0; i < len(line); i++ {
		ch := line[i]
		if ch == '"' && (i == 0 || line[i-1] != '\\') {
			inStr = !inStr
		}
		if !inStr && ch == '=' {
			return i
		}
	}
	return -1
}

// unquoteHOCONKey strips surrounding quotes from a HOCON key.
func unquoteHOCONKey(s string) string {
	if len(s) >= 2 && s[0] == '"' && s[len(s)-1] == '"' {
		return s[1 : len(s)-1]
	}
	return s
}

// findMatchingClose finds the position of the matching close brace/bracket.
// openIdx is the position of the opening character in text.
func findMatchingClose(text string, openIdx int, openCh, closeCh byte) int {
	depth := 1
	inStr := false
	for i := openIdx + 1; i < len(text); i++ {
		ch := text[i]
		if ch == '"' && (i == 0 || text[i-1] != '\\') {
			inStr = !inStr
		}
		if inStr {
			continue
		}
		if ch == openCh {
			depth++
		} else if ch == closeCh {
			depth--
			if depth == 0 {
				return i + 1
			}
		}
	}
	return len(text)
}

// countLeadingSpaces counts leading spaces/tabs in a line.
func countLeadingSpaces(line string) int {
	n := 0
	for n < len(line) && (line[n] == ' ' || line[n] == '\t') {
		n++
	}
	return n
}

// countLeadingCloseBraces counts leading '}' characters in trimmed text.
func countLeadingCloseBraces(trimmed string) int {
	n := 0
	for n < len(trimmed) && trimmed[n] == '}' {
		n++
	}
	return n
}

// matchPath checks if two string slices are equal.
func matchPath(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// --- Substitution handling ---

// subPattern matches ${?VAR} or ${VAR} substitution references.
var subPattern = regexp.MustCompile(`\$\{(\??\w+)}`)

// encodeSubstitutions replaces ${VAR} / ${?VAR} with safe marker strings
// that go-hocon will treat as regular strings. Returns the encoded text
// and a map from marker → original for decoding.
func encodeSubstitutions(input string) (string, map[string]string) {
	substs := make(map[string]string)
	counter := 0

	encoded := subPattern.ReplaceAllStringFunc(input, func(match string) string {
		marker := fmt.Sprintf("__SUB_%d__", counter)
		substs[marker] = match
		counter++
		return fmt.Sprintf(`"%s"`, marker)
	})
	return encoded, substs
}

// decodeSubstitutions reverses encodeSubstitutions, restoring original ${} syntax.
func decodeSubstitutions(input string, substs map[string]string) string {
	for marker, original := range substs {
		input = strings.ReplaceAll(input, fmt.Sprintf(`"%s"`, marker), original)
		input = strings.ReplaceAll(input, marker, original)
	}
	return input
}

// stripComments removes HOCON comments from input.
func stripComments(input string) string {
	var out strings.Builder
	out.Grow(len(input))

	inString := false
	inTripleQuote := false
	stringChar := byte(0)

	for i := 0; i < len(input); i++ {
		ch := input[i]

		if !inString && i+2 < len(input) && input[i] == '"' && input[i+1] == '"' && input[i+2] == '"' {
			if !inTripleQuote {
				inTripleQuote = true
				out.WriteString(`"""`)
				i += 2
				continue
			}
		}
		if inTripleQuote {
			out.WriteByte(ch)
			if i+2 < len(input) && input[i] == '"' && input[i+1] == '"' && input[i+2] == '"' {
				inTripleQuote = false
				i += 2
				out.WriteString(`""`)
			}
			continue
		}

		if ch == '"' && !inString {
			inString = true
			stringChar = '"'
			out.WriteByte(ch)
			continue
		}
		if inString {
			out.WriteByte(ch)
			if ch == stringChar && (i == 0 || input[i-1] != '\\') {
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

// --- Shared helpers (used by both text-based set and go-hocon get) ---

func setHOCONPath(obj hocon.Object, keys []string, v hocon.Value) error {
	for i := 0; i < len(keys)-1; i++ {
		existing, ok := obj[keys[i]]
		if ok {
			if child, isObj := existing.(hocon.Object); isObj {
				obj = child
				continue
			}
			obj[keys[i]] = hocon.Object{}
		} else {
			obj[keys[i]] = hocon.Object{}
		}
		obj = obj[keys[i]].(hocon.Object)
	}
	obj[keys[len(keys)-1]] = v
	return nil
}

func findHOCONPath(obj hocon.Object, keys []string) hocon.Value {
	for i := 0; i < len(keys)-1; i++ {
		child, ok := obj[keys[i]]
		if !ok {
			return nil
		}
		childObj, ok := child.(hocon.Object)
		if !ok {
			return nil
		}
		obj = childObj
	}
	return obj[keys[len(keys)-1]]
}

func writeHOCONObject(b *strings.Builder, obj hocon.Object, depth int) {
	if len(obj) == 0 {
		_, _ = fmt.Fprint(b, strings.Repeat("  ", depth))
		b.WriteString("{}\n")
		return
	}
	indent := strings.Repeat("  ", depth)
	childIndent := strings.Repeat("  ", depth+1)

	b.WriteString(indent)
	b.WriteString("{\n")

	for key, value := range obj {
		if value == nil {
			continue
		}
		b.WriteString(childIndent)
		writeHOCONKey(b, key)
		writeHOCONValue(b, value, depth+1)
	}

	b.WriteString(indent)
	b.WriteString("}\n")
}

func writeHOCONKey(b *strings.Builder, key string) {
	if needsHOCONQuote(key) {
		b.WriteByte('"')
		b.WriteString(key)
		b.WriteByte('"')
	} else {
		b.WriteString(key)
	}
	b.WriteString(" = ")
}

func needsHOCONQuote(s string) bool {
	if s == "" {
		return true
	}
	if s[0] >= '0' && s[0] <= '9' || s[0] == '-' {
		return true
	}
	switch s {
	case "true", "false", "null", "yes", "no", "on", "off", "include":
		return true
	}
	for _, ch := range s {
		switch {
		case ch >= 'a' && ch <= 'z':
		case ch >= 'A' && ch <= 'Z':
		case ch >= '0' && ch <= '9':
		case ch == '_':
		default:
			return true
		}
	}
	return false
}

func writeHOCONValue(b *strings.Builder, v hocon.Value, depth int) {
	if v == nil {
		b.WriteString("null\n")
		return
	}
	switch val := v.(type) {
	case hocon.Object:
		b.WriteByte('\n')
		writeHOCONObject(b, val, depth)
	case hocon.Array:
		writeHOCONArray(b, val)
		b.WriteByte('\n')
	case hocon.Boolean:
		if bool(val) {
			b.WriteString("true\n")
		} else {
			b.WriteString("false\n")
		}
	case hocon.Int:
		_, _ = fmt.Fprintf(b, "%d\n", int(val))
	case hocon.Float32:
		_, _ = fmt.Fprintf(b, "%v\n", float32(val))
	case hocon.Float64:
		_, _ = fmt.Fprintf(b, "%v\n", float64(val))
	case *hocon.Substitution:
		b.WriteString(v.String())
		b.WriteByte('\n')
	case hocon.String:
		s := string(val)
		if after, ok := strings.CutPrefix(s, subMarker); ok {
			b.WriteString(after)
			b.WriteByte('\n')
		} else if s == "" {
			b.WriteString("\"\"\n")
		} else if strings.Contains(s, "\n") {
			_, _ = fmt.Fprintf(b, `"""%s"""`+"\n", s)
		} else if needsHOCONQuote(s) {
			_, _ = fmt.Fprintf(b, `"%s"`+"\n", s)
		} else {
			_, _ = fmt.Fprintf(b, "%s\n", s)
		}
	case hocon.Null:
		b.WriteString("null\n")
	default:
		b.WriteString(v.String())
		b.WriteByte('\n')
	}
}

func writeHOCONArray(b *strings.Builder, arr hocon.Array) {
	if len(arr) == 0 {
		b.WriteString("[]")
		return
	}
	b.WriteByte('[')
	for i, item := range arr {
		if i > 0 {
			b.WriteString(", ")
		}
		switch val := item.(type) {
		case hocon.String:
			b.WriteByte('"')
			b.WriteString(string(val))
			b.WriteByte('"')
		default:
			b.WriteString(item.String())
		}
	}
	b.WriteByte(']')
}

func formatHOCONValue(v hocon.Value) string {
	if v == nil {
		return "null"
	}
	switch val := v.(type) {
	case hocon.Object:
		return val.String()
	case hocon.Array:
		return val.String()
	case hocon.Boolean:
		return fmt.Sprintf("%v", bool(val))
	case hocon.Int:
		return fmt.Sprintf("%d", int(val))
	case hocon.Float64:
		return fmt.Sprintf("%v", float64(val))
	case *hocon.Substitution:
		return val.String()
	case hocon.String:
		if after, ok := strings.CutPrefix(string(val), subMarker); ok {
			return after
		}
		return string(val)
	case hocon.Null:
		return "null"
	default:
		return v.String()
	}
}
