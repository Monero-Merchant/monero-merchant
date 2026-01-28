package org.monerokon.xmrpos.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.monerokon.xmrpos.data.remote.backend.model.BackendCreateTransactionRequest
import org.monerokon.xmrpos.data.remote.backend.model.BackendTransactionStatusUpdate
import org.monerokon.xmrpos.di.ApplicationScope
import org.monerokon.xmrpos.shared.DataResult
import org.monerokon.xmrpos.ui.PaymentSuccess
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

@Singleton
class TransactionManager @Inject constructor(
    private val backendRepository: BackendRepository,
    private val dataStoreRepository: DataStoreRepository,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {
    // Active transaction context
    private val _currentTransaction = MutableStateFlow<ActiveTransaction?>(null)
    val currentTransaction: StateFlow<ActiveTransaction?> = _currentTransaction.asStateFlow()

    // Completed transactions (for notifications/success)
    private val _acceptedTransaction = MutableStateFlow<AcceptedTransaction?>(null)
    val acceptedTransaction: StateFlow<AcceptedTransaction?> = _acceptedTransaction.asStateFlow()

    init {
        coroutineScope.launch {
            backendRepository.currentTransactionStatus.collect { status ->
                handleTransactionStatusUpdate(status)
            }
        }
    }

    suspend fun createAndRegisterTransaction(
        fiatAmount: Double,
        primaryFiatCurrency: String,
        xmrAmount: BigDecimal,
        exchangeRate: Double
    ): TransactionResult {
        val normalizedAmount = xmrAmount.setScale(12, RoundingMode.UP)
        val atomicAmount = normalizedAmount
            .movePointRight(12)
            .longValueExact()

        val randomizedAtomicAmount = atomicAmount - (atomicAmount % 1000) + Random.nextLong(1, 1000)
        val randomizedNormalizedAmount = BigDecimal.valueOf(randomizedAtomicAmount, 12)

        val requiredConfirmations = dataStoreRepository.getBackendConfValue().first().split("-")[0].toInt()
        val backendCreateTransactionRequest = BackendCreateTransactionRequest(
            randomizedAtomicAmount,
            "XMRpos",
            fiatAmount,
            primaryFiatCurrency,
            requiredConfirmations
        )

        return when (val response = backendRepository.createTransaction(backendCreateTransactionRequest)) {
            is DataResult.Failure -> {
                backendRepository.stopObservingTransactionUpdates()
                TransactionResult.Error(response.message)
            }
            is DataResult.Success -> {
                backendRepository.observeCurrentTransactionUpdates(response.data.id)

                registerActiveTransaction(
                    fiatAmount = fiatAmount,
                    primaryFiatCurrency = primaryFiatCurrency,
                    xmrAmount = randomizedNormalizedAmount.toDouble(),
                    exchangeRate = exchangeRate,
                    transactionId = response.data.id
                )

                val formattedAmount = randomizedNormalizedAmount.toPlainString()
                TransactionResult.Success(
                    address = response.data.address,
                    qrCodeUri = "monero:${response.data.address}?tx_amount=$formattedAmount&tx_description=XMRpos",
                    transactionId = response.data.id
                )
            }
        }
    }

    private fun registerActiveTransaction(
        fiatAmount: Double,
        primaryFiatCurrency: String,
        xmrAmount: Double,
        exchangeRate: Double,
        transactionId: Int
    ) {
        _currentTransaction.value = ActiveTransaction(
            fiatAmount = fiatAmount,
            primaryFiatCurrency = primaryFiatCurrency,
            xmrAmount = xmrAmount,
            exchangeRate = exchangeRate,
            transactionId = transactionId
        )
    }

    fun clearCurrentTransaction() {
        _currentTransaction.value = null
    }

    fun clearAcceptedTransaction() {
        _acceptedTransaction.value = null
    }

    private suspend fun handleTransactionStatusUpdate(status: BackendTransactionStatusUpdate?) {
        if (status == null) return

        val currentTx = _currentTransaction.value

        // Only process if this matches our current transaction
        if (currentTx?.transactionId != status.id) return

        // Only emit if accepted
        if (!status.accepted) return

        // Get printer setting
        val showPrintReceipt = dataStoreRepository.getPrinterConnectionType().first() != "none"

        _acceptedTransaction.value = AcceptedTransaction(
            fiatAmount = currentTx.fiatAmount,
            primaryFiatCurrency = currentTx.primaryFiatCurrency,
            txId = status.subTransactions.firstOrNull()?.txHash ?: "",
            xmrAmount = status.amount / 10.0.pow(12),
            exchangeRate = currentTx.exchangeRate,
            timestamp = status.updatedAt,
            showPrintReceipt = showPrintReceipt,
            transactionId = status.id
        )

        // Clear current transaction after completion
        clearCurrentTransaction()
    }

    fun toPaymentSuccess(completed: AcceptedTransaction): PaymentSuccess {
        return PaymentSuccess(
            fiatAmount = completed.fiatAmount,
            primaryFiatCurrency = completed.primaryFiatCurrency,
            txId = completed.txId,
            xmrAmount = completed.xmrAmount,
            exchangeRate = completed.exchangeRate,
            timestamp = completed.timestamp,
            showPrintReceipt = completed.showPrintReceipt
        )
    }
}

data class ActiveTransaction(
    val fiatAmount: Double,
    val primaryFiatCurrency: String,
    val xmrAmount: Double,
    val exchangeRate: Double,
    val transactionId: Int?
)

data class AcceptedTransaction(
    val fiatAmount: Double,
    val primaryFiatCurrency: String,
    val txId: String,
    val xmrAmount: Double,
    val exchangeRate: Double,
    val timestamp: String,
    val showPrintReceipt: Boolean,
    val transactionId: Int
)

sealed class TransactionResult {
    data class Success(
        val address: String,
        val qrCodeUri: String,
        val transactionId: Int
    ) : TransactionResult()

    data class Error(val message: String) : TransactionResult()
}