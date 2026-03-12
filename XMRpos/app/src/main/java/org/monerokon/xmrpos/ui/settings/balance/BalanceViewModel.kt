package org.monerokon.xmrpos.ui.settings.balance

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.monerokon.xmrpos.data.remote.backend.model.BackendBalancePosResponse
import org.monerokon.xmrpos.shared.DataResult
import org.monerokon.xmrpos.ui.Settings
import org.monerokon.xmrpos.data.repository.BackendRepository
import org.monerokon.xmrpos.data.repository.ExchangeRateRepository
import javax.inject.Inject

@HiltViewModel
class BalanceViewModel @Inject constructor(
    private val backendRepository: BackendRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) : ViewModel() {

    private val logTag = "BalanceViewModel"

    private var navController: NavHostController? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    fun navigateToMainSettings() {
        navController?.popBackStack()
    }

    var posBalance: DataResult<BackendBalancePosResponse>? by mutableStateOf(null)

    var primaryFiatCurrency by mutableStateOf("")

    var exchangeRate by mutableDoubleStateOf(0.0)


    init {
        viewModelScope.launch {
            posBalance = backendRepository.fetchPosBalance()
        }
        viewModelScope.launch {
            val primaryFiatCurrencyResponse = exchangeRateRepository.getPrimaryFiatCurrency().first()
            primaryFiatCurrency = primaryFiatCurrencyResponse
            val exchangeRateResponse = exchangeRateRepository.fetchPrimaryExchangeRate().first()
            exchangeRate = when (exchangeRateResponse) {
                is DataResult.Failure -> 0.0
                is DataResult.Success -> exchangeRateResponse.data.values.first()
            }
        }
    }
}
