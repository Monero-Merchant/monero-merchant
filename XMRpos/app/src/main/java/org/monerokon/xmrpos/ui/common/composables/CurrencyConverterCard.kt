package org.monerokon.xmrpos.ui.common.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation. layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose. material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx. compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CurrencyConverterCard(
    currency: String,
    exchangeRate: Double?,
    paymentValue: String,
    targetXMRvalue:  BigDecimal?  = null,
    emphasize: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement. SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = currency, style = MaterialTheme.typography.labelSmall)
                if (emphasize) {
                    Text(
                        text = "$paymentValue $currency",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "$paymentValue $currency",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (exchangeRate != null) {
                    Text(
                        text = "1 XMR = $exchangeRate $currency",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.width(48.dp).padding(vertical = 7.5.dp)
                    )
                }
                if (exchangeRate != null && exchangeRate != 0.0) {
                    val rate = BigDecimal.valueOf(exchangeRate)
                    val xmrAmount = targetXMRvalue ?: paymentValue.toDoubleOrNull()?.let {
                        BigDecimal.valueOf(it).divide(rate, 12, RoundingMode.HALF_UP)
                    }

                    if (xmrAmount != null) {
                        Text(
                            text = "${xmrAmount.setScale(5, RoundingMode.HALF_UP).toPlainString()} XMR",
                            style = MaterialTheme.typography. labelSmall
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.width(48.dp)
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}