package stats

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"
)

type BotInfo struct {
	BotID   string `json:"botId"`
	BotName string `json:"botName"`
}

type GroupInfo struct {
	GroupID   string `json:"groupId"`
	GroupName string `json:"groupName"`
}

type PAD struct {
	P float64 `json:"p"`
	A float64 `json:"a"`
	D float64 `json:"d"`
}

type FlowState struct {
	Meter float64 `json:"meter"`
	State string  `json:"state"`
}

type VolitionState struct {
	Stimulus    float64 `json:"stimulus"`
	Fatigue     float64 `json:"fatigue"`
	ShouldSpeak bool    `json:"shouldSpeak"`
}

type Fact struct {
	Keyword     string   `json:"keyword"`
	Description string   `json:"description"`
	Values      string   `json:"values"`
	Subjects    []string `json:"subjects"`
}

type Facts struct {
	Group []Fact `json:"group"`
	User  []Fact `json:"user"`
}

type UserProfile struct {
	ID          string `json:"id"`
	Profile     string `json:"profile"`
	Preferences string `json:"preferences"`
}

type Meme struct {
	ID          int      `json:"id"`
	Description string   `json:"description"`
	Purpose     string   `json:"purpose"`
	Tags        []string `json:"tags"`
	SeenCount   int      `json:"seenCount"`
	UsageCount  int      `json:"usageCount"`
}

type PluginStats struct {
	TotalExtensions   int `json:"totalExtensions"`
	CmdExtensions     int `json:"cmdExtensions"`
	RouteExtensions   int `json:"routeExtensions"`
	PassiveExtensions int `json:"passiveExtensions"`
}

type BehaviorProfile struct {
	Emotion        string `json:"emotion"`
	Tone           string `json:"tone"`
	Aggressiveness string `json:"aggressiveness"`
	EmojiLevel     string `json:"emojiLevel"`
}

type GroupStatus struct {
	BotID            string           `json:"botId"`
	BotName          string           `json:"botName"`
	GroupID          string           `json:"groupId"`
	GroupName        string           `json:"groupName"`
	BehaviorProfile  *BehaviorProfile `json:"behaviorProfile"`
	PAD              *PAD             `json:"pad"`
	FlowState        FlowState        `json:"flowState"`
	VolitionState    VolitionState    `json:"volitionState"`
	Vocabularies     []string         `json:"vocabularies"`
	Summary          *string          `json:"summary"`
	FactSize         int64            `json:"factSize"`
	UserProfileSize  int64            `json:"userProfileSize"`
	Facts            Facts            `json:"facts"`
	UserProfiles     []UserProfile    `json:"userProfiles"`
	MemeSize         int64            `json:"memeSize"`
	AnalyzedMemeSize int64            `json:"analyzedMemeSize"`
	Memes            []Meme           `json:"memes"`
	PluginStats      PluginStats      `json:"pluginStats"`
}

type API struct {
	port     int
	username string
	password string
	client   *http.Client
}

func NewAPI(port int, username, password string) *API {
	return &API{
		port:     port,
		username: username,
		password: password,
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func (a *API) baseURL() string {
	return fmt.Sprintf("http://localhost:%d", a.port)
}

func (a *API) doRequest(method, path string) ([]byte, error) {
	url := a.baseURL() + path
	log.Printf("[API] GET %s", url)
	req, err := http.NewRequest(method, url, nil)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(a.username, a.password)

	resp, err := a.client.Do(req)
	if err != nil {
		log.Printf("[API] Error: %v", err)
		return nil, err
	}
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(resp.Body)

	log.Printf("[API] Status: %d", resp.StatusCode)

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("[API] Read body error: %v", err)
		return nil, err
	}
	log.Printf("[API] Response: %s", string(body))
	return body, nil
}

func (a *API) GetBots() ([]BotInfo, error) {
	data, err := a.doRequest("GET", "/api/bots")
	if err != nil {
		return nil, err
	}
	var bots []BotInfo
	if err := json.Unmarshal(data, &bots); err != nil {
		return nil, err
	}
	return bots, nil
}

func (a *API) GetGroups(botID string) ([]GroupInfo, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/groups", botID))
	if err != nil {
		return nil, err
	}
	var groups []GroupInfo
	if err := json.Unmarshal(data, &groups); err != nil {
		return nil, err
	}
	return groups, nil
}

func (a *API) GetGroupStatus(botID, groupID string) (*GroupStatus, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/status", botID, groupID))
	if err != nil {
		return nil, err
	}
	var status GroupStatus
	if err := json.Unmarshal(data, &status); err != nil {
		return nil, err
	}
	return &status, nil
}
