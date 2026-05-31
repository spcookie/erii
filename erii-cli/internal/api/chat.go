package api

import (
	"encoding/json"
	"fmt"
)

// ChatSendResponse is the response body from POST /api/chat/send
type ChatSendResponse struct {
	Response string `json:"response"`
	Error    string `json:"error"`
}

// ChatHealthResponse is the response body from GET /api/chat/health
type ChatHealthResponse struct {
	Status       string `json:"status"`
	MockBotReady bool   `json:"mockBotReady"`
	RoleSelected bool   `json:"roleSelected"`
}

// ChatRole represents a bot role returned by GET /api/chat/roles
type ChatRole struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Character string `json:"character"`
	Emoticon  string `json:"emoticon"`
}

// ChatSelectRoleRequest is the request body for POST /api/chat/select-role
type ChatSelectRoleRequest struct {
	RoleId string `json:"roleId"`
}

// ChatSelectRoleResponse is the response body from POST /api/chat/select-role
type ChatSelectRoleResponse struct {
	Success bool      `json:"success"`
	Error   string    `json:"error"`
	Role    *ChatRole `json:"role"`
}

// ListChatRoles fetches all available bot roles from the server.
func (c *Client) ListChatRoles() ([]ChatRole, error) {
	return doJSONRequest[[]ChatRole](c, "GET", "/api/chat/roles", nil)
}

// SelectChatRole sends a role selection to the server.
func (c *Client) SelectChatRole(roleId string) (*ChatSelectRoleResponse, error) {
	data, err := c.doRequest("POST", "/api/chat/select-role", ChatSelectRoleRequest{RoleId: roleId})
	if err != nil {
		return nil, fmt.Errorf("failed to select role: %w", err)
	}

	var resp ChatSelectRoleResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse select-role response: %w", err)
	}

	if !resp.Success {
		return nil, fmt.Errorf("role selection failed: %s", resp.Error)
	}

	return &resp, nil
}

// ChatHistoryEntry represents a single message in the chat history.
type ChatHistoryEntry struct {
	ID        int64  `json:"id"`
	Sender    string `json:"sender"`
	Content   string `json:"content"`
	Timestamp int64  `json:"timestamp"`
}

// ChatHistoryResponse is the response from GET /api/chat/history
type ChatHistoryResponse struct {
	Entries []ChatHistoryEntry `json:"entries"`
	HasMore bool               `json:"hasMore"`
}

// GetChatHistory fetches chat history from the server.
func (c *Client) GetChatHistory(before int64, limit int) (*ChatHistoryResponse, error) {
	url := fmt.Sprintf("/api/chat/history?limit=%d", limit)
	if before > 0 {
		url += fmt.Sprintf("&before=%d", before)
	}
	resp, err := doJSONRequest[ChatHistoryResponse](c, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch history: %w", err)
	}
	return &resp, nil
}

// CheckChatHealth checks if the chat bridge is ready.
func (c *Client) CheckChatHealth() (*ChatHealthResponse, error) {
	resp, err := doJSONRequest[ChatHealthResponse](c, "GET", "/api/chat/health", nil)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}
