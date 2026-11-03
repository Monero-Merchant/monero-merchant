// PaymentEntryViewModel.kt
package com.moneromerchant.pos.ui.payment.entry

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.moneromerchant.pos.data.repository.DataStoreRepository
import com.moneromerchant.pos.data.repository.ExchangeRateRepository
import com.moneromerchant.pos.data.repository.TransactionManager
import com.moneromerchant.pos.shared.DataResult
import com.moneromerchant.pos.ui.PaymentCheckout
import com.moneromerchant.pos.ui.Settings
import javax.inject.Inject

@HiltViewModel
class PaymentEntryViewModel @Inject constructor(
    private val exchangeRateRepository: ExchangeRateRepository,
    private val dataStoreRepository: DataStoreRepository,
    private val transactionManager: TransactionManager,
) : ViewModel() {

    private val logTag = "PaymentEntryViewModel"

    private var navController: NavHostController? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    var primaryFiatCurrency by mutableStateOf("")
    var paymentValue by mutableStateOf("0")
    var exchangeRate: Double? by mutableStateOf(null)
    var rateSources by mutableStateOf(listOf<String>())
    var ratesStale by mutableStateOf(false)

    var openSettingsPinCodeDialog by mutableStateOf(false)
    var requirePinCodeOpenSettings by mutableStateOf(true)
    var pinCodeOpenSettings by mutableStateOf("`")

    var errorMessage by mutableStateOf("")
    private var isFetchingExchangeRate = false

    init {
        viewModelScope.launch {
            dataStoreRepository.getRequirePinCodeOnOpenSettings().collect {  storedRequirePinCodeOpenSettings ->
                requirePinCodeOpenSettings = storedRequirePinCodeOpenSettings
            }
        }
        viewModelScope.launch {
            dataStoreRepository.getPinCodeOpenSettings().collect { storedPinCodeOpenSettings ->
                pinCodeOpenSettings = storedPinCodeOpenSettings
            }
        }
        // This screen is reused between orders, so reset the amount once one is paid
        viewModelScope.launch {
            transactionManager.acceptedTransaction.filterNotNull().collect { clear() }
        }
    }

    fun fetchExchangeRate() {
        if (isFetchingExchangeRate) return
        viewModelScope.launch {
            isFetchingExchangeRate = true
            try {
                val primaryFiatCurrencyResponse = exchangeRateRepository.getPrimaryFiatCurrency().first()
                primaryFiatCurrency = primaryFiatCurrencyResponse

                when (val exchangeRateResponse = exchangeRateRepository.fetchPrimaryExchangeRate().first()) {
                    is DataResult.Failure -> {
                        errorMessage = exchangeRateResponse.message
                    }
                    is DataResult.Success -> {
                        val rate = exchangeRateResponse.data.rates.values.firstOrNull()
                        if (rate != null) {
                            exchangeRate = rate
                            rateSources = exchangeRateResponse.data.sources
                            ratesStale = exchangeRateResponse.data.stale
                            errorMessage = ""
                        } else {
                            errorMessage = "Missing exchange rate"
                        }
                    }
                }
            } finally {
                isFetchingExchangeRate = false
            }
        }
    }


    fun addDigit(digit: String) {
        // Rules to prevent invalid input
        if (paymentValue.length >= 16) {
            return
        }
        if (paymentValue.isEmpty() && digit == "0") {
            return
        }
        if (paymentValue.isEmpty() && digit == ".") {
            paymentValue += "0."
            return
        }
        if (paymentValue.contains(".") && digit == ".") {
            return
        }
        if (paymentValue == "0" && digit != ".") {
            paymentValue = digit
            return
        }
        paymentValue += digit
    }

    fun removeDigit() {
        if (paymentValue.length == 1) {
            paymentValue = "0"
            return
        }
        if (paymentValue.isNotEmpty()) {
            paymentValue = paymentValue.dropLast(1)
        }
    }

    fun clear() {
        paymentValue = "0"
    }

    fun submit() {
        Log.i(logTag, "Going to next screen!")
        if (paymentValue.toDouble() == 0.0) {
            return
        }
        navController?.navigate(PaymentCheckout(paymentValue.toDouble(), primaryFiatCurrency))
    }

    fun tryOpenSettings() {
        Log.i(logTag, "open settings")
        if (requirePinCodeOpenSettings && pinCodeOpenSettings != "") {
            openSettingsPinCodeDialog = true
        } else {
            navController?.navigate(Settings)
        }
    }

    fun openSettings() {
        navController?.navigate(Settings)
    }

    fun updateOpenSettingsPinCodeDialog(newOpenSettingsPinCodeDialog: Boolean) {
        openSettingsPinCodeDialog = newOpenSettingsPinCodeDialog
    }

    fun resetErrorMessage() {
        errorMessage = ""
    }

}