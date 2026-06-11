package com.byron.trucaller.ui.stolen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class PendingRemoteAction { ALARM, LOCK, LOCATION, WIPE }

private val SuccessColor = Color(0xFF4CAF50)
private val WarningColor = BrandGold

private val TIMELINE_DOT_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1565C0), Color(0xFFFB8C00), Color(0xFF43A047),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF5E35B1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteActionsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel,
    alarmViewModel: AlarmViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var alarmLoading by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var lockLoading by remember { mutableStateOf(false) }
    var wipeLoading by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showAlarmConfirm by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showWipeConfirm1 by remember { mutableStateOf(false) }
    var showWipeConfirm2 by remember { mutableStateOf(false) }
    var showRecoverConfirm by remember { mutableStateOf(false) }
    var showRecoverSuccess by remember { mutableStateOf(false) }
    var recoverLoading by remember { mutableStateOf(false) }

    // PIN verification state
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinFailedAttempts by remember { mutableIntStateOf(0) }
    var pinCooldownUntil by remember { mutableLongStateOf(0L) }
    var pendingAction by remember { mutableStateOf<PendingRemoteAction?>(null) }
    var showNoPinMessage by remember { mutableStateOf(false) }

    // Cooldown ticker
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pinCooldownUntil) {
        while (pinCooldownUntil > 0 && System.currentTimeMillis() < pinCooldownUntil) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
        currentTime = System.currentTimeMillis()
    }
    val pinCooldownActive = pinCooldownUntil > currentTime

    val hasPin = authViewModel.hasSecurityPin()

    val alarmPlaying by alarmViewModel.alarmPlaying.collectAsState()
    val alarmSentDeviceId by alarmViewModel.alarmSentDeviceId.collectAsState()
    val actionMessage by alarmViewModel.actionMessage.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val user = authState.user

    val device by deviceViewModel.userDevice.collectAsState()

    /** Gate an action behind PIN verification */
    fun requirePin(action: PendingRemoteAction) {
        if (!hasPin) {
            showNoPinMessage = true
            return
        }
        pendingAction = action
        pinInput = ""
        pinError = null
        showPinDialog = true
    }

    /** Called when PIN is submitted */
    fun onPinSubmit() {
        if (pinCooldownActive) return
        if (pinInput.length != 4) {
            pinError = "PIN must be 4 digits"
            return
        }
        if (authViewModel.verifySecurityPin(pinInput)) {
            // Success — execute the pending action
            showPinDialog = false
            pinFailedAttempts = 0
            pinInput = ""
            pinError = null
            when (pendingAction) {
                PendingRemoteAction.ALARM -> showAlarmConfirm = true
                PendingRemoteAction.LOCK -> showLockConfirm = true
                PendingRemoteAction.WIPE -> showWipeConfirm1 = true
                PendingRemoteAction.LOCATION -> {
                    val currentUser = user ?: return
                    val currentDevice = device ?: return
                    locationLoading = true
                    alarmViewModel.requestLocation(
                        deviceId = currentDevice.id,
                        triggeredBy = currentUser.id,
                        triggeredByName = currentUser.fullName,
                        triggeredByRole = "owner"
                    )
                }
                null -> {}
            }
            pendingAction = null
        } else {
            pinFailedAttempts++
            pinInput = ""
            if (pinFailedAttempts >= 3) {
                pinCooldownUntil = System.currentTimeMillis() + 30_000L
                pinError = "Too many attempts. Try again in 30 seconds."
            } else {
                pinError = "Incorrect PIN (${3 - pinFailedAttempts} attempts remaining)"
            }
        }
    }

    LaunchedEffect(user?.id) {
        user?.id?.let { deviceViewModel.loadUserDevice(it) }
    }

    val deviceIpLogs by remember(device?.id) {
        device?.id?.let { deviceViewModel.getIpLogs(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val sortedIpLogs = remember(deviceIpLogs) {
        deviceIpLogs.sortedByDescending { it.timestamp }.take(3)
    }
    val lastIpLog = sortedIpLogs.firstOrNull()

    val stolenReports by remember(device?.id) {
        device?.id?.let { stolenReportViewModel.getReportsByDevice(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val stolenReport = stolenReports.firstOrNull()

    val reportDate = stolenReport?.let {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(it.reportedAt)
            SimpleDateFormat("d MMM yyyy", Locale.US).format(date!!)
        } catch (_: Exception) { "Unknown" }
    } ?: "Unknown"

    // Pulsing animation for alarm active banner
    val alarmPulseTransition = rememberInfiniteTransition(label = "alarmPulse")
    val alarmPulseScale by alarmPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmScale"
    )
    val alarmPulseAlpha by alarmPulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmAlpha"
    )

    // Pulsing animation for STOLEN badge
    val stolenBadgeTransition = rememberInfiniteTransition(label = "stolenBadge")
    val stolenBadgeScale by stolenBadgeTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stolenBadgeScale"
    )
    val stolenBadgeAlpha by stolenBadgeTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stolenBadgeAlpha"
    )

    // Shield glow animation
    val shieldGlowTransition = rememberInfiniteTransition(label = "shieldGlow")
    val shieldGlowAlpha by shieldGlowTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldGlowAlpha"
    )

    // Map visibility state for fade-in animation
    var mapVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lastIpLog) {
        if (lastIpLog != null && lastIpLog.latitude != 0.0 && lastIpLog.longitude != 0.0) {
            delay(200)
            mapVisible = true
        }
    }

    // Auto-clear action message and stop location loading when request completes
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            locationLoading = false
            kotlinx.coroutines.delay(3000)
            alarmViewModel.clearActionMessage()
        }
    }

    // PIN verification dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pendingAction = null
                pinInput = ""
                pinError = null
            },
            title = { Text("Security PIN Required") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Enter your 4-digit security PIN to proceed.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    // 4-digit PIN input boxes
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = Color.Transparent),
                        enabled = !pinCooldownActive,
                        decorationBox = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(4) { index ->
                                    val char = pinInput.getOrNull(index)?.toString() ?: ""
                                    val isFocused = pinInput.length == index
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .border(
                                                if (isFocused) 2.dp else 1.5.dp,
                                                if (isFocused || char.isNotEmpty()) colorScheme.primary else colorScheme.outlineVariant,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .background(
                                                colorScheme.surfaceContainerHighest,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (char.isNotEmpty()) "\u2022" else "",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    )
                    if (pinCooldownActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val remaining = ((pinCooldownUntil - currentTime) / 1000).coerceAtLeast(0)
                        Text(
                            "Too many attempts. Try again in ${remaining}s.",
                            color = colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (pinError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(pinError!!, color = colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onPinSubmit() },
                    enabled = pinInput.length == 4 && !pinCooldownActive
                ) { Text("Verify") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pendingAction = null
                    pinInput = ""
                    pinError = null
                }) { Text("Cancel") }
            }
        )
    }
    // No PIN set message
    if (showNoPinMessage) {
        AlertDialog(
            onDismissRequest = { showNoPinMessage = false },
            title = { Text("No Security PIN Set") },
            text = { Text("You need to set a security PIN before using remote actions. Go to Security settings to set one.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoPinMessage = false
                    navController.navigate("security")
                }) { Text("Go to Security") }
            },
            dismissButton = {
                TextButton(onClick = { showNoPinMessage = false }) { Text("Cancel") }
            }
        )
    }

    // Dialogs
    if (showAlarmConfirm) {
        AlertDialog(
            onDismissRequest = { showAlarmConfirm = false },
            title = { Text("Trigger Remote Alarm") },
            text = { Text("This will sound a loud alarm on the device, even if it is on silent mode.") },
            confirmButton = {
                TextButton(onClick = {
                    showAlarmConfirm = false
                    val currentUser = user ?: return@TextButton
                    val currentDevice = device ?: return@TextButton
                    alarmLoading = true
                    scope.launch {
                        alarmViewModel.triggerAlarm(
                            deviceId = currentDevice.id,
                            triggeredBy = currentUser.id,
                            triggeredByName = currentUser.fullName,
                            triggeredByRole = "owner"
                        )
                        alarmLoading = false
                        showAlarmDialog = true
                    }
                }) { Text("Trigger Alarm", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Alarm Triggered") },
            text = { Text("Alarm triggered successfully! The device should now be sounding an alert.") },
            confirmButton = {
                TextButton(onClick = { showAlarmDialog = false }) { Text("OK") }
            }
        )
    }
    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("Lock Device") },
            text = { Text("This will immediately lock the device screen. Device Admin permission is required.") },
            confirmButton = {
                TextButton(onClick = {
                    showLockConfirm = false
                    val currentUser = user ?: return@TextButton
                    val currentDevice = device ?: return@TextButton
                    lockLoading = true
                    alarmViewModel.lockDevice(
                        deviceId = currentDevice.id,
                        triggeredBy = currentUser.id,
                        triggeredByName = currentUser.fullName,
                        triggeredByRole = "owner"
                    )
                    lockLoading = false
                }) { Text("Lock Now", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showRecoverConfirm) {
        AlertDialog(
            onDismissRequest = { showRecoverConfirm = false },
            title = { Text("Mark as Recovered") },
            text = { Text("Are you sure? This will remove the stolen flag and stop tracking escalation.") },
            confirmButton = {
                TextButton(onClick = {
                    showRecoverConfirm = false
                    val currentDevice = device ?: return@TextButton
                    recoverLoading = true
                    scope.launch {
                        stolenReportViewModel.markDeviceRecovered(currentDevice.id)
                        recoverLoading = false
                        showRecoverSuccess = true
                    }
                }) { Text("Mark Recovered", color = SuccessColor) }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showRecoverSuccess) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Device Recovered") },
            text = { Text("Your device has been marked as recovered. Glad you got it back!") },
            confirmButton = {
                TextButton(onClick = {
                    showRecoverSuccess = false
                    navController.popBackStack()
                }) { Text("OK") }
            }
        )
    }

    // Wipe — first confirmation
    if (showWipeConfirm1) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm1 = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, null, tint = colorScheme.error, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wipe Device?", color = colorScheme.error)
                }
            },
            text = { Text("This will permanently erase ALL data on the device — apps, photos, contacts, and settings. The device will be reset to factory defaults.\n\nThis action CANNOT be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm1 = false
                    showWipeConfirm2 = true
                }) { Text("Continue", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm1 = false }) { Text("Cancel") }
            }
        )
    }

    // Wipe — final confirmation
    if (showWipeConfirm2) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm2 = false },
            title = { Text("Last Chance — Are you sure?", color = colorScheme.error) },
            text = { Text("Confirming will immediately send a factory reset command to the device. All data will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm2 = false
                    val currentUser = user ?: return@TextButton
                    val currentDevice = device ?: return@TextButton
                    wipeLoading = true
                    scope.launch {
                        alarmViewModel.wipeDevice(
                            deviceId = currentDevice.id,
                            triggeredBy = currentUser.id,
                            triggeredByName = currentUser.fullName,
                            triggeredByRole = "owner"
                        )
                        wipeLoading = false
                    }
                }) { Text("WIPE DEVICE", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm2 = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            // Banner with gradient
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.error.copy(alpha = 0.85f),
                                colorScheme.error.copy(red = colorScheme.error.red * 0.7f, green = 0f, blue = 0f)
                            )
                        )
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Compact shield icon with glow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .alpha(shieldGlowAlpha)
                                .background(colorScheme.onError.copy(alpha = 0.15f), CircleShape)
                        )
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Stolen device",
                            tint = colorScheme.onError,
                            modifier = Modifier.size(48.dp)
                        )
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(18.dp).align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Pulsing STOLEN badge — compact
                        Row(
                            modifier = Modifier
                                .scale(stolenBadgeScale)
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = colorScheme.onError, modifier = Modifier.size(12.dp))
                            Text("STOLEN", color = colorScheme.onError, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            device?.let { "${it.manufacturer} ${it.model}" } ?: "Loading...",
                            color = colorScheme.onError, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        Text("Reported $reportDate", color = colorScheme.onError.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // Pulsing alarm active banner (local alarm OR remotely-triggered alarm)
                val deviceId = device?.id
                if (alarmPlaying || (deviceId != null && alarmSentDeviceId == deviceId)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .scale(alarmPulseScale)
                            .alpha(alarmPulseAlpha)
                            .background(colorScheme.error, RoundedCornerShape(BorderRadius.md))
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Alarm, null, tint = colorScheme.onError, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ALARM ACTIVE", color = colorScheme.onError, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Alarm is sounding on device", color = colorScheme.onError.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    if (deviceId != null) alarmViewModel.stopRemoteAlarm(deviceId)
                                    else alarmViewModel.stopAlarm()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.onError)
                            ) {
                                Text("STOP", color = colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Remote Actions — 3 compact cards
                TruCallerCard(
                    cornerRadius = 20.dp,
                    containerColor = colorScheme.error.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Alarm, null, tint = colorScheme.error, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trigger Alarm", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorScheme.onSurface)
                            Text("Loud alarm, even on silent", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        TruCallerButton(
                            text = "Trigger",
                            onClick = { requirePin(PendingRemoteAction.ALARM) },
                            style = TruCallerButtonStyle.Danger,
                            enabled = !alarmLoading && device != null && user != null,
                            isLoading = alarmLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TruCallerCard(
                    cornerRadius = 20.dp,
                    containerColor = colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Device", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorScheme.onSurface)
                            Text("Immediately lock the screen", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        TruCallerButton(
                            text = "Lock",
                            onClick = { requirePin(PendingRemoteAction.LOCK) },
                            style = TruCallerButtonStyle.Primary,
                            enabled = !lockLoading && device != null && user != null,
                            isLoading = lockLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TruCallerCard(
                    cornerRadius = 20.dp,
                    containerColor = colorScheme.tertiary.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GpsFixed, null, tint = colorScheme.tertiary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Request Location", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorScheme.onSurface)
                            Text("Refresh IP-based location", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        TruCallerButton(
                            text = "Refresh",
                            onClick = { requirePin(PendingRemoteAction.LOCATION) },
                            style = TruCallerButtonStyle.Primary,
                            enabled = !locationLoading && device != null && user != null,
                            isLoading = locationLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TruCallerCard(
                    cornerRadius = 20.dp,
                    containerColor = colorScheme.error.copy(alpha = 0.06f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DeleteForever, null, tint = colorScheme.error, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wipe Device", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorScheme.error)
                            Text("Factory reset — erases all data", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        TruCallerButton(
                            text = "Wipe",
                            onClick = { requirePin(PendingRemoteAction.WIPE) },
                            style = TruCallerButtonStyle.Danger,
                            enabled = !wipeLoading && device != null && user != null,
                            isLoading = wipeLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Last Known Location card
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Last Known Location", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorScheme.onSurface)
                    }

                    if (lastIpLog != null) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.background, RoundedCornerShape(BorderRadius.md))
                                .padding(Spacing.md)
                        ) {
                            Column {
                                LocationRow("City", "${lastIpLog.city}, ${lastIpLog.country}")
                                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = Spacing.sm))
                                LocationRow("ISP", lastIpLog.isp)
                                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = Spacing.sm))
                                LocationRow("IP Address", lastIpLog.ipAddress, mono = true)
                                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = Spacing.sm))
                                LocationRow("Last Seen", formatRelativeTime(lastIpLog.timestamp))
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text("No IP data available", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }

                    // Embedded Map View with fade-in + zoom animation
                    Spacer(modifier = Modifier.height(Spacing.md))
                    if (lastIpLog != null && lastIpLog.latitude != 0.0 && lastIpLog.longitude != 0.0) {
                        val lat = lastIpLog.latitude
                        val lon = lastIpLog.longitude

                        AnimatedVisibility(
                            visible = mapVisible,
                            enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.95f, animationSpec = tween(600))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(BorderRadius.md))
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                                        org.osmdroid.views.MapView(ctx).apply {
                                            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                                            setMultiTouchControls(true)
                                            controller.setZoom(14.0)
                                            controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                                            // Add marker
                                            val marker = org.osmdroid.views.overlay.Marker(this)
                                            marker.position = org.osmdroid.util.GeoPoint(lat, lon)
                                            marker.setAnchor(
                                                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                                            )
                                            marker.title = "Last Known Location"
                                            marker.snippet = "${lastIpLog.city}, ${lastIpLog.country}"
                                            overlays.add(marker)
                                        }
                                    },
                                    update = { mapView ->
                                        mapView.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                                        mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>()
                                            .firstOrNull()?.position = org.osmdroid.util.GeoPoint(lat, lon)
                                        mapView.invalidate()
                                    }
                                )
                                // Gradient overlay for visual integration
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    colorScheme.surface.copy(alpha = 0.6f)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Open in Google Maps button — filled style
                        Button(
                            onClick = {
                                val geoUri = Uri.parse(
                                    "geo:$lat,$lon?q=$lat,$lon(Last+Known+Location)"
                                )
                                val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.google.com/maps?q=$lat,$lon")
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Open in Google Maps", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(colorScheme.background, RoundedCornerShape(BorderRadius.md)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No coordinates available", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // IP Trail card with animated timeline
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timeline, null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("IP Trail Since Report", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (sortedIpLogs.isEmpty()) {
                        Text("No IP trail data available", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        sortedIpLogs.forEachIndexed { index, log ->
                            val dotColor = TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]
                            val isLast = index == sortedIpLogs.lastIndex

                            // Sequential dot animation: each dot fades in with a staggered delay
                            val dotAlpha = remember { Animatable(0f) }
                            LaunchedEffect(log.timestamp) {
                                delay(index * 120L)
                                dotAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 400)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(dotAlpha.value)
                            ) {
                                // Timeline left column with Canvas connecting line
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        // Ring-style dot with index number
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .border(2.dp, dotColor, CircleShape)
                                                .background(Color.Transparent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${index + 1}",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = dotColor,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        // Dashed connecting line drawn with Canvas
                                        if (!isLast) {
                                            Canvas(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(56.dp)
                                            ) {
                                                val lineColor = colorScheme.primary
                                                val dashEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                                    0f
                                                )
                                                drawLine(
                                                    color = lineColor.copy(alpha = 0.4f),
                                                    start = Offset(size.width / 2, 0f),
                                                    end = Offset(size.width / 2, size.height),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                    pathEffect = dashEffect
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                // Content
                                Column(modifier = Modifier.weight(1f).padding(bottom = Spacing.md)) {
                                    Text(
                                        log.ipAddress,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        "${log.isp} \u2022 ${log.city}",
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(formatRelativeTime(log.timestamp), fontSize = 12.sp, color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        val isWifi = log.networkType == "wifi"
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isWifi) SuccessColor.copy(alpha = 0.1f) else WarningColor.copy(alpha = 0.1f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = Spacing.sm, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower,
                                                    null,
                                                    tint = if (isWifi) SuccessColor else WarningColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    if (isWifi) "WiFi" else "Mobile",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isWifi) SuccessColor else WarningColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manage Geofences button
                TruCallerButton(
                    text = "Manage Geofences",
                    onClick = {
                        val currentDevice = device ?: return@TruCallerButton
                        navController.navigate("geofence_management/${currentDevice.id}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = TruCallerButtonStyle.Primary,
                    enabled = device != null,
                    leadingIcon = Icons.Default.FmdGood,
                    iconSize = 22.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Network Forensics button
                TruCallerButton(
                    text = "Network Forensics",
                    onClick = {
                        val currentDevice = device ?: return@TruCallerButton
                        navController.navigate("network_forensics/${currentDevice.id}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = TruCallerButtonStyle.Primary,
                    enabled = device != null,
                    leadingIcon = Icons.Default.Timeline,
                    iconSize = 22.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mark as Recovered button — Success-colored outlined style
                OutlinedButton(
                    onClick = { showRecoverConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !recoverLoading && device != null,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SuccessColor
                    ),
                    border = BorderStroke(1.5.dp, SuccessColor),
                    shape = RoundedCornerShape(BorderRadius.md)
                ) {
                    if (recoverLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = SuccessColor
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = SuccessColor
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    }
                    Text(
                        "Mark as Recovered",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = SuccessColor
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }

        // Snackbar for action messages
        actionMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.md),
                action = {
                    TextButton(onClick = { alarmViewModel.clearActionMessage() }) {
                        Text("OK", color = colorScheme.onError)
                    }
                }
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun LocationRow(label: String, value: String, mono: Boolean = false) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
