package tree

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Parser parses a config file into a ConfigNode tree.
type Parser interface {
	Parse(path string) (ConfigNode, error)
	Save(path string, root ConfigNode) error
}

// DetectParser returns a parser based on file extension.
func DetectParser(path string) Parser {
	lower := strings.ToLower(path)
	switch {
	case strings.HasSuffix(lower, ".json"):
		return &JSONParser{}
	case strings.HasSuffix(lower, ".properties"):
		return &PropertiesParser{}
	case strings.HasSuffix(lower, ".env") || strings.HasSuffix(lower, ".env.local"):
		return &EnvParser{}
	case strings.HasSuffix(lower, ".conf"):
		return &HOCONParser{}
	default:
		return &EnvParser{}
	}
}

// JSONParser parses JSON config files.
type JSONParser struct{}

func (p *JSONParser) Parse(path string) (ConfigNode, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return NewBranch("root", "Configuration root", nil), nil
		}
		return nil, err
	}
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, err
	}
	return mapToNode("root", "Configuration root", raw), nil
}

func (p *JSONParser) Save(path string, root ConfigNode) error {
	m := nodeToMap(root)
	data, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}

func mapToNode(title, desc string, m map[string]any) ConfigNode {
	branch := NewBranch(title, desc)
	for k, v := range m {
		branch.AddChild(valueToNode(k, "", v))
	}
	return branch
}

func valueToNode(key, desc string, v any) ConfigNode {
	switch val := v.(type) {
	case map[string]any:
		return mapToNode(key, "Object", val)
	case []any:
		if len(val) == 0 {
			leaf := NewLeaf(key, "Empty array", TypeArray, []string{})
			return leaf
		}
		// Check if array of primitives or objects
		if _, ok := val[0].(map[string]any); ok {
			branch := NewBranch(key, fmt.Sprintf("Array (%d items)", len(val)))
			branch.SetIsArray(true)
			for i, item := range val {
				if m, ok := item.(map[string]any); ok {
					child := mapToNode(fmt.Sprintf("[%d]", i), "", m)
					if cb, ok := child.(*BranchNode); ok {
						cb.SetIsArray(false)
					}
					branch.AddChild(child)
				}
			}
			return branch
		}
		// Array of primitives
		var strs []string
		for _, item := range val {
			strs = append(strs, fmt.Sprintf("%v", item))
		}
		leaf := NewLeaf(key, "Array of values", TypeArray, strs)
		return leaf
	case string:
		leaf := NewLeaf(key, desc, TypeString, val)
		if len(val) > 100 {
			leaf.valueType = TypeText
		}
		return leaf
	case float64:
		return NewLeaf(key, desc, TypeNumber, val)
	case bool:
		return NewLeaf(key, desc, TypeBool, val)
	case nil:
		return NewLeaf(key, desc, TypeString, "")
	default:
		return NewLeaf(key, desc, TypeString, fmt.Sprintf("%v", v))
	}
}

func nodeToMap(node ConfigNode) map[string]any {
	result := make(map[string]any)
	if branch, ok := node.(*BranchNode); ok {
		for _, child := range branch.Children() {
			if leaf, ok := child.(*LeafNode); ok {
				result[leaf.Title()] = leaf.Value()
			} else if b, ok := child.(*BranchNode); ok {
				if b.IsArray() {
					var arr []any
					for _, c := range b.Children() {
						if cb, ok := c.(*BranchNode); ok {
							arr = append(arr, nodeToMap(cb))
						} else if cl, ok := c.(*LeafNode); ok {
							arr = append(arr, cl.Value())
						}
					}
					result[b.Title()] = arr
				} else {
					result[b.Title()] = nodeToMap(b)
				}
			}
		}
	}
	return result
}

// PropertiesParser parses .properties files.
type PropertiesParser struct{}

