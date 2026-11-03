package vendor

import (
	"context"
	"errors"

	"github.com/monero-merchant/monero-merchant/backend/internal/core/models"
	"gorm.io/gorm"
)

type VendorRepository interface {
	VendorByNameExists(ctx context.Context, name string) (bool, error)
	FindInviteByCode(ctx context.Context, inviteCode string) (*models.Invite, error)
	CreateVendorWithInvite(ctx context.Context, vendor *models.Vendor, inviteID uint) error
	GetVendorByID(ctx context.Context, vendorID uint) (*models.Vendor, error)
	DeleteVendor(ctx context.Context, vendorID uint) error
	DeleteAllTransactionsForVendor(ctx context.Context, vendorID uint) error
	DeleteAllPosForVendor(ctx context.Context, vendorID uint) error
	PosByNameExistsForVendor(ctx context.Context, name string, vendorID uint) (bool, error)
	CreatePos(ctx context.Context, pos *models.Pos) error
	GetBalance(ctx context.Context, vendorID uint) (int64, error)
	GetActiveTransferByVendorID(ctx context.Context, vendorID uint) (*models.Transfer, error)
	GetAllTransferableTransactions(ctx context.Context, vendorID uint) ([]*models.Transaction, error)
	CreateTransfer(ctx context.Context, transfer *models.Transfer) error
	GetTransfersToComplete(ctx context.Context, limit int) ([]*models.Transfer, error)
	MarkTransactionsTransferred(ctx context.Context, transferID uint, transactionIDs []uint) error
	SetTransferTx(ctx context.Context, transferID uint, amountTransferred int64, txHash string, txMetadata string) error
	GetUnrelayedTransfers(ctx context.Context) ([]*models.Transfer, error)
	MarkTransfersCompleted(ctx context.Context, txHash string) error
	GetPosDevicesByVendorID(ctx context.Context, vendorID uint) ([]*models.Pos, error)
	FindTransactionsByVendorID(ctx context.Context, vendorID uint) ([]*models.Transaction, error)
	FindCompletedTransfersByVendorID(ctx context.Context, vendorID uint) ([]*models.Transfer, error)
	// WithTransaction runs fn with a repository whose writes commit together, or roll back if fn returns an error
	WithTransaction(ctx context.Context, fn func(VendorRepository) error) error
}

type vendorRepository struct {
	db *gorm.DB
}

func NewVendorRepository(db *gorm.DB) VendorRepository {
	return &vendorRepository{db: db}
}

func (r *vendorRepository) VendorByNameExists(ctx context.Context, name string) (bool, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var count int64
	if err := r.db.WithContext(ctx).Model(&models.Vendor{}).
		Where("name = ? AND deleted_at IS NULL", name).
		Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

func (r *vendorRepository) FindInviteByCode(ctx context.Context, inviteCode string) (*models.Invite, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var invite models.Invite
	if err := r.db.WithContext(ctx).Where("invite_code = ?", inviteCode).First(&invite).Error; err != nil {
		return nil, err
	}
	return &invite, nil
}

var ErrInviteUsed = errors.New("invite code already used")

// CreateVendorWithInvite claims the invite and creates the vendor together, so an invite is only used once
func (r *vendorRepository) CreateVendorWithInvite(ctx context.Context, vendor *models.Vendor, inviteID uint) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		res := tx.Model(&models.Invite{}).Where("id = ? AND used = ?", inviteID, false).Update("used", true)
		if res.Error != nil {
			return res.Error
		}
		if res.RowsAffected == 0 {
			return ErrInviteUsed
		}
		return tx.Create(vendor).Error
	})
}

func (r *vendorRepository) GetVendorByID(ctx context.Context, vendorID uint) (*models.Vendor, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var vendor models.Vendor
	if err := r.db.WithContext(ctx).First(&vendor, vendorID).Error; err != nil {
		return nil, err
	}
	return &vendor, nil
}

func (r *vendorRepository) DeleteVendor(ctx context.Context, vendorID uint) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Delete(&models.Vendor{}, vendorID).Error
}

func (r *vendorRepository) DeleteAllTransactionsForVendor(ctx context.Context, vendorID uint) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Where("vendor_id = ?", vendorID).Delete(&models.Transaction{}).Error
}

func (r *vendorRepository) DeleteAllPosForVendor(ctx context.Context, vendorID uint) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Where("vendor_id = ?", vendorID).Delete(&models.Pos{}).Error
}

func (r *vendorRepository) PosByNameExistsForVendor(ctx context.Context, name string, vendorID uint) (bool, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var count int64
	if err := r.db.WithContext(ctx).Model(&models.Pos{}).
		Where("name = ? AND vendor_id = ? AND deleted_at IS NULL", name, vendorID).
		Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

func (r *vendorRepository) CreatePos(ctx context.Context, pos *models.Pos) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Create(pos).Error
}

