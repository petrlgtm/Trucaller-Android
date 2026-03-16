package com.byron.trucaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.SpamCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts incoming SMS messages and checks
 * the sender against the caller ID / spam database.
 *
 * When a message arrives from a known spam number, a notification is
 * shown warning the user.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderNumber = messages[0].displayOriginatingAddress ?: return
        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }

        Log.d(TAG, "SMS received from: $senderNumber")

        val app = context.applicationContext as? TruCallerApplication ?: return
        val callerIdRepo = app.container.callerIdRepository
        val blockedRepo = app.container.blockedNumberRepository
        val userPrefs = app.container.userPreferences

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get current logged-in user ID
                val userId = userPrefs.loggedInUserId.firstOrNull()

                // Check if number is blocked
                if (userId != null && blockedRepo.isBlocked(userId, senderNumber)) {
                    Log.d(TAG, "SMS from blocked number: $senderNumber — suppressed notification")
                    return@launch
                }

                // Check caller ID database for spam
                val lookupResult = callerIdRepo.lookupNumber(senderNumber)
                val entry = lookupResult.callerIdEntry

                if (entry != null && entry.category in listOf(SpamCategory.SPAM, SpamCategory.FRAUD)) {
                    Log.d(TAG, "SPAM SMS detected from: $senderNumber (score: ${entry.spamScore})")
                    SmsNotificationHelper.showSpamSmsNotification(
                        context = context,
                        sender = entry.name,
                        senderNumber = senderNumber,
                        messagePreview = fullBody.take(80),
                        spamScore = entry.spamScore
                    )
                } else if (entry != null && entry.category == SpamCategory.SUSPECTED_SPAM) {
                    Log.d(TAG, "Suspected spam SMS from: $senderNumber (score: ${entry.spamScore})")
                    SmsNotificationHelper.showSuspectedSpamNotification(
                        context = context,
                        sender = entry.name,
                        senderNumber = senderNumber,
                        messagePreview = fullBody.take(80)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking SMS sender", e)
            }
        }
    }
}
