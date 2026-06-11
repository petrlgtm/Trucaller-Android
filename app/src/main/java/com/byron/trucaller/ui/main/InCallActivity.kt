package com.byron.trucaller.ui.main

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallDirection
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.repository.LookupResult
import com.byron.trucaller.service.CallRecordingService
import com.byron.trucaller.service.CustomInCallService
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.theme.MontserratFontFamily
import com.byron.trucaller.ui.theme.TruCallerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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

// ── DTMF keypad data ─────────────────────────────────────────────────────────
private data class DtmfKey(val digit: Char, val main: String, val sub: String)
private val DTMF_KEYS = listOf(
    DtmfKey('1', "1", ""),       DtmfKey('2', "2", "ABC"),  DtmfKey('3', "3", "DEF"),
    DtmfKey('4', "4", "GHI"),    DtmfKey('5', "5", "JKL"),  DtmfKey('6', "6", "MNO"),
    DtmfKey('7', "7", "PQRS"),   DtmfKey('8', "8", "TUV"),  DtmfKey('9', "9", "WXYZ"),
    DtmfKey('*', "＊", ""),       DtmfKey('0', "0", "+"),    DtmfKey('#', "#", ""),
)

// ── Root in-call composable ──────────────────────────────────────────────────
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

    var lookupResult by remember { mutableStateOf<LookupResult?>(null) }
    var isCallEnded by remember { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }

    LaunchedEffect(call) {
        if (call != null) {
            val number = try {
                call!!.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null } ?: ""
            if (number.isNotEmpty()) {
                val app = context.applicationContext as TruCallerApplication
                val userId = app.container.userPreferences.loggedInUserId.firstOrNull()
                // Full lookup: local contacts/aliases (scoped to userId) → backend community names
                lookupResult = app.container.callerIdRepository.lookupNumber(number, userId)
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
            modifier = Modifier.fillMaxSize().background(Color(0xFF050C18)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Connecting…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    val callerInfo = lookupResult?.callerIdEntry
    val contactPhotoUri = lookupResult?.contactMatch?.photoUri
    val lookupSource = lookupResult?.source

    val state = if (isCallEnded) Call.STATE_DISCONNECTED else call?.state ?: Call.STATE_DISCONNECTED
    val rawNumber = try {
        call?.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
        ?: callerInfo?.phoneNumber?.takeIf { it.isNotBlank() } ?: ""
    val phoneNumber = when {
        rawNumber.isEmpty() -> "Private Number"
        rawNumber == "-1"   -> "Unknown"
        rawNumber == "-2"   -> "Voicemail"
        else                -> rawNumber
    }
    val displayName = callerInfo?.name?.takeIf { it.isNotBlank() }
        ?: if (phoneNumber != "Private Number" && phoneNumber != "Unknown") phoneNumber else "Unknown Caller"

    val isSpam     = callerInfo?.category == SpamCategory.SPAM || callerInfo?.category == SpamCategory.FRAUD || (callerInfo?.spamScore ?: 0) > 60
    val isSuspected = callerInfo?.category == SpamCategory.SUSPECTED_SPAM && !isSpam
    val isVerified  = lookupSource == "caller_id_db" && !isSpam

    val isMuted       = audioState?.isMuted ?: false
    val isSpeaker     = (audioState?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER
    val isBluetooth   = (audioState?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_BLUETOOTH
    val bluetoothAvail = ((audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH) != 0
    val isHeld        = state == Call.STATE_HOLDING

    val callDirection = when (state) {
        Call.STATE_RINGING -> CallDirection.INCOMING
        Call.STATE_DIALING -> CallDirection.OUTGOING
        else               -> CallDirection.INCOMING
    }

    var durationSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE) {
            while (true) { delay(1000); durationSeconds++ }
        }
    }

    val statusText = when (state) {
        Call.STATE_RINGING      -> "INCOMING CALL"
        Call.STATE_DIALING      -> "DIALING"
        Call.STATE_CONNECTING   -> "CONNECTING"
        Call.STATE_HOLDING      -> "ON HOLD"
        Call.STATE_DISCONNECTED -> "CALL ENDED"
        Call.STATE_ACTIVE -> {
            val h = durationSeconds / 3600
            val m = (durationSeconds % 3600) / 60
            val s = durationSeconds % 60
            if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
        else -> "IN CALL"
    }

    val accentColor = when {
        isSpam     -> Color(0xFFE53935)
        isSuspected -> Color(0xFFFFB300)
        else        -> Color(0xFF42A5F5)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "callPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (state == Call.STATE_RINGING) 1.22f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        ImmersiveBackground(
            isSpam = isSpam,
            isSuspected = isSuspected,
            contactPhotoUri = contactPhotoUri
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Identity block ─────────────────────────────────────────
                PremiumIdentityBlock(
                    displayName   = displayName,
                    phoneNumber   = phoneNumber,
                    statusText    = statusText,
                    state         = state,
                    isSpam        = isSpam,
                    isSuspected   = isSuspected,
                    isVerified    = isVerified,
                    isHeld        = isHeld,
                    accentColor   = accentColor,
                    contactPhotoUri = contactPhotoUri,
                    pulseScale    = pulseScale,
                    callerSpamScore = callerInfo?.spamScore ?: -1
                )

                // ── Controls ───────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        state == Call.STATE_RINGING -> {
                            Text(
                                text = "TAP TO RESPOND",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 28.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 52.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PremiumCallActionButton(
                                    icon   = Icons.Default.CallEnd,
                                    label  = "Decline",
                                    color  = Color(0xFFE53935),
                                    onClick = { call?.let { onDecline(it) } }
                                )
                                PremiumCallActionButton(
                                    icon   = Icons.Default.Call,
                                    label  = "Answer",
                                    color  = Color(0xFF2E7D32),
                                    onClick = { call?.let { onAnswer(it) } }
                                )
                            }
                        }

                        state != Call.STATE_DISCONNECTED -> {
                            // Glassmorphism function bar
                            GlassyFunctionBar(
                                isMuted          = isMuted,
                                isSpeaker        = isSpeaker,
                                isBluetooth      = isBluetooth,
                                bluetoothAvail   = bluetoothAvail,
                                isRecording      = isRecording,
                                isHeld           = isHeld,
                                showDialpad      = showDialpad,
                                accentColor      = accentColor,
                                rawNumber        = rawNumber,
                                callDirection    = callDirection,
                                onToggleMute     = { CustomInCallService.toggleMute(isMuted) },
                                onToggleSpeaker  = { CustomInCallService.toggleSpeaker(isSpeaker) },
                                onToggleBluetooth = { if (bluetoothAvail) CustomInCallService.toggleBluetooth(isBluetooth) },
                                onToggleDialpad  = {
                                    showDialpad = !showDialpad
                                    if (!showDialpad) dtmfDigits = ""
                                },
                                onToggleHold     = { CustomInCallService.toggleHold() },
                                onToggleRecording = {
                                    if (isRecording) {
                                        CallRecordingService.stop(context)
                                    } else {
                                        CallRecordingService.start(context, rawNumber, callDirection)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // End call button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            Color(0xFFE53935).copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = { call?.let { onHangUp(it) } },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935))
                                    ) {
                                        Icon(
                                            Icons.Default.CallEnd, "End Call",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "End Call",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 12.sp,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Recording status capsule ───────────────────────────────────
            if (isRecording) {
                RecordingStatusCapsule(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                )
            }

            // ── Dialpad ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showDialpad && state != Call.STATE_RINGING && state != Call.STATE_DISCONNECTED,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                ) + fadeIn(tween(220)),
                exit  = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                ) + fadeOut(tween(200))
            ) {
                GlassDialpadPanel(
                    dtmfDigits  = dtmfDigits,
                    onKeyDown   = { digit ->
                        CustomInCallService.playDtmf(digit)
                        dtmfDigits += digit.toString()
                    },
                    onKeyUp     = { CustomInCallService.stopDtmf() },
                    onBackspace = { if (dtmfDigits.isNotEmpty()) dtmfDigits = dtmfDigits.dropLast(1) },
                    onClose     = { showDialpad = false; dtmfDigits = "" }
                )
            }
        }
    }
}

// ── Immersive background with aurora mesh ────────────────────────────────────
@Composable
private fun ImmersiveBackground(
    isSpam: Boolean,
    isSuspected: Boolean,
    contactPhotoUri: String?,
    content: @Composable BoxScope.() -> Unit
) {
    val baseColor1 = when {
        isSpam     -> Color(0xFF0A0000)
        isSuspected -> Color(0xFF090600)
        else        -> Color(0xFF040B16)
    }
    val baseColor2 = when {
        isSpam     -> Color(0xFF1C0303)
        isSuspected -> Color(0xFF170E00)
        else        -> Color(0xFF0A1628)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Base dark gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(baseColor1, baseColor2, baseColor1)))
        )

        // 2. Blurred contact photo (API 31+ gets actual blur, older gets dark overlay)
        if (contactPhotoUri != null && File(contactPhotoUri).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(contactPhotoUri))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Modifier.blur(48.dp)
                        else
                            Modifier
                    )
                    .alpha(0.28f)
            )
        }

        // 3. Animated aurora mesh
        AuroraMesh(isSpam = isSpam, isSuspected = isSuspected)

        // 4. Vignette — top + bottom dark edges to frame the content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
        )

        content()
    }
}

