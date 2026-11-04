package com.moneromerchant.pos.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import com.moneromerchant.pos.BuildConfig
import com.moneromerchant.pos.data.remote.configureHttpLogging
import com.moneromerchant.pos.data.remote.auth.configureAuthenticationClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking 
import kotlinx.serialization.json.Json
import com.moneromerchant.pos.data.remote.auth.model.AuthTokenResponse
import com.moneromerchant.pos.data.remote.backend.BackendRemoteDataSource
import com.moneromerchant.pos.data.repository.AuthRepository
import com.moneromerchant.pos.data.repository.BackendRepository
import com.moneromerchant.pos.data.repository.DataStoreRepository
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.text.isNotBlank

// Custom Qualifier for the main Ktor client used by BackendRepository
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainKtorClient

// Custom Qualifier for the Ktor client used ONLY for token refresh
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshKtorClient

@Module
@InstallIn(SingletonComponent::class)
object BackendModule {

    @Provides
    @Singleton
    fun provideBackendRepository(
        backendRemoteDataSource: BackendRemoteDataSource,
        @ApplicationScope applicationScope: CoroutineScope,
        dataStoreRepository: DataStoreRepository,
    ): BackendRepository {
        return BackendRepository(backendRemoteDataSource, applicationScope,dataStoreRepository)
    }


    @Provides
    @Singleton
    fun provideJsonSerializer(): Json {
        return Json {
            prettyPrint = true // Good for debugging, consider false for release
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Provides
    @RefreshKtorClient // Client specifically for token refresh; no Auth plugin, no dynamic base URL from DefaultRequest
    @Singleton
    fun provideRefreshKtorClient(
        json: Json
    ): HttpClient {
        return HttpClient(CIO) {
            expectSuccess = false // Handle success/failure manually in refreshTokens block
            configureAuthenticationClient(json)
            // NO DefaultRequest with base URL here. The full URL will be set in the refreshTokens call.
        }
    }

    @Provides
    @MainKtorClient // Main client for general API calls
    @Singleton
    fun provideMainKtorClient(
        json: Json,
        dataStoreRepository: DataStoreRepository,
        authRepository: AuthRepository,
        @RefreshKtorClient refreshClient: HttpClient // Inject the refresh client
    ): HttpClient {
        return HttpClient(CIO) {
            expectSuccess = true

            install(DefaultRequest) {
                contentType(ContentType.Application.Json)

                val currentBackendUrl = runBlocking {
                    dataStoreRepository.getBackendInstanceUrl().firstOrNull()
                }

                if (currentBackendUrl != null && currentBackendUrl.isNotBlank()) {
                    // Trailing slash so relative paths like "pos/balance" resolve under /api/
                    url.takeFrom(currentBackendUrl.trimEnd('/') + "/api/")
                } else {
                    android.util.Log.w("MainKtorClient", "Backend URL not set in DefaultRequest!")
                }
            }

            install(ContentNegotiation) {
                json(json)
            }

            configureHttpLogging(BuildConfig.DEBUG, object : Logger {
                override fun log(message: String) {
                    android.util.Log.d("MainKtorClient", message)
                }
            })

            install(WebSockets)

            install(Auth) {
                bearer {
                    // Always read tokens from DataStore so a new login is used right away
                    cacheTokens = false

                    loadTokens {
                        // Load tokens from DataStore
                        val accessToken = dataStoreRepository.getBackendAccessToken().firstOrNull()
                        val refreshToken = dataStoreRepository.getBackendRefreshToken().firstOrNull()
                        if (accessToken != null && refreshToken != null) {
                            BearerTokens(accessToken, refreshToken)
                        } else {
                            null
                        }
                    }

                    refreshTokens {
                        // This block is executed when a 401 is received
                        android.util.Log.d("MainKtorClient", "Attempting to refresh tokens...")
                        val currentRefreshToken = oldTokens?.refreshToken ?: run {
                            android.util.Log.w("MainKtorClient", "No old refresh token found for refresh.")
                            return@refreshTokens null
                        }

                        // IMPORTANT: The refresh call itself needs the base URL
                        val backendUrlForRefresh = runBlocking { dataStoreRepository.getBackendInstanceUrl().firstOrNull() }
                        if (backendUrlForRefresh.isNullOrBlank()) {
                            android.util.Log.e("MainKtorClientAuth", "Cannot refresh token: Backend URL is not set.")
                            return@refreshTokens null
                        }

                        val authTokenResponse: AuthTokenResponse = try {
                            val response = refreshClient.post {
                                url {
                                    takeFrom(backendUrlForRefresh)
                                    appendPathSegments("api", "auth", "refresh")
                                }
                                header(HttpHeaders.ContentType, ContentType.Application.Json)
                                setBody(mapOf("refresh_token" to currentRefreshToken))
                            }
                            if (response.status == HttpStatusCode.Unauthorized) {
                                // Refresh token rejected, log out
                                authRepository.logoutIfCurrent(currentRefreshToken)
                                return@refreshTokens null
                            }
                            if (!response.status.isSuccess()) {
                                android.util.Log.e("MainKtorClientAuth", "Token refresh failed with status: ${response.status}")
                                return@refreshTokens null
                            }
                            response.body()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Stay logged in on network errors
                            android.util.Log.e("MainKtorClientAuth", "Token refresh failed.")
                            return@refreshTokens null
                        }

                        val replaced = dataStoreRepository.replaceBackendTokensIfCurrent(
                            currentRefreshToken,
                            authTokenResponse.access_token,
                            authTokenResponse.refresh_token,
                        )
                        if (replaced) BearerTokens(authTokenResponse.access_token, authTokenResponse.refresh_token) else null
                    }
                }
            }
        }
    }
}

