// MainSettingsScreen.kt
package org.monerokon.xmrpos.ui.settings.main

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.common.composables.StyledTopAppBar


@Composable
fun MainSettingsScreenRoot(viewModel: MainSettingsViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    MainSettingsScreen(
        onBackClick = viewModel::navigateToPayment,
        navigateToCompanyInformation = viewModel::navigateToCompanyInformation,
        navigateToFiatCurrencies = viewModel::navigateToFiatCurrencies,
        navigateToSecurity = viewModel::navigateToSecurity,
        navigateToTransactionHistory = viewModel::navigateToTransactionHistory,
        navigateToBackend = viewModel::navigateToBackend,
        navigateToPrinterSettings = viewModel::navigateToPrinterSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    onBackClick: () -> Unit,
    navigateToCompanyInformation: () -> Unit,
    navigateToFiatCurrencies: () -> Unit,
    navigateToSecurity: () -> Unit,
    navigateToTransactionHistory: () -> Unit,
    navigateToBackend: () -> Unit,
    navigateToPrinterSettings: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context.packageName) {
        try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager
                    .getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    ).versionName.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)?.versionName.orEmpty()
            }
        } catch (ignored: PackageManager.NameNotFoundException) {
            ""
        }
    }
    Scaffold(
        topBar = {
            StyledTopAppBar(
                text = "Settings",
                onBackClick = onBackClick
            )
        },
    ) { innerPadding ->
        Column (
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            SettingsCard(text = "Company information", onClick = {navigateToCompanyInformation()})
            Spacer(modifier = Modifier.height(10.dp))
            SettingsCard(text = "Fiat currencies", onClick = {navigateToFiatCurrencies()})
            Spacer(modifier = Modifier.height(10.dp))
            SettingsCard(text = "Security", onClick = {navigateToSecurity()})
            Spacer(modifier = Modifier.height(10.dp))
            SettingsCard(text = "Transaction history", onClick = {navigateToTransactionHistory()})
            Spacer(modifier = Modifier.height(10.dp))
            SettingsCard(text = "Backend", onClick = {navigateToBackend()})
            Spacer(modifier = Modifier.height(10.dp))
            SettingsCard(text = "Printer settings", onClick = {navigateToPrinterSettings()})
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCard(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(57.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(text, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                painter = painterResource(id = R.drawable.arrow_forward_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