// ── Aurora animated mesh gradient blobs ──────────────────────────────────────
@Composable
private fun AuroraMesh(isSpam: Boolean, isSuspected: Boolean) {
    val auroraColors = when {
        isSpam -> listOf(
            Color(0xFFB71C1C), Color(0xFF880E4F), Color(0xFF4A0000)
        )
        isSuspected -> listOf(
            Color(0xFFE65100), Color(0xFF4E342E), Color(0xFFBF360C)
        )
        else -> listOf(
            Color(0xFF0D47A1), Color(0xFF006064), Color(0xFF1565C0)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val t1 by infiniteTransition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "t1"
    )
    val t2 by infiniteTransition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(12500, easing = LinearEasing)),
        label = "t2"
    )
    val t3 by infiniteTransition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(7800, easing = LinearEasing), RepeatMode.Reverse),
        label = "t3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Blob 1 — drifts across upper half
        val b1 = Offset(w * (0.3f + 0.35f * sin(t1)), h * (0.18f + 0.22f * cos(t1 * 0.7f)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auroraColors[0].copy(alpha = 0.38f), Color.Transparent),
                center = b1, radius = w * 0.65f
            ),
            radius = w * 0.65f, center = b1
        )

        // Blob 2 — drifts across lower half
        val b2 = Offset(w * (0.65f - 0.3f * cos(t2)), h * (0.62f + 0.2f * sin(t2 * 0.9f)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auroraColors[1].copy(alpha = 0.32f), Color.Transparent),
                center = b2, radius = w * 0.58f
            ),
            radius = w * 0.58f, center = b2
        )

        // Blob 3 — slow center drift
        val b3 = Offset(w * (0.5f + 0.28f * sin(t3 + 1f)), h * (0.42f - 0.18f * cos(t3)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auroraColors[2].copy(alpha = 0.28f), Color.Transparent),
                center = b3, radius = w * 0.52f
            ),
            radius = w * 0.52f, center = b3
        )
    }
}

