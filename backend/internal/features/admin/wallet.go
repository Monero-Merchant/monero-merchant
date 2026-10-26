package admin

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"strings"

	"github.com/monero-merchant/monero-merchant/backend/internal/core/models"
	"github.com/monero-merchant/monero-merchant/backend/internal/core/rpc"
)

const walletRPCErrNotOpen = -13

type WalletInfo struct {
	Exists     bool   `json:"exists"`
	Address    string `json:"address,omitempty"`
	WalletName string `json:"wallet_name"`
}

type WalletBalance struct {
	Total    uint64 `json:"total"`
	Unlocked uint64 `json:"unlocked"`
	Locked   uint64 `json:"locked"`
	// Confirmed sales not yet paid out, from the database. The wallet should hold at least this much.
	VendorBalances int64 `json:"vendor_balances"`
}

func isWalletNotOpen(err error) bool {
	var rpcErr *rpc.Error
	return errors.As(err, &rpcErr) && rpcErr.Code == walletRPCErrNotOpen
}

// create_wallet returns -21 and restore_deterministic_wallet -1, both say "already exists"
func isWalletExists(err error) bool {
	var rpcErr *rpc.Error
	return errors.As(err, &rpcErr) && strings.Contains(strings.ToLower(rpcErr.Message), "already exists")
}

func (s *AdminService) walletAddress(ctx context.Context) (string, error) {
	var resp struct {
		Address string `json:"address"`
	}
	err := s.rpcClient.Call(ctx, "get_address", map[string]any{"account_index": 0}, &resp)
	return resp.Address, err
}

func (s *AdminService) walletParams() map[string]any {
	return map[string]any{
		"filename": s.config.WalletName,
		"password": s.config.WalletPassword,
		"language": "English",
	}
}

func (s *AdminService) GetWalletInfo(ctx context.Context) (*WalletInfo, *models.HTTPError) {
	info := &WalletInfo{WalletName: s.config.WalletName}

	address, err := s.walletAddress(ctx)
	if isWalletNotOpen(err) {
		// wallet-rpc may have restarted, try opening the configured wallet
		params := map[string]any{"filename": s.config.WalletName, "password": s.config.WalletPassword}
		if s.rpcClient.Call(ctx, "open_wallet", params, nil) != nil {
			return info, nil
		}
		address, err = s.walletAddress(ctx)
	}
	if err != nil {
		return nil, models.NewHTTPError(http.StatusBadGateway, err.Error())
	}

	info.Exists = true
	info.Address = address
	return info, nil
}

// createWallet runs create_wallet or restore_deterministic_wallet, refusing to replace an open
// or existing wallet. Both RPCs would otherwise close the open one.
func (s *AdminService) createWallet(ctx context.Context, method string, params map[string]any) *models.HTTPError {
	_, err := s.walletAddress(ctx)
	if err == nil {
		return models.NewHTTPError(http.StatusConflict, "a wallet is already open")
	}
	if !isWalletNotOpen(err) {
		return models.NewHTTPError(http.StatusBadGateway, err.Error())
	}

	if err := s.rpcClient.Call(ctx, method, params, nil); err != nil {
		if isWalletExists(err) {
			return models.NewHTTPError(http.StatusConflict, "a wallet with this name already exists")
		}
		return models.NewHTTPError(http.StatusBadGateway, err.Error())
	}
	return nil
}

func (s *AdminService) SetupWallet(ctx context.Context) *models.HTTPError {
	if httpErr := s.createWallet(ctx, "create_wallet", s.walletParams()); httpErr != nil {
		return httpErr
	}
	log.Printf("Wallet %q created", s.config.WalletName)
	return nil
}

func (s *AdminService) CloseWallet(ctx context.Context) *models.HTTPError {
	if err := s.rpcClient.Call(ctx, "close_wallet", map[string]any{"autosave_current": true}, nil); err != nil {
		if isWalletNotOpen(err) {
			return models.NewHTTPError(http.StatusNotFound, "no wallet is open")
		}
		return models.NewHTTPError(http.StatusBadGateway, err.Error())
	}
	return nil
}

