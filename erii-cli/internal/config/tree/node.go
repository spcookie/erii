package tree

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

func (b *BranchNode) Title() string          { return b.title }
func (b *BranchNode) SetTitle(t string)      { b.title = t }
func (b *BranchNode) Description() string    { return b.description }
func (b *BranchNode) IsLeaf() bool           { return false }
func (b *BranchNode) IsArray() bool          { return b.isArray }
func (b *BranchNode) SetIsArray(v bool)      { b.isArray = v }
func (b *BranchNode) Children() []ConfigNode { return b.children }
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
}

func NewLeaf(title, description string, vt ValueType, value any) *LeafNode {
	return &LeafNode{
		title:       title,
		description: description,
		valueType:   vt,
		value:       value,
	}
}

func (l *LeafNode) Title() string            { return l.title }
func (l *LeafNode) SetTitle(t string)        { l.title = t }
func (l *LeafNode) Description() string      { return l.description }
func (l *LeafNode) IsLeaf() bool             { return true }
func (l *LeafNode) ValueType() ValueType     { return l.valueType }
func (l *LeafNode) Value() any               { return l.value }
func (l *LeafNode) SetValue(v any)           { l.value = v }
func (l *LeafNode) Options() []string        { return l.options }
func (l *LeafNode) SetOptions(opts []string) { l.options = opts }
func (l *LeafNode) Placeholder() string      { return l.placeholder }
func (l *LeafNode) SetPlaceholder(p string)  { l.placeholder = p }