// ── Identity block ────────────────────────────────────────────────────────────
@Composable
private fun PremiumIdentityBlock(
    displayName: String,
    phoneNumber: String,
    statusText: String,
    state: Int,
    isSpam: Boolean,
    isSuspected: Boolean,
    isVerified: Boolean,
    isHeld: Boolean,
    accentColor: Color,
    contactPhotoUri: String?,
    pulseScale: Float,
    callerSpamScore: Int
) {
    val borderColor = when {
        isSpam     -> Color(0xFFEF9A9A)
        isSuspected -> Color(0xFFFFCC80)
        else        -> Color(0xFFB0BEC5)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        // Avatar with pulsing glow ring
        PremiumAvatar(
            name           = displayName,
            contactPhotoUri = contactPhotoUri,
            isRinging      = state == Call.STATE_RINGING,
            accentColor    = accentColor,
            borderColor    = borderColor,
            pulseScale     = pulseScale
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Status chip
        val statusBg = when {
            isHeld -> Color(0xFF2E7D32).copy(alpha = 0.2f)
            else   -> accentColor.copy(alpha = 0.14f)
        }
        val statusFg = if (isHeld) Color(0xFF81C784) else accentColor

        Box(
            modifier = Modifier
                .background(statusBg, RoundedCornerShape(20.dp))
                .border(0.5.dp, statusFg.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = statusText,
                color = statusFg,
                fontSize = 11.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spam / verified badge
        when {
            isSpam -> {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE53935).copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        "⚠  SPAM / FRAUD RISK",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            isSuspected -> {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFB300).copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0xFFFFB300).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        "⚠  Suspected Spam",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
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

        // Caller name — Montserrat Black
        Text(
            text = displayName,
            fontSize = 34.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 42.sp,
            letterSpacing = (-0.5).sp
        )

        // Phone number
        Text(
            text = phoneNumber,
            fontSize = 16.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 5.dp),
            letterSpacing = 0.5.sp
        )

        // Spam score bar
        if (callerSpamScore >= 0 && (isSpam || isSuspected)) {
            Spacer(modifier = Modifier.height(14.dp))
            SpamScoreBar(score = callerSpamScore, accentColor = accentColor)
        }
    }
}

// ── Premium avatar with glow + border ────────────────────────────────────────
@Composable
private fun PremiumAvatar(
    name: String,
    contactPhotoUri: String?,
    isRinging: Boolean,
    accentColor: Color,
    borderColor: Color,
    pulseScale: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        // Outer pulse ring (ringing state)
        if (isRinging) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f * pulseScale.coerceIn(0f, 1f)))
            )
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.10f))
            )
        }

        // Glow halo (always present, pulsing)
        Canvas(modifier = Modifier.size(150.dp)) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = glowAlpha),
                        accentColor.copy(alpha = 0f)
                    ),
                    radius = r
                ),
                radius = r
            )
        }

        // Border ring
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .border(1.5.dp, borderColor.copy(alpha = 0.7f), CircleShape)
        ) {
            TruCallerAvatar(
                name     = name,
                size     = 136.dp,
                imageUri = contactPhotoUri
            )
        }
    }
}

