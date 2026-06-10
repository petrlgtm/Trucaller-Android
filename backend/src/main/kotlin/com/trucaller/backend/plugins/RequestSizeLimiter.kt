package com.trucaller.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Simple request body size limiter based on Content-Length header.
 * Rejects requests that declare a body larger than [maxBytes].
 */
class RequestSizeLimiterConfig {
    var maxBytes: Long = 1_048_576L // 1 MB default
}

val RequestSizeLimiterPlugin = createApplicationPlugin(
    name = "RequestSizeLimiter",
    createConfiguration = ::RequestSizeLimiterConfig
) {
    val maxBytes = pluginConfig.maxBytes

    onCall { call ->
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                com.trucaller.backend.data.models.ApiResponse<Unit>(success = false, error = "Request body too large. Maximum size is ${maxBytes / 1024}KB.")
            )
            return@onCall
        }
    }
}
