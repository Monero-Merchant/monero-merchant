// FiatCurrenciesScreen.kt
package org.monerokon.xmrpos.ui.settings.fiatcurrencies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.ui.settings.companyinformation.CompanyInformationViewModel
import kotlin.math.exp
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.common.composables.InputTile
import org.monerokon.xmrpos.ui.common.composables.StyledTopAppBar

@Composable
fun FiatCurrenciesScreenRoot(viewModel: FiatCurrenciesViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    FiatCurrenciesScreen(
        onBackClick = viewModel::navigateToMainSettings,
        fiatOptions = viewModel.fiatOptions,
        primaryFiatCurrency = viewModel.primaryFiatCurrency,
        updatePrimaryFiatCurrency = viewModel::updatePrimaryFiatCurrency,
        referenceFiatCurrencies = viewModel.referenceFiatCurrencies,
        addReferenceFiatCurrency = viewModel::addReferenceFiatCurrency,
        removeReferenceFiatCurrency = viewModel::removeReferenceFiatCurrency,
        moveReferenceFiatCurrencyUp = viewModel::moveReferenceFiatCurrencyUp,
        moveReferenceFiatCurrencyDown = viewModel::moveReferenceFiatCurrencyDown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiatCurrenciesScreen(
    onBackClick: () -> Unit,
    fiatOptions: List<String>,
    primaryFiatCurrency: String,
    updatePrimaryFiatCurrency: (String) -> Unit,
    referenceFiatCurrencies: List<String>,
    addReferenceFiatCurrency: (String) -> Unit,
    removeReferenceFiatCurrency: (Int) -> Unit,
    moveReferenceFiatCurrencyUp: (Int) -> Unit,
    moveReferenceFiatCurrencyDown: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            StyledTopAppBar(
                text = "Fiat currencies",
                onBackClick = onBackClick
            )
        },
    ) { innerPadding ->
        Column (
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Text("Primary fiat currency", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("This is the currency that will be entered to take an order. It will also be displayed on the receipt along with the exchange rate.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(20.dp))
            InputTile.DropdownInput(
                value = primaryFiatCurrency,
                items = fiatOptions,
                onItemSelected = {updatePrimaryFiatCurrency(it)},
                prefix = "Primary fiat currency",
                padding = PaddingValues(16.dp)
            )
            Spacer(modifier = Modifier.height(50.dp))
            Text("Reference fiat currencies", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            ReferenceFiatCurrenciesCard(referenceFiatCurrencies, removeReferenceFiatCurrency, moveReferenceFiatCurrencyUp, moveReferenceFiatCurrencyDown)
            Spacer(modifier = Modifier.height(24.dp))
            ReferenceCurrencySelector(fiatOptions, addReferenceFiatCurrency)
    }}
}


@Composable
fun ReferenceFiatCurrenciesCard(
    referenceFiatCurrencies: List<String>,
    removeReferenceFiatCurrency: (Int) -> Unit,
    moveReferenceFiatCurrencyUp: (Int) -> Unit,
    moveReferenceFiatCurrencyDown: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 300.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(all = 16.dp)
        ) {
            items(referenceFiatCurrencies.size) { index ->
                ReferenceFiatCurrencyRow(
                    fiatCurrency = referenceFiatCurrencies[index],
                    onRemoveClick = { removeReferenceFiatCurrency(index) },
                    onMoveReferenceFiatCurrencyUpClick = { moveReferenceFiatCurrencyUp(index) },
                    onMoveReferenceFiatCurrencyDownClick = { moveReferenceFiatCurrencyDown(index) }
                )

                if (index < referenceFiatCurrencies.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ReferenceFiatCurrencyRow(
    fiatCurrency: String,
    onRemoveClick: () -> Unit,
    onMoveReferenceFiatCurrencyUpClick: () -> Unit,
    onMoveReferenceFiatCurrencyDownClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(4.dp)
    ) {
        Text(fiatCurrency, style = MaterialTheme.typography.labelSmall)
        Row (
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { onMoveReferenceFiatCurrencyUpClick() }) {
                Icon(
                    painter = painterResource(id = R.drawable.keyboard_arrow_up_24px),
                    contentDescription = "Remove reference fiat currency",
                )
            }
            IconButton(onClick = { onMoveReferenceFiatCurrencyDownClick() }) {
                Icon(
                    painter = painterResource(id = R.drawable.keyboard_arrow_down_24px),
                    contentDescription = "Remove reference fiat currency",
                )
            }
            Row {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onRemoveClick() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.delete_24px),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        contentDescription = "Remove reference fiat currency",
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceCurrencySelector(fiatOptions: List<String>, addReferenceFiatCurrency: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }


    Column {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {expanded = !expanded}, modifier = modifier) {
            FilledTonalButton(
                colors = ButtonDefaults.filledTonalButtonColors().copy(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth(),
                onClick = {},
            ) {
                Text("Add reference fiat currency", style = MaterialTheme.typography.labelSmall)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {expanded = false},
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                fiatOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.labelSmall) },
                        onClick = {
                            expanded = false
                            addReferenceFiatCurrency(option)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}