// ── Recording status capsule with waveform ────────────────────────────────────
@Composable
private fun RecordingStatusCapsule(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val barScales = (0..4).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                tween(380 + i * 70, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "bar$i"
        ).value
    }

    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.38f),
                RoundedCornerShape(20.dp)
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red recording dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFFE53935), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Waveform bars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(16.dp)
        ) {
            barScales.forEachIndexed { _, scale ->
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxSize(scale)
                        .align(Alignment.CenterVertically)
                        .background(Color(0xFFEF5350), RoundedCornerShape(1.dp))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "REC",
            color = Color(0xFFEF5350),
            fontSize = 10.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp
        )
    }
}

// ── Glassmorphism function bar ─────────────────────────────────────────────
@Composable
private fun GlassyFunctionBar(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isBluetooth: Boolean,
    bluetoothAvail: Boolean,
    isRecording: Boolean,
    isHeld: Boolean,
    showDialpad: Boolean,
    accentColor: Color,
    rawNumber: String,
    callDirection: CallDirection,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onToggleDialpad: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(24.dp)
            )
            .border(
                0.5.dp,
                Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(24.dp)
            )
            .padding(vertical = 16.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute
            PremiumIconButton(
                icon        = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label       = if (isMuted) "Unmute" else "Mute",
                active      = isMuted,
                activeColor = Color(0xFFEF5350),
                onClick     = onToggleMute
            )

            // Speaker with wave animation
            SpeakerButton(
                isActive    = isSpeaker,
                accentColor = accentColor,
                onClick     = onToggleSpeaker
            )

            // Record — transforms between record and stop
            RecordButton(
                isRecording = isRecording,
                onClick     = onToggleRecording
            )

            // Dialpad
            PremiumIconButton(
                icon        = Icons.Default.Dialpad,
                label       = "Keypad",
                active      = showDialpad,
                activeColor = accentColor,
                onClick     = onToggleDialpad
            )

            // Hold / Resume
            PremiumIconButton(
                icon        = if (isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                label       = if (isHeld) "Resume" else "Hold",
                active      = isHeld,
                activeColor = Color(0xFF66BB6A),
                onClick     = onToggleHold
            )
        }
    }
}

