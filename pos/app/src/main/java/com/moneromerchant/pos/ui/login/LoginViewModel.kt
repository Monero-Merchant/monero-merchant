// LoginViewModel.kt
package com.moneromerchant.pos.ui.payment.login

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.moneromerchant.pos.data.repository.AuthRepository
import com.moneromerchant.pos.data.repository.BackendRepository
import com.moneromerchant.pos.data.repository.DataStoreRepository
import com.moneromerchant.pos.data.repository.ExchangeRateRepository
import com.moneromerchant.pos.shared.DataResult
import com.moneromerchant.pos.ui.PaymentCheckout
import com.moneromerchant.pos.ui.Settings
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val logTag = "LoginViewModel"

    private var navController: NavHostController? = null

    fun setNavController(navController: NavHostController) {
        this.navController = navController
    }

    var instanceUrl by mutableStateOf("")

    var vendorID by mutableStateOf("")

    var username by mutableStateOf("")

    var password by mutableStateOf("")

    var errorMessage by mutableStateOf("")

    var inProgress by mutableStateOf(false)

    init {

    }

    fun updateInstanceUrl(instanceUrl: String) {
        this.instanceUrl = instanceUrl
    }

    fun updateVendorID(vendorID: String) {
        this.vendorID = vendorID
    }

    fun updateUsername(username: String) {
        this.username = username
    }

    fun updatePassword(password: String) {
        this.password = password
    }

    fun resetErrorMessage() {
        errorMessage = ""
    }

    fun loginPressed(requestLocalNetworkPermission: (() -> Unit)? = null) {
        if (inProgress) return
        errorMessage = ""
        val trimmedInstanceUrl = instanceUrl.trim()
        if (trimmedInstanceUrl.isEmpty()) {
            errorMessage = "Instance URL is required"
            return
        }
        if (vendorID == "") {
            errorMessage = "Vendor ID is required"
            return
        }
        try {
            val id = vendorID.toInt()
        } catch (
            e: NumberFormatException
        ) {
            errorMessage = "Vendor ID must be a number"
            return
        }
        if (username == "") {
            errorMessage = "Username is required"
            return
        }
        if (password == "") {
            errorMessage = "Password is required"
            return
        }

        val normalizedInstanceUrl = normalizeInstanceUrl(trimmedInstanceUrl).getOrElse { err ->
            errorMessage = err.message ?: "Invalid URL"
            return
        }
        instanceUrl = normalizedInstanceUrl

        viewModelScope.launch {
            inProgress = true
            try {
                val needsPermission = requestLocalNetworkPermission != null && withContext(Dispatchers.IO) {
                    isLocalServer(normalizedInstanceUrl)
                }
                if (needsPermission) {
                    requestLocalNetworkPermission.invoke()
                } else {
                    login(normalizedInstanceUrl)
                }
            } finally {
                inProgress = false
            }
        }
    }

    private suspend fun login(normalizedInstanceUrl: String) {
        val resp = authRepository.login(instanceUrl = normalizedInstanceUrl, vendorID = vendorID.toInt(), username = username, password = password)
        resp.fold(
            onSuccess = {

            },
            onFailure = {
                errorMessage = it.message ?: "Unknown error"
            }
        )
    }

    private fun normalizeInstanceUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            "://".let { trimmed.contains(it) } -> return Result.failure(IllegalArgumentException("Only http and https URLs are supported"))
            else -> "https://$trimmed"
        }

        val parsed = try {
            URI(withScheme)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Instance URL is invalid"))
        }

        val scheme = parsed.scheme?.lowercase()
            ?: return Result.failure(IllegalArgumentException("Instance URL must include http or https"))
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("Only http and https URLs are supported"))
        }

        return Result.success(withScheme)
    }

}
