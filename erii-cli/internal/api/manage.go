package api

import "fmt"

func (c *Client) GetBots() ([]BotInfo, error) {
	return doJSONRequest[[]BotInfo](c, "GET", "/api/bots", nil)
}

func (c *Client) GetGroups(botID string) ([]GroupInfo, error) {
	return doJSONRequest[[]GroupInfo](c, "GET", fmt.Sprintf("/api/bot/%s/groups", botID), nil)
}

func (c *Client) GetFacts(botID, groupID string) ([]FactRecord, error) {
	return doJSONRequest[[]FactRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/facts", botID, groupID), nil)
}

func (c *Client) GetFact(botID, groupID string, id int) (*FactRecord, error) {
	return doJSONRequest[*FactRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), nil)
}

func (c *Client) CreateFact(botID, groupID string, req FactRequest) error {
	_, err := c.doRequest("POST", fmt.Sprintf("/api/bot/%s/group/%s/facts", botID, groupID), req)
	return err
}

func (c *Client) UpdateFact(botID, groupID string, id int, req FactRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), req)
	return err
}

func (c *Client) DeleteFact(botID, groupID string, id int) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/facts/%d", botID, groupID, id), nil)
	return err
}

func (c *Client) GetUserProfiles(botID, groupID string) ([]UserProfileRecord, error) {
	return doJSONRequest[[]UserProfileRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles", botID, groupID), nil)
}

func (c *Client) GetUserProfile(botID, groupID, userID string) (*UserProfileRecord, error) {
	return doJSONRequest[*UserProfileRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), nil)
}

func (c *Client) UpdateUserProfile(botID, groupID, userID string, req UpdateUserProfileRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), req)
	return err
}

func (c *Client) DeleteUserProfile(botID, groupID, userID string) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/user-profiles/%s", botID, groupID, userID), nil)
	return err
}

func (c *Client) GetMemes(botID, groupID string) ([]MemeRecord, error) {
	return doJSONRequest[[]MemeRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/memes", botID, groupID), nil)
}

func (c *Client) GetMeme(botID, groupID string, id int) (*MemeRecord, error) {
	return doJSONRequest[*MemeRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), nil)
}

func (c *Client) UpdateMeme(botID, groupID string, id int, req UpdateMemeRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), req)
	return err
}

func (c *Client) DeleteMeme(botID, groupID string, id int) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/memes/%d", botID, groupID, id), nil)
	return err
}

func (c *Client) GetVocabularies(botID, groupID string) ([]VocabRecord, error) {
	return doJSONRequest[[]VocabRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary", botID, groupID), nil)
}

func (c *Client) GetVocabulary(botID, groupID string, id int) (*VocabRecord, error) {
	return doJSONRequest[*VocabRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), nil)
}

func (c *Client) CreateVocabulary(botID, groupID string, req VocabRequest) error {
	_, err := c.doRequest("POST", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary", botID, groupID), req)
	return err
}

func (c *Client) UpdateVocabulary(botID, groupID string, id int, req VocabRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), req)
	return err
}

func (c *Client) DeleteVocabulary(botID, groupID string, id int) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/vocabulary/%d", botID, groupID, id), nil)
	return err
}

func (c *Client) GetSummaries(botID, groupID string) ([]SummaryRecord, error) {
	return doJSONRequest[[]SummaryRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/summaries", botID, groupID), nil)
}

func (c *Client) GetSummary(botID, groupID string, id int) (*SummaryRecord, error) {
	return doJSONRequest[*SummaryRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), nil)
}

func (c *Client) UpdateSummary(botID, groupID string, id int, req UpdateSummaryRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), req)
	return err
}

func (c *Client) DeleteSummary(botID, groupID string, id int) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/summaries/%d", botID, groupID, id), nil)
	return err
}

func (c *Client) GetHistory(botID, groupID string) ([]HistoryRecord, error) {
	return doJSONRequest[[]HistoryRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/history", botID, groupID), nil)
}

func (c *Client) GetHistoryByID(botID, groupID string, id int) (*HistoryRecord, error) {
	return doJSONRequest[*HistoryRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), nil)
}

func (c *Client) UpdateHistory(botID, groupID string, id int, req UpdateHistoryRequest) error {
	_, err := c.doRequest("PUT", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), req)
	return err
}

func (c *Client) DeleteHistory(botID, groupID string, id int) error {
	_, err := c.doRequest("DELETE", fmt.Sprintf("/api/bot/%s/group/%s/history/%d", botID, groupID, id), nil)
	return err
}

func (c *Client) GetResources(botID, groupID string) ([]ResourceRecord, error) {
	return doJSONRequest[[]ResourceRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/resources", botID, groupID), nil)
}

func (c *Client) GetEmotion(botID, groupID string) (*EmotionRecord, error) {
	return doJSONRequest[*EmotionRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/emotion", botID, groupID), nil)
}

func (c *Client) UpdateEmotion(botID, groupID string, req UpdateEmotionRequest) (*EmotionRecord, error) {
	return doJSONRequest[*EmotionRecord](c, "PUT", fmt.Sprintf("/api/bot/%s/group/%s/emotion", botID, groupID), req)
}

func (c *Client) GetFlow(botID, groupID string) (*FlowRecord, error) {
	return doJSONRequest[*FlowRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/flow", botID, groupID), nil)
}

func (c *Client) UpdateFlow(botID, groupID string, req UpdateFlowRequest) (*FlowRecord, error) {
	return doJSONRequest[*FlowRecord](c, "PUT", fmt.Sprintf("/api/bot/%s/group/%s/flow", botID, groupID), req)
}

func (c *Client) GetVolition(botID, groupID string) (*VolitionRecord, error) {
	return doJSONRequest[*VolitionRecord](c, "GET", fmt.Sprintf("/api/bot/%s/group/%s/volition", botID, groupID), nil)
}

func (c *Client) UpdateVolition(botID, groupID string, req UpdateVolitionRequest) (*VolitionRecord, error) {
	return doJSONRequest[*VolitionRecord](c, "PUT", fmt.Sprintf("/api/bot/%s/group/%s/volition", botID, groupID), req)
}

func (c *Client) RefreshConfig() error {
	_, err := c.doRequest("POST", "/api/config/refresh", nil)
	return err
}
