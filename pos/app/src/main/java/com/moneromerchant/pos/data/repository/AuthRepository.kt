package com.moneromerchant.pos.data.repository

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO // Or your preferred engine
import com.moneromerchant.pos.data.remote.auth.configureAuthenticationClient
import com.moneromerchant.pos.data.remote.auth.safeLoginResult
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import com.moneromerchant.pos.data.remote.auth.model.AuthLoginRequest
import com.moneromerchant.pos.data.remote.auth.model.AuthTokenResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val jsonSerializer: Json,
    private val dataStoreRepository: DataStoreRepository,
    private val storageRepository: StorageRepository,
) {

    private val TAG = "AuthRepository"

    suspend fun login(instanceUrl: String, vendorID: Int, username: String, password: String): Result<Unit> {
        Log.d(TAG, "Attempting login for user: ${username} at URL: $instanceUrl")

        // Create a temporary Ktor client specifically for this login attempt
        val loginClient = HttpClient(CIO) {
            expectSuccess = true
            configureAuthenticationClient(jsonSerializer)
        }

        return try {
            val result = safeLoginResult {
                val response: AuthTokenResponse = loginClient.post {
                    url {
                        takeFrom(instanceUrl)
                        if (protocol == URLProtocol.HTTP && !instanceUrl.startsWith("http://") && !instanceUrl.startsWith("https://")) {
                            protocol = URLProtocol.HTTP
                        }
                        encodedPath = "/api/auth/login-pos"
                    }
                    contentType(ContentType.Application.Json)
                    setBody(AuthLoginRequest(vendorID, username, password))
                }.body()

                dataStoreRepository.saveBackendInstanceUrl(instanceUrl)
                dataStoreRepository.saveBackendAccessToken(response.access_token)
                dataStoreRepository.saveBackendRefreshToken(response.refresh_token)
            }

            if (result.isSuccess) {
                Log.i(TAG, "Login successful to $instanceUrl. Tokens and URL stored.")
            } else {
                Log.e(TAG, "Login failed.")
            }
            result
        } finally {
            loginClient.close()
        }
    }

    suspend fun logout() {
        Log.d(TAG, "Attempting logout.")

        dataStoreRepository.clearDataStore()
        // The logo is stored as a file
        storageRepository.deleteImage("logo.png")

        Log.i(TAG, "DataStore cleared. Logout complete.")
    }

    // Logout after the backend rejected this refresh token
    suspend fun logoutIfCurrent(refreshToken: String) {
        if (dataStoreRepository.clearDataStoreIfCurrent(refreshToken)) {
            storageRepository.deleteImage("logo.png")
            Log.i(TAG, "Refresh token rejected. Logout complete.")
        }
    }

    fun isLoggedIn(): Flow<Boolean> {
        return dataStoreRepository.getBackendAccessToken().map {it.isNotBlank() }
    }

    fun observeAccessToken(): Flow<String?> = dataStoreRepository.getBackendAccessToken()
    fun observeRefreshToken(): Flow<String?> = dataStoreRepository.getBackendRefreshToken()
    fun observeBackendUrl(): Flow<String?> = dataStoreRepository.getBackendInstanceUrl()
}
