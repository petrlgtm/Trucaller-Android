package com.byron.trucaller.util

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsType

/**
 * Reads SMS messages from the device's SMS content provider.
 */
object SmsReader {

    private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
    private val INBOX_URI: Uri = Telephony.Sms.Inbox.CONTENT_URI
    private val SENT_URI: Uri = Telephony.Sms.Sent.CONTENT_URI

    /**
     * Read all SMS messages (inbox + sent), sorted by date descending.
     */
    fun readAllMessages(
        contentResolver: ContentResolver,
        limit: Int = 500
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        val cursor: Cursor? = contentResolver.query(
            SMS_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val typeInt = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val read = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1

                val type = when (typeInt) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsType.INBOX
                    Telephony.Sms.MESSAGE_TYPE_SENT -> SmsType.SENT
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> SmsType.DRAFT
                    else -> SmsType.INBOX
                }

                messages.add(
                    SmsMessage(
                        id = id,
                        address = address,
                        body = body,
                        date = date,
                        type = type,
                        read = read
                    )
                )
                count++
            }
        }

        return messages
    }

    /**
     * Read messages from a specific sender/conversation.
     */
    fun readConversation(
        contentResolver: ContentResolver,
        address: String,
        limit: Int = 100
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val normalizedAddress = normalizePhoneForQuery(address)

        val cursor: Cursor? = contentResolver.query(
            SMS_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.ADDRESS} LIKE ?",
            arrayOf("%$normalizedAddress%"),
            "${Telephony.Sms.DATE} ASC"
        )

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                val addr = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val typeInt = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val read = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1

                val type = when (typeInt) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> SmsType.SENT
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> SmsType.DRAFT
                    else -> SmsType.INBOX
                }

                messages.add(
                    SmsMessage(
                        id = id,
                        address = addr,
                        body = body,
                        date = date,
                        type = type,
                        read = read
                    )
                )
                count++
            }
        }

        return messages
    }

    /**
     * Strips the number down to the last significant digits for fuzzy matching.
     */
    private fun normalizePhoneForQuery(phone: String): String {
        val digits = phone.replace(Regex("[^\\d]"), "")
        return if (digits.length > 9) digits.takeLast(9) else digits
    }
}
