// PaymentCheckoutViewModel.kt
package com.moneromerchant.pos.ui.payment.checkout

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.moneromerchant.pos.data.repository.ExchangeRateRepository
import com.moneromerchant.pos.data.repository.HceRepository
import com.moneromerchant.pos.data.repository.TransactionManager
import com.moneromerchant.pos.data.repository.TransactionResult
import com.moneromerchant.pos.shared.DataResult
import com.moneromerchant.pos.ui.PaymentCheckout
import com.moneromerchant.pos.ui.PaymentEntry
import com.moneromerchant.pos.ui.PaymentSuccess
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Hashtable
import javax.inject.Inject

@HiltViewModel
class PaymentCheckoutViewModel @Inject constructor(
    private val exchangeRateRepository: ExchangeRateRepository,
    private val hceRepository: HceRepository,
    private val transactionManager: TransactionManager,
) : ViewModel() {

    private val logTag = "PaymentCheckoutViewModel"

    private var navController: NavHostController? = null
    private var isFetchingExchangeRates = false
    private var createTransactionJob: Job? = null
    private var observePaymentJob: Job? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    fun navigateBack() {
        stopReceive()
        navController?.popBackStack<PaymentEntry>(inclusive = false)
    }

    var paymentValue by mutableStateOf(0.0)
    var primaryFiatCurrency by mutableStateOf("")
    var referenceFiatCurrencies by mutableStateOf(listOf<String>())
    var exchangeRates: Map<String, Double>? by mutableStateOf(null)
    var rateSources by mutableStateOf(listOf<String>())
    var ratesStale by mutableStateOf(false)
    var targetXMRvalue by mutableStateOf(BigDecimal.ZERO)

    var qrCodeUri by mutableStateOf("")
    var address by mutableStateOf("")
    var errorMessage by mutableStateOf("")

    init {
        fetchExchangeRates()
    }

    fun updatePaymentValue(value: Double) {
        paymentValue = value
        recalculateTargetAmount()
    }

    fun updatePrimaryFiatCurrency(value: String) {
        primaryFiatCurrency = value
        recalculateTargetAmount()
    }

    private fun fetchExchangeRates() {
        if (isFetchingExchangeRates) return
        viewModelScope.launch {
            isFetchingExchangeRates = true
            try {
                val primaryFiatCurrencyResponse = exchangeRateRepository.getPrimaryFiatCurrency().first()
                primaryFiatCurrency = primaryFiatCurrencyResponse

                val referenceFiatCurrenciesResponse = exchangeRateRepository.getReferenceFiatCurrencies().first()
                referenceFiatCurrencies = referenceFiatCurrenciesResponse

                when (val exchangeRatesResponse = exchangeRateRepository.fetchExchangeRates().first()) {
                    is DataResult.Failure -> {
                        errorMessage = exchangeRatesResponse.message
                        exchangeRates = null
                        targetXMRvalue = BigDecimal.ZERO
                    }
                    is DataResult.Success -> {
                        exchangeRates = exchangeRatesResponse.data.rates
                        rateSources = exchangeRatesResponse.data.sources
                        ratesStale = exchangeRatesResponse.data.stale
                        errorMessage = ""
                        recalculateTargetAmount()
                        Log.i(logTag, "Reference exchange rates: $referenceFiatCurrencies")
                        Log.i(logTag, "Exchange rates: $exchangeRates")
                    }
                }
            } finally {
                isFetchingExchangeRates = false
            }
        }
    }

    private fun startPayReceive() {
        val xmrAmount = targetXMRvalue
        if (xmrAmount.compareTo(BigDecimal.ZERO) <= 0) return

        createTransactionJob?.cancel()
        createTransactionJob = viewModelScope.launch {
            val rate = exchangeRates?.get(primaryFiatCurrency) ?: 0.0

            val result = withContext(Dispatchers.IO) {
                transactionManager.createAndRegisterTransaction(
                    fiatAmount = paymentValue,
                    primaryFiatCurrency = primaryFiatCurrency,
                    xmrAmount = xmrAmount,
                    exchangeRate = rate
                )
            }

            when (result) {
                is TransactionResult.Error -> {
                    errorMessage = result.message
                    hceRepository.updateUri("")
                }
                is TransactionResult.Success -> {
                    address = result.address
                    qrCodeUri = result.qrCodeUri
                    hceRepository.updateUri(result.qrCodeUri)
                    Log.i(logTag, "Transaction created: ${result.transactionId}")
                    startObservingPaymentStatus(result.transactionId)
                }
            }
            createTransactionJob = null
        }
    }

    private fun recalculateTargetAmount() {
        val rate = exchangeRates?.get(primaryFiatCurrency) ?: 0.0
        targetXMRvalue = if (rate > 0.0) {
            BigDecimal.valueOf(paymentValue)
                .divide(BigDecimal.valueOf(rate), 12, RoundingMode.UP)
        } else {
            BigDecimal.ZERO
        }
        maybeStartPayReceive()
    }

    private fun maybeStartPayReceive() {
        if (targetXMRvalue.compareTo(BigDecimal.ZERO) > 0 &&
            exchangeRates != null &&
            createTransactionJob == null &&
            qrCodeUri.isEmpty()
        ) {
            startPayReceive()
        }
    }

    private fun startObservingPaymentStatus(transactionId: Int) {
        observePaymentJob?.cancel()

        observePaymentJob = viewModelScope.launch {
            val accepted = transactionManager.acceptedTransaction
                .filterNotNull()
                .first { it.transactionId == transactionId }
            Log.d(logTag, "Payment accepted!")
            navigateToPaymentSuccess(transactionManager.toPaymentSuccess(accepted))
        }
    }

    fun navigateToPaymentSuccess(paymentSuccess: PaymentSuccess) {
        navController?.navigate(paymentSuccess) {
            popUpTo<PaymentCheckout> { inclusive = true }
        }
    }

    fun generateQRCode(text: String, width: Int, height: Int, margin: Int, color: Int, background: Int): Bitmap {
        val writer = QRCodeWriter()
        val hints = Hashtable<EncodeHintType, Any>().apply {
            this[EncodeHintType.MARGIN] = margin
        }
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    setPixel(x, y, if (bitMatrix[x, y]) color else background)
                }
            }
        }
    }

    fun stopReceive() {
        hceRepository.updateUri("")
        createTransactionJob?.cancel()
        createTransactionJob = null
        observePaymentJob?.cancel()
        observePaymentJob = null
    }

    fun resetErrorMessage() {
        errorMessage = ""
    }

    override fun onCleared() {
        super.onCleared()
        stopReceive()
    }
}