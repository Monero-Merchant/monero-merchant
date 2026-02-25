package vendor

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"time"

	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/models"
	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/utils"
)

type VendorHandler struct {
	service *VendorService
}

func NewVendorHandler(service *VendorService) *VendorHandler {
	return &VendorHandler{service: service}
}

type createVendorRequest struct {
	Name             string `json:"name"`
	Email            string `json:"email"`
	Password         string `json:"password"`
	InviteCode       string `json:"invite_code"`
	MoneroSubaddress string `json:"monero_subaddress"`
}

type createVendorResponse struct {
	Success bool `json:"success"`
	ID      uint `json:"id"`
}

func (h *VendorHandler) CreateVendor(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)

	var req createVendorRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	id, httpErr := h.service.CreateVendor(ctx, req.Name, req.Email, req.Password, req.InviteCode, req.MoneroSubaddress)

	if httpErr != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(httpErr.Code)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"success": false,
			"message": httpErr.Message,
		})
		io.Copy(io.Discard, r.Body)
		return
	}

	resp := createVendorResponse{
		Success: true,
		ID:      id,
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

func (h *VendorHandler) DeleteVendor(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	httpErr := h.service.DeleteVendor(ctx, *(vendorID.(*uint)))
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	resp := "Vendor deleted successfully"
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

type createPosRequest struct {
	Name     string `json:"name"`
	Password string `json:"password"`
}

type createPosResponse struct {
	Success  bool   `json:"success"`
	Name     string `json:"name"`
	VendorID uint   `json:"vendor_id"`
}

type vendorBalanceResponse struct {
	Balance int64 `json:"balance"`
}

func (h *VendorHandler) CreatePos(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)

	var req createPosRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	id := *(vendorID.(*uint))

	httpErr := h.service.CreatePos(ctx, req.Name, req.Password, id)

	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	resp := createPosResponse{
		Success:  true,
		Name:     req.Name,
		VendorID: id,
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

func (h *VendorHandler) GetAccountBalance(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	balance, err := h.service.GetVendorAccountBalance(ctx, *(vendorID.(*uint)))
	if err != nil {
		http.Error(w, "Failed to retrieve balance", http.StatusInternalServerError)
		return
	}

	resp := vendorBalanceResponse{Balance: balance}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}

func (h *VendorHandler) TransferBalance(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	httpErr := h.service.CreateTransfer(ctx, *(vendorID.(*uint)))
	if httpErr != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(httpErr.Code)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"success": false,
			"error":   httpErr.Message,
		})
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": true,
		"message": "Transfer initiated successfully. It will be processed shortly.",
	})
}

type posDeviceResponse struct {
	ID        uint   `json:"id"`
	Name      string `json:"name"`
	CreatedAt string `json:"created_at"`
}

func (h *VendorHandler) ListPosDevices(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	devices, httpErr := h.service.ListPosDevices(ctx, *(vendorID.(*uint)))
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	resp := make([]posDeviceResponse, len(devices))
	for i, d := range devices {
		resp[i] = posDeviceResponse{
			ID:        d.ID,
			Name:      d.Name,
			CreatedAt: d.CreatedAt.Format(time.RFC3339),
		}
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"devices": resp,
	})
}

func (h *VendorHandler) ListTransactions(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	result, httpErr := h.service.ListTransactionsByVendor(ctx, *(vendorID.(*uint)))
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(result)
}

func (h *VendorHandler) ExportTransactions(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "vendor" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vendorID, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsVendorIDKey)
	if !ok {
		http.Error(w, "Unauthorized: vendorID not found", http.StatusUnauthorized)
		return
	}

	csvData, httpErr := h.service.ExportTransactionsByVendor(ctx, *(vendorID.(*uint)))
	if httpErr != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(httpErr.Code)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"error": httpErr.Message,
		})
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"csv_data": csvData,
	})
}
