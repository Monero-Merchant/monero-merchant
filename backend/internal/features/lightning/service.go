package lightning

import (
	"context"
	"fmt"
	"time"

	"monero-merchant/backend/internal/thirdparty/trocador"
)

// Service handles Lightning payment business logic
type Service struct {
	trocadorClient *trocador.Client
	webhookURL     string
	webhookKey     string
}

// NewService creates a new Lightning payment service
func NewService(trocadorClient *trocador.Client, webhookURL, webhookKey string) *Service {
	return &Service{
		trocadorClient: trocadorClient,
		webhookURL:     webhookURL,
		webhookKey:     webhookKey,
	}
}

// PaymentRequest represents a request to create a Lightning payment
type PaymentRequest struct {
	TransactionID string
	XMRAmount     float64
	XMRAddress    string
}

// PaymentResponse represents the response for a Lightning payment
type PaymentResponse struct {
	TradeID      string
	Invoice      string
	InvoiceSats  float64
	ExpiresAt    time.Time
	XMRAmount    float64
	Provider     string
}

// CreatePayment initiates a Lightning payment via Trocador
func (s *Service) CreatePayment(ctx context.Context, req PaymentRequest) (*PaymentResponse, error) {
	// Get current exchange rate
	rate, err := s.trocadorClient.GetRate(req.XMRAmount)
	if err != nil {
		return nil, fmt.Errorf("get exchange rate: %w", err)
	}

	// Create trade on Trocador
	fixed := true
	tradeReq := trocador.NewTradeRequest{
		TickerFrom:  "btc",
		NetworkFrom: "Lightning",
		TickerTo:    "xmr",
		NetworkTo:   "Mainnet",
		Address:     req.XMRAddress,
		AmountTo:    &req.XMRAmount,
		Fixed:       &fixed,
		Webhook:     &s.webhookURL,
		WebhookKey:  &s.webhookKey,
	}

	trade, err := s.trocadorClient.CreateTrade(tradeReq)
	if err != nil {
		return nil, fmt.Errorf("create trade: %w", err)
	}

	return &PaymentResponse{
		TradeID:     trade.ID,
		Invoice:     trade.Invoice,
		InvoiceSats: trade.InvoiceAmount,
		ExpiresAt:   time.Now().Add(15 * time.Minute), // Lightning invoices expire in 15 min
		XMRAmount:   req.XMRAmount,
		Provider:    trade.Provider,
	}, nil
}

// CheckPaymentStatus checks the status of a Lightning payment
func (s *Service) CheckPaymentStatus(ctx context.Context, tradeID string) (string, error) {
	status, err := s.trocadorClient.GetTradeStatus(tradeID)
	if err != nil {
		return "", fmt.Errorf("get trade status: %w", err)
	}
	return status.Status, nil
}

// HandleWebhook processes incoming Trocador webhook callbacks
func (s *Service) HandleWebhook(ctx context.Context, tradeID, status string) error {
	// Verify webhook signature using webhookKey
	// Update transaction status in database
	// Notify merchant if payment completed
	fmt.Printf("Webhook received: trade=%s status=%s\n", tradeID, status)
	return nil
}
