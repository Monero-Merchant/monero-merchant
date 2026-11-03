package com.moneromerchant.pos.ui.payment.success

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.moneromerchant.pos.data.repository.PrinterRepository
import com.moneromerchant.pos.ui.PaymentEntry
import com.moneromerchant.pos.ui.PaymentSuccess
import javax.inject.Inject

@HiltViewModel
class PaymentSuccessViewModel @Inject constructor(
    private val printerRepository: PrinterRepository,
) : ViewModel() {

    private var navController: NavHostController? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    fun navigateToEntry() {
        navController?.navigate(PaymentEntry) {
            popUpTo<PaymentEntry>()
            launchSingleTop = true
        }
    }

    var printingInProgress by mutableStateOf(false)

    fun printReceipt(paymentSuccess: PaymentSuccess) {
        printingInProgress = true
        viewModelScope.launch {
            printerRepository.printReceipt(paymentSuccess)
            printingInProgress = false
        }
    }
}