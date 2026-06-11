package com.byron.trucaller.ui.main

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.service.CallRecordingService
import com.byron.trucaller.service.CustomInCallService
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.theme.TruCallerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull

class InCallActivity : ComponentActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Trucaller:InCallProximity"
        )

        setContent {
            TruCallerTheme {
                val call by CustomInCallService.activeCall.collectAsState()
                DisposableEffect(call?.state) {
                    if (call?.state == Call.STATE_ACTIVE) {
                        if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire()
                    } else {
                        if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                    }
                    onDispose { if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release() }
                }
                InCallContent(
                    onAnswer  = { c -> c.answer(VideoProfile.STATE_AUDIO_ONLY) },
                    onDecline = { c -> c.reject(false, null) },
                    onHangUp  = { c -> c.disconnect() },
                    onFinish  = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
    }
}

// ── Key layout for DTMF dialpad ──────────────────────────────────────────────
private data class DtmfKey(val digit: Char, val main: String, val sub: String)
private val DTMF_KEYS = listOf(
    DtmfKey('1', "1", ""),       DtmfKey('2', "2", "ABC"),  DtmfKey('3', "3", "DEF"),
    DtmfKey('4', "4", "GHI"),    DtmfKey('5', "5", "JKL"),  DtmfKey('6', "6", "MNO"),
    DtmfKey('7', "7", "PQRS"),   DtmfKey('8', "8", "TUV"),  DtmfKey('9', "9", "WXYZ"),
    DtmfKey('*', "＊", ""),       DtmfKey('0', "0", "+"),    DtmfKey('#', "#", ""),
)

@Composable
fun InCallContent(
    onAnswer: (Call) -> Unit,
    onDecline: (Call) -> Unit,
    onHangUp: (Call) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val call by CustomInCallService.activeCall.collectAsState()
    val audioState by CustomInCallService.audioState.collectAsState()
    val isRecording by CallRecordingService.isRecording.collectAsState()

    var callerInfo by remember { mutableStateOf<CallerIdEntry?>(null) }
    var isCallEnded by remember { mutableStateOf(false) }
    var lookupSource by remember { mutableStateOf<String?>(null) }
    var showDialpad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }

    LaunchedEffect(call) {
        if (call != null) {
            val number = try { call!!.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } } catch (_: Exception) { null } ?: ""
            if (number.isNotEmpty()) {
                val repo = (context.applicationContext as TruCallerApplication).container.callerIdRepository
                val result = repo.lookupNumberLocal(number)
                callerInfo = result.callerIdEntry
                lookupSource = result.source
            }
        } else {
            if (!isCallEnded) {
                isCallEnded = true
                showDialpad = false
                delay(1500)
                onFinish()
            }
        }
    }

    if (call == null && !isCallEnded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1B2A)),
            contentAlignment = Alignment.Center
        ) {
            Text("Connecting…", color = Color.White, fontSize = 18.sp)
        }
        return
    }

    val state = if (isCallEnded) Call.STATE_DISCONNECTED else call?.state ?: Call.STATE_DISCONNECTED
    val rawNumber = try { call?.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
        ?: callerInfo?.phoneNumber?.takeIf { it.isNotBlank() } ?: ""
    val phoneNumber = when {
        rawNumber.isEmpty()  -> "Private Number"
        rawNumber == "-1"    -> "Unknown"
        rawNumber == "-2"    -> "Voicemail"
        else                 -> rawNumber
    }
    val displayName = callerInfo?.name?.takeIf { it.isNotBlank() }
        ?: if (phoneNumber != "Private Number" && phoneNumber != "Unknown") phoneNumber else "Unknown Caller"
    val isSpam = callerInfo?.category == SpamCategory.SPAM || callerInfo?.category == SpamCategory.FRAUD || (callerInfo?.spamScore ?: 0) > 60
    val isSuspected = callerInfo?.category == SpamCategory.SUSPECTED_SPAM && !isSpam
    val isVerified = lookupSource == "caller_id_db" && !isSpam

    val isMuted    = audioState?.isMuted ?: false
    val isSpeaker  = (audioState?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER
    val isBluetooth = (audioState?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_BLUETOOTH
    val bluetoothAvailable = ((audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH) != 0
    val isHeld = state == Call.STATE_HOLDING

    var durationSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE) {
            while (true) { delay(1000); durationSeconds++ }
        }
    }

    val statusText = when (state) {
        Call.STATE_RINGING     -> "Incoming Call"
        Call.STATE_DIALING     -> "Dialing…"
        Call.STATE_CONNECTING  -> "Connecting…"
        Call.STATE_HOLDING     -> "On Hold"
        Call.STATE_DISCONNECTED -> "Call Ended"
        Call.STATE_ACTIVE -> {
            val m = durationSeconds / 60; val s = durationSeconds % 60
            "%02d:%02d".format(m, s)
        }
        else -> "In Call"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == Call.STATE_RINGING) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recAlpha"
    )

    val bgBrush = when {
        isSpam    -> Brush.verticalGradient(listOf(Color(0xFF1A0000), Color(0xFF3D0000), Color(0xFF1A0000)))
        isSuspected -> Brush.verticalGradient(listOf(Color(0xFF1A1200), Color(0xFF2E2200), Color(0xFF1A1200)))
        isHeld    -> Brush.verticalGradient(listOf(Color(0xFF0D1A0D), Color(0xFF162616), Color(0xFF0D1A0D)))
        else      -> Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF16253E), Color(0xFF0A1628)))
    }

    val accentColor = when {
        isSpam    -> Color(0xFFE53935)
        isSuspected -> Color(0xFFFFB300)
        else      -> Color(0xFF1E88E5)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {

            // ── Main content ───────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Identity section ───────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Avatar with pulse ring
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
                        if (state == Call.STATE_RINGING) {
                            Box(
                                modifier = Modifier.size(168.dp).clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.18f)).scale(pulseScale)
                            )
                            Box(
                                modifier = Modifier.size(148.dp).clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.12f)).scale(pulseScale * 0.96f)
                            )
                        }
                        TruCallerAvatar(name = displayName, size = 130.dp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Status chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isHeld) Color(0xFF2E7D32).copy(alpha = 0.25f)
                                        else accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = if (isHeld) Color(0xFF81C784) else accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Spam / Verified badge
                    when {
                        isSpam -> {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE53935).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "⚠  SPAM / FRAUD RISK",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        isSuspected -> {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFB300).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "⚠  Suspected Spam",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        isVerified -> {
                            TruCallerBadge(text = "Verified Identity", type = BadgeType.Success, icon = Icons.Default.Shield)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        else -> Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = displayName,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 42.sp
                    )
                    Text(
                        text = phoneNumber,
                        fontSize = 17.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    // Spam score bar (if spam and score known)
                    val score = callerInfo?.spamScore ?: -1
                    if (score >= 0 && (isSpam || isSuspected)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SpamScoreBar(score = score, accentColor = accentColor)
                    }
                }

                // ── Controls section ───────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        state == Call.STATE_RINGING -> {
                            Text(
                                text = "Swipe or tap to respond",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LargeCallButton(
                                    icon = Icons.Default.CallEnd,
                                    label = "Decline",
                                    color = Color(0xFFE53935),
                                    onClick = { call?.let { onDecline(it) } }
                                )
                                LargeCallButton(
                                    icon = Icons.Default.Call,
                                    label = "Answer",
                                    color = Color(0xFF2E7D32),
                                    onClick = { call?.let { onAnswer(it) } }
                                )
                            }
                        }

                        state != Call.STATE_DISCONNECTED -> {
                            // 4-button control row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                RoundControlButton(
                                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    label = if (isMuted) "Unmute" else "Mute",
                                    active = isMuted,
                                    activeColor = Color(0xFFE53935),
                                    onClick = { CustomInCallService.toggleMute(isMuted) }
                                )
                                RoundControlButton(
                                    icon = Icons.Default.VolumeUp,
                                    label = "Speaker",
                                    active = isSpeaker,
                                    onClick = { CustomInCallService.toggleSpeaker(isSpeaker) }
                                )
                                RoundControlButton(
                                    icon = if (isBluetooth) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                    label = "Bluetooth",
                                    active = isBluetooth,
                                    enabled = bluetoothAvailable,
                                    onClick = { if (bluetoothAvailable) CustomInCallService.toggleBluetooth(isBluetooth) }
                                )
                                RoundControlButton(
                                    icon = Icons.Default.Dialpad,
                                    label = "Keypad",
                                    active = showDialpad,
                                    onClick = {
                                        showDialpad = !showDialpad
                                        if (!showDialpad) dtmfDigits = ""
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Hold button (pill style)
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isHeld) Color(0xFF2E7D32).copy(alpha = 0.3f)
                                                else Color.White.copy(alpha = 0.07f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .then(Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = { CustomInCallService.toggleHold() })
                                    })
                                    .padding(horizontal = 28.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isHeld) "Resume" else "Hold",
                                        tint = if (isHeld) Color(0xFF81C784) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isHeld) "Resume Call" else "Hold",
                                        color = if (isHeld) Color(0xFF81C784) else Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // End call button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { call?.let { onHangUp(it) } },
                                    modifier = Modifier.size(76.dp).clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                ) {
                                    Icon(
                                        Icons.Default.CallEnd, "End Call",
                                        tint = Color.White, modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("End Call", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ── Recording indicator ────────────────────────────────────────
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 20.dp)
                        .background(Color(0xFFE53935).copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord, null,
                        tint = Color(0xFFE53935).copy(alpha = recAlpha),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("REC", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }

            // ── Dialpad panel (slides up from bottom) ─────────────────────
            AnimatedVisibility(
                visible = showDialpad && state != Call.STATE_RINGING && state != Call.STATE_DISCONNECTED,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(tween(280)),
                exit  = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(220))
            ) {
                DialpadPanel(
                    dtmfDigits = dtmfDigits,
                    onKeyDown  = { digit ->
                        CustomInCallService.playDtmf(digit)
                        dtmfDigits += digit.toString()
                    },
                    onKeyUp    = { CustomInCallService.stopDtmf() },
                    onBackspace = { if (dtmfDigits.isNotEmpty()) dtmfDigits = dtmfDigits.dropLast(1) },
                    onClose    = { showDialpad = false; dtmfDigits = "" }
                )
            }
        }
    }
}

