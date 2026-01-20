// PaymentEntryScreen.kt
package org.monerokon.xmrpos.ui.payment.entry

import org.monerokon.xmrpos.ui.common.composables.CurrencyConverterCard
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.common.composables.CustomAlertDialog
import org.monerokon.xmrpos.ui.common.composables.CustomOutlinedTextField
import java.math.RoundingMode

// PaymentEntryScreenRoot
@Composable
fun PaymentEntryScreenRoot(viewModel: PaymentEntryViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    PaymentEntryScreen(
        paymentValue = viewModel.paymentValue,
        primaryFiatCurrency = viewModel.primaryFiatCurrency,
        exchangeRate = viewModel.exchangeRate,
        onDigitClick = viewModel::addDigit,
        onBackspaceClick = viewModel::removeDigit,
        onClearClick = viewModel::clear,
        onSubmitClick = viewModel::submit,
        onSettingsClick = viewModel::tryOpenSettings,
        openSettingsPinCodeDialog = viewModel.openSettingsPinCodeDialog,
        pinCodeOpenSettings = viewModel.pinCodeOpenSettings,
        updateOpenSettingsPinCodeDialog = viewModel::updateOpenSettingsPinCodeDialog,
        openSettings = viewModel::openSettings,
        errorMessage = viewModel.errorMessage,
        resetErrorMessage = viewModel::resetErrorMessage,
    )
}

// PaymentEntryScreen
@Composable
fun PaymentEntryScreen(
    paymentValue: String,
    primaryFiatCurrency: String,
    exchangeRate: Double?,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onSettingsClick: () -> Unit,
    openSettingsPinCodeDialog: Boolean,
    pinCodeOpenSettings: String,
    updateOpenSettingsPinCodeDialog: (Boolean) -> Unit,
    openSettings: () -> Unit,
    errorMessage: String,
    resetErrorMessage: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp )
        ) {
            Column (
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxHeight()
            ) {
                Column (
                    verticalArrangement = Arrangement.Top,
                ){
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        FilledIconButton(
                            onClick = {onSettingsClick()},
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                                    modifier = Modifier.
                                    then(Modifier.size(40.dp)),
                        ) {
                            Icon(painterResource(R.drawable.settings_24px), contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    CurrencyConverterCard(
                        currency = primaryFiatCurrency,
                        exchangeRate = exchangeRate,
                        paymentValue = paymentValue.toBigDecimal().setScale(
                            maxOf(3, paymentValue.toBigDecimal().scale()),
                            RoundingMode. HALF_UP
                        ).toPlainString(),
                        emphasize = true,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    PaymentValue(value = paymentValue, currency = primaryFiatCurrency)
                    Spacer(modifier = Modifier.height(10.dp))
                    PaymentEntryButtons(
                        onDigitClick = onDigitClick,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PaymentEntryControlButtons(
                        onBackspaceClick = onBackspaceClick,
                        onClearClick = onClearClick,
                        onSubmitClick = onSubmitClick
                    )
                }
            }
        }
        when {
            errorMessage != "" -> {
                CustomAlertDialog(
                    onDismissRequest = { resetErrorMessage() },
                    onConfirmation = {
                        resetErrorMessage()
                    },
                    dialogTitle = "Error",
                    dialogText = errorMessage,
                    confirmButtonText = "Ok",
                    dismissButtonText = null,
                    icon = {Icon(
                        painter = painterResource(R.drawable.warning_24px),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Warning",
                    )}
                )
            }
        }
    }
    when {
        openSettingsPinCodeDialog -> {
            OpenSettingsDialog(
                onDismissRequest = { updateOpenSettingsPinCodeDialog(false) },
                onConfirmation = {
                    openSettings()
                },
                pinCode = pinCodeOpenSettings,
            )
        }
    }
}

@Composable
fun OpenSettingsDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    pinCode: String
) {
    var currentPinCode by remember { mutableStateOf("") }
    CustomAlertDialog(
        onDismissRequest = {
            onDismissRequest()
        },
        onConfirmation = {
            if (currentPinCode == pinCode) {
                onConfirmation()
            }
        },
        dialogTitle = "Settings locked",
        dialogContent = {
            CustomOutlinedTextField(
                value = currentPinCode,
                onValueChange = {currentPinCode = it},
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = "Enter your PIN",
                useDarkTheme = false
            )
        },
        confirmButtonText = "Unlock",
        dismissButtonText = "Go back",
        icon = {
            Icon(painter = painterResource(R.drawable.lock_24px), tint = MaterialTheme.colorScheme.primary, contentDescription = "Locked")
        },
    )
}

// PaymentValue
@Composable
fun PaymentValue(value: String, currency: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)

        ) {
            Text(
                text = currency,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
            )
            Text(
                text = value,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

// PaymentEntryButtons
@Composable
fun PaymentEntryButtons(
    onDigitClick: (String) -> Unit,
) {
    Column {
        Row {
            PaymentEntryButton(
                text = "1",
                onClick = { onDigitClick("1") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "2",
                onClick = { onDigitClick("2") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "3",
                onClick = { onDigitClick("3") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            PaymentEntryButton(
                text = "4",
                onClick = { onDigitClick("4") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "5",
                onClick = { onDigitClick("5") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "6",
                onClick = { onDigitClick("6") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            PaymentEntryButton(
                text = "7",
                onClick = { onDigitClick("7") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "8",
                onClick = { onDigitClick("8") },
                modifier = Modifier.weight(1f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = "9",
                onClick = { onDigitClick("9") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row (
            modifier = Modifier.fillMaxWidth()
        ) {
            PaymentEntryButton(
                text = "0",
                onClick = { onDigitClick("0") },
                modifier = Modifier.weight(2f)
            )
            ButtonSpacing()
            PaymentEntryButton(
                text = ".",
                onClick = { onDigitClick(".") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ButtonSpacing() {
    Spacer(
        modifier = Modifier.padding(horizontal = 5.dp)
    )
}


@Composable
fun PaymentEntryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shadowAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val shadowColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .height(64.dp)
            .dropShadow(
                shape = MaterialTheme.shapes.extraSmall,
                block = {
                    radius = 40.dp.value
                    color = shadowColor.copy(alpha = shadowAlpha.value)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    coroutineScope.launch {
                        // Animate TO 1f (glow in) very quickly.
                        shadowAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 100, easing = EaseOut)
                        )
                        // Then, immediately start the animation back TO 0f (glow out) slowly.
                        shadowAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 300, easing = EaseInOut)
                        )
                    }
                    onClick()
                }
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}




// PaymentEntryControlButtons (backspace, clear, and forward)
@Composable
fun PaymentEntryControlButtons(
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmitClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        PaymentEntryControlButton(
            icon = painterResource(R.drawable.close_24px),
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentDescription = "Clear",
            onClick = onClearClick,
            containerColor = Color(0xFFFFFFFF),
            modifier = Modifier.weight(1f)
        )
        PaymentEntryControlButton(
            icon = painterResource(R.drawable.arrow_back_24px),
            iconColor = Color(0xFFFFFFFF),
            contentDescription = "Back",
            onClick = onBackspaceClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.weight(1f)
        )
        PaymentEntryControlButton(
            icon = painterResource(R.drawable.check_24px),
            iconColor = Color(0xFFFFFFFF),
            contentDescription = "Done",
            onClick = onSubmitClick,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
    }
}

// PaymentEntryControlButton
@Composable
fun PaymentEntryControlButton(
    icon: Painter,
    iconColor: Color,
    contentDescription: String?,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = containerColor
        ),
        modifier = modifier.height(64.dp)
    ) {
        Icon(painter = icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(28.dp))
    }
}

