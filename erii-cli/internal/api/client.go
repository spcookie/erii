package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"erii-cli/internal/ipc"
)

type Client struct {
	baseURL  string
	username string
	password string
	http     *http.Client
}

func NewClient(port int, username, password string) *Client {
	return &Client{
		baseURL:  fmt.Sprintf("http://localhost:%d", port),
		username: username,
		password: password,
		http: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func (c *Client) doRequest(method, path string, body any) ([]byte, error) {
	url := c.baseURL + path

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
	req.SetBasicAuth(c.username, c.password)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := c.http.Do(req)
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

func doJSONRequest[T any](c *Client, method, path string, body any) (T, error) {
	var zero T
	data, err := c.doRequest(method, path, body)
	if err != nil {
		return zero, err
	}
	var result T
	if err := json.Unmarshal(data, &result); err != nil {
		return zero, err
	}
	return result, nil
}

func NewClientFromIPC() (*Client, error) {
	config, err := ipc.ReadConfig()
	if err != nil {
		return nil, fmt.Errorf("failed to read server config: %w", err)
	}
	if config == nil {
		return nil, fmt.Errorf("server config is nil")
	}
	port := config.Port
	if port == 0 {
		port = 8080
	}
	return NewClient(port, config.Username, config.Password), nil
}

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
