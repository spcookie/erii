package tree

import "strings"

type ValueType int

const (
	TypeString ValueType = iota
	TypeNumber
	TypeBool
	TypeArray
	TypeObject
	TypeEnum
	TypeText
)

func (t ValueType) String() string {
	switch t {
	case TypeString:
		return "string"
	case TypeNumber:
		return "number"
	case TypeBool:
		return "bool"
	case TypeArray:
		return "array"
	case TypeObject:
		return "object"
	case TypeEnum:
		return "enum"
	case TypeText:
		return "text"
	}
	return "unknown"
}

type ConfigNode interface {
	Title() string
	Description() string
	IsLeaf() bool
}

type BranchNode struct {
	title       string
	description string
	children    []ConfigNode
	isArray     bool
}

func NewBranch(title, description string, children ...ConfigNode) *BranchNode {
	return &BranchNode{
		title:       title,
		description: description,
		children:    children,
	}
}

func (b *BranchNode) Title() string           { return b.title }
func (b *BranchNode) SetTitle(t string)       { b.title = t }
func (b *BranchNode) Description() string     { return b.description }
func (b *BranchNode) SetDescription(d string) { b.description = d }
func (b *BranchNode) IsLeaf() bool            { return false }
func (b *BranchNode) IsArray() bool           { return b.isArray }
func (b *BranchNode) SetIsArray(v bool)       { b.isArray = v }
func (b *BranchNode) Children() []ConfigNode  { return b.children }
func (b *BranchNode) AddChild(n ConfigNode) {
	b.children = append(b.children, n)
}
func (b *BranchNode) SetChildren(children []ConfigNode) {
	b.children = children
}
func (b *BranchNode) RemoveChildAt(index int) bool {
	if index < 0 || index >= len(b.children) {
		return false
	}
	b.children = append(b.children[:index], b.children[index+1:]...)
	return true
}

type LeafNode struct {
	title       string
	description string
	valueType   ValueType
	value       any
	options     []string
	placeholder string
	isEnvRef    bool
	isNull      bool
	valueConfig *ValueConfig
}

func NewLeaf(title, description string, vt ValueType, value any) *LeafNode {
	return &LeafNode{
		title:       title,
		description: description,
		valueType:   vt,
		value:       value,
	}
}

func (l *LeafNode) Title() string                 { return l.title }
func (l *LeafNode) SetTitle(t string)             { l.title = t }
func (l *LeafNode) Description() string           { return l.description }
func (l *LeafNode) SetDescription(d string)       { l.description = d }
func (l *LeafNode) IsLeaf() bool                  { return true }
func (l *LeafNode) ValueType() ValueType          { return l.valueType }
func (l *LeafNode) Value() any                    { return l.value }
func (l *LeafNode) SetValue(v any)                { l.value = v }
func (l *LeafNode) Options() []string             { return l.options }
func (l *LeafNode) SetOptions(opts []string)      { l.options = opts }
func (l *LeafNode) Placeholder() string           { return l.placeholder }
func (l *LeafNode) SetPlaceholder(p string)       { l.placeholder = p }
func (l *LeafNode) IsEnvRef() bool                { return l.isEnvRef }
func (l *LeafNode) SetEnvRef(b bool)              { l.isEnvRef = b }
func (l *LeafNode) IsNull() bool                  { return l.isNull }
func (l *LeafNode) SetNull(b bool)                { l.isNull = b }
func (l *LeafNode) ValueConfig() *ValueConfig     { return l.valueConfig }
func (l *LeafNode) SetValueConfig(c *ValueConfig) { l.valueConfig = c }

// CloneNode creates a deep copy of a ConfigNode tree.
// Leaf nodes copy their value, type, env ref status, null status, and metadata.
// Branch nodes copy their children recursively.
func CloneNode(node ConfigNode) ConfigNode {
	if leaf, ok := node.(*LeafNode); ok {
		var emptyVal any
		switch leaf.ValueType() {
		case TypeString, TypeText, TypeEnum:
			emptyVal = ""
		case TypeNumber:
			emptyVal = float64(0)
		case TypeBool:
			emptyVal = false
		case TypeArray:
			emptyVal = []string{}
		case TypeObject:
			emptyVal = map[string]any{}
		}
		val := emptyVal
		if !leaf.IsNull() && leaf.Value() != nil {
			val = leaf.Value()
		}
		newLeaf := NewLeaf(leaf.Title(), leaf.Description(), leaf.ValueType(), val)
		newLeaf.SetNull(leaf.IsNull())
		newLeaf.SetEnvRef(leaf.IsEnvRef())
		newLeaf.SetValueConfig(leaf.ValueConfig())
		if leaf.ValueType() == TypeEnum {
			newLeaf.SetOptions(leaf.Options())
		}
		return newLeaf
	}
	if branch, ok := node.(*BranchNode); ok {
		newBranch := NewBranch(branch.Title(), branch.Description())
		newBranch.SetIsArray(branch.IsArray())
		for _, child := range branch.Children() {
			newBranch.AddChild(CloneNode(child))
		}
		return newBranch
	}
	return nil
}

// FindNodeByPath walks a ConfigNode tree by dot-separated path segments.
// Returns the matched node or nil if any segment is not found.
func FindNodeByPath(root ConfigNode, path string) ConfigNode {
	if path == "" || path == "root" {
		return root
	}
	parts := strings.Split(path, ".")
	current := root
	for _, part := range parts {
		branch, ok := current.(*BranchNode)
		if !ok {
			return nil
		}
		found := false
		for _, child := range branch.Children() {
			if child.Title() == part {
				current = child
				found = true
				break
			}
		}
		if !found {
			return nil
		}
	}
	return current
}

// FindParentByPath walks to the parent branch of the given dot-separated path.
// Returns the parent branch and the last path segment.
func FindParentByPath(root ConfigNode, path string) (*BranchNode, string) {
	if path == "" || path == "root" {
		return nil, ""
	}
	parts := strings.Split(path, ".")
	if len(parts) == 1 {
		if b, ok := root.(*BranchNode); ok {
			return b, parts[0]
		}
		return nil, ""
	}
	parentPath := strings.Join(parts[:len(parts)-1], ".")
	parent := FindNodeByPath(root, parentPath)
	if parent == nil {
		return nil, ""
	}
	if b, ok := parent.(*BranchNode); ok {
		return b, parts[len(parts)-1]
	}
	return nil, ""
}

// fixValueForType converts the leaf value to match its valueType after a type override.
// This fixes the mismatch when e.g. a null value (initially TypeString with "")
// is overridden to TypeArray by value.json metadata.
// Null values are intentionally left untouched — null ≠ empty array.
func (l *LeafNode) fixValueForType() {
	if l.isNull {
		return
	}
	switch l.valueType {
	case TypeArray:
		switch l.value.(type) {
		case []string, []any:
			return // already correct
		}
		l.value = []string{}
	case TypeObject:
		if _, ok := l.value.(map[string]any); ok {
			return
		}
		l.value = map[string]any{}
	}
}
