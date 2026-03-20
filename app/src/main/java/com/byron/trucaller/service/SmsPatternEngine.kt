package com.byron.trucaller.service

import com.byron.trucaller.data.model.SmsCategory

/**
 * A compiled regex pattern used for SMS classification.
 *
 * @param regex Pre-compiled regex (case-insensitive by default).
 * @param category The SMS category this pattern signals.
 * @param weight How strongly this pattern contributes (higher = more significant).
 * @param description Human-readable explanation of what the pattern matches.
 */
data class SmsPattern(
    val regex: Regex,
    val category: SmsCategory,
    val weight: Int,
    val description: String
)

/**
 * Result of the pattern-engine classification.
 *
 * @param category The determined category.
 * @param confidence 0.0–1.0; higher means more patterns matched with stronger weight.
 * @param matchedPatterns Descriptions of every pattern that fired.
 * @param spamScore Numeric spam score (0-100) for UI display and sorting.
 */
data class SmsClassification(
    val category: SmsCategory,
    val confidence: Float,
    val matchedPatterns: List<String>,
    val spamScore: Int
)

/**
 * Regex-based SMS classification engine.
 *
 * Evaluates the sender address and message body against a curated set of
 * compiled regex patterns covering SPAM, PROMOTIONAL, and TRANSACTIONAL
 * categories. Each matching pattern contributes a weighted score; the
 * category with the highest aggregate score wins.
 *
 * Usage:
 * ```
 * val result = SmsPatternEngine.classify("MTN", "Your OTP is 482910")
 * // result.category == SmsCategory.TRANSACTIONAL
 * ```
 */
object SmsPatternEngine {

    // ── Pattern registry ─────────────────────────────────────────────

