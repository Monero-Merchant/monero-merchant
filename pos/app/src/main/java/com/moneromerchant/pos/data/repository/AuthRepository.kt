package com.moneromerchant.pos.data.repository

import android.util.Log
import com.moneromerchant.pos.BuildConfig
import com.moneromerchant.pos.data.remote.auth.model.AuthLoginRequest
import com.moneromerchant.pos.data.remote.auth.model.AuthTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class AuthRepository @Inject constructor(
    private val jsonSerializer: Json,
    private val dataStoreRepository: DataStoreRepository,
    private val storageRepository: StorageRepository,
) {

    private val TAG = "AuthRepository"

    suspend fun login(instanceUrl: String, vendorID: Int, username: String, password: String): Result<Unit> {
        Log.d(TAG, "Attempting login at URL: $instanceUrl")

        // Create a temporary Ktor client specifically for this login attempt
        val loginClient = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(jsonSerializer)
            }
        }

        return try {
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

            Log.i(TAG, "Login successful to $instanceUrl. Tokens and URL stored.")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Decode errors can include the response JSON
            if (BuildConfig.DEBUG) Log.e(TAG, "Login failed", e) else Log.e(TAG, "Login failed")
            Result.failure(IllegalStateException(loginErrorMessage(e), e))
        } finally {
            loginClient.close()
        }
    }

    // Shown to the user, so not e.message
    private suspend fun loginErrorMessage(e: Exception): String = when (e) {
        is ResponseException -> {
            // The backend sends the reason as plain text
            val reason = if (
                e.response.status == HttpStatusCode.Unauthorized &&
                e.response.contentType()?.match(ContentType.Text.Plain) == true
            ) readReason(e) else ""
            if (reason.isNotEmpty()) "Login failed: $reason" else "Login failed (HTTP ${e.response.status.value})"
        }
        is UnresolvedAddressException -> "Server not found. Check the instance URL."
        // Must come before ConnectException, which it extends
        is ConnectTimeoutException, is SocketTimeoutException, is HttpRequestTimeoutException ->
            "The server took too long to respond."
        is ConnectException -> "Could not connect to the server."
        is CertificateException, is SSLException -> "Secure connection failed. Check the server's certificate."
        is ContentConvertException, is NoTransformationFoundException ->
            "Unexpected response from the server. Check the instance URL."
        else -> "Login failed (${e.javaClass.simpleName})"
    }

    private suspend fun readReason(e: ResponseException): String = try {
        e.response.bodyAsText().trim()
    } catch (ce: CancellationException) {
        throw ce
    } catch (ex: Exception) {
        ""
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
