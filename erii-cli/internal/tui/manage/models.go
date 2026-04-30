package manage

// ── Bot / Group (shared with stats) ──

type BotInfo struct {
	BotID   string `json:"botId"`
	BotName string `json:"botName"`
}

type GroupInfo struct {
	GroupID   string `json:"groupId"`
	GroupName string `json:"groupName"`
}

// ── Facts ──

type FactRecord struct {
	ID          int     `json:"id"`
	BotMark     string  `json:"botMark"`
	GroupID     string  `json:"groupId"`
	Keyword     string  `json:"keyword"`
	Description string  `json:"description"`
	Values      string  `json:"values"`
	Subjects    string  `json:"subjects"`
	ScopeType   string  `json:"scopeType"`
	CreatedAt   string  `json:"createdAt"`
	ValidFrom   string  `json:"validFrom"`
	ValidTo     *string `json:"validTo"`
	VectorID    *string `json:"vectorId"`
}

type FactRequest struct {
	Keyword     string `json:"keyword"`
	Description string `json:"description"`
	Values      string `json:"values"`
	Subjects    string `json:"subjects"`
	ScopeType   string `json:"scopeType"`
}

// ── User Profiles ──

type UserProfileRecord struct {
	ID          int    `json:"id"`
	BotMark     string `json:"botMark"`
	GroupID     string `json:"groupId"`
	UserID      string `json:"userId"`
	Profile     string `json:"profile"`
	Preferences string `json:"preferences"`
	CreatedAt   string `json:"createdAt"`
}

type UpdateUserProfileRequest struct {
	Profile     string `json:"profile"`
	Preferences string `json:"preferences"`
}

// ── Memes ──

type MemeRecord struct {
	ID                int     `json:"id"`
	BotID             string  `json:"botId"`
	GroupID           string  `json:"groupId"`
	ResourceID        int     `json:"resourceId"`
	Md5               string  `json:"md5"`
	SeenCount         int     `json:"seenCount"`
	LastAnalyzedCount int     `json:"lastAnalyzedCount"`
	Description       *string `json:"description"`
	Purpose           *string `json:"purpose"`
	Tags              *string `json:"tags"`
	UsageCount        int     `json:"usageCount"`
	CreatedAt         string  `json:"createdAt"`
	UpdatedAt         string  `json:"updatedAt"`
}

type UpdateMemeRequest struct {
	Description *string `json:"description"`
	Purpose     *string `json:"purpose"`
	Tags        *string `json:"tags"`
}

// ── Vocabulary ──

type VocabRecord struct {
	ID        int    `json:"id"`
	BotMark   string `json:"botMark"`
	GroupID   string `json:"groupId"`
	Word      string `json:"word"`
	Type      string `json:"type"`
	Meaning   string `json:"meaning"`
	Example   string `json:"example"`
	Weight    int    `json:"weight"`
	LastSeen  string `json:"lastSeen"`
	CreatedAt string `json:"createdAt"`
}

type VocabRequest struct {
	Word    string `json:"word"`
	Type    string `json:"type"`
	Meaning string `json:"meaning"`
	Example string `json:"example"`
	Weight  int    `json:"weight"`
}

// ── Summaries ──

type SummaryRecord struct {
	ID               int     `json:"id"`
	BotMark          string  `json:"botMark"`
	GroupID          string  `json:"groupId"`
	TimeRange        string  `json:"timeRange"`
	Content          string  `json:"content"`
	KeyPoints        string  `json:"keyPoints"`
	EmotionalTone    *string `json:"emotionalTone"`
	ParticipantCount int     `json:"participantCount"`
	MessageCount     int     `json:"messageCount"`
	CreatedAt        string  `json:"createdAt"`
}

type UpdateSummaryRequest struct {
	TimeRange        string  `json:"timeRange"`
	Content          string  `json:"content"`
	KeyPoints        string  `json:"keyPoints"`
	EmotionalTone    *string `json:"emotionalTone"`
	ParticipantCount int     `json:"participantCount"`
	MessageCount     int     `json:"messageCount"`
}

// ── Resource Type Enum ──

type ResourceType int

const (
	ResourceFacts ResourceType = iota
	ResourceProfiles
	ResourceMemes
	ResourceVocabularies
	ResourceSummaries
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
