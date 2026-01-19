package callback

import (
	"context"
	"log"
	"sync"

	"net/http"

	"time"

	"github.com/monerokon/xmrpos/xmrpos-backend/internal/features/pos"

	"github.com/golang-jwt/jwt/v5"
	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/config"
	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/models"
	"github.com/monerokon/xmrpos/xmrpos-backend/internal/thirdparty/moneropay"
)

type CallbackService struct {
	repo      CallbackRepository
	config    *config.Config
	moneroPay *moneropay.MoneroPayAPIClient
	mu        sync.Mutex
}

func NewCallbackService(repo CallbackRepository, cfg *config.Config, moneroPay *moneropay.MoneroPayAPIClient) *CallbackService {
	return &CallbackService{repo: repo, config: cfg, moneroPay: moneroPay}
}

type LwsHookRequest struct {
	Event         string      `json:"event"`
	PaymentID     string      `json:"payment_id"`
	Token         string      `json:"token"`
	Confirmations int64       `json:"confirmations"`
	EventID       string      `json:"event_id"`
	ID            string      `json:"id"`
	Amount        int64       `json:"amount"`
	Height        *int64      `json:"height,omitempty"`
	TxHash        string      `json:"tx_hash"`
	Timestamp     *time.Time  `json:"timestamp,omitempty"`
	TxInfo        *LwsTxInfo  `json:"tx_info,omitempty"`
	Extra         interface{} `json:"extra,omitempty"`
}

type LwsTxInfo struct {
	ID struct {
		High int64 `json:"high"`
		Low  int64 `json:"low"`
	} `json:"id"`
	Block        *int64 `json:"block"`
	Index        *int64 `json:"index"`
	Amount       int64  `json:"amount"`
	Timestamp    int64  `json:"timestamp"`
	TxHash       string `json:"tx_hash"`
	TxPrefixHash string `json:"tx_prefix_hash"`
	TxPublic     string `json:"tx_public"`
	RctMask      string `json:"rct_mask"`
	PaymentID    string `json:"payment_id"`
	UnlockTime   int64  `json:"unlock_time"`
	MixinCount   int64  `json:"mixin_count"`
	Coinbase     bool   `json:"coinbase"`
}

func (s *CallbackService) StartConfirmationChecker(ctx context.Context, interval time.Duration) {
	go func() {
		runSweep := func(parent context.Context) {
			sweepCtx, cancel := context.WithTimeout(parent, 20*time.Second)
			s.checkUnconfirmedTransactions(sweepCtx)
			cancel()
		}

		runSweep(ctx)
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				runSweep(ctx)
			case <-ctx.Done():
				return
			}
		}
	}()
}

// This method queries for unconfirmed transactions and checks MoneroPay
func (s *CallbackService) checkUnconfirmedTransactions(ctx context.Context) {
	s.mu.Lock()
	defer s.mu.Unlock()

	unconfirmed, err := s.repo.FindUnconfirmedTransactions(ctx)
	if err != nil {
		return
	}

	for _, tx := range unconfirmed {
		callCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
		// Skip transactions without a subaddress
		if tx.SubAddress == nil {
			cancel()
			continue
		}
		moneroStatus, err := s.moneroPay.GetReceiveAddress(callCtx, *tx.SubAddress, &moneropay.GetReceiveAddressParams{})
		cancel()
		if err != nil {
			continue
		}

		if moneroStatus != nil {
			_ = s.processTransaction(ctx, tx.ID, *moneroStatus)
		}
	}
}

func (s *CallbackService) processTransaction(ctx context.Context, transactionID uint, transactionToProcess moneropay.ReceiveAddressResponse) *models.HTTPError {

	// Get the transaction by ID
	transaction, err := s.repo.FindTransactionByID(ctx, transactionID)
	if err != nil {
		return models.NewHTTPError(http.StatusNotFound, "Transaction not found")
	}

	for _, subTxToProcess := range transactionToProcess.Transactions {
		// Create or update the subtransaction
		subTransaction := &models.SubTransaction{
			TransactionID:   transaction.ID,
			Amount:          subTxToProcess.Amount,
			Confirmations:   subTxToProcess.Confirmations,
			DoubleSpendSeen: subTxToProcess.DoubleSpendSeen,
			Fee:             subTxToProcess.Fee,
			Height:          subTxToProcess.Height,
			Timestamp:       subTxToProcess.Timestamp,
			TxHash:          subTxToProcess.TxHash,
			UnlockTime:      subTxToProcess.UnlockTime,
			Locked:          subTxToProcess.Locked,
		}

		// See if the txHash already exists in the transaction's subtransactions
		existing := false
		for _, subTx := range transaction.SubTransactions {
			if subTx.TxHash == subTransaction.TxHash {
				subTransaction.ID = subTx.ID // Ensure we set the ID for update
				existing = true
				break
			}
		}

		if !existing {
			// Create new subtransaction
			_, err := s.repo.CreateSubTransaction(ctx, subTransaction)
			if err != nil {
				return models.NewHTTPError(http.StatusInternalServerError, "Failed to create subtransaction: "+err.Error())
			}
		} else {
			// Update existing subtransaction
			_, err := s.repo.UpdateSubTransaction(ctx, subTransaction)
			if err != nil {
				return models.NewHTTPError(http.StatusInternalServerError, "Failed to update subtransaction: "+err.Error())
			}
		}
	}

	// Get the updated transaction with subtransactions
	transaction, err = s.repo.FindTransactionByID(ctx, transaction.ID)
	if err != nil {
		return models.NewHTTPError(http.StatusNotFound, "Transaction not found after update")
	}

	// Calculate if transaction is accepted
	allAccepted := true
	for _, subTx := range transaction.SubTransactions {
		if subTx.Confirmations < transaction.RequiredConfirmations {
			allAccepted = false
			break
		}
	}

	if transactionToProcess.Amount.Covered.Total < transaction.Amount {
		allAccepted = false
	}

	transaction.Accepted = allAccepted

	// Calculate if the transaction is confirmed
	allConfirmed := true
	for _, subTx := range transaction.SubTransactions {
		if subTx.Confirmations < 10 {
			allConfirmed = false
			break
		}
	}

	if transactionToProcess.Amount.Covered.Unlocked < transaction.Amount {
		allConfirmed = false
	}

	transaction.Confirmed = allConfirmed

	// Update the transaction in the repository
	_, err = s.repo.UpdateTransaction(ctx, transaction)
	if err != nil {
		return models.NewHTTPError(http.StatusInternalServerError, "Failed to update transaction: "+err.Error())
	}

	go pos.NotifyTransactionUpdate(transaction.ID, transaction)

	return nil
}

