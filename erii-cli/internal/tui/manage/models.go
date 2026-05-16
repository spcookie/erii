package manage

// ── Resource Type Enum ──

type ResourceType int

const (
	ResourceFacts ResourceType = iota
	ResourceProfiles
	ResourceMemes
	ResourceVocabularies
	ResourceSummaries
	ResourceHistory
	ResourceResource
)

func (r ResourceType) String() string {
	switch r {
	case ResourceFacts:
		return "facts"
	case ResourceProfiles:
		return "user-profiles"
	case ResourceMemes:
		return "memes"
	case ResourceVocabularies:
		return "vocabulary"
	case ResourceSummaries:
		return "summaries"
	case ResourceHistory:
		return "history"
	case ResourceResource:
		return "resources"
	default:
		return "unknown"
	}
}

func (r ResourceType) Title() string {
	switch r {
	case ResourceFacts:
		return "Memory (Facts)"
	case ResourceProfiles:
		return "User Profiles"
	case ResourceMemes:
		return "Memes"
	case ResourceVocabularies:
		return "Vocabulary"
	case ResourceSummaries:
		return "Summaries"
	case ResourceHistory:
		return "History"
	case ResourceResource:
		return "Resources"
	default:
		return "Unknown"
	}
}

func (r ResourceType) Icon() string {
	switch r {
	case ResourceFacts:
		return "\xf0\x9f\xa7\xa0"
	case ResourceProfiles:
		return "\xf0\x9f\x91\xa4"
	case ResourceMemes:
		return "\xf0\x9f\x96\xbc\xef\xb8\x8f"
	case ResourceVocabularies:
		return "\xf0\x9f\x94\xa5"
	case ResourceSummaries:
		return "\xf0\x9f\x93\x9d"
	case ResourceHistory:
		return "\xf0\x9f\x93\x9c"
	case ResourceResource:
		return "\xf0\x9f\x93\x8e"
	default:
		return "?"
	}
}

func (r ResourceType) CanCreate() bool {
	switch r {
	case ResourceFacts, ResourceVocabularies:
		return true
	default:
		return false
	}
}

// ── State type enum ──

type StateType int

const (
	StateEmotion StateType = iota
	StateFlow
	StateVolition
)

func (s StateType) String() string {
	switch s {
	case StateEmotion:
		return "emotion"
	case StateFlow:
		return "flow"
	case StateVolition:
		return "volition"
	default:
		return "unknown"
	}
}

func (s StateType) Title() string {
	switch s {
	case StateEmotion:
		return "Emotion"
	case StateFlow:
		return "Flow"
	case StateVolition:
		return "Volition"
	default:
		return "Unknown"
	}
}

func (s StateType) Icon() string {
	switch s {
	case StateEmotion:
		return "\xf0\x9f\x92\x96"
	case StateFlow:
		return "\xf0\x9f\x8c\x8a"
	case StateVolition:
		return "\xe2\x9a\xa1"
	default:
		return "?"
	}
}