func (s *AdminService) OpenWallet(ctx context.Context, walletName, walletPassword string) *models.HTTPError {
	walletName = strings.TrimSpace(walletName)
	if walletName == "" {
		return models.NewHTTPError(http.StatusBadRequest, "wallet_name is required")
	}

	params := map[string]any{
		"filename":         walletName,
		"password":         walletPassword,
		"autosave_current": true,
	}
	if err := s.rpcClient.Call(ctx, "open_wallet", params, nil); err != nil {
		return models.NewHTTPError(http.StatusBadGateway, err.Error())
	}
	return nil
}

func (s *AdminService) ListWallets(ctx context.Context) ([]string, *models.HTTPError) {
	if err := ctx.Err(); err != nil {
		return nil, models.NewHTTPError(http.StatusRequestTimeout, err.Error())
	}
	if s.config.WalletDirectory == "" {
		return nil, models.NewHTTPError(http.StatusServiceUnavailable, "wallet directory is not configured")
	}

	entries, err := os.ReadDir(s.config.WalletDirectory)
	if err != nil {
		log.Printf("Failed to list wallet directory: %v", err)
		return nil, models.NewHTTPError(http.StatusInternalServerError, "failed to list wallet directory")
	}

	wallets := make([]string, 0)
	for _, entry := range entries {
		// Each wallet has a .keys file; caches and backups are not separate wallets.
		if !entry.Type().IsRegular() || !strings.HasSuffix(entry.Name(), ".keys") {
			continue
		}
		name := strings.TrimSuffix(entry.Name(), ".keys")
		if name != "" {
			wallets = append(wallets, name)
		}
	}
	return wallets, nil
}

func (s *AdminService) RestoreWallet(ctx context.Context, seed string, restoreHeight uint64) *models.HTTPError {
	seed = strings.Join(strings.Fields(seed), " ")
	if seed == "" {
		return models.NewHTTPError(http.StatusBadRequest, "seed is required")
	}

	params := s.walletParams()
	params["seed"] = seed
	params["restore_height"] = restoreHeight
	if httpErr := s.createWallet(ctx, "restore_deterministic_wallet", params); httpErr != nil {
		return httpErr
	}
	log.Printf("Wallet %q restored from seed", s.config.WalletName)
	return nil
}

func (s *AdminService) GetWalletSeed(ctx context.Context) (string, *models.HTTPError) {
	var resp struct {
		Key string `json:"key"`
	}
	if err := s.rpcClient.Call(ctx, "query_key", map[string]any{"key_type": "mnemonic"}, &resp); err != nil {
		if isWalletNotOpen(err) {
			return "", models.NewHTTPError(http.StatusNotFound, "no wallet is open")
		}
		return "", models.NewHTTPError(http.StatusBadGateway, err.Error())
	}
	return resp.Key, nil
}

func (s *AdminService) GetWalletBalance(ctx context.Context) (*WalletBalance, *models.HTTPError) {
	var resp struct {
		Balance         uint64 `json:"balance"`
		UnlockedBalance uint64 `json:"unlocked_balance"`
	}
	if err := s.rpcClient.Call(ctx, "get_balance", map[string]any{"account_index": 0}, &resp); err != nil {
		return nil, models.NewHTTPError(http.StatusBadGateway, err.Error())
	}

	vendorBalances, err := s.repo.GetVendorBalancesTotal(ctx)
	if err != nil {
		return nil, models.NewHTTPError(http.StatusInternalServerError, err.Error())
	}

	return &WalletBalance{
		Total:          resp.Balance,
		Unlocked:       resp.UnlockedBalance,
		Locked:         resp.Balance - resp.UnlockedBalance,
		VendorBalances: vendorBalances,
	}, nil
}
