package org.monerokon.xmrpos.ui.payment.checkout

import org.monerokon.xmrpos.ui.common.composables.CurrencyConverterCard
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.PaymentSuccess
import org.monerokon.xmrpos.ui.common.composables.CustomAlertDialog
import org.monerokon.xmrpos.ui.common.composables.FiatCard
import java.math.BigDecimal

@Composable
fun PaymentCheckoutScreenRoot(viewModel: PaymentCheckoutViewModel, navController: NavHostController, fiatAmount: Double, primaryFiatCurrency: String) {
    viewModel.setNavController(navController)
    LaunchedEffect(fiatAmount, primaryFiatCurrency) {
        viewModel.updatePaymentValue(fiatAmount)
        viewModel.updatePrimaryFiatCurrency(primaryFiatCurrency)
    }
    PaymentCheckoutScreen(
        paymentValue = fiatAmount,
        primaryFiatCurrency = primaryFiatCurrency,
        referenceFiatCurrencies = viewModel.referenceFiatCurrencies,
        exchangeRates = viewModel.exchangeRates,
        targetXMRvalue = viewModel.targetXMRvalue,
        qrCodeUri = viewModel.qrCodeUri,
        generateQRCode = viewModel::generateQRCode,
        navigateBack = viewModel::navigateBack,
        errorMessage = viewModel.errorMessage,
        resetErrorMessage = viewModel::resetErrorMessage,
        navigateToPaymentSuccess = viewModel::navigateToPaymentSuccess
    )
}

@Composable
fun PaymentCheckoutScreen(
    paymentValue: Double,
    primaryFiatCurrency: String,
    referenceFiatCurrencies: List<String>,
    exchangeRates: Map<String, Double>?,
    targetXMRvalue: BigDecimal,
    qrCodeUri: String,
    generateQRCode: (String, Int, Int, Int, Int, Int) -> Bitmap,
    navigateBack: () -> Unit,
    errorMessage: String,
    resetErrorMessage: () -> Unit,
    navigateToPaymentSuccess: (PaymentSuccess) -> Unit
) {
    val openAlertDialog = remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(innerPadding).padding(horizontal = 24.dp).fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                QRCodeWithImage(qrCodeUri, generateQRCode)
            }
            Spacer(modifier = Modifier.height(20.dp))
            FiatCard("Total Amount", primaryFiatCurrency, exchangeRates?.get(primaryFiatCurrency), paymentValue.toString(), xmrValue = targetXMRvalue)
            Spacer(modifier = Modifier.height(16.dp))
            ReferenceCurrenciesCard(referenceFiatCurrencies, exchangeRates, paymentValue, targetXMRvalue)
            Spacer(modifier = Modifier.height(16.dp))
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text("Waiting on payment to complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = {openAlertDialog.value = !openAlertDialog.value}) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator()
            }
            when {
                errorMessage != "" -> {
                    CustomAlertDialog(
                        onDismissRequest = { resetErrorMessage()},
                        onConfirmation = {
                            resetErrorMessage()
                        },
                        dialogTitle = "Error",
                        dialogText = errorMessage,
                        confirmButtonText = "Ok",
                        dismissButtonText = null,
                        icon = {Icon(painter = painterResource(R.drawable.arrow_back_24px), tint = MaterialTheme.colorScheme.primary, contentDescription = "Go back")},
                    )
                }
            }
            when {
                openAlertDialog.value -> {
                    CustomAlertDialog(
                        onDismissRequest = { openAlertDialog.value = false },
                        onConfirmation = {
                            openAlertDialog.value = false
                            navigateBack()
                        },
                        dialogTitle = "Warning",
                        dialogText = "If you go back while the customer is paying, the payment will not be confirmed in the app. Please only go back if the customer has not started paying yet.",
                        confirmButtonText = "Go back",
                        dismissButtonText = "Stay here",
                        icon = {Icon(painter = painterResource(R.drawable.warning_24px), tint = MaterialTheme.colorScheme.primary, contentDescription = "Warning")},
                    )
                }
            }
        }
    }
}

@Composable
fun ReferenceCurrenciesCard(
    referenceFiatCurrencies: List<String>,
    exchangeRates: Map<String, Double>?,
    paymentValue: Double,
    targetXMRvalue: BigDecimal
) {
    Surface (
        shape = MaterialTheme. shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp), // Use heightIn instead of height
    ) {
        LazyColumn {
            items(referenceFiatCurrencies.size) { index ->
                CurrencyConverterCard(
                    referenceFiatCurrencies[index],
                    exchangeRates?.get(referenceFiatCurrencies[index]),
                    paymentValue. toString(),
                    targetXMRvalue = targetXMRvalue
                )
                if (index < referenceFiatCurrencies.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

// QR code with image in center
@Composable
fun QRCodeWithImage(uri: String, generateQRCode: (String, Int, Int, Int, Int, Int) -> Bitmap) {
    var qrCodeBitmap: Bitmap? = null
    if (uri != "") {
        val qrForegroundColor = 0xFF000000.toInt()
        val qrBackgroundColor = 0xFFFFFFFF.toInt()
        qrCodeBitmap = generateQRCode(uri, 400, 400, 1, qrForegroundColor, qrBackgroundColor)
    }
    Surface (
        color = Color(0xFBFBFBFF),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(1.dp).fillMaxSize()
        ) {
            if (qrCodeBitmap != null) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0xFBFBFBFF))
                )
            }
            Image(
                painterResource(R.drawable.monero_symbol),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.22f)
            )
        }
    }
}