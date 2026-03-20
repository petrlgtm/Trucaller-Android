package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Type of calls to block when a schedule is active.
 */
enum class ScheduleBlockType {
    /** Block all calls from numbers not in the caller-ID database. */
    ALL_UNKNOWN,
    /** Block all calls flagged as spam. */
    ALL_SPAM,
    /** Block every call except those from the user's contacts. */
    ALL_EXCEPT_CONTACTS,
    /** Block every call except those from starred/favourite contacts. */
    ALL_EXCEPT_STARRED
}

/**
 * Room entity representing a user-defined schedule for automatic call blocking.
 *
 * Each schedule defines a time window (start/end in minutes since midnight, 0-1439)
 * and a bitmask of days-of-week when it is active.
 *
 * Day-of-week bitmask uses [java.util.Calendar] constants:
 * - Sunday    = 1 (bit 0)
 * - Monday    = 2 (bit 1)
 * - Tuesday   = 4 (bit 2)
 * - Wednesday = 8 (bit 3)
 * - Thursday  = 16 (bit 4)
 * - Friday    = 32 (bit 5)
 * - Saturday  = 64 (bit 6)
 *
 * Example: weekdays only = 2 + 4 + 8 + 16 + 32 = 62
 */
@Entity(tableName = "blocking_schedules")
data class BlockingSchedule(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean = true,
    /** Start of blocking window in minutes since midnight (0-1439). */
    val startTimeMinutes: Int,
    /** End of blocking window in minutes since midnight (0-1439). */
    val endTimeMinutes: Int,
    /** Bitmask of active days (bit 0 = Sunday, bit 6 = Saturday). */
    val daysOfWeek: Int,
    val blockType: ScheduleBlockType,
    val createdAt: String
)
