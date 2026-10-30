// LoginScreen.kt
package com.moneromerchant.pos.ui.payment.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneromerchant.pos.ui.common.composables.CustomAlertDialog
import com.moneromerchant.pos.R
import com.moneromerchant.pos.ui.common.composables.CustomOutlinedTextField

// LoginScreenRoot
@Composable
fun LoginScreenRoot(viewModel: LoginViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    val context = LocalContext.current
    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loginPressed()
        } else {
            viewModel.errorMessage = "Nearby devices permission is required to connect to a local merchant server. Allow it in Settings > Apps > Monero Merchant > Permissions > Nearby devices, then retry login."
        }
    }
    LoginScreen(
        instanceUrl = viewModel.instanceUrl,
        vendorID = viewModel.vendorID,
        username = viewModel.username,
        password = viewModel.password,
        errorMessage = viewModel.errorMessage,
        inProgress = viewModel.inProgress,
        updateInstanceUrl = viewModel::updateInstanceUrl,
        updateVendorID = viewModel::updateVendorID,
        updateUsername = viewModel::updateUsername,
        updatePassword = viewModel::updatePassword,
        resetErrorMessage = viewModel::resetErrorMessage,
        loginPressed = {
            if (Build.VERSION.SDK_INT >= 37 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.loginPressed(requestLocalNetworkPermission = {
                    localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                })
            } else {
                viewModel.loginPressed()
            }
        },
    )
}

// LoginScreen
@Composable
fun LoginScreen(
    instanceUrl: String,
    vendorID: String,
    username: String,
    password: String,
    errorMessage: String,
    inProgress: Boolean,
    updateInstanceUrl: (String) -> Unit,
    updateVendorID: (String) -> Unit,
    updateUsername: (String) -> Unit,
    updatePassword: (String) -> Unit,
    resetErrorMessage: () -> Unit,
    loginPressed: () -> Unit

) {
    Scaffold(modifier = Modifier.fillMaxSize().imePadding()) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 80.dp)
        ) {
            Text("Login to POS account", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(48.dp))
            CustomOutlinedTextField(
                value = instanceUrl, label = "Instance url", onValueChange = {updateInstanceUrl(it)}
            )
            CustomOutlinedTextField(
                value = vendorID, label = "Vendor id", onValueChange = {updateVendorID(it)}, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            CustomOutlinedTextField(
                value = username, label = "Username", onValueChange = {updateUsername(it)}
            )
            CustomOutlinedTextField(
                value = password, label = "Password", onValueChange = {updatePassword(it)}, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {loginPressed()},
                enabled = !inProgress,
            ) {
                Row {
                    Text("Login")
                    when {
                        inProgress -> {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                        }
                    }
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
                    icon = {
                        Icon(painter = painterResource(R.drawable.warning_24px), tint = MaterialTheme.colorScheme.primary, contentDescription = "Warning")
                    }
                )
            }
        }

    }
}

