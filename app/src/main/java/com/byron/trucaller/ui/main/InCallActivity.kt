package com.byron.trucaller.ui.main

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.VideoProfile
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
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

        // Show over lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Initialize Proximity WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Trucaller:InCallProximity"
            )
        }
        
        setContent {
            TruCallerTheme {
                // Manage proximity lock lifecycle
                val call by CustomInCallService.activeCall.collectAsState()
                DisposableEffect(call?.state) {
                    if (call?.state == Call.STATE_ACTIVE) {
                        if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire()
                    } else {
                        if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                    }
                    onDispose {
                        if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                    }
                }

                InCallContent(
                    onAnswer = { c -> c.answer(VideoProfile.STATE_AUDIO_ONLY) },
                    onDecline = { c -> c.reject(false, null) },
                    onHangUp = { c -> c.disconnect() },
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
    }
}

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
    val colorScheme = MaterialTheme.colorScheme
    
    var callerInfo by remember { mutableStateOf<CallerIdEntry?>(null) }
    var isCallEnded by remember { mutableStateOf(false) }
    var lookupSource by remember { mutableStateOf<String?>(null) }

    // identity lookup
    LaunchedEffect(call) {
        if (call != null) {
            val handle = try { call!!.details.handle } catch (_: Exception) { null }
            val number = handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: ""
            if (number.isNotEmpty()) {
                val app = context.applicationContext as TruCallerApplication
                val repo = app.container.callerIdRepository
                val result = repo.lookupNumberLocal(number)
                callerInfo = result.callerIdEntry
                lookupSource = result.source
            }
        } else {
            if (!isCallEnded) {
                isCallEnded = true
                delay(1500)
                onFinish()
            }
        }
    }

    if (call == null && !isCallEnded) return

    val state = if (isCallEnded) Call.STATE_DISCONNECTED else call?.state ?: Call.STATE_DISCONNECTED
    val handle = try { call?.details?.handle } catch (_: Exception) { null }
    val rawPhoneNumber = handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } 
        ?: callerInfo?.phoneNumber?.takeIf { it.isNotBlank() } 
        ?: ""
    
    val phoneNumber = when {
        rawPhoneNumber.isEmpty() -> "Private Number"
        rawPhoneNumber == "-1" -> "Unknown"
        rawPhoneNumber == "-2" -> "Voicemail"
        else -> rawPhoneNumber
    }
    
    val displayName = callerInfo?.name?.takeIf { it.isNotBlank() } 
        ?: if (phoneNumber != "Private Number" && phoneNumber != "Unknown") phoneNumber else "Unknown Caller"
        
    val isSpam = callerInfo?.category == SpamCategory.SPAM || (callerInfo?.spamScore ?: 0) > 60
    val isIdentifiedByApp = lookupSource == "caller_id_db"

    var durationSeconds by remember { mutableStateOf(0L) }
    
    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000)
                durationSeconds++
            }
        }
    }

    val durationText = when (state) {
        Call.STATE_ACTIVE -> {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        }
        Call.STATE_RINGING -> "Incoming Call"
        Call.STATE_DIALING -> "Dialing..."
        Call.STATE_CONNECTING -> "Connecting..."
        Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Call Active"
    }

    val isMuted = audioState?.isMuted ?: false
    val isSpeaker = (audioState?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER

    // Pulse animation for ringing
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == Call.STATE_RINGING) 1.15f else 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val backgroundBrush = if (isSpam) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color(0xFF2E0000), Color(0xFF630000), Color(0xFF2E0000))
        )
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color(0xFF0F1724), Color(0xFF1E293B), Color(0xFF0F1724))
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp, bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Upper: Identity
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Background pulse circle
                        if (state == Call.STATE_RINGING) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSpam) Color.Red.copy(alpha = 0.2f) 
                                        else colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                    .scale(pulseScale)
                            )
                        }
                        
                        TruCallerAvatar(
                            name = displayName,
                            size = 140.dp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    if (isIdentifiedByApp && !isSpam) {
                        TruCallerBadge(
                            text = "Verified Identity",
                            type = BadgeType.Success,
                            icon = Icons.Default.Shield
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (isSpam) {
                        Text(
                            text = "POTENTIAL SPAM",
                            color = Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = displayName,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp
                    )
                    
                    Text(
                        text = phoneNumber,
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Surface(
                        color = if (isSpam) Color.Red.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                        shape = CircleShape,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = durationText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSpam) Color.Red else colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }

                // Lower: Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state == Call.STATE_RINGING) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CallButton(
                                icon = Icons.Default.CallEnd,
                                label = "Decline",
                                color = Color(0xFFEB002B),
                                onClick = { call?.let { onDecline(it) } }
                            )
                            CallButton(
                                icon = Icons.Default.Call,
                                label = "Answer",
                                color = Color(0xFF24B024),
                                onClick = { call?.let { onAnswer(it) } }
                            )
                        }
                    } else if (state != Call.STATE_DISCONNECTED) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SmallControlItem(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Muted" else "Mute",
                                active = isMuted,
                                onClick = { 
                                    CustomInCallService.toggleMute(isMuted)
                                }
                            )
                            SmallControlItem(
                                icon = Icons.Default.VolumeUp,
                                label = "Speaker",
                                active = isSpeaker,
                                onClick = { 
                                    CustomInCallService.toggleSpeaker(isSpeaker)
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        IconButton(
                            onClick = { onHangUp(call!!) },
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEB002B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "End Call", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun SmallControlItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}
