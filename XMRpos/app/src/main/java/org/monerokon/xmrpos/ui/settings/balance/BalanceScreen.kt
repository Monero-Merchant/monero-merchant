package org.monerokon.xmrpos.ui.settings.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.data.remote.backend.model.BackendBalancePosResponse
import org.monerokon.xmrpos.shared.DataResult
import org.monerokon.xmrpos.ui.common.composables.FiatCard
import java.math.BigDecimal
import java.math.RoundingMode


@Composable
fun BalanceScreenRoot(viewModel: BalanceViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    BalanceScreen(
        onBackClick = viewModel::navigateToMainSettings,
        posBalance = viewModel.posBalance,
        primaryFiatCurrency = viewModel.primaryFiatCurrency,
        exchangeRate = viewModel.exchangeRate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(
    onBackClick: () -> Unit,
    posBalance: DataResult<BackendBalancePosResponse>?,
    primaryFiatCurrency: String,
    exchangeRate: Double?,
) {
    Scaffold { innerPadding ->
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Balance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            when (posBalance) {
                is DataResult.Success -> {
                    val xmrBalance = BigDecimal.valueOf(posBalance.data.balance)
                        .divide(BigDecimal.valueOf(1000000000000), 14, RoundingMode.HALF_UP)
                    FiatCard(
                        label = "Balance Amount",
                        currency = primaryFiatCurrency,
                        exchangeRate = exchangeRate,
                        fiatValue = BigDecimal.valueOf(exchangeRate ?: 0.0).multiply(xmrBalance).setScale(3,
                            RoundingMode.HALF_UP).toPlainString(),
                        xmrValue = xmrBalance,
                    )
                }

                is DataResult.Failure -> {
                    Text("Error: ${posBalance.message}", style = MaterialTheme.typography.bodyMedium)
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onBackClick,
            ) {
                Text("Go Back")
            }
        }
    }
}
