package admin

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/httprate"
	"github.com/monero-merchant/monero-merchant/backend/internal/core/models"
	"github.com/monero-merchant/monero-merchant/backend/internal/core/utils"
	vendorfeature "github.com/monero-merchant/monero-merchant/backend/internal/features/vendor"
)

type AdminHandler struct {
	service       *AdminService
	vendorService *vendorfeature.VendorService
	seedLimiter   *httprate.RateLimiter
}

func NewAdminHandler(service *AdminService, vendorService *vendorfeature.VendorService) *AdminHandler {
	return &AdminHandler{
		service:       service,
		vendorService: vendorService,
		seedLimiter:   httprate.NewRateLimiter(5, 1*time.Minute),
	}
}

type createInviteRequest struct {
	ValidUntil int64   `json:"valid_until"`
	ForcedName *string `json:"forced_name"`
}

type createInviteResponse struct {
	InviteCode string `json:"invite_code"`
}

type transferBalanceRequest struct {
	VendorID uint `json:"vendor_id"`
}

type deleteVendorRequest struct {
	VendorID uint `json:"vendor_id"`
}

type deleteVendorResponse struct {
	Success bool `json:"success"`
	ID      uint `json:"id"`
}

func (h *AdminHandler) CreateInvite(w http.ResponseWriter, r *http.Request) {
	// check jwt if admin

	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)

	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20) // 1MB cap

	var req createInviteRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.ValidUntil <= time.Now().Unix() {
		http.Error(w, "valid_until must be in the future", http.StatusBadRequest)
		return
	}

	inviteCode, err := h.service.CreateInvite(ctx, time.Unix(req.ValidUntil, 0), req.ForcedName)

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	resp := createInviteResponse{
		InviteCode: inviteCode,
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

func (h *AdminHandler) ListVendors(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	vendors, err := h.service.ListVendorsWithBalances(ctx)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	resp := struct {
		Vendors []VendorSummary `json:"vendors"`
	}{Vendors: vendors}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}

func (h *AdminHandler) GetWalletBalance(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	balance, httpErr := h.service.GetWalletBalance(ctx)
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(balance)
}

func (h *AdminHandler) TransferBalance(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if h.vendorService == nil {
		http.Error(w, "Vendor service not configured", http.StatusInternalServerError)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 8*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	var req transferBalanceRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.VendorID == 0 {
		http.Error(w, "vendor_id is required", http.StatusBadRequest)
		return
	}

	httpErr := h.vendorService.CreateTransfer(ctx, req.VendorID)
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	resp := "Transfer initiated successfully"
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

func (h *AdminHandler) DeleteVendor(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	r = r.WithContext(ctx)

	var req deleteVendorRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.VendorID == 0 {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"success": false,
			"message": "vendor_id is required",
		})
		io.Copy(io.Discard, r.Body)
		return
	}

	httpErr := h.service.DeleteVendor(ctx, req.VendorID)
	if httpErr != nil {
		message := httpErr.Message
		if httpErr.Code == http.StatusNotFound {
			message = "No vendor with ID"
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(httpErr.Code)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"success": false,
			"message": message,
		})
		io.Copy(io.Discard, r.Body)
		return
	}

	resp := deleteVendorResponse{
		Success: true,
		ID:      req.VendorID,
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
	io.Copy(io.Discard, r.Body)
}

func (h *AdminHandler) ListInvites(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	invites, err := h.service.ListInvites(ctx)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"invites": invites})
}

func (h *AdminHandler) ListTransactions(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	limit, offset := 100, 0
	if v := r.URL.Query().Get("limit"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 1 || n > 500 {
			http.Error(w, "Invalid limit", http.StatusBadRequest)
			return
		}
		limit = n
	}
	if v := r.URL.Query().Get("offset"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 0 {
			http.Error(w, "Invalid offset", http.StatusBadRequest)
			return
		}
		offset = n
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	transactions, total, err := h.service.ListTransactions(ctx, limit, offset)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"transactions": transactions, "total": total})
}

func (h *AdminHandler) ListPosDevices(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	devices, err := h.service.ListPosDevices(ctx)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"devices": devices})
}

func (h *AdminHandler) GetWalletInfo(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	info, httpErr := h.service.GetWalletInfo(ctx)
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(info)
}

func (h *AdminHandler) SetupWallet(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	if httpErr := h.service.SetupWallet(ctx); httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true})
}

func (h *AdminHandler) CloseWallet(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	if httpErr := h.service.CloseWallet(ctx); httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true})
}

type openWalletRequest struct {
	WalletName     string `json:"wallet_name"`
	WalletPassword string `json:"wallet_password"`
}

func (h *AdminHandler) OpenWallet(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req openWalletRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	if httpErr := h.service.OpenWallet(ctx, req.WalletName, req.WalletPassword); httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true})
}

func (h *AdminHandler) ListWallets(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()

	wallets, httpErr := h.service.ListWallets(ctx)
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"wallets": wallets})
}

type restoreWalletRequest struct {
	Seed          string `json:"seed"`
	RestoreHeight uint64 `json:"restore_height"`
}

func (h *AdminHandler) RestoreWallet(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req restoreWalletRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 90*time.Second)
	defer cancel()

	if httpErr := h.service.RestoreWallet(ctx, req.Seed, req.RestoreHeight); httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true})
}

type walletSeedRequest struct {
	Password string `json:"password"`
}

func (h *AdminHandler) GetWalletSeed(w http.ResponseWriter, r *http.Request) {
	role, ok := utils.GetClaimFromContext(r.Context(), models.ClaimsRoleKey)
	if !ok || role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req walletSeedRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if h.seedLimiter.RespondOnLimit(w, r, "wallet-seed") {
		return
	}
	if !h.service.VerifyPassword(req.Password) {
		http.Error(w, "invalid credentials", http.StatusUnauthorized)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()

	seed, httpErr := h.service.GetWalletSeed(ctx)
	if httpErr != nil {
		http.Error(w, httpErr.Message, httpErr.Code)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(map[string]string{"seed": seed})
}
