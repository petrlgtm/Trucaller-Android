package com.byron.trucaller.util

import android.content.Context
import android.provider.CallLog
import com.byron.trucaller.data.model.CallLogEntry
import com.byron.trucaller.data.model.CallType

/**
 * Reads call log entries from the device's CallLog content provider.
 */
object CallLogReader {

    fun readCallLog(context: Context, limit: Int = 100): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls._ID))
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "Unknown"
                val cachedName = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                val typeInt = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))

                val callType = when (typeInt) {
                    1 -> CallType.INCOMING  // CallLog.Calls.INCOMING_TYPE
                    2 -> CallType.OUTGOING  // CallLog.Calls.OUTGOING_TYPE
                    3 -> CallType.MISSED    // CallLog.Calls.MISSED_TYPE
                    5 -> CallType.REJECTED  // CallLog.Calls.REJECTED_TYPE
                    6 -> CallType.BLOCKED   // CallLog.Calls.BLOCKED_TYPE
                    else -> CallType.INCOMING
                }

                entries.add(
                    CallLogEntry(
                        id = id.toString(),
                        phoneNumber = number,
                        name = cachedName,
                        callType = callType,
                        duration = duration,
                        timestamp = date
                    )
                )
                count++
            }
        }

        return entries
    }
}
