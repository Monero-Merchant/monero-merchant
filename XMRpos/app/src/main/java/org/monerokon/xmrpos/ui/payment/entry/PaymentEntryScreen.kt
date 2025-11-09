// PaymentEntryScreen.kt
package org.monerokon.xmrpos.ui.payment.entry

import CurrencyConverterCard
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.ui.common.composables.CustomAlertDialog

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
            modifier = Modifier.padding(start = 24.dp, top = innerPadding.calculateTopPadding() + 24.dp, end = 24.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp )
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
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                                    modifier = Modifier.
                                    then(Modifier.size(40.dp)),
                        ) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    CurrencyConverterCard(
                        currency = primaryFiatCurrency,
                        exchangeRate = exchangeRate,
                        paymentValue = paymentValue,
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
                    icon = Icons.Default.Warning
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
    AlertDialog(
        icon = {
            Icon(Icons.Default.Lock, contentDescription = "Locked")
        },
        title = {
            Text(text = "Settings locked")
        },
        text = {
            Column {
                TextField(
                    value = currentPinCode,
                    onValueChange = {currentPinCode = it},
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Enter your PIN") }
                )
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentPinCode == pinCode) {
                        onConfirmation()
                    }
                }
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Go back")
            }
        }
    )
}

// PaymentValue
@Composable
fun PaymentValue(value: String, currency: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
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
                fontSize = 16.sp,
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
    // 1. Create an InteractionSource to track the pressed state.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 2. Animate the elevation for a smoother transition.
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 0.dp, // Shadow appears on press
        label = "elevationAnimation"
    )

    Surface(
        modifier = modifier
            .height(64.dp)
            // 3. Apply the animated shadow.
            .shadow(
                elevation = elevation,
                shape = MaterialTheme.shapes.medium,
                ambientColor = MaterialTheme.colorScheme.secondary,
                spotColor = MaterialTheme.colorScheme.secondary,
            )
            // 4. Use the clickable modifier.
            .clickable(
                interactionSource = interactionSource,
                // Pass null to disable the default ripple effect.
                indication = null,
                onClick = onClick
            ),
        // Use standard button colors and shape for consistency.
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        // Center the text within the Surface.
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary) // titleLarge is often better for large buttons
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
            icon = Icons.Rounded.Clear,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentDescription = "Clear",
            onClick = onClearClick,
            containerColor = Color(0xFFFFFFFF),
            modifier = Modifier.weight(1f)
        )
        PaymentEntryControlButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            iconColor = Color(0xFFFFFFFF),
            contentDescription = "Back",
            onClick = onBackspaceClick,
            containerColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        PaymentEntryControlButton(
            icon = Icons.Rounded.Done,
            iconColor = Color(0xFFFFFFFF),
            contentDescription = "Done",
            onClick = onSubmitClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

// PaymentEntryControlButton
@Composable
fun PaymentEntryControlButton(
    icon: ImageVector,
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
        Icon(imageVector = icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(28.dp))
    }
}

