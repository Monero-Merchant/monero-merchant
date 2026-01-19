package callback

import (
	"context"
	"time"

	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/models"
	"gorm.io/gorm"
)

type CallbackRepository interface {
	FindTransactionByID(ctx context.Context, id uint) (*models.Transaction, error)
	FindUnconfirmedTransactions(ctx context.Context) ([]*models.Transaction, error)
	FindRecentPendingTransactionsByAmount(ctx context.Context, amount int64, createdAfter time.Time) ([]*models.Transaction, error)
	UpdateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error)
	UpdateSubTransaction(ctx context.Context, subTx *models.SubTransaction) (*models.SubTransaction, error)
	CreateSubTransaction(ctx context.Context, subTx *models.SubTransaction) (*models.SubTransaction, error)
}

type callbackRepository struct {
	db *gorm.DB
}

func NewCallbackRepository(db *gorm.DB) CallbackRepository {
	return &callbackRepository{db: db}
}

func (r *callbackRepository) FindTransactionByID(ctx context.Context, id uint) (*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transaction models.Transaction
	if err := r.db.WithContext(ctx).Preload("SubTransactions").First(&transaction, id).Error; err != nil {
		return nil, err
	}
	return &transaction, nil
}

func (r *callbackRepository) FindUnconfirmedTransactions(ctx context.Context) ([]*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transactions []*models.Transaction
	if err := r.db.WithContext(ctx).
		Preload("SubTransactions").
		Where("confirmed = ?", false).
		Find(&transactions).Error; err != nil {
		return nil, err
	}
	return transactions, nil
}

func (r *callbackRepository) FindRecentPendingTransactionsByAmount(ctx context.Context, amount int64, createdAfter time.Time) ([]*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}

	var transactions []*models.Transaction
	if err := r.db.WithContext(ctx).
		Preload("SubTransactions").
		Where("amount = ? AND confirmed = ? AND created_at >= ?", amount, false, createdAfter).
		Order("created_at DESC").
		Find(&transactions).Error; err != nil {
		return nil, err
	}
	return transactions, nil
}

// Update only the main transaction fields
func (r *callbackRepository) UpdateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if err := r.db.WithContext(ctx).Model(&models.Transaction{}).Where("id = ?", transaction.ID).Updates(transaction).Error; err != nil {
		return nil, err
	}
	return transaction, nil
}

// Update an existing subtransaction (by ID)
func (r *callbackRepository) UpdateSubTransaction(ctx context.Context, subTx *models.SubTransaction) (*models.SubTransaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if err := r.db.WithContext(ctx).Model(&models.SubTransaction{}).Where("id = ?", subTx.ID).Updates(subTx).Error; err != nil {
		return nil, err
	}
	return subTx, nil
}

// Create a new subtransaction
func (r *callbackRepository) CreateSubTransaction(ctx context.Context, subTx *models.SubTransaction) (*models.SubTransaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if err := r.db.WithContext(ctx).Create(subTx).Error; err != nil {
		return nil, err
	}
	return subTx, nil
}
