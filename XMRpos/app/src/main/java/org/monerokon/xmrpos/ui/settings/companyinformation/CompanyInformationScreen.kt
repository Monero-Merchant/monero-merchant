// CompanyInformationScreen.kt
package org.monerokon.xmrpos.ui.settings.companyinformation

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.monerokon.xmrpos.R
import org.monerokon.xmrpos.ui.common.composables.CustomOutlinedTextField
import org.monerokon.xmrpos.ui.common.composables.DisplayImageFromFile
import org.monerokon.xmrpos.ui.common.composables.StyledTopAppBar
import java.io.File

@Composable
fun CompanyInformationScreenRoot(viewModel: CompanyInformationViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    CompanyInformationScreen(
        onBackClick = viewModel::navigateToMainSettings,
        companyLogo = viewModel.companyLogo,
        companyName = viewModel.companyName,
        updateCompanyName = viewModel::updateCompanyName,
        contactInformation = viewModel.contactInformation,
        updateContactInformation = viewModel::updateContactInformation,
        receiptFooter = viewModel.receiptFooter,
        updateReceiptFooter = viewModel::updateReceiptFooter,
        saveLogo = viewModel::saveLogo,
        deleteLogo = viewModel::deleteLogo
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyInformationScreen(
    onBackClick: () -> Unit,
    companyLogo: File?,
    companyName: String,
    updateCompanyName: (String) -> Unit,
    contactInformation: String,
    updateContactInformation: (String) -> Unit,
    receiptFooter: String,
    updateReceiptFooter: (String) -> Unit,
    saveLogo: (Uri) -> Unit,
    deleteLogo: () -> Unit
) {

    // Registers a photo picker activity launcher in single-select mode.
    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            Log.d("PhotoPicker", "Selected URI: $uri")
            saveLogo(uri)
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    Scaffold(
        topBar = {
            StyledTopAppBar(
                text = "Company information",
                onBackClick = onBackClick
            )
        },
    ) { innerPadding ->
        Column (
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.padding(innerPadding).padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalIconButton (
                    onClick = { if (companyLogo == null) {pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))} else {deleteLogo()} },
                    modifier = Modifier.size(98.dp)
                ) {
                    if (companyLogo != null) {
                        DisplayImageFromFile(companyLogo)
                        Icon(
                            painter = painterResource(id = R.drawable.delete_24px),
                            contentDescription = "Delete company logo",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.add_2_24px),
                            contentDescription = "Upload company logo",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text("Upload logo", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This logo will be shown on the printed receipts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            CustomOutlinedTextField(
                value = companyName,
                onValueChange = {updateCompanyName(it)},
                label = "Company name",
                supportingText = "Shown on the receipts",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(40.dp))
            CustomOutlinedTextField(
                value = contactInformation,
                onValueChange = {updateContactInformation(it)},
                label = "Contact information",
                supportingText = "Shown on the receipts",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(40.dp))
            CustomOutlinedTextField(
                value = receiptFooter,
                onValueChange = {updateReceiptFooter(it)},
                label = "Receipt footer",
                supportingText = "Shown at the end of the receipt",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}