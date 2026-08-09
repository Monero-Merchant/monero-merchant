package server

import (
	"context"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httprate"
	"github.com/monero-merchant/monero-merchant/backend/internal/core/config"
	"github.com/monero-merchant/monero-merchant/backend/internal/core/rpc"
	localMiddleware "github.com/monero-merchant/monero-merchant/backend/internal/core/server/middleware"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/admin"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/auth"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/callback"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/misc"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/pos"
	"github.com/monero-merchant/monero-merchant/backend/internal/features/vendor"
	"github.com/monero-merchant/monero-merchant/backend/internal/thirdparty/moneropay"

	"gorm.io/gorm"
)

var adminLoginRateLimiter = httprate.NewRateLimiter(5, 1*time.Minute)
var vendorLoginRateLimiter = httprate.NewRateLimiter(5, 1*time.Minute)
var posLoginRateLimiter = httprate.NewRateLimiter(5, 1*time.Minute)

// Accept a context tied to server lifecycle to stop background loops on shutdown
func NewRouter(ctx context.Context, cfg *config.Config, db *gorm.DB, rpcClient *rpc.Client, moneroPayClient *moneropay.MoneroPayAPIClient) *chi.Mux {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)

	// Resolve client IP from either a trusted proxy header or remote address
	if header := cfg.TrustedProxyHeader; header != "" {
		r.Use(middleware.ClientIPFromHeader(header))
	} else {
		r.Use(middleware.ClientIPFromRemoteAddr)
	}

	r.Use(middleware.Logger)

	r.Use(middleware.Recoverer)

	// Rate limiting for all routes
	r.Use(httprate.LimitBy(100, time.Minute, func(r *http.Request) (string, error) {
		return httprate.CanonicalizeIP(middleware.GetClientIP(r.Context())), nil
	}))

	// Max request body size of 64 KB
	r.Use(middleware.RequestSize(64 * 1024))

	if moneroPayClient == nil {
		moneroPayClient = moneropay.NewMoneroPayAPIClient()
		if cfg.MoneroPayBaseURL != "" {
			moneroPayClient.BaseURL = cfg.MoneroPayBaseURL
		}
	}

	if rpcClient == nil {
		rpcClient = rpc.NewClient(
			cfg.MoneroWalletRPCEndpoint,
			cfg.MoneroWalletRPCUsername,
			cfg.MoneroWalletRPCPassword,
		)
	}

	// Initialize repositories
	adminRepository := admin.NewAdminRepository(db)
	authRepository := auth.NewAuthRepository(db)
	vendorRepository := vendor.NewVendorRepository(db)
	posRepository := pos.NewPosRepository(db)
	callbackRepository := callback.NewCallbackRepository(db)
	miscRepository := misc.NewMiscRepository(db)

	// Initialize services
	vendorService := vendor.NewVendorService(vendorRepository, db, cfg, rpcClient, moneroPayClient)
	vendorService.StartTransferCompleter(ctx, 30*time.Second) // Check every 30 seconds
	adminService := admin.NewAdminService(adminRepository, cfg, vendorService)
	authService := auth.NewAuthService(authRepository, cfg)
	posService := pos.NewPosService(posRepository, cfg, moneroPayClient)
	posService.StartPendingCleanup(ctx, 15*time.Minute, 2*time.Hour)
	callbackService := callback.NewCallbackService(callbackRepository, cfg, moneroPayClient)
	callbackService.StartConfirmationChecker(ctx, 2*time.Second) // Check for confirmations every 2 seconds
	miscService := misc.NewMiscService(miscRepository, cfg, moneroPayClient)

	// Initialize handlers
	adminHandler := admin.NewAdminHandler(adminService, vendorService)
	authHandler := auth.NewAuthHandler(authService)
	vendorHandler := vendor.NewVendorHandler(vendorService)
	posHandler := pos.NewPosHandler(posService)
	callbackHandler := callback.NewCallbackHandler(callbackService)
	miscHandler := misc.NewMiscHandler(miscService)

	// Public routes
	r.Group(func(r chi.Router) {
		// Auth routes
		r.Post("/auth/login-admin", authHandler.LoginAdmin)
		r.Post("/auth/login-vendor", authHandler.LoginVendor)
		r.Post("/auth/login-pos", authHandler.LoginPos)
		r.Post("/auth/refresh", authHandler.RefreshToken)

		// Vendor routes
		r.Post("/vendor/create", vendorHandler.CreateVendor)

		// Callback routes
		r.Post("/callback/receive/{jwt}", callbackHandler.ReceiveTransaction)
		r.Post("/receive/{jwt}", callbackHandler.ReceiveTransaction)
		r.Post("/callback/lws-hook/{jwt}", callbackHandler.LwsHook)

		// Miscellaneous routes
		r.Get("/misc/health", miscHandler.GetHealth)
	})

	// Protected routes
	r.Group(func(r chi.Router) {
		r.Use(localMiddleware.AuthMiddleware(cfg, authRepository))

		// Auth routes
		r.Post("/auth/update-password", authHandler.UpdatePassword)

		// Admin routes
		r.Post("/admin/invite", adminHandler.CreateInvite)
		r.Get("/admin/vendors", adminHandler.ListVendors)
		r.Get("/admin/balance", adminHandler.GetWalletBalance)
		r.Post("/admin/transfer-balance", adminHandler.TransferBalance)
		r.Post("/admin/delete", adminHandler.DeleteVendor)

		// Vendor routes
		r.Post("/vendor/delete", vendorHandler.DeleteVendor)
		r.Post("/vendor/create-pos", vendorHandler.CreatePos)
		r.Get("/vendor/balance", vendorHandler.GetAccountBalance)
		r.Post("/vendor/transfer-balance", vendorHandler.TransferBalance)
		r.Get("/vendor/pos-list", vendorHandler.ListPosDevices)
		r.Get("/vendor/transactions", vendorHandler.ListTransactions)
		r.Get("/vendor/export", vendorHandler.ExportTransactions)

		// POS routes
		r.Post("/pos/create-transaction", posHandler.CreateTransaction)
		r.Get("/pos/transaction/{id}", posHandler.GetTransaction)
		r.Get("/pos/transactions", posHandler.ListTransactions)
		r.Get("/pos/balance", posHandler.GetPosBalance)
		r.Get("/pos/export", posHandler.ExportTransactions)
		r.HandleFunc("/pos/ws/transaction", posHandler.TransactionWS)
	})

	return r
}
