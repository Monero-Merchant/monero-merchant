package com.moneromerchant.pos.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

internal fun HttpClientConfig<*>.configureHttpLogging(debug: Boolean, log: Logger) {
    if (debug) {
        install(Logging) {
            logger = log
            level = LogLevel.ALL
            sanitizeHeader { it.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }
}
