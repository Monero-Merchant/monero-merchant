package com.moneromerchant.pos.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath

internal fun HttpClientConfig<*>.configureHttpLogging(debug: Boolean, log: Logger) {
    if (debug) {
        install(Logging) {
            logger = log
            level = LogLevel.ALL
            sanitizeHeader { it.equals(HttpHeaders.Authorization, ignoreCase = true) }
            filter { request ->
                request.url.encodedPath.split('/').none { it.equals("auth", ignoreCase = true) }
            }
        }
    }
}