func (s *CallbackService) HandleCallback(ctx context.Context, jwtToken string, callback moneropay.CallbackResponse) (httpErr *models.HTTPError) {
	if ctx == nil {
		return models.NewHTTPError(http.StatusInternalServerError, "context required")
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	// Validate JWT
	if jwtToken == "" {
		return models.NewHTTPError(http.StatusUnauthorized, "JWT is required")
	}

	type Claims struct {
		TransactionID uint `json:"transaction_id"`
		jwt.RegisteredClaims
	}
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(jwtToken, claims, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, models.NewHTTPError(http.StatusUnauthorized, "invalid signing method")
		}
		return []byte(s.config.JWTMoneroPaySecret), nil
	})

	if err != nil {
		return models.NewHTTPError(http.StatusUnauthorized, "Invalid token: "+err.Error())
	}

	if !token.Valid {
		return models.NewHTTPError(http.StatusUnauthorized, "Invalid token")
	}

	httpErr = s.processTransaction(ctx, claims.TransactionID, callback.ToReceiveAddressResponse())
	if httpErr != nil {
		return httpErr
	}
	return nil
}

func (s *CallbackService) HandleLwsHook(ctx context.Context, jwtToken string, payload LwsHookRequest) (httpErr *models.HTTPError) {
	if ctx == nil {
		return models.NewHTTPError(http.StatusInternalServerError, "context required")
	}

	eventTimestamp := time.Now().UTC()
	amount := payload.Amount
	txHash := payload.TxHash
	if payload.TxInfo != nil {
		if amount == 0 {
			amount = payload.TxInfo.Amount
		}
		if txHash == "" {
			txHash = payload.TxInfo.TxHash
		}
		if payload.TxInfo.Timestamp > 0 {
			eventTimestamp = time.Unix(payload.TxInfo.Timestamp, 0).UTC()
		}
	}
	if payload.Timestamp != nil && !payload.Timestamp.IsZero() {
		eventTimestamp = *payload.Timestamp
	}

	if amount == 0 || txHash == "" {
		log.Printf("lws-hook: missing amount/tx_hash (amount=%d, tx_hash=%s)", amount, txHash)
		return models.NewHTTPError(http.StatusBadRequest, "amount and tx_hash are required")
	}

	if time.Since(eventTimestamp) > time.Minute {
		log.Printf("lws-hook: stale payload ts=%s now=%s", eventTimestamp, time.Now().UTC())
		return models.NewHTTPError(http.StatusUnauthorized, "Stale LWS payload")
	}

	if jwtToken != s.config.JWTLwsToken {
		log.Printf("lws-hook: invalid token")
		return models.NewHTTPError(http.StatusUnauthorized, "Invalid token")
	}

	candidates, err := s.repo.FindRecentPendingTransactionsByAmount(ctx, amount, time.Now().Add(-1*time.Minute))
	if err != nil {
		log.Printf("lws-hook: db error resolving by amount: %v", err)
		return models.NewHTTPError(http.StatusUnauthorized, "Unable to resolve transaction for LWS hook")
	}
	if len(candidates) != 1 {
		log.Printf("lws-hook: ambiguous candidates for amount=%d count=%d", amount, len(candidates))
		return models.NewHTTPError(http.StatusUnauthorized, "Unable to uniquely resolve transaction for LWS hook")
	}
	transactionID := candidates[0].ID

	ts := eventTimestamp
	height := int64(0)
	if payload.Height != nil {
		height = *payload.Height
	} else if payload.TxInfo != nil && payload.TxInfo.Block != nil {
		height = *payload.TxInfo.Block
	}

	receive := moneropay.ReceiveAddressResponse{
		Amount: moneropay.Amount{
			Expected: amount,
			Covered: moneropay.Covered{
				Total:    amount,
				Unlocked: 0,
			},
		},
		Transactions: []moneropay.Transaction{
			{
				Amount:          amount,
				Confirmations:   payload.Confirmations,
				DoubleSpendSeen: false,
				Fee:             0,
				Height:          height,
				Timestamp:       ts,
				TxHash:          txHash,
				UnlockTime:      0,
				Locked:          payload.Confirmations == 0,
			},
		},
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	httpErr = s.processTransaction(ctx, transactionID, receive)
	if httpErr != nil {
		return httpErr
	}
	return nil
}
