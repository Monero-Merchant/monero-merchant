package org.monerokon.xmrpos.ui.payment.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.monerokon.xmrpos.data.repository.AcceptedTransaction
import org.monerokon.xmrpos.data.repository.PrinterRepository
import org.monerokon.xmrpos.data.repository.TransactionManager
import org.monerokon.xmrpos.ui.PaymentEntry
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val printerRepository: PrinterRepository,
    private val transactionManager: TransactionManager
) : ViewModel() {

    private var navController: NavHostController? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    // Candidate notification (UI decides whether to show it)
    private val _candidateNotification = MutableStateFlow<AcceptedTransaction?>(null)
    val candidateNotification: StateFlow<AcceptedTransaction?> = _candidateNotification.asStateFlow()

    // Track shown transaction IDs to prevent duplicates
    private val shownTransactionIds = mutableSetOf<Int>()

    var printingInProgress by mutableStateOf(false)

    init {
        collectTransactionStatus()
    }

    private fun collectTransactionStatus() {
        viewModelScope.launch {
            transactionManager.acceptedTransaction.collect { accepted ->
                if (accepted != null) {
                    handleTransactionAccepted(accepted);
                }
            }
        }
    }

    private fun handleTransactionAccepted(accepted: AcceptedTransaction) {
        // Check if already shown
        if (accepted.transactionId in shownTransactionIds) return
        // Mark as shown and emit
        shownTransactionIds.add(accepted.transactionId)
        _candidateNotification.value = accepted
    }

    fun clearCandidate() {
        _candidateNotification.value = null
    }

    fun navigateToPaymentEntry() {
        clearCandidate()
        navController?.navigate(PaymentEntry)
    }

    fun printReceipt(accepted: AcceptedTransaction) {
        printingInProgress = true
        viewModelScope.launch {
            printerRepository.printReceipt(transactionManager.toPaymentSuccess(accepted))
            printingInProgress = false
        }
    }
}