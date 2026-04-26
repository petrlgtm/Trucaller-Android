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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
            val number = handle?.schemeSpecificPart ?: ""
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
    val phoneNumber = handle?.schemeSpecificPart ?: callerInfo?.phoneNumber ?: "Unknown"
    val displayName = callerInfo?.name ?: if (phoneNumber != "Unknown") phoneNumber else "Unknown Caller"
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isSpam) Color(0xFF450000) else Color(0xFF0F1724)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper: Identity
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TruCallerAvatar(
                    name = displayName,
                    size = 140.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isIdentifiedByApp && !isSpam) {
                    TruCallerBadge(
                        text = "Identified by Trucaller",
                        type = BadgeType.Success,
                        icon = Icons.Default.Shield
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isSpam) {
                    Text(
                        text = "POTENTIAL SPAM",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = displayName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                
                Text(
                    text = phoneNumber,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = durationText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSpam) Color.Red else colorScheme.primary
                )
            }

            // Lower: Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state == Call.STATE_RINGING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            label = "Reject",
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
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmallControlItem(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Unmute" else "Mute",
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
                    
                    CallButton(
                        icon = Icons.Default.CallEnd,
                        label = "End",
                        color = Color(0xFFEB002B),
                        onClick = { onHangUp(call!!) }
                    )
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
