package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Type of pattern matching used by an SMS classification rule.
 */
enum class SmsRuleType {
    SENDER_MATCH,
    KEYWORD_MATCH,
    REGEX_MATCH
}

/**
 * Room entity representing a user-defined SMS classification rule.
 *
 * When a new SMS is enriched, active rules are evaluated in descending priority order
 * before the built-in auto-classification pipeline runs.  The first matching rule wins.
 */
@Entity(tableName = "sms_rules")
data class SmsRule(
    @PrimaryKey val id: String,
    val userId: String,
    val ruleType: SmsRuleType,
    /** The pattern to match against (sender address, keyword, or regex). */
    val pattern: String,
    /** The category to assign when this rule matches. */
    val targetCategory: SmsCategory,
    /** Priority from 1 (lowest) to 10 (highest). Higher-priority rules are evaluated first. */
    val priority: Int = 5,
    val isActive: Boolean = true,
    val createdAt: String
)