// ── Spam score bar ────────────────────────────────────────────────────────────
@Composable
private fun SpamScoreBar(score: Int, accentColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Spam Risk", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text("$score / 100", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(score / 100f).height(4.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ── Large answer / decline buttons (ringing state) ───────────────────────────
@Composable
fun LargeCallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(76.dp)
                .background(color.copy(alpha = 0.18f), CircleShape)
                .padding(4.dp)
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(color)
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Round control button ──────────────────────────────────────────────────────
@Composable
fun RoundControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = Color(0xFF1E88E5),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bgColor = when {
        !enabled -> Color.White.copy(alpha = 0.04f)
        active   -> activeColor.copy(alpha = 0.25f)
        else     -> Color.White.copy(alpha = 0.1f)
    }
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.25f)
        active   -> activeColor
        else     -> Color.White
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = { if (enabled) onClick() },
            modifier = Modifier.size(60.dp).clip(CircleShape).background(bgColor)
        ) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = if (enabled) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
    }
}

// ── Dialpad panel ─────────────────────────────────────────────────────────────
@Composable
fun DialpadPanel(
    dtmfDigits: String,
    onKeyDown: (Char) -> Unit,
    onKeyUp: () -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xCC0A1628), Color(0xFF0A1628))),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(top = 8.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier.width(40.dp).height(4.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Entered digits display
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = dtmfDigits.ifEmpty { " " },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                modifier = Modifier.weight(1f).padding(start = 48.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onBackspace,
                modifier = Modifier.size(48.dp).alpha(if (dtmfDigits.isNotEmpty()) 1f else 0.25f)
            ) {
                Text("⌫", color = Color.White, fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Key grid (4 rows × 3 columns)
        DTMF_KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    DialpadKey(
                        key = key,
                        onKeyDown = onKeyDown,
                        onKeyUp = onKeyUp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Close button
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .then(Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onClose() })
                })
                .padding(horizontal = 32.dp, vertical = 10.dp)
        ) {
            Text("Close Keypad", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DialpadKey(key: DtmfKey, onKeyDown: (Char) -> Unit, onKeyUp: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (pressed) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
            )
            .pointerInput(key.digit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onKeyDown(key.digit)
                        tryAwaitRelease()
                        onKeyUp()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.main, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            if (key.sub.isNotEmpty()) {
                Text(key.sub, color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}
