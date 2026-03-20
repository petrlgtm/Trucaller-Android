package com.trucaller.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.trucaller.backend.auth.JwtConfig
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Unit tests for [JwtConfig] — JWT token generation, claim structure, and expiry.
 */
class JwtConfigTest {

    /**
     * Initialises [JwtConfig] from test application.conf before each test.
     */
    private fun initJwtConfig() {
        testApplication {
            application {
                JwtConfig.init(environment)
            }
        }
    }

    // ── Token generation ────────────────────────────────────────────────────

    @Test
    fun `makeToken produces a non-blank JWT string`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("user-123", "user")
        assertTrue(token.isNotBlank(), "Token must not be blank")
        // JWT compact form has three dot-separated parts
        assertEquals(3, token.split(".").size, "JWT must have header.payload.signature")
    }

    @Test
    fun `makeToken embeds userId claim`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("user-abc-456")
        val decoded = JWT.decode(token)

        assertEquals("user-abc-456", decoded.getClaim("userId").asString())
    }

    @Test
    fun `makeToken embeds role claim defaulting to user`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("u1")
        val decoded = JWT.decode(token)

        assertEquals("user", decoded.getClaim("role").asString())
    }

    @Test
    fun `makeToken embeds custom role claim when specified`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("admin-1", role = "admin")
        val decoded = JWT.decode(token)

        assertEquals("admin", decoded.getClaim("role").asString())
    }

    @Test
    fun `makeToken sets correct issuer and audience`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("u2")
        val decoded = JWT.decode(token)

        assertEquals("trucaller-backend", decoded.issuer)
        assertTrue(decoded.audience.contains("trucaller-users"), "Audience must contain trucaller-users")
    }

    @Test
    fun `makeToken sets expiration in the future`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("u3")
        val decoded = JWT.decode(token)

        assertNotNull(decoded.expiresAt, "Token must have an expiration date")
        assertTrue(
            decoded.expiresAt.time > System.currentTimeMillis(),
            "Token expiration must be in the future"
        )
    }

    @Test
    fun `makeToken expiration is approximately 24 hours from now`() {
        initJwtConfig()

        val beforeMs = System.currentTimeMillis()
        val token = JwtConfig.makeToken("u4")
        val afterMs = System.currentTimeMillis()

        val decoded = JWT.decode(token)
        val expiresMs = decoded.expiresAt.time
        val twentyFourHoursMs = 24L * 60 * 60 * 1000

        // Expiry should be within [before + 24h, after + 24h]
        assertTrue(
            expiresMs >= beforeMs + twentyFourHoursMs - 1000 &&
                expiresMs <= afterMs + twentyFourHoursMs + 1000,
            "Token expiry should be ~24 hours from now"
        )
    }

    // ── Token verification ──────────────────────────────────────────────────

    @Test
    fun `generated token verifies with correct secret`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("u5")
        val verifier = JWT.require(Algorithm.HMAC256("test-secret-key-for-unit-tests"))
            .withIssuer("trucaller-backend")
            .withAudience("trucaller-users")
            .build()

        val verified = verifier.verify(token)
        assertEquals("u5", verified.getClaim("userId").asString())
    }

    @Test
    fun `generated token fails verification with wrong secret`() {
        initJwtConfig()

        val token = JwtConfig.makeToken("u6")
        val wrongVerifier = JWT.require(Algorithm.HMAC256("wrong-secret"))
            .withIssuer("trucaller-backend")
            .withAudience("trucaller-users")
            .build()

        assertFails("Token signed with wrong secret must fail verification") {
            wrongVerifier.verify(token)
        }
    }

    // ── expiresInSeconds ────────────────────────────────────────────────────

    @Test
    fun `expiresInSeconds returns 86400 for 24-hour token`() {
        initJwtConfig()

        assertEquals(86400L, JwtConfig.expiresInSeconds(), "24h = 86400 seconds")
    }

    // ── Different userId values ─────────────────────────────────────────────

    @Test
    fun `different userIds produce different tokens`() {
        initJwtConfig()

        val token1 = JwtConfig.makeToken("alice")
        val token2 = JwtConfig.makeToken("bob")

        assertNotEquals(token1, token2, "Tokens for different users must differ")

        val decoded1 = JWT.decode(token1)
        val decoded2 = JWT.decode(token2)
        assertEquals("alice", decoded1.getClaim("userId").asString())
        assertEquals("bob", decoded2.getClaim("userId").asString())
    }
}
