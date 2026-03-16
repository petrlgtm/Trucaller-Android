package com.trucaller.backend.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

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
