package com.byron.trucaller.ui.security

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.service.LockManager
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen, un-dismissible anti-theft lock. Shown by [LockManager] when a
 * remote LOCK_DEVICE command arrives. It draws over the keyguard, blocks Back,
 * and re-launches itself if the user navigates away (Home/Recents) — so the
 * device is unusable until the owner enters their app password.
 *
 * This is the app-level enforcement of "only opens with the user's app password":
 * a non-Device-Owner app cannot reprogram the system lock screen, so the unlock
 * credential is the owner's account password (or security PIN), verified locally.
 */
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw over the keyguard and force the screen on.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Block the Back button entirely while locked.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* swallow — cannot back out of the lock */ }
        })

        setContent {
            MaterialTheme {
                LockScreen(onUnlocked = {
                    LockManager.unlock(this)
                    finish()
                })
            }
        }
    }

    /** Home / app-switch pressed → bounce straight back to the lock while still locked. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (LockManager.isLocked(this)) LockManager.showLockScreen(this)
    }

    override fun onStop() {
        super.onStop()
        if (LockManager.isLocked(this)) LockManager.showLockScreen(this)
    }

    private suspend fun verifyAppPassword(input: String): Boolean {
        val app = application as? TruCallerApplication ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val hashed = hashPassword(input)
                val prefs = app.container.userPreferences

                // Primary: the device owner's own app password (or security PIN).
                val userId = prefs.loggedInUserId.first()
                val user = userId?.let { app.container.userRepository.getUserById(it) }
                if (user != null &&
                    (user.passwordHash == hashed || (user.securityPin != null && user.securityPin == hashed))
                ) return@withContext true

                // Recovery path: the admin password also unlocks (covers admin-managed
                // devices, and a user who recovers access via a temp password from us).
                val adminId = prefs.adminId.first()
                val admin = adminId?.let { app.container.userRepository.getAdminById(it) }
                if (admin != null && admin.password == hashed) return@withContext true

                false
            } catch (_: Exception) {
                false
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen(onUnlocked: () -> Unit) {
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0E12))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Device Locked",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This device has been locked by its owner. Enter your app password to unlock.",
                color = Color(0xFFB9B9C6),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("App password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (password.isBlank() || checking) return@Button
                    checking = true
                    scope.launch {
                        val ok = verifyAppPassword(password)
                        checking = false
                        if (ok) onUnlocked() else { error = "Incorrect password"; password = "" }
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "Checking…" else "Unlock")
            }
        }
    }
}
