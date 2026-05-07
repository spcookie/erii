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
	TimeRange     string  `json:"timeRange"`
	Content       string  `json:"content"`
	KeyPoints     string  `json:"keyPoints"`
	EmotionalTone *string `json:"emotionalTone"`
}

// ── History ──

type HistoryRecord struct {
	ID          int            `json:"id"`
	BotMark     string         `json:"botMark"`
	GroupID     string         `json:"groupId"`
	UserID      string         `json:"userId"`
	Nick        string         `json:"nick"`
	MessageType string         `json:"messageType"`
	Content     *string        `json:"content"`
	Resource    *ResourceBrief `json:"resource"`
	CreatedAt   string         `json:"createdAt"`
}

type ResourceBrief struct {
	ID       int    `json:"id"`
	FileName string `json:"fileName"`
	URL      string `json:"url"`
}

type UpdateHistoryRequest struct {
	Content *string `json:"content"`
	Nick    string  `json:"nick"`
}

// ── Resources ──

type ResourceRecord struct {
	ID        int    `json:"id"`
	BotMark   string `json:"botMark"`
	GroupID   string `json:"groupId"`
	URL       string `json:"url"`
	FileName  string `json:"fileName"`
	Size      int64  `json:"size"`
	Md5       string `json:"md5"`
	CreatedAt string `json:"createdAt"`
}

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

// ── PAD ──

type PAD struct {
	P float64 `json:"p"`
	A float64 `json:"a"`
	D float64 `json:"d"`
}

// ── BehaviorProfile ──

type BehaviorProfile struct {
	Emotion        string `json:"emotion"`
	Tone           string `json:"tone"`
	Aggressiveness string `json:"aggressiveness"`
	EmojiLevel     string `json:"emojiLevel"`
}

// ── Emotion ──

type EmotionRecord struct {
	ID                      int             `json:"id"`
	BotMark                 string          `json:"botMark"`
	GroupID                 string          `json:"groupId"`
	EmotionalTendency       string          `json:"emotionalTendency"`
	Stimulus                PAD             `json:"stimulus"`
	Emotion                 PAD             `json:"emotion"`
	Mood                    PAD             `json:"mood"`
	Behavior                BehaviorProfile `json:"behavior"`
	HistoryMessageProcessed int             `json:"historyMessageProcessed"`
}

type UpdateEmotionRequest struct {
	EmotionalTendency string          `json:"emotionalTendency"`
	Stimulus          PAD             `json:"stimulus"`
	Emotion           PAD             `json:"emotion"`
	Mood              PAD             `json:"mood"`
	Behavior          BehaviorProfile `json:"behavior"`
}

// ── Flow ──

type FlowRecord struct {
	ID                     int     `json:"id"`
	BotMark                string  `json:"botMark"`
	GroupID                string  `json:"groupId"`
	LastProcessedHistoryId int     `json:"lastProcessedHistoryId"`
	LastProcessedAt        string  `json:"lastProcessedAt"`
	CurrentTopic           string  `json:"currentTopic"`
	FlowValue              float64 `json:"flowValue"`
	LastUpdateTime         int64   `json:"lastUpdateTime"`
}

type UpdateFlowRequest struct {
	FlowValue    float64 `json:"flowValue"`
	CurrentTopic string  `json:"currentTopic"`
}

// ── Volition ──

type VolitionRecord struct {
	ID                     int     `json:"id"`
	BotMark                string  `json:"botMark"`
	GroupID                string  `json:"groupId"`
	Fatigue                float64 `json:"fatigue"`
	Stimulus               float64 `json:"stimulus"`
	LastActiveTime         int64   `json:"lastActiveTime"`
	LastProcessedHistoryId int     `json:"lastProcessedHistoryId"`
	LastProcessedAt        string  `json:"lastProcessedAt"`
}

type UpdateVolitionRequest struct {
	Fatigue  float64 `json:"fatigue"`
	Stimulus float64 `json:"stimulus"`
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
