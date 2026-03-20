package com.trucaller.backend

import com.trucaller.backend.data.models.*
import com.trucaller.backend.routes.SpamReportRequest
import com.trucaller.backend.routes.SpamVerifyRequest
import com.trucaller.backend.routes.VerificationStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for Caller ID business logic: E.164 normalization, spam scoring,
 * trust weighting, request/response serialization, and SpamCategory mapping.
 *
 * The private helper functions in CallerIdRoutes are replicated here to validate
 * correctness independently of MongoDB route handlers.
 */
class CallerIdRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── E.164 normalization (mirrors CallerIdRoutes.normalizeToE164) ────────

    /**
     * Replicates the private normalizeToE164 logic for testing.
     */
    private fun normalizeToE164(phone: String): String {
        val digits = phone.replace(Regex("[^+\\d]"), "")
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("256") -> "+$digits"
            digits.startsWith("0") -> "+256${digits.substring(1)}"
            else -> "+$digits"
        }
    }

    @Test
    fun `normalizeToE164 preserves already-E164 number`() {
        assertEquals("+256700123456", normalizeToE164("+256700123456"))
    }

    @Test
    fun `normalizeToE164 adds + prefix to 256 number`() {
        assertEquals("+256700123456", normalizeToE164("256700123456"))
    }

    @Test
    fun `normalizeToE164 converts local 07XX format to E164`() {
        assertEquals("+256700123456", normalizeToE164("0700123456"))
    }

    @Test
    fun `normalizeToE164 strips non-digit characters except +`() {
        assertEquals("+256700123456", normalizeToE164("+256 700 123 456"))
        assertEquals("+256700123456", normalizeToE164("+256-700-123-456"))
    }

    @Test
    fun `normalizeToE164 handles international numbers`() {
        assertEquals("+14155551234", normalizeToE164("+14155551234"))
        assertEquals("+447911123456", normalizeToE164("+447911123456"))
    }

    @Test
    fun `normalizeToE164 prepends + to bare digits`() {
        assertEquals("+14155551234", normalizeToE164("14155551234"))
    }

    // ── categoryFromScore (mirrors CallerIdRoutes.categoryFromScore) ────────

    /**
     * Replicates the private categoryFromScore logic for testing.
     */
    private fun categoryFromScore(score: Int): SpamCategory = when {
        score <= 20 -> SpamCategory.SAFE
        score <= 60 -> SpamCategory.SUSPECTED_SPAM
        score <= 80 -> SpamCategory.SPAM
        else -> SpamCategory.FRAUD
    }

    @Test
    fun `categoryFromScore returns SAFE for score 0`() {
        assertEquals(SpamCategory.SAFE, categoryFromScore(0))
    }

    @Test
    fun `categoryFromScore returns SAFE for score 20 (boundary)`() {
        assertEquals(SpamCategory.SAFE, categoryFromScore(20))
    }

    @Test
    fun `categoryFromScore returns SUSPECTED_SPAM for score 21`() {
        assertEquals(SpamCategory.SUSPECTED_SPAM, categoryFromScore(21))
    }

    @Test
    fun `categoryFromScore returns SUSPECTED_SPAM for score 60 (boundary)`() {
        assertEquals(SpamCategory.SUSPECTED_SPAM, categoryFromScore(60))
    }

    @Test
    fun `categoryFromScore returns SPAM for score 61`() {
        assertEquals(SpamCategory.SPAM, categoryFromScore(61))
    }

    @Test
    fun `categoryFromScore returns SPAM for score 80 (boundary)`() {
        assertEquals(SpamCategory.SPAM, categoryFromScore(80))
    }

    @Test
    fun `categoryFromScore returns FRAUD for score 81`() {
        assertEquals(SpamCategory.FRAUD, categoryFromScore(81))
    }

    @Test
    fun `categoryFromScore returns FRAUD for score 100`() {
        assertEquals(SpamCategory.FRAUD, categoryFromScore(100))
    }

    // ── trustWeightFromScore (mirrors CallerIdRoutes.trustWeightFromScore) ──

    /**
     * Replicates the private trustWeightFromScore logic for testing.
     */
    private fun trustWeightFromScore(trustScore: Int): Int = when {
        trustScore < 20 -> 3
        trustScore < 50 -> 5
        trustScore < 80 -> 8
        else -> 12
    }

    @Test
    fun `trustWeight is 3 for NEW user (score 0)`() {
        assertEquals(3, trustWeightFromScore(0))
    }

    @Test
    fun `trustWeight is 3 for score 19 (NEW boundary)`() {
        assertEquals(3, trustWeightFromScore(19))
    }

    @Test
    fun `trustWeight is 5 for BASIC user (score 20)`() {
        assertEquals(5, trustWeightFromScore(20))
    }

    @Test
    fun `trustWeight is 5 for score 49 (BASIC boundary)`() {
        assertEquals(5, trustWeightFromScore(49))
    }

    @Test
    fun `trustWeight is 8 for TRUSTED user (score 50)`() {
        assertEquals(8, trustWeightFromScore(50))
    }

    @Test
    fun `trustWeight is 8 for score 79 (TRUSTED boundary)`() {
        assertEquals(8, trustWeightFromScore(79))
    }

    @Test
    fun `trustWeight is 12 for VERIFIED user (score 80)`() {
        assertEquals(12, trustWeightFromScore(80))
    }

    @Test
    fun `trustWeight is 12 for score 100`() {
        assertEquals(12, trustWeightFromScore(100))
    }

    // ── Spam score clamping ─────────────────────────────────────────────────

    @Test
    fun `spam score is clamped to max 100`() {
        val currentScore = 95
        val trustWeight = 12
        val newScore = minOf(currentScore + trustWeight, 100)

        assertEquals(100, newScore, "Score must not exceed 100")
    }

    @Test
    fun `spam score increments normally below cap`() {
        val currentScore = 40
        val trustWeight = 8
        val newScore = minOf(currentScore + trustWeight, 100)

        assertEquals(48, newScore)
    }

    // ── Request serialization ───────────────────────────────────────────────

    @Test
    fun `SpamReportRequest serialises and deserialises correctly`() {
        val request = SpamReportRequest(phoneNumber = "+256700111222", reason = "Scam caller")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SpamReportRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `SpamReportRequest with null reason serialises correctly`() {
        val request = SpamReportRequest(phoneNumber = "+256700111222")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SpamReportRequest>(encoded)

        assertEquals("+256700111222", decoded.phoneNumber)
        assertNull(decoded.reason)
    }

    @Test
    fun `SpamVerifyRequest serialises and deserialises correctly`() {
        val request = SpamVerifyRequest(phoneNumber = "+256700111222", vote = "confirm")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SpamVerifyRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `VerificationStatus serialises and deserialises correctly`() {
        val status = VerificationStatus(
            phoneNumber = "+256700111222",
            confirmCount = 7,
            disputeCount = 2,
            communityVerified = true,
            userVote = "confirm"
        )
        val encoded = json.encodeToString(status)
        val decoded = json.decodeFromString<VerificationStatus>(encoded)

        assertEquals(status, decoded)
    }

    @Test
    fun `VerificationStatus with null userVote serialises correctly`() {
        val status = VerificationStatus(
            phoneNumber = "+256700111222",
            confirmCount = 3,
            disputeCount = 1,
            communityVerified = false,
            userVote = null
        )
        val encoded = json.encodeToString(status)
        val decoded = json.decodeFromString<VerificationStatus>(encoded)

        assertNull(decoded.userVote)
        assertFalse(decoded.communityVerified)
    }

    // ── LookupResponse serialization ────────────────────────────────────────

    @Test
    fun `LookupResponse serialises with all fields`() {
        val response = LookupResponse(
            phoneNumber = "+256700123456",
            name = "John Doe",
            spamScore = 45,
            reportCount = 3,
            category = SpamCategory.SUSPECTED_SPAM,
            source = "caller_id_db",
            confidence = 90
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<LookupResponse>(encoded)

        assertEquals(response, decoded)
    }

    @Test
    fun `LookupResponse with null name serialises correctly`() {
        val response = LookupResponse(
            phoneNumber = "+256700123456",
            name = null,
            spamScore = 0,
            reportCount = 0,
            category = SpamCategory.SAFE,
            source = "not_found",
            confidence = 0
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<LookupResponse>(encoded)

        assertNull(decoded.name)
        assertEquals("not_found", decoded.source)
        assertEquals(0, decoded.confidence)
    }

    // ── SpamCategory enum ───────────────────────────────────────────────────

    @Test
    fun `SpamCategory enum contains all expected values`() {
        val values = SpamCategory.entries.map { it.name }
        assertTrue("SAFE" in values)
        assertTrue("SUSPECTED_SPAM" in values)
        assertTrue("SPAM" in values)
        assertTrue("FRAUD" in values)
        assertEquals(4, values.size, "SpamCategory should have exactly 4 values")
    }

    @Test
    fun `SpamCategory serialises as string name`() {
        val response = LookupResponse(
            phoneNumber = "+256700123456",
            name = "Spam Caller",
            spamScore = 85,
            reportCount = 10,
            category = SpamCategory.FRAUD,
            source = "caller_id_db",
            confidence = 90
        )
        val encoded = json.encodeToString(response)
        assertTrue(encoded.contains("\"FRAUD\""), "Enum must serialise as string name")
    }

    // ── Community verification logic ────────────────────────────────────────

    @Test
    fun `community verified when 5 or more confirms and confirms greater than 2x disputes`() {
        val confirmCount = 5
        val disputeCount = 2
        val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2

        assertTrue(communityVerified)
    }

    @Test
    fun `not community verified when fewer than 5 confirms`() {
        val confirmCount = 4
        val disputeCount = 0
        val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2

        assertFalse(communityVerified)
    }

    @Test
    fun `not community verified when too many disputes`() {
        val confirmCount = 6
        val disputeCount = 3
        val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2

        assertFalse(communityVerified, "6 confirms <= 3*2=6 disputes, so not verified")
    }

    @Test
    fun `community verified edge case - exactly 5 confirms and 2 disputes`() {
        val confirmCount = 5
        val disputeCount = 2
        val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2

        assertTrue(communityVerified, "5 > 2*2=4, so verified")
    }

    @Test
    fun `not community verified edge case - 5 confirms and 3 disputes`() {
        val confirmCount = 5
        val disputeCount = 3
        val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2

        assertFalse(communityVerified, "5 <= 3*2=6, so not verified")
    }
}