func (p *PropertiesParser) Parse(path string) (ConfigNode, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return NewBranch("root", "Properties configuration", nil), nil
		}
		return nil, err
	}
	root := NewBranch("root", "Properties configuration")
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx == -1 {
			continue
		}
		key := strings.TrimSpace(line[:idx])
		val := strings.TrimSpace(line[idx+1:])
		root.AddChild(NewLeaf(key, "", TypeString, val))
	}
	return root, scanner.Err()
}

func (p *PropertiesParser) Save(path string, root ConfigNode) error {
	var lines []string
	if branch, ok := root.(*BranchNode); ok {
		for _, child := range branch.Children() {
			if leaf, ok := child.(*LeafNode); ok {
				lines = append(lines, fmt.Sprintf("%s=%v", leaf.Title(), leaf.Value()))
			}
		}
	}
	return os.WriteFile(path, []byte(strings.Join(lines, "\n")+"\n"), 0644)
}

// EnvParser parses .env files.
type EnvParser struct{}

func (p *EnvParser) Parse(path string) (ConfigNode, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return NewBranch("root", "Environment variables", nil), nil
		}
		return nil, err
	}
	root := NewBranch("root", "Environment variables")
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx == -1 {
			continue
		}
		key := strings.TrimSpace(line[:idx])
		val := strings.TrimSpace(line[idx+1:])
		root.AddChild(NewLeaf(key, "", TypeString, val))
	}
	return root, scanner.Err()
}

func (p *EnvParser) Save(path string, root ConfigNode) error {
	// Preserve existing comments and structure if possible.
	var lines []string
	var written = make(map[string]bool)

	if data, err := os.ReadFile(path); err == nil {
		scanner := bufio.NewScanner(strings.NewReader(string(data)))
		for scanner.Scan() {
			line := scanner.Text()
			trimmed := strings.TrimSpace(line)
			if trimmed == "" || strings.HasPrefix(trimmed, "#") {
				lines = append(lines, line)
				continue
			}
			idx := strings.IndexByte(trimmed, '=')
			if idx == -1 {
				lines = append(lines, line)
				continue
			}
			key := strings.TrimSpace(trimmed[:idx])
			if val := getLeafValue(root, key); val != nil {
				lines = append(lines, fmt.Sprintf("%s=%v", key, val))
				written[key] = true
			} else {
				lines = append(lines, line)
			}
		}
	}

	// Append new keys
	if branch, ok := root.(*BranchNode); ok {
		for _, child := range branch.Children() {
			if leaf, ok := child.(*LeafNode); ok {
				if !written[leaf.Title()] {
					lines = append(lines, fmt.Sprintf("%s=%v", leaf.Title(), leaf.Value()))
				}
			}
		}
	}

	return os.WriteFile(path, []byte(strings.Join(lines, "\n")+"\n"), 0644)
}

func getLeafValue(root ConfigNode, key string) any {
	if branch, ok := root.(*BranchNode); ok {
		for _, child := range branch.Children() {
			if leaf, ok := child.(*LeafNode); ok && leaf.Title() == key {
				return leaf.Value()
			}
		}
	}
	return nil
}

// HOCONParser wraps the existing HOCON config into the tree model.
type HOCONParser struct{}

func (p *HOCONParser) Parse(path string) (ConfigNode, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return NewBranch("root", "HOCON configuration", nil), nil
		}
		return nil, err
	}
	return parseHOCON(string(data)), nil
}

func (p *HOCONParser) Save(path string, root ConfigNode) error {
	// For now, serialize as HOCON-like format.
	var b strings.Builder
	writeHOCON(&b, root, 0)
	return os.WriteFile(path, []byte(b.String()), 0644)
}

