package com.trucaller.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

/**
 * Encapsulates JWT configuration read from `application.conf` and provides
 * helper functions for token generation and Ktor auth-plugin installation.
 */
object JwtConfig {

    private lateinit var secret: String
    private lateinit var issuer: String
    private lateinit var audience: String
    private lateinit var realm: String

    private const val TOKEN_EXPIRATION_MS = 24L * 60 * 60 * 1000 // 24 hours

    /**
     * Initialises config values from the Ktor application environment.
     * Must be called before [makeToken] or [configureAuth].
     */
    fun init(environment: ApplicationEnvironment) {
        secret = environment.config.property("jwt.secret").getString()
        issuer = environment.config.property("jwt.issuer").getString()
        audience = environment.config.property("jwt.audience").getString()
        realm = environment.config.property("jwt.realm").getString()
    }

    /**
     * Creates a signed JWT containing the given [userId] and [role] claims.
     *
     * @return The compact-serialised JWT string.
     */
    fun makeToken(userId: String, role: String = "user"): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_EXPIRATION_MS))
            .sign(Algorithm.HMAC256(secret))
    }

    /**
     * Returns the token expiration duration in seconds (for inclusion in API responses).
     */
    fun expiresInSeconds(): Long = TOKEN_EXPIRATION_MS / 1000

    /**
     * Installs the Ktor JWT authentication plugin on the given [application].
     */
    fun configureAuth(application: Application) {
        application.install(Authentication) {
            jwt("auth-jwt") {
                this.realm = JwtConfig.realm
                verifier(
                    JWT.require(Algorithm.HMAC256(secret))
                        .withIssuer(issuer)
                        .withAudience(audience)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString().isNullOrEmpty()) {
                        null
                    } else {
                        JWTPrincipal(credential.payload)
                    }
                }
            }
        }
    }
}
