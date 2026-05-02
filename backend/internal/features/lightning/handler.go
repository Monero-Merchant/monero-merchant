package lightning

import (
	"encoding/json"
	"net/http"

	"github.com/gin-gonic/gin"
)

// Handler handles HTTP requests for Lightning payments
type Handler struct {
	service *Service
}

// NewHandler creates a new Lightning payment HTTP handler
func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

// CreateLightningPaymentRequest represents the request body for creating a Lightning payment
type CreateLightningPaymentRequest struct {
	TransactionID string  `json:"transaction_id" binding:"required"`
	XMRAmount     float64 `json:"xmr_amount" binding:"required"`
	XMRAddress    string  `json:"xmr_address" binding:"required"`
}

// CreateLightningPayment handles POST /api/pos/transactions/:id/lightning
func (h *Handler) CreateLightningPayment(c *gin.Context) {
	var req CreateLightningPaymentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	payment, err := h.service.CreatePayment(c.Request.Context(), PaymentRequest{
		TransactionID: req.TransactionID,
		XMRAmount:     req.XMRAmount,
		XMRAddress:    req.XMRAddress,
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"trade_id":      payment.TradeID,
		"invoice":       payment.Invoice,
		"invoice_sats":  payment.InvoiceSats,
		"expires_at":    payment.ExpiresAt,
		"xmr_amount":    payment.XMRAmount,
		"provider":      payment.Provider,
	})
}

// CheckLightningPaymentStatus handles GET /api/pos/lightning/:id/status
func (h *Handler) CheckLightningPaymentStatus(c *gin.Context) {
	tradeID := c.Param("id")

	status, err := h.service.CheckPaymentStatus(c.Request.Context(), tradeID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"trade_id": tradeID,
		"status":   status,
	})
}

// HandleWebhook handles POST /api/callback/lightning/:jwt
func (h *Handler) HandleWebhook(c *gin.Context) {
	var payload struct {
		ID     string `json:"id"`
		Status string `json:"status"`
	}

	if err := json.NewDecoder(c.Request.Body).Decode(&payload); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	if err := h.service.HandleWebhook(c.Request.Context(), payload.ID, payload.Status); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// GetExchangeRate handles GET /api/pos/lightning/rate
func (h *Handler) GetExchangeRate(c *gin.Context) {
	amount := c.Query("amount")
	// Parse amount and get rate from service
	// Simplified implementation
	c.JSON(http.StatusOK, gin.H{
		"rate_type": "btc_lightning_to_xmr",
		"network":   "mainnet",
	})
}
