package org.monerokon.xmrpos.ui.settings.transactionhistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.monerokon.xmrpos.data.remote.backend.model.BackendConfirmedTransaction
import org.monerokon.xmrpos.data.remote.backend.model.BackendPendingTransaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.monerokon.xmrpos.ui.common.composables.StyledTopAppBar

@Composable
fun TransactionHistoryScreenRoot(
    viewModel: TransactionHistoryViewModel,
    navController: NavHostController,
) {
    viewModel.setNavController(navController)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionHistoryScreen(
        uiState = uiState,
        onBackClick = viewModel::navigateToMainSettings,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    uiState: TransactionHistoryUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            StyledTopAppBar(
                text = "Transaction history",
                onBackClick = onBackClick
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onRefresh) {
                            Text("Try again")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                ) {
                    item {
                        Text(
                            text = "Pending transactions",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        if (uiState.pendingTransactions.isEmpty()) {
                            EmptyStateMessage("No pending transactions")
                        } else {
                            PendingTransactionsTable(uiState.pendingTransactions)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Confirmed transactions",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    if (uiState.confirmedTransactions.isEmpty()) {
                        item {
                            EmptyStateMessage("No confirmed transactions")
                        }
                    } else {
                        items(uiState.confirmedTransactions, key = { it.transactionId }) { transaction ->
                            Spacer(modifier = Modifier.height(10.dp))
                            ConfirmedTransactionRow(transaction)
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PendingTransactionsTable(transactions: List<BackendPendingTransaction>) {Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = MaterialTheme.shapes.medium,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TableText("ID", Modifier.weight(0.6f))
            TableText("Amount (XMR)", Modifier.weight(1.8f))
            TableText("Accepted", Modifier.weight(1f))
            TableText("Confirmed", Modifier.weight(0.9f))
        }

        transactions.forEachIndexed { index, transaction ->
            val backgroundColor = if (index % 2 == 0) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableDataText(transaction.id.toString(), Modifier.weight(0.6f))
                TableDataText(
                    formatAtomicAmount(transaction.amount),
                    Modifier.weight(1.8f)
                )
                TableDataText(
                    if (transaction.accepted) "Yes" else "No",
                    Modifier.weight(1f)
                )
                TableDataText(
                    if (transaction.confirmed) "Yes" else "No",
                    Modifier.weight(0.9f)
                )
            }
        }
    }
}
}

@Composable
private fun TableText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
        maxLines = 1
    )
}

@Composable
private fun TableDataText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible
    )
}


@Composable
private fun ConfirmedTransactionRow(transaction: BackendConfirmedTransaction) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Text(
            text = "Transaction #${transaction.transactionId}",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TableHeaderCell("Tx hash")
            TableCell(
                text = transaction.txHash,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell("Timestamp", formatTimestamp(transaction.timestamp))
            DetailCell("Height", transaction.height.toString())
        }
    }
}

@Composable
private fun RowScope.TableHeaderCell(text: String, weight: Float = 1f) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = Modifier
            .weight(weight)
    )
}

@Composable
private fun RowScope.TableCell(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    weight: Float = 1f,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(weight)
    )
}

@Composable
private fun RowScope.DetailCell(label: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatAtomicAmount(amount: Long): String {
    val xmrValue = amount / 1_000_000_000_000.0
    return String.format(Locale.US, "%.12f", xmrValue)
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val instant = Instant.parse(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        formatter.format(instant.atZone(ZoneId.systemDefault()))
    } catch (e: Exception) {
        timestamp
    }
}