    private val patterns: List<SmsPattern> = buildList {

        // ── SPAM patterns ────────────────────────────────────────────

        // Prize / lottery scams
        add(SmsPattern(
            Regex("(?:you(?:'ve|\\s+have)\\s+won|congratulations\\s+you|selected\\s+as\\s+(?:a\\s+)?winner)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 5, "Prize/lottery scam language"
        ))
        add(SmsPattern(
            Regex("\\b(?:lottery|jackpot|claim\\s+your\\s+prize|sweepstakes)\\b", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 5, "Lottery/sweepstakes terminology"
        ))

        // Advance-fee / Nigerian scams
        add(SmsPattern(
            Regex("\\b(?:beneficiary|next\\s+of\\s+kin|inheritance|million\\s+(?:dollars|usd)|diplomat)\\b", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 5, "Advance-fee fraud indicators"
        ))

        // Phishing / urgency
        add(SmsPattern(
            Regex("(?:act\\s+now|click\\s+here\\s+immediately|verify\\s+immediately|urgent\\s+action\\s+required|confirm\\s+your\\s+identity)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 4, "Phishing urgency language"
        ))
        add(SmsPattern(
            Regex("(?:your\\s+account\\s+(?:will\\s+be|has\\s+been)\\s+(?:suspended|blocked|deactivated|locked))", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 4, "Account suspension phishing"
        ))

        // Money scams
        add(SmsPattern(
            Regex("(?:free\\s+money|earn\\s+money\\s+fast|make\\s+money\\s+online|double\\s+your\\s+money|investment\\s+opportunity)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 4, "Money scam language"
        ))

        // Uganda mobile-money scams
        add(SmsPattern(
            Regex("(?:wrong\\s+transfer|mistakenly\\s+sent|reverse\\s+the\\s+money|mobile\\s+money\\s+pin|confirm\\s+withdrawal)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 5, "Mobile money reversal scam (Uganda)"
        ))
        add(SmsPattern(
            Regex("(?:send\\s+airtime\\s+to|send\\s+to\\s+this\\s+number\\s+to\\s+receive)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 4, "Airtime solicitation scam"
        ))

        // Uganda telecom scam patterns
        add(SmsPattern(
            Regex("(?:your\\s+number\\s+has\\s+been\\s+selected|you\\s+have\\s+been\\s+selected\\s+to\\s+receive|dial\\s+to\\s+claim)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 5, "Fake selection/winning scam"
        ))
        add(SmsPattern(
            Regex("(?:claim\\s+(?:your\\s+)?free\\s+(?:airtime|data)|win\\s+airtime|free\\s+(?:mbs?|data)\\b)", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 4, "Free airtime/data scam"
        ))

        // Suspicious shortened URLs
        add(SmsPattern(
            Regex("https?://(?:bit\\.ly|t\\.co|tinyurl\\.com|is\\.gd|goo\\.gl|ow\\.ly|buff\\.ly)/\\S+", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 3, "Shortened URL (potential phishing)"
        ))
        // Generic suspicious short-domain URLs (e.g. http://ab.cd/xyz)
        add(SmsPattern(
            Regex("https?://[a-z]{2,5}\\.[a-z]{2}/[a-z0-9]", RegexOption.IGNORE_CASE),
            SmsCategory.SPAM, 2, "Suspicious short-domain URL"
        ))

        // Excessive punctuation / caps
        add(SmsPattern(
            Regex("[!]{3,}|[?]{3,}"),
            SmsCategory.SPAM, 2, "Excessive exclamation/question marks"
        ))

        // ── PROMOTIONAL patterns ─────────────────────────────────────

        // Offers & deals
        add(SmsPattern(
            Regex("\\b\\d{1,3}\\s?%\\s?off\\b", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 3, "Percentage discount"
        ))
        add(SmsPattern(
            Regex("(?:flash\\s+sale|limited\\s+(?:time|offer)|exclusive\\s+offer|special\\s+offer|mega\\s+deal|best\\s+deal)", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 3, "Sales/deal terminology"
        ))
        add(SmsPattern(
            Regex("\\b(?:promo\\s*code|coupon|voucher|cashback|cash\\s+back|redeem|reward\\s+points|loyalty)\\b", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 3, "Promotion/reward terminology"
        ))

        // Marketing opt-out
        add(SmsPattern(
            Regex("(?:unsubscribe|opt\\s+out|reply\\s+stop|text\\s+stop|sms\\s+stop)", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 4, "Marketing opt-out language"
        ))

        // Call-to-action
        add(SmsPattern(
            Regex("(?:shop\\s+now|buy\\s+now|order\\s+now|download\\s+now|get\\s+yours|don't\\s+miss|hurry)", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 2, "Marketing call-to-action"
        ))

        // Uganda telco promotions
        add(SmsPattern(
            Regex("(?:data\\s+bundle|bonus\\s+data|free\\s+airtime|voice\\s+bundle|daily\\s+bundle|weekly\\s+bundle|unlimited\\s+calls)", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 3, "Telecom bundle promotion (Uganda)"
        ))
        add(SmsPattern(
            Regex("(?:mtn\\s+pulse|airtel\\s+(?:xtra|smartcash)|africell\\s+(?:big|mega))", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 2, "Uganda telco product promo"
        ))
        add(SmsPattern(
            Regex("(?:dial\\s+\\*\\d{2,4}#|activate\\s+now)", RegexOption.IGNORE_CASE),
            SmsCategory.PROMOTIONAL, 2, "Dial/activate USSD promo"
        ))

        // ── TRANSACTIONAL patterns ───────────────────────────────────

        // OTP / authentication
        add(SmsPattern(
            Regex("(?:(?:your\\s+)?(?:otp|verification\\s+code|security\\s+code|login\\s+code|one[- ]time\\s+(?:password|pin)|2fa\\s+code)\\s*(?:is)?\\s*:?\\s*\\d{4,8})", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 5, "OTP/verification code with digits"
        ))
        add(SmsPattern(
            Regex("\\b\\d{4,8}\\b.*(?:code|otp|pin|verify|confirm)|(?:code|otp|pin|verify|confirm).*\\b\\d{4,8}\\b", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 4, "Numeric code near verification keyword"
        ))

        // Financial transactions
        add(SmsPattern(
            Regex("(?:(?:payment|transaction)\\s+(?:of|received|confirmed|successful)|(?:credited|debited)\\s+(?:to|from)|account\\s+balance)", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 4, "Financial transaction confirmation"
        ))
        add(SmsPattern(
            Regex("(?:receipt\\s+(?:no|number|#)|invoice\\s+(?:no|number|#)|statement\\s+for)", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 3, "Receipt/invoice reference"
        ))

        // Uganda mobile money transaction patterns
        add(SmsPattern(
            Regex("(?:you\\s+have\\s+received|sent\\s+to\\s+\\S+|new\\s+balance|transaction\\s+id|txn\\s*(?:id|ref))", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 4, "Mobile money transaction (Uganda)"
        ))
        add(SmsPattern(
            Regex("(?:m[- ]?pesa|mtn\\s*mo(?:bile)?\\s*mo(?:ney)?|airtel\\s+money)", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 3, "Uganda mobile money provider"
        ))
        // UGX currency amounts (e.g., "UGX 50,000" or "UGX50000" or "UGX 1,234,567")
        add(SmsPattern(
            Regex("ugx\\s?[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 3, "Uganda shilling amount"
        ))
        // KES / TZS / RWF amounts (East Africa)
        add(SmsPattern(
            Regex("(?:kes|tzs|rwf)\\s?[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 2, "East African currency amount"
        ))

        // Delivery / orders
        add(SmsPattern(
            Regex("(?:(?:your\\s+)?(?:order|package|parcel|shipment)\\s+(?:has\\s+been\\s+)?(?:delivered|shipped|dispatched|confirmed)|out\\s+for\\s+delivery|tracking\\s+(?:number|id|#))", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 4, "Delivery/shipping notification"
        ))
        add(SmsPattern(
            Regex("(?:booking\\s+confirmed|reservation\\s+confirmed|appointment\\s+(?:on|at|scheduled))", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 3, "Booking/appointment confirmation"
        ))

        // Security alerts
        add(SmsPattern(
            Regex("(?:password\\s+(?:changed|reset|updated)|login\\s+from\\s+(?:new|a)\\s+device|new\\s+device\\s+login|unusual\\s+(?:activity|login))", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 4, "Account security alert"
        ))

        // Uganda banks
        add(SmsPattern(
            Regex("(?:stanbic|centenary|dfcu|absa|postbank|bank\\s+of\\s+africa|equity\\s+bank|housing\\s+finance|standard\\s+chartered|orient\\s+bank)\\b", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 2, "Uganda bank name"
        ))

        // Uganda utilities & government
        add(SmsPattern(
            Regex("(?:nwsc|umeme|kcca|\\bura\\b|yaka|national\\s+water|tax\\s+payment|payment\\s+registration\\s+number|\\bprn\\b)", RegexOption.IGNORE_CASE),
            SmsCategory.TRANSACTIONAL, 3, "Uganda utility/government reference"
        ))
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Classify an SMS message by evaluating [sender] and [body] against all
     * registered regex patterns.
     *
     * Returns `null` when no pattern fires (confidence would be zero), so the
     * caller can fall back to a keyword-based scorer.
     */
    fun classify(sender: String, body: String): SmsClassification? {
        val combinedText = "$sender $body"

        // Accumulate weighted scores per category
        val categoryScores = mutableMapOf(
            SmsCategory.SPAM to 0,
            SmsCategory.PROMOTIONAL to 0,
            SmsCategory.TRANSACTIONAL to 0
        )
        val matchedDescriptions = mutableListOf<String>()
        var totalPossibleWeight = 0

        for (pattern in patterns) {
            totalPossibleWeight += pattern.weight
            if (pattern.regex.containsMatchIn(combinedText)) {
                categoryScores[pattern.category] =
                    (categoryScores[pattern.category] ?: 0) + pattern.weight
                matchedDescriptions.add(pattern.description)
            }
        }

        // No patterns matched — signal caller to use fallback
        if (matchedDescriptions.isEmpty()) return null

        val spamScore = categoryScores[SmsCategory.SPAM] ?: 0
        val promoScore = categoryScores[SmsCategory.PROMOTIONAL] ?: 0
        val transactionalScore = categoryScores[SmsCategory.TRANSACTIONAL] ?: 0
        val maxScore = maxOf(spamScore, promoScore, transactionalScore)

        // Determine winning category (SPAM wins ties as the most dangerous)
        val category = when {
            spamScore == maxScore && spamScore > 0 -> SmsCategory.SPAM
            promoScore == maxScore && promoScore > 0 -> SmsCategory.PROMOTIONAL
            transactionalScore == maxScore && transactionalScore > 0 -> SmsCategory.TRANSACTIONAL
            else -> SmsCategory.PERSONAL
        }

        // Confidence = matched weight / total possible weight, clamped to 0–1
        val matchedWeight = maxScore.toFloat()
        val confidence = (matchedWeight / totalPossibleWeight.coerceAtLeast(1)).coerceIn(0f, 1f)

        // Numeric spam score for UI (0–100 scale)
        val numericSpamScore = when (category) {
            SmsCategory.SPAM -> (60 + (confidence * 40).toInt()).coerceAtMost(100)
            SmsCategory.PROMOTIONAL -> (20 + (confidence * 30).toInt()).coerceAtMost(50)
            SmsCategory.TRANSACTIONAL -> 0
            SmsCategory.PERSONAL -> 0
        }

        return SmsClassification(
            category = category,
            confidence = confidence,
            matchedPatterns = matchedDescriptions,
            spamScore = numericSpamScore
        )
    }
}
