package trocador

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// Client handles communication with the Trocador swap API
type Client struct {
	BaseURL    string
	HTTPClient *http.Client
	APIKey     string
}

// NewClient creates a new Trocador API client
func NewClient(baseURL, apiKey string) *Client {
	if baseURL == "" {
		baseURL = "https://trocador.app"
	}
	return &Client{
		BaseURL: baseURL,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		APIKey: apiKey,
	}
}

// NewTradeRequest represents a request to create a new swap trade
type NewTradeRequest struct {
	TickerFrom  string  `json:"ticker_from"`
	NetworkFrom string  `json:"network_from"`
	TickerTo    string  `json:"ticker_to"`
	NetworkTo   string  `json:"network_to"`
	Address     string  `json:"address"`
	AmountFrom  *float64 `json:"amount_from,omitempty"`
	AmountTo    *float64 `json:"amount_to,omitempty"`
	Provider    *string  `json:"provider,omitempty"`
	Fixed       *bool    `json:"fixed,omitempty"`
	Webhook     *string  `json:"webhook,omitempty"`
	WebhookKey  *string  `json:"webhook_key,omitempty"`
}

// NewTradeResponse represents the response from creating a trade
type NewTradeResponse struct {
	ID            string  `json:"id"`
	Invoice       string  `json:"invoice"`
	InvoiceAmount float64 `json:"invoice_amount"`
	Provider      string  `json:"provider"`
	Status        string  `json:"status"`
	CreatedAt     string  `json:"created_at"`
}

// TradeStatusResponse represents the response for checking trade status
type TradeStatusResponse struct {
	ID           string  `json:"id"`
	Status       string  `json:"status"`
	Invoice      string  `json:"invoice"`
	InvoiceAmount float64 `json:"invoice_amount"`
	PaidAt       *string `json:"paid_at"`
	CompletedAt  *string `json:"completed_at"`
	XmrAmount    *string `json:"xmr_amount"`
	XmrAddress   *string `json:"xmr_address"`
}

// RateResponse represents the exchange rate response
type RateResponse struct {
	AmountFrom float64 `json:"amount_from"`
	AmountTo   float64 `json:"amount_to"`
	Provider   string  `json:"provider"`
	ExpiresAt  string  `json:"expires_at"`
}

// CreateTrade initiates a new BTC Lightning to XMR swap
func (c *Client) CreateTrade(req NewTradeRequest) (*NewTradeResponse, error) {
	url := fmt.Sprintf("%s/api/trade", c.BaseURL)
	
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	httpReq, err := http.NewRequest("POST", url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	if c.APIKey != "" {
		httpReq.Header.Set("X-API-Key", c.APIKey)
	}

	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		return nil, fmt.Errorf("API error %d: %s", resp.StatusCode, string(respBody))
	}

	var trade NewTradeResponse
	if err := json.Unmarshal(respBody, &trade); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	return &trade, nil
}

// GetTradeStatus checks the status of an existing trade
func (c *Client) GetTradeStatus(tradeID string) (*TradeStatusResponse, error) {
	url := fmt.Sprintf("%s/api/trade/%s", c.BaseURL, tradeID)

	httpReq, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	if c.APIKey != "" {
		httpReq.Header.Set("X-API-Key", c.APIKey)
	}

	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API error %d: %s", resp.StatusCode, string(respBody))
	}

	var status TradeStatusResponse
	if err := json.Unmarshal(respBody, &status); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	return &status, nil
}

// GetRate gets the current exchange rate for BTC Lightning to XMR
func (c *Client) GetRate(amount float64) (*RateResponse, error) {
	url := fmt.Sprintf("%s/api/rate?ticker_from=btc&network_from=Lightning&ticker_to=xmr&network_to=Mainnet&amount_from=%.2f",
		c.BaseURL, amount)

	httpReq, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	resp, err := c.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API error %d: %s", resp.StatusCode, string(respBody))
	}

	var rate RateResponse
	if err := json.Unmarshal(respBody, &rate); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	return &rate, nil
}
