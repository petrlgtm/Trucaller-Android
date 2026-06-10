package com.trucaller.backend

import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.OtpSentResponse
import com.trucaller.backend.data.models.TrustResponse
import com.trucaller.backend.routes.SpamSyncEntry
import com.trucaller.backend.routes.SpamSyncPage
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for response-body serialization through Ktor content
 * negotiation. Heterogeneous `Map<String, Any>` payloads (e.g. Boolean + Long
 * values) throw SerializationException at respond() time, which used to turn
 * successful /send-otp, /spam-sync, and error responses into 500s. These tests
 * pin the typed DTOs that replaced them.
 */
class MixedMapSerializationProbeTest {

    private fun ApplicationTestBuilder.configure(block: Route.() -> Unit) {
        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            routing { block() }
        }
    }

    @Test
    fun `OtpSentResponse serializes through content negotiation`() = testApplication {
        configure {
            get("/probe") {
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = OtpSentResponse(otpSent = true, expiresInMinutes = 10L),
                        message = "Verification code sent to your email."
                    )
                )
            }
        }

        val response = client.get("/probe")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue("\"otpSent\": true" in body)
        assertTrue("\"expiresInMinutes\": 10" in body)
    }

    @Test
    fun `SpamSyncPage serializes through content negotiation`() = testApplication {
        configure {
            get("/probe") {
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SpamSyncPage(
                            entries = listOf(
                                SpamSyncEntry(
                                    phoneNumber = "+256700000001",
                                    name = "Known Spammer",
                                    spamScore = 85,
                                    reportCount = 12,
                                    category = "FRAUD",
                                    lastUpdated = "2026-01-01T00:00:00Z"
                                )
                            ),
                            nextCursor = "abc123",
                            hasMore = false
                        )
                    )
                )
            }
        }

        val response = client.get("/probe")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue("\"entries\"" in body)
        assertTrue("\"hasMore\": false" in body)
    }

    @Test
    fun `TrustResponse serializes through content negotiation`() = testApplication {
        configure {
            get("/probe") {
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = TrustResponse(userId = "u1", trustScore = 40, trustLevel = "BASIC")
                    )
                )
            }
        }

        val response = client.get("/probe")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"trustScore\": 40" in response.bodyAsText())
    }

    @Test
    fun `error body ApiResponse-Unit serializes through content negotiation`() = testApplication {
        configure {
            get("/probe") {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiResponse<Unit>(success = false, error = "Rate limit exceeded. Try again later.")
                )
            }
        }

        val response = client.get("/probe")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertTrue("\"error\"" in response.bodyAsText())
    }
}
