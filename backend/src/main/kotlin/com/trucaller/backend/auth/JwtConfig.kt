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

    private const val TOKEN_EXPIRATION_MS = 15L * 60 * 1000          // 15 minutes
    const val REFRESH_TOKEN_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    /**
     * Initialises config values from the Ktor application environment.
     * Must be called before [makeToken] or [configureAuth].
     *
     * Throws [IllegalStateException] if the JWT secret is still the insecure
     * placeholder value and the application is not running in development mode.
     */
    fun init(environment: ApplicationEnvironment) {
        secret = environment.config.property("jwt.secret").getString()
        issuer = environment.config.property("jwt.issuer").getString()
        audience = environment.config.property("jwt.audience").getString()
        realm = environment.config.property("jwt.realm").getString()

        val env = System.getenv("KTOR_ENV") ?: "development"
        check(env == "development" || secret != "change-me-in-production") {
            "JWT_SECRET must be set to a strong random value in production. " +
            "Set the JWT_SECRET environment variable."
        }
    }

    /**
     * Creates a short-lived signed access JWT (15 min) with a unique `jti` claim
     * so individual tokens can be revoked via the jti blocklist.
     */
    fun makeToken(userId: String, role: String = "user"): String {
        return JWT.create()
            .withJWTId(UUID.randomUUID().toString())
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_EXPIRATION_MS))
            .sign(Algorithm.HMAC256(secret))
    }

    /**
     * Creates a long-lived refresh token JWT (30 days). Stored in the
     * `refreshTokens` MongoDB collection; rotated on every use.
     */
    fun makeRefreshToken(userId: String): String {
        return JWT.create()
            .withJWTId(UUID.randomUUID().toString())
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
            .sign(Algorithm.HMAC256(secret))
    }

    /**
     * Returns the access token expiration duration in seconds.
     */
    fun expiresInSeconds(): Long = TOKEN_EXPIRATION_MS / 1000

    /**
     * Verifies a raw access token string and returns the decoded JWT, or null
     * if the token is invalid, expired, or revoked. Used for WebSocket auth
     * where the standard Ktor JWT auth plugin is unavailable.
     */
    suspend fun verifyAccessToken(token: String): com.auth0.jwt.interfaces.DecodedJWT? = try {
        val decoded = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
            .verify(token)
        val jti = decoded.id
        if (jti != null && com.trucaller.backend.service.RedisCache.isRevoked(jti)) null
        else decoded
    } catch (_: Exception) { null }

    /**
     * Decodes a refresh token and returns the userId claim, or null if invalid.
     */
    fun decodeRefreshToken(token: String): String? = try {
        val verifier = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "refresh")
            .build()
        val decoded = verifier.verify(token)
        decoded.getClaim("userId").asString()
    } catch (_: Exception) { null }

    /**
     * Installs the Ktor JWT authentication plugin on the given [application].
     * Tokens whose `jti` appears in the Redis revocation set are rejected.
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
                    val userId = credential.payload.getClaim("userId")?.asString()
                    val jti = credential.payload.id
                    if (userId.isNullOrEmpty()) return@validate null
                    // Reject revoked tokens (jti stored in Redis revocation set)
                    if (jti != null && com.trucaller.backend.service.RedisCache.isRevoked(jti)) {
                        return@validate null
                    }
                    JWTPrincipal(credential.payload)
                }
            }
        }
    }
}
