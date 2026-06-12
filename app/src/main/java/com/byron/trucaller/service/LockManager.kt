package com.byron.trucaller.service

import android.content.Context
import android.content.Intent
import com.byron.trucaller.ui.security.LockScreenActivity

/**
 * Tracks and enforces the remote anti-theft device lock.
 *
 * A normally-installed app (Device Admin, not Device Owner) cannot reprogram the
 * system keyguard, so the lock is enforced at the app level: [lock] records a
 * persistent "locked" flag and launches the full-screen [LockScreenActivity],
 * which can only be cleared by entering the owner's app password ([unlock]).
 *
 * The flag is stored in SharedPreferences so the lock survives process death and
 * reboot — [com.byron.trucaller.TruCallerApplication] and the boot receiver
 * re-show the lock screen on startup while [isLocked] is true.
 */
object LockManager {

    private const val PREFS = "device_lock_state"
    private const val KEY_LOCKED = "is_locked"

    fun lock(context: Context) {
        prefs(context).edit().putBoolean(KEY_LOCKED, true).apply()
        showLockScreen(context)
    }

    fun unlock(context: Context) {
        prefs(context).edit().putBoolean(KEY_LOCKED, false).apply()
    }

    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)

    /** Brings the lock screen to the front. Safe to call repeatedly. */
    fun showLockScreen(context: Context) {
        val ctx = context.applicationContext
        val intent = Intent(ctx, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        ctx.startActivity(intent)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
