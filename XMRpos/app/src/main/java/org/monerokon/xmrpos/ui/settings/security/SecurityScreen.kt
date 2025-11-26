// MoneroPayScreen.kt
package org.monerokon.xmrpos.ui.settings.moneropay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.common.composables.CustomOutlinedTextField
import org.monerokon.xmrpos.ui.common.composables.CustomSwitch
import org.monerokon.xmrpos.ui.common.composables.StyledTopAppBar

@Composable
fun SecurityScreenRoot(viewModel: SecurityViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    SecurityScreen(
        onBackClick = viewModel::navigateToMainSettings,
        requirePinCodeOnAppStart = viewModel.requirePinCodeOnAppStart,
        requirePinCodeOpenSettings = viewModel.requirePinCodeOpenSettings,
        pinCodeOnAppStart = viewModel.pinCodeOnAppStart,
        pinCodeOpenSettings = viewModel.pinCodeOpenSettings,
        updateRequirePinCodeOnAppStart = viewModel::updateRequirePinCodeOnAppStart,
        updateRequirePinCodeOpenSettings = viewModel::updateRequirePinCodeOpenSettings,
        updatePinCodeOnAppStart = viewModel::updatePinCodeOnAppStart,
        updatePinCodeOpenSettings = viewModel::updatePinCodeOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onBackClick: () -> Unit,
    requirePinCodeOnAppStart: Boolean,
    requirePinCodeOpenSettings: Boolean,
    pinCodeOnAppStart: String,
    pinCodeOpenSettings: String,
    updateRequirePinCodeOnAppStart: (Boolean) -> Unit,
    updateRequirePinCodeOpenSettings: (Boolean) -> Unit,
    updatePinCodeOnAppStart: (String) -> Unit,
    updatePinCodeOpenSettings: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            StyledTopAppBar(text = "Security", onBackClick = onBackClick)
        },
    ) { innerPadding ->
        Column (
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
           Row (
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
           ) {
                Text("Require PIN code on app start", style = MaterialTheme.typography.labelSmall)
               CustomSwitch(
                    checked = requirePinCodeOnAppStart,
                    onCheckedChange = { updateRequirePinCodeOnAppStart(it) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row (
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ){
                Text("Require PIN code to open settings", style = MaterialTheme.typography.labelSmall)
                CustomSwitch(
                    checked = requirePinCodeOpenSettings,
                    onCheckedChange = { updateRequirePinCodeOpenSettings(it) }
                )
           }
            Spacer(modifier = Modifier.height(50.dp))
            CustomOutlinedTextField(
                enabled = requirePinCodeOnAppStart,
                label = "PIN code on app start",
                value = pinCodeOnAppStart,
                onValueChange = { updatePinCodeOnAppStart(it) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = "Enter PIN code"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CustomOutlinedTextField(
                enabled = requirePinCodeOpenSettings,
                label = "PIN code to open settings",
                value = pinCodeOpenSettings,
                onValueChange = { updatePinCodeOpenSettings(it) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = "Enter PIN code"
            )
        }}
}

