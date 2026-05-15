package manage

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

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

func (a *API) doRequest(method, path string, body any) ([]byte, error) {
	url := a.baseURL() + path

	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(a.username, a.password)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := a.client.Do(req)
	if err != nil {
		return nil, friendlyRequestError(err)
	}
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(resp.Body)

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}
	return respBody, nil
}

// friendlyRequestError converts network-level errors into user-friendly messages.
func friendlyRequestError(err error) error {
	msg := strings.ToLower(err.Error())
	switch {
	case strings.Contains(msg, "connection refused"):
		return fmt.Errorf("cannot connect to Erii service, please make sure it is running")
	case strings.Contains(msg, "timeout") || strings.Contains(msg, "timed out"):
		return fmt.Errorf("connection to Erii service timed out, please check network or service status")
	case strings.Contains(msg, "no such host"):
		return fmt.Errorf("unable to resolve service address")
	default:
		return fmt.Errorf("request failed: %w", err)
	}
}

// ── Bots & Groups (shared) ──

func (a *API) GetBots() ([]BotInfo, error) {
	data, err := a.doRequest("GET", "/api/bots", nil)
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
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/groups", botID), nil)
	if err != nil {
		return nil, err
	}
	var groups []GroupInfo
	if err := json.Unmarshal(data, &groups); err != nil {
		return nil, err
	}
	return groups, nil
}

// ── Facts ──

func (a *API) GetFacts(botID, groupID string) ([]FactRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/facts", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []FactRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetFact(botID, groupID string, id int) (*FactRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), nil)
	if err != nil {
		return nil, err
	}
	var record FactRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) CreateFact(botID, groupID string, req FactRequest) error {
	_, err := a.doRequest("POST", fmt.Sprintf("/api/bot/%s/group/%s/facts", botID, groupID), req)
	return err
}

func (a *API) UpdateFact(botID, groupID string, id int, req FactRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), req)
	return err
}

func (a *API) DeleteFact(botID, groupID string, id int) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), nil)
	return err
}

// ── User Profiles ──

func (a *API) GetUserProfiles(botID, groupID string) ([]UserProfileRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []UserProfileRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetUserProfile(botID, groupID, userID string) (*UserProfileRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), nil)
	if err != nil {
		return nil, err
	}
	var record UserProfileRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateUserProfile(botID, groupID, userID string, req UpdateUserProfileRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), req)
	return err
}

func (a *API) DeleteUserProfile(botID, groupID, userID string) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), nil)
	return err
}

// ── Memes ──

func (a *API) GetMemes(botID, groupID string) ([]MemeRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/memes", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []MemeRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetMeme(botID, groupID string, id int) (*MemeRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), nil)
	if err != nil {
		return nil, err
	}
	var record MemeRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateMeme(botID, groupID string, id int, req UpdateMemeRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), req)
	return err
}

func (a *API) DeleteMeme(botID, groupID string, id int) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), nil)
	return err
}

// ── Vocabulary ──

func (a *API) GetVocabularies(botID, groupID string) ([]VocabRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []VocabRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetVocabulary(botID, groupID string, id int) (*VocabRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), nil)
	if err != nil {
		return nil, err
	}
	var record VocabRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) CreateVocabulary(botID, groupID string, req VocabRequest) error {
	_, err := a.doRequest("POST", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary", botID, groupID), req)
	return err
}

func (a *API) UpdateVocabulary(botID, groupID string, id int, req VocabRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), req)
	return err
}

func (a *API) DeleteVocabulary(botID, groupID string, id int) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), nil)
	return err
}

// ── Summaries ──

func (a *API) GetSummaries(botID, groupID string) ([]SummaryRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/summaries", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []SummaryRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetSummary(botID, groupID string, id int) (*SummaryRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), nil)
	if err != nil {
		return nil, err
	}
	var record SummaryRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateSummary(botID, groupID string, id int, req UpdateSummaryRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), req)
	return err
}

func (a *API) DeleteSummary(botID, groupID string, id int) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), nil)
	return err
}

// ── History ──

func (a *API) GetHistory(botID, groupID string) ([]HistoryRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/history", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []HistoryRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

func (a *API) GetHistoryByID(botID, groupID string, id int) (*HistoryRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), nil)
	if err != nil {
		return nil, err
	}
	var record HistoryRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateHistory(botID, groupID string, id int, req UpdateHistoryRequest) error {
	_, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), req)
	return err
}

func (a *API) DeleteHistory(botID, groupID string, id int) error {
	_, err := a.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), nil)
	return err
}

// ── Resources ──

func (a *API) GetResources(botID, groupID string) ([]ResourceRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/resources", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var records []ResourceRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, err
	}
	return records, nil
}

// ── Emotion ──

func (a *API) GetEmotion(botID, groupID string) (*EmotionRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/emotion", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var record EmotionRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateEmotion(botID, groupID string, req UpdateEmotionRequest) (*EmotionRecord, error) {
	data, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/emotion", botID, groupID), req)
	if err != nil {
		return nil, err
	}
	var record EmotionRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

// ── Flow ──

func (a *API) GetFlow(botID, groupID string) (*FlowRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/flow", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var record FlowRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateFlow(botID, groupID string, req UpdateFlowRequest) (*FlowRecord, error) {
	data, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/flow", botID, groupID), req)
	if err != nil {
		return nil, err
	}
	var record FlowRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

// ── Volition ──

func (a *API) GetVolition(botID, groupID string) (*VolitionRecord, error) {
	data, err := a.doRequest("GET", fmt.Sprintf("/api/bot/%s/group/%s/volition", botID, groupID), nil)
	if err != nil {
		return nil, err
	}
	var record VolitionRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}

func (a *API) UpdateVolition(botID, groupID string, req UpdateVolitionRequest) (*VolitionRecord, error) {
	data, err := a.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/volition", botID, groupID), req)
	if err != nil {
		return nil, err
	}
	var record VolitionRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}
	return &record, nil
}
