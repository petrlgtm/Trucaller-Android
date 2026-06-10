package com.trucaller.backend.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*

/**
 * Returns the real client IP address.
 *
 * On Render.com (and any reverse-proxy deployment) the actual client IP
 * is forwarded in the X-Forwarded-For header. We take the FIRST entry,
 * which is the original client IP as appended by the outermost proxy.
 * Falls back to X-Real-IP, then the raw socket address.
 */
fun ApplicationCall.clientIp(): String =
    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        ?: request.header("X-Real-IP")?.trim()
        ?: request.local.remoteAddress

/**
 * Extracts the authenticated user's ID from the JWT token.
 * Throws [UnauthorizedException] if the token is missing or invalid.
 */
fun ApplicationCall.userId(): String {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: throw UnauthorizedException("Authentication required. Missing or invalid token.")
}

fun ApplicationCall.userRole(): String {
    return principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString() ?: "user"
}

/**
 * Thrown when a request lacks valid authentication.
 * Route handlers should catch this and return 401.
 */
class UnauthorizedException(message: String) : RuntimeException(message)