func parseHOCON(data string) ConfigNode {
	root := NewBranch("root", "Application configuration")
	stack := []*BranchNode{root}
	lines := strings.Split(data, "\n")

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		if idx := strings.IndexByte(trimmed, '#'); idx >= 0 {
			trimmed = strings.TrimSpace(trimmed[:idx])
		}
		if trimmed == "" {
			continue
		}
		if trimmed == "}" {
			if len(stack) > 1 {
				stack = stack[:len(stack)-1]
			}
			continue
		}
		if strings.HasSuffix(trimmed, "{") {
			key := strings.TrimSpace(strings.TrimSuffix(trimmed, "{"))
			key = strings.TrimSuffix(key, "=")
			key = strings.TrimSpace(key)
			node := NewBranch(key, "Configuration section")
			stack[len(stack)-1].AddChild(node)
			stack = append(stack, node)
			continue
		}
		if idx := strings.IndexByte(trimmed, '='); idx != -1 {
			key := strings.TrimSpace(trimmed[:idx])
			val := strings.TrimSpace(trimmed[idx+1:])
			leaf := guessLeaf(key, val)
			stack[len(stack)-1].AddChild(leaf)
		}
	}
	return root
}

func guessLeaf(key, val string) *LeafNode {
	val = strings.Trim(val, `"`)
	if val == "null" {
		return NewLeaf(key, "", TypeString, "")
	}
	if val == "true" || val == "false" {
		return NewLeaf(key, "", TypeBool, val == "true")
	}
	if strings.HasPrefix(val, "[") && strings.HasSuffix(val, "]") {
		inner := strings.Trim(val, "[]")
		if inner == "" {
			return NewLeaf(key, "", TypeArray, []string{})
		}
		parts := strings.Split(inner, ",")
		var items []string
		for _, p := range parts {
			p = strings.TrimSpace(p)
			p = strings.Trim(p, `"`)
			if p != "" {
				items = append(items, p)
			}
		}
		return NewLeaf(key, "", TypeArray, items)
	}
	if _, err := strconv.ParseFloat(val, 64); err == nil {
		f, _ := strconv.ParseFloat(val, 64)
		return NewLeaf(key, "", TypeNumber, f)
	}
	leaf := NewLeaf(key, "", TypeString, val)
	if len(val) > 100 {
		leaf.valueType = TypeText
	}
	return leaf
}

func writeHOCON(b *strings.Builder, node ConfigNode, depth int) {
	indent := strings.Repeat("  ", depth)
	if branch, ok := node.(*BranchNode); ok {
		if branch.Title() != "root" {
			b.WriteString(fmt.Sprintf("%s%s {\n", indent, branch.Title()))
			depth++
			indent = strings.Repeat("  ", depth)
		}
		for _, child := range branch.Children() {
			if leaf, ok := child.(*LeafNode); ok {
				b.WriteString(fmt.Sprintf("%s%s = %s\n", indent, leaf.Title(), formatHOCONValue(leaf)))
			} else {
				writeHOCON(b, child, depth)
			}
		}
		if branch.Title() != "root" {
			b.WriteString(fmt.Sprintf("%s}\n", strings.Repeat("  ", depth-1)))
		}
	}
}

func formatHOCONValue(leaf *LeafNode) string {
	switch leaf.ValueType() {
	case TypeBool:
		if leaf.Value().(bool) {
			return "true"
		}
		return "false"
	case TypeNumber:
		switch v := leaf.Value().(type) {
		case float64:
			return strconv.FormatFloat(v, 'f', -1, 64)
		case int:
			return strconv.Itoa(v)
		case int64:
			return strconv.FormatInt(v, 10)
		default:
			return fmt.Sprintf("%v", v)
		}
	case TypeArray:
		var items []string
		switch v := leaf.Value().(type) {
		case []string:
			for _, s := range v {
				items = append(items, fmt.Sprintf(`"%s"`, s))
			}
		case []any:
			for _, a := range v {
				items = append(items, fmt.Sprintf(`"%v"`, a))
			}
		}
		return "[" + strings.Join(items, ", ") + "]"
	case TypeText:
		return fmt.Sprintf(`"""%s"""`, leaf.Value())
	default:
		return fmt.Sprintf(`"%v"`, leaf.Value())
	}
}
