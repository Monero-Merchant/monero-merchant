package org.monerokon.xmrpos.ui.payment.notification

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.data.repository.AcceptedTransaction
import org.monerokon.xmrpos.ui.common.composables.CustomAlertDialog

/**
 * Notification dialog that monitors transaction status and displays
 * notifications based on route suppression rules.
 *
 * @param navController The navigation controller to monitor current route
 * @param suppressedRoutes Set of route class qualified names where notifications should be suppressed
 * @param viewModel The ViewModel managing notification state
 */
@Composable
fun NotificationDialog(
    navController: NavHostController,
    suppressedRoutes: Set<String>,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    viewModel.setNavController(navController)

    var shownNotification by remember { mutableStateOf<AcceptedTransaction?>(null) }

    val candidateNotification by viewModel.candidateNotification.collectAsState()

    LaunchedEffect(candidateNotification) {
        val candidate = candidateNotification ?: return@LaunchedEffect

        val currentRoute = navController.currentBackStackEntry?.destination?.route

        // Check if current route suppresses notifications
        val isSuppressed = currentRoute != null && suppressedRoutes.any { suppressedRoute ->
            currentRoute.startsWith(suppressedRoute)
        }

        if (isSuppressed) {
            viewModel.clearCandidate()
            return@LaunchedEffect
        }

        // Check if already showing a notification
        if (shownNotification != null) {
            viewModel.clearCandidate()
            return@LaunchedEffect
        }

        // Show the notification
        shownNotification = candidate
        viewModel.clearCandidate()
    }

    // Render dialog if notification exists
    shownNotification?.let { notification ->
        CustomAlertDialog(
            onDismissRequest = {
                shownNotification = null
            },
            onDismissButton = {
                viewModel.printReceipt(notification)
                shownNotification = null
            },
            onConfirmation = {
                shownNotification = null
                viewModel.navigateToPaymentEntry()
            },
            confirmButtonText = "New Order",
            dismissButtonText = "Print Receipt",
            dialogTitle = "Payment successful",
            dialogText = "The payment has been confirmed.\nYou can now start a new order.",
            icon = {
                Image(
                    painterResource(R.drawable.success),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            },
            showCloseX = true,
            reverseButtonOrder = true,
            dismissButtonLoading = viewModel.printingInProgress,
        )
    }
}