// ── Premium icon button with glow + haptic ──────────────────────────────────
@Composable
fun PremiumIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = Color(0xFF42A5F5),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "btnShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "shimmerOffset"
    )

    val bgColor = when {
        !enabled -> Color.White.copy(alpha = 0.04f)
        active   -> activeColor.copy(alpha = 0.22f)
        else     -> Color.White.copy(alpha = 0.09f)
    }
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.22f)
        active   -> activeColor
        else     -> Color.White.copy(alpha = 0.9f)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bgColor)
                .then(
                    if (active && enabled) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.18f),
                                        Color.Transparent
                                    ),
                                    start = Offset(size.width * (shimmerOffset - 0.5f), 0f),
                                    end   = Offset(size.width * (shimmerOffset + 0.5f), size.height)
                                )
                            )
                        }
                    } else Modifier
                )
                .border(
                    width = if (active) 1.dp else 0.dp,
                    color = if (active) activeColor.copy(alpha = 0.5f) else Color.Transparent,
                    shape = CircleShape
                )
                .pointerInput(enabled) {
                    detectTapGestures(onTap = {
                        if (enabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClick()
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            color = if (enabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.22f),
            fontSize = 10.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

// ── Speaker button with animated sound waves ─────────────────────────────────
@Composable
private fun SpeakerButton(
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "speakerWave")

    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseOutCubic)),
        label = "w1a"
    )
    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseOutCubic)),
        label = "w1s"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseOutCubic, delayMillis = 300)),
        label = "w2a"
    )
    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseOutCubic, delayMillis = 300)),
        label = "w2s"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp)
        ) {
            // Wave rings (only when active)
            if (isActive) {
                Canvas(modifier = Modifier.size(56.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension / 2f

                    drawCircle(
                        color  = accentColor.copy(alpha = wave1Alpha * 0.35f),
                        radius = baseRadius * wave1Scale,
                        center = center,
                        style  = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color  = accentColor.copy(alpha = wave2Alpha * 0.25f),
                        radius = baseRadius * wave2Scale,
                        center = center,
                        style  = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) accentColor.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.09f)
                    )
                    .border(
                        width = if (isActive) 1.dp else 0.dp,
                        color = if (isActive) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClick()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VolumeUp, "Speaker",
                    tint = if (isActive) accentColor else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Speaker",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

// ── Record button: hollow circle ↔ solid red square ──────────────────────────
@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "recShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "recShimmerOffset"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) Color(0xFFE53935).copy(alpha = 0.22f)
                    else Color.White.copy(alpha = 0.09f)
                )
                .then(
                    if (isRecording) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.22f),
                                        Color.Transparent
                                    ),
                                    start = Offset(size.width * (shimmerOffset - 0.5f), 0f),
                                    end   = Offset(size.width * (shimmerOffset + 0.5f), size.height)
                                )
                            )
                        }
                    } else Modifier
                )
                .border(
                    width = if (isRecording) 1.dp else 1.dp,
                    color = if (isRecording) Color(0xFFE53935).copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.25f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(200)))
                },
                label = "recordState"
            ) { recording ->
                if (recording) {
                    // Solid red rounded square = "stop recording"
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFE53935), RoundedCornerShape(3.dp))
                    )
                } else {
                    // Hollow circle with red border = "start recording"
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val r = size.minDimension / 2f
                        drawCircle(
                            color  = Color(0xFFEF5350).copy(alpha = 0.9f),
                            radius = r,
                            style  = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (isRecording) "Stop" else "Record",
            color = if (isRecording) Color(0xFFEF5350) else Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

// ── Spam score bar ────────────────────────────────────────────────────────────
@Composable
private fun SpamScoreBar(score: Int, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "SPAM RISK",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Text(
                "$score / 100",
                color = accentColor,
                fontSize = 10.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score / 100f)
                    .height(3.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ── Premium answer/decline buttons (ringing) ─────────────────────────────────
@Composable
fun PremiumCallActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(color.copy(alpha = 0.12f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClick()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ── Glassy DTMF dialpad ───────────────────────────────────────────────────────
@Composable
fun GlassDialpadPanel(
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
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(top = 10.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(18.dp))

        // Digit display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = dtmfDigits.ifEmpty { " " },
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 48.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (dtmfDigits.isNotEmpty()) 1f else 0.22f)
            ) {
                Text("⌫", color = Color.White, fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Key grid
        DTMF_KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    GlassDialpadKey(key = key, onKeyDown = onKeyDown, onKeyUp = onKeyUp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Close
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
                .padding(horizontal = 36.dp, vertical = 10.dp)
        ) {
            Text(
                "Close Keypad",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun GlassDialpadKey(key: DtmfKey, onKeyDown: (Char) -> Unit, onKeyUp: () -> Unit) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(
                if (pressed) Color.White.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.07f)
            )
            .border(0.5.dp, Color.White.copy(alpha = if (pressed) 0.3f else 0.1f), CircleShape)
            .pointerInput(key.digit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
            Text(
                key.main,
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Light
            )
            if (key.sub.isNotEmpty()) {
                Text(
                    key.sub,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 8.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

// ── Keep old public names usable by any callers outside this file ─────────────
@Composable
fun LargeCallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) =
    PremiumCallActionButton(icon = icon, label = label, color = color, onClick = onClick)

@Composable
fun RoundControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = Color(0xFF42A5F5),
    enabled: Boolean = true,
    onClick: () -> Unit
) = PremiumIconButton(icon = icon, label = label, active = active, activeColor = activeColor, enabled = enabled, onClick = onClick)

@Composable
fun DialpadPanel(
    dtmfDigits: String,
    onKeyDown: (Char) -> Unit,
    onKeyUp: () -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit
) = GlassDialpadPanel(
    dtmfDigits = dtmfDigits,
    onKeyDown  = onKeyDown,
    onKeyUp    = onKeyUp,
    onBackspace = onBackspace,
    onClose    = onClose
)
