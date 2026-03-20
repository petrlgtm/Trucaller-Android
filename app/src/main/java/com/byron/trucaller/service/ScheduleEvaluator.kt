package com.byron.trucaller.service

import com.byron.trucaller.data.model.BlockingSchedule
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.ScheduleBlockType
import java.util.Calendar

/**
 * Decision produced by [ScheduleEvaluator] for a single incoming call.
 *
 * @property shouldBlock `true` when the call should be silently rejected.
 * @property reason Human-readable explanation of why the call was blocked (or allowed).
 * @property scheduleName Name of the schedule that triggered the block, or `null` if allowed.
 */
data class BlockDecision(
    val shouldBlock: Boolean,
    val reason: String,
    val scheduleName: String? = null
)

/**
 * Evaluates user-defined [BlockingSchedule]s against an incoming call to decide
 * whether it should be blocked.
 *
 * The evaluator is stateless — all required data is passed into [shouldBlockCall].
 */
object ScheduleEvaluator {

    /**
     * Determine whether an incoming call should be blocked based on active schedules.
     *
     * The first matching schedule wins (schedules are evaluated in the order provided).
     *
     * @param schedules Active [BlockingSchedule]s for the current user.
     * @param phoneNumber The incoming caller's phone number.
     * @param contacts The user's contact list (used for CONTACTS / STARRED exemptions).
     * @param currentTime A [Calendar] representing the current date/time (injectable for testing).
     * @return A [BlockDecision] indicating whether to block and why.
     */
    fun shouldBlockCall(
        schedules: List<BlockingSchedule>,
        phoneNumber: String,
        contacts: List<Contact>,
        currentTime: Calendar = Calendar.getInstance()
    ): BlockDecision {
        if (schedules.isEmpty()) {
            return BlockDecision(shouldBlock = false, reason = "No active schedules")
        }

        val currentMinute = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
        val dayBit = dayOfWeekBit(currentTime.get(Calendar.DAY_OF_WEEK))

        for (schedule in schedules) {
            if (!isWithinTimeWindow(currentMinute, schedule.startTimeMinutes, schedule.endTimeMinutes)) {
                continue
            }
            if (schedule.daysOfWeek and dayBit == 0) {
                continue
            }

            // Schedule is active at this moment — evaluate block type
            val blocked = evaluateBlockType(schedule.blockType, phoneNumber, contacts)
            if (blocked) {
                return BlockDecision(
                    shouldBlock = true,
                    reason = "Blocked by schedule '${schedule.name}' (${schedule.blockType.name})",
                    scheduleName = schedule.name
                )
            }
        }

        return BlockDecision(shouldBlock = false, reason = "No matching schedule blocked this call")
    }

    /**
     * Check whether [currentMinute] falls within the window [start]..[end].
     *
     * Supports overnight windows where [end] < [start] (e.g. 22:00 - 06:00
     * represented as 1320..360).
     */
    private fun isWithinTimeWindow(currentMinute: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            // Same-day window: e.g. 09:00 (540) to 17:00 (1020)
            currentMinute in start..end
        } else {
            // Overnight window: e.g. 22:00 (1320) to 06:00 (360)
            currentMinute >= start || currentMinute <= end
        }
    }

    /**
     * Convert a [Calendar.DAY_OF_WEEK] constant (1=Sunday..7=Saturday) to the
     * bitmask position used in [BlockingSchedule.daysOfWeek].
     *
     * - Sunday    = 1  (bit 0)
     * - Monday    = 2  (bit 1)
     * - ...
     * - Saturday  = 64 (bit 6)
     */
    private fun dayOfWeekBit(calendarDay: Int): Int {
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        // Bit position = calendarDay - 1
        return 1 shl (calendarDay - 1)
    }

    /**
     * Evaluate whether a call from [phoneNumber] should be blocked under the given [blockType].
     */
    private fun evaluateBlockType(
        blockType: ScheduleBlockType,
        phoneNumber: String,
        contacts: List<Contact>
    ): Boolean {
        return when (blockType) {
            ScheduleBlockType.ALL_UNKNOWN -> {
                // Block if the number is not in the user's contacts
                contacts.none { normalizedMatch(it.phoneNumber, phoneNumber) }
            }
            ScheduleBlockType.ALL_SPAM -> {
                // ALL_SPAM is handled by the spam-score check already in the
                // screening service, so this schedule type always blocks.
                // Callers known as contacts are NOT exempted — spam means spam.
                true
            }
            ScheduleBlockType.ALL_EXCEPT_CONTACTS -> {
                // Block every call unless the caller is a saved contact
                contacts.none { normalizedMatch(it.phoneNumber, phoneNumber) }
            }
            ScheduleBlockType.ALL_EXCEPT_STARRED -> {
                // Block every call unless the caller is a starred/favourite contact
                contacts.none { it.isFavourite && normalizedMatch(it.phoneNumber, phoneNumber) }
            }
        }
    }

    /**
     * Compares two phone numbers by their trailing digits to handle different
     * formatting / country-code prefixes (e.g. "+254712..." vs "0712...").
     *
     * We compare the last 9 digits which is sufficient for local numbers;
     * a full E.164 normalization layer exists in [com.byron.trucaller.util.PhoneUtils].
     */
    private fun normalizedMatch(contactPhone: String, incomingPhone: String): Boolean {
        val a = contactPhone.filter { it.isDigit() }
        val b = incomingPhone.filter { it.isDigit() }
        if (a.isEmpty() || b.isEmpty()) return false
        val tailLength = minOf(9, a.length, b.length)
        return a.takeLast(tailLength) == b.takeLast(tailLength)
    }
}
