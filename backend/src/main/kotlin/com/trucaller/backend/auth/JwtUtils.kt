package com.trucaller.backend.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.userId(): String {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: throw IllegalStateException("Missing userId in JWT")
}

fun ApplicationCall.userRole(): String {
    return principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString() ?: "user"
}
