package pos

import (
	"context"
	"time"

	"github.com/monerokon/xmrpos/xmrpos-backend/internal/core/models"
	"gorm.io/gorm"
)

type PosRepository interface {
	FindTransactionByID(ctx context.Context, id uint) (*models.Transaction, error)
	CreateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error)
	UpdateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error)
	FindTransactionsByPosID(ctx context.Context, vendorID uint, posID uint) ([]*models.Transaction, error)
	DeletePendingTransactionsBefore(ctx context.Context, cutoff time.Time) (int64, error)
}

type posRepository struct {
	db *gorm.DB
}

func NewPosRepository(db *gorm.DB) PosRepository {
	return &posRepository{db: db}
}

func (r *posRepository) FindTransactionByID(ctx context.Context, id uint) (*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transaction models.Transaction
	if err := r.db.WithContext(ctx).Preload("SubTransactions").First(&transaction, id).Error; err != nil {
		return nil, err
	}
	return &transaction, nil
}

func (r *posRepository) CreateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if err := r.db.WithContext(ctx).Create(transaction).Error; err != nil {
		return nil, err
	}
	return transaction, nil
}

func (r *posRepository) UpdateTransaction(ctx context.Context, transaction *models.Transaction) (*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if err := r.db.WithContext(ctx).Save(transaction).Error; err != nil {
		return nil, err
	}
	return transaction, nil
}

func (r *posRepository) FindTransactionsByPosID(ctx context.Context, vendorID uint, posID uint) ([]*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}

	var transactions []*models.Transaction
	if err := r.db.WithContext(ctx).
		Preload("SubTransactions").
		Where("vendor_id = ? AND pos_id = ?", vendorID, posID).
		Order("created_at DESC").
		Find(&transactions).Error; err != nil {
		return nil, err
	}

	return transactions, nil
}

func (r *posRepository) DeletePendingTransactionsBefore(ctx context.Context, cutoff time.Time) (int64, error) {
	if ctx == nil {
		ctx = context.Background()
	}

	tx := r.db.WithContext(ctx).Begin()
	if tx.Error != nil {
		return 0, tx.Error
	}

	var ids []uint
	if err := tx.
		Model(&models.Transaction{}).
		Where("confirmed = ? AND created_at < ?", false, cutoff).
		Pluck("id", &ids).Error; err != nil {
		tx.Rollback()
		return 0, err
	}

	if len(ids) == 0 {
		if err := tx.Commit().Error; err != nil {
			return 0, err
		}
		return 0, nil
	}

	if err := tx.Where("transaction_id IN ?", ids).Delete(&models.SubTransaction{}).Error; err != nil {
		tx.Rollback()
		return 0, err
	}

	res := tx.Where("id IN ?", ids).Delete(&models.Transaction{})
	if res.Error != nil {
		tx.Rollback()
		return 0, res.Error
	}

	if err := tx.Commit().Error; err != nil {
		return 0, err
	}

	return res.RowsAffected, nil
}
