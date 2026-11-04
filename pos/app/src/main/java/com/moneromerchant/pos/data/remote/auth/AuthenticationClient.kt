package com.moneromerchant.pos.data.remote.auth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal fun HttpClientConfig<*>.configureAuthenticationClient(serializer: Json) {
    install(ContentNegotiation) { json(serializer) }
}

internal suspend fun <T> safeLoginResult(operation: suspend () -> T): Result<T> = try {
    Result.success(operation())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    val message = if (e is ResponseException) {
        "Login failed (HTTP ${e.response.status.value})."
    } else {
        "Login failed. Please try again."
    }
    Result.failure(IllegalStateException(message))
}