func (r *vendorRepository) GetBalance(ctx context.Context, vendorID uint) (int64, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var balance int64
	err := r.db.WithContext(ctx).Model(&models.Transaction{}).
		Where("vendor_id = ? AND confirmed = ? AND transferred = ?", vendorID, true, false).
		Select("COALESCE(SUM(amount), 0)").
		Scan(&balance).Error
	if err != nil {
		return 0, err
	}
	return balance, nil
}

func (r *vendorRepository) GetActiveTransferByVendorID(ctx context.Context, vendorID uint) (*models.Transfer, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transfer models.Transfer
	err := r.db.WithContext(ctx).Where("vendor_id = ? AND completed = ?", vendorID, false).First(&transfer).Error
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil // No transfer found
		}
		return nil, err
	}
	return &transfer, nil
}

func (r *vendorRepository) GetAllTransferableTransactions(ctx context.Context, vendorID uint) ([]*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transactions []*models.Transaction
	if err := r.db.WithContext(ctx).
		Where("vendor_id = ? AND confirmed = ? AND transferred = ?", vendorID, true, false).
		Find(&transactions).Error; err != nil {
		return nil, err
	}
	return transactions, nil
}

func (r *vendorRepository) CreateTransfer(ctx context.Context, transfer *models.Transfer) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Create(transfer).Error
}

func (r *vendorRepository) GetTransfersToComplete(ctx context.Context, limit int) ([]*models.Transfer, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transfers []*models.Transfer
	if err := r.db.WithContext(ctx).
		Preload("Transactions").
		Where("completed = ? AND tx_hash IS NULL", false).
		Order("created_at ASC").
		Limit(limit).
		Find(&transfers).Error; err != nil {
		return nil, err
	}
	return transfers, nil
}

func (r *vendorRepository) MarkTransactionsTransferred(ctx context.Context, transferID uint, transactionIDs []uint) error {
	if ctx == nil {
		ctx = context.Background()
	}
	return r.db.WithContext(ctx).Model(&models.Transaction{}).
		Where("id IN ?", transactionIDs).
		Updates(map[string]interface{}{
			"transferred": true,
			"transfer_id": transferID,
		}).Error
}

func (r *vendorRepository) SetTransferTx(ctx context.Context, transferID uint, amountTransferred int64, txHash string, txMetadata string) error {
	return r.db.WithContext(ctx).Model(&models.Transfer{}).
		Where("id = ?", transferID).
		Updates(map[string]interface{}{
			"tx_hash":            txHash,
			"tx_metadata":        txMetadata,
			"amount_transferred": amountTransferred,
		}).Error
}

func (r *vendorRepository) GetUnrelayedTransfers(ctx context.Context) ([]*models.Transfer, error) {
	var transfers []*models.Transfer
	err := r.db.WithContext(ctx).
		Where("completed = ? AND tx_hash IS NOT NULL", false).
		Find(&transfers).Error
	return transfers, err
}

func (r *vendorRepository) MarkTransfersCompleted(ctx context.Context, txHash string) error {
	return r.db.WithContext(ctx).Model(&models.Transfer{}).
		Where("tx_hash = ?", txHash).
		Updates(map[string]interface{}{
			"completed":   true,
			"tx_metadata": nil,
		}).Error
}

func (r *vendorRepository) GetPosDevicesByVendorID(ctx context.Context, vendorID uint) ([]*models.Pos, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var devices []*models.Pos
	if err := r.db.WithContext(ctx).
		Where("vendor_id = ? AND deleted_at IS NULL", vendorID).
		Order("created_at DESC").
		Find(&devices).Error; err != nil {
		return nil, err
	}
	return devices, nil
}

func (r *vendorRepository) FindTransactionsByVendorID(ctx context.Context, vendorID uint) ([]*models.Transaction, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transactions []*models.Transaction
	if err := r.db.WithContext(ctx).
		Preload("SubTransactions").
		Preload("Pos").
		Where("vendor_id = ?", vendorID).
		Order("created_at DESC").
		Find(&transactions).Error; err != nil {
		return nil, err
	}
	return transactions, nil
}

func (r *vendorRepository) WithTransaction(ctx context.Context, fn func(VendorRepository) error) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		return fn(&vendorRepository{db: tx})
	})
}

func (r *vendorRepository) FindCompletedTransfersByVendorID(ctx context.Context, vendorID uint) ([]*models.Transfer, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var transfers []*models.Transfer
	err := r.db.WithContext(ctx).
		Where("vendor_id = ? AND completed = ? AND tx_hash IS NOT NULL", vendorID, true).
		Order("updated_at ASC, id ASC").
		Find(&transfers).Error
	return transfers, err
}
