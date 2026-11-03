// Package koinly writes the Koinly Simple CSV format.
package koinly

import (
	"encoding/csv"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/monero-merchant/monero-merchant/backend/internal/core/models"
)

type Row struct {
	Timestamp   time.Time
	Amount      int64
	Tag         string
	TxHash      string
	Description string
	Fee         *int64
}

func PaymentRows(transactions []*models.Transaction) []Row {
	var rows []Row
	for _, tx := range transactions {
		if !tx.Confirmed {
			continue
		}
		description := ""
		if tx.Description != nil {
			description = *tx.Description
		}
		for _, sub := range tx.SubTransactions {
			rows = append(rows, Row{
				Timestamp:   sub.Timestamp,
				Amount:      sub.Amount,
				Tag:         "other income",
				TxHash:      sub.TxHash,
				Description: fmt.Sprintf("%d - %s - %s", tx.PosID, tx.Pos.Name, description),
			})
		}
	}
	return rows
}

func WithdrawalRows(transfers []*models.Transfer) ([]Row, error) {
	var rows []Row
	for _, transfer := range transfers {
		if !transfer.Completed || transfer.TxHash == nil || *transfer.TxHash == "" {
			continue
		}
		if transfer.Amount <= 0 {
			return nil, fmt.Errorf("invalid amount for transfer %d", transfer.ID)
		}
		row := Row{
			// UpdatedAt records successful relay, not the earlier payout request.
			Timestamp:   transfer.UpdatedAt,
			Amount:      -transfer.Amount,
			TxHash:      *transfer.TxHash,
			Description: transfer.Address,
		}
		if transfer.AmountTransferred != nil {
			if *transfer.AmountTransferred <= 0 || *transfer.AmountTransferred > transfer.Amount {
				return nil, fmt.Errorf("invalid delivered amount for transfer %d", transfer.ID)
			}
			// Fees are subtracted from outputs. Amount is the gross debit;
			// the fee column describes its fee component, not an extra debit.
			fee := transfer.Amount - *transfer.AmountTransferred
			row.Fee = &fee
		}
		rows = append(rows, row)
	}
	return rows, nil
}

func Write(rows []Row) (string, error) {
	sort.SliceStable(rows, func(i, j int) bool {
		if rows[i].Timestamp.Equal(rows[j].Timestamp) {
			if rows[i].TxHash == rows[j].TxHash {
				return rows[i].Description < rows[j].Description
			}
			return rows[i].TxHash < rows[j].TxHash
		}
		return rows[i].Timestamp.Before(rows[j].Timestamp)
	})
	var output strings.Builder
	w := csv.NewWriter(&output)
	if err := w.Write([]string{"Koinly Date", "Amount", "Currency", "Tag", "TxHash", "Description", "Fee Amount", "Fee Currency"}); err != nil {
		return "", err
	}
	for _, row := range rows {
		fee, feeCurrency := "", ""
		if row.Fee != nil {
			fee, feeCurrency = formatAtomicAmount(*row.Fee), "XMR"
		}
		if err := w.Write([]string{
			row.Timestamp.UTC().Format("2006-01-02 15:04:05"),
			formatAtomicAmount(row.Amount), "XMR", row.Tag, row.TxHash,
			row.Description, fee, feeCurrency,
		}); err != nil {
			return "", err
		}
	}
	w.Flush()
	return output.String(), w.Error()
}

func formatAtomicAmount(amount int64) string {
	const units uint64 = 1_000_000_000_000
	sign := ""
	magnitude := uint64(amount)
	if amount < 0 {
		sign = "-"
		// Also handles MinInt64 without signed overflow.
		magnitude = uint64(-(amount + 1)) + 1
	}
	return fmt.Sprintf("%s%d.%012d", sign, magnitude/units, magnitude%units)
}
