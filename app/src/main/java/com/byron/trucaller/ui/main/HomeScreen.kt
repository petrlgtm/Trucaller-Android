package com.byron.trucaller.ui.main

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.CardGradientEnd
import com.byron.trucaller.ui.theme.CardGradientStart
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.delay

data class ActivityItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val color: Color
)

@Composable
fun HomeScreen(
    rootNavController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    contactsViewModel: ContactsViewModel,
    alarmViewModel: AlarmViewModel,
    stolenReportViewModel: StolenReportViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    LaunchedEffect(user.id) {
        deviceViewModel.loadUserDevice(user.id)
    }

    val userDevice by deviceViewModel.userDevice.collectAsState()

    val ipLogs by remember(userDevice?.id) {
        userDevice?.let { deviceViewModel.getIpLogs(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val alarmLogs by remember(userDevice?.id) {
        userDevice?.let { alarmViewModel.getLogsByDevice(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val stolenReports by remember(userDevice?.id) {
        userDevice?.let { stolenReportViewModel.getReportsByDevice(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val syncMessage by contactsViewModel.syncMessage.collectAsState()

    val context = LocalContext.current

    // Staggered animations
    var showHeader by remember { mutableStateOf(false) }
    var showDevice by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHeader = true
        delay(100)
        showDevice = true
        delay(100)
        showActions = true
        delay(100)
        showActivity = true
    }

    // Location permission — re-register device with GPS after granted
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            // Re-register device to update location from "Unknown" to actual GPS
            deviceViewModel.refreshDeviceLocation(user.id)
        }
    }

    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsViewModel.syncContacts(user.id)
    }

    // Google Drive sign-in launcher for backup
    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            contactsViewModel.syncToGoogleDrive()
        }
    }

    val activities = remember(ipLogs, alarmLogs, stolenReports) {
        val items = mutableListOf<ActivityItem>()
        ipLogs.forEach { log ->
            items.add(ActivityItem(log.id, "ip", "IP Location Update", "${log.city}, ${log.country} - ${log.isp}", log.timestamp, Brand))
        }
        stolenReports.forEach { report ->
            items.add(ActivityItem(report.id, "stolen", "Stolen Report", report.description.take(60) + "...", report.reportedAt, Danger))
        }
        alarmLogs.forEach { alarm ->
            items.add(ActivityItem(alarm.id, "alarm", "Alarm: ${alarm.type.name.replace("_", " ")}", "Result: ${alarm.result.name} - ${alarm.notes ?: ""}", alarm.triggeredAt, Warning))
        }
        items.sortedByDescending { it.timestamp }.take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Gradient header ──────────────────────────────────────────
        AnimatedVisibility(
            visible = showHeader,
            enter = fadeIn(tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CardGradientStart, CardGradientEnd)
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Welcome back,", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(
                            user.fullName,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    // Uganda flag accent
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .background(BrandDark, RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .background(Brand, RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .background(Accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Sync message snackbar
            if (syncMessage != null) {
                val needsDriveSignIn = !contactsViewModel.isDriveSignedIn()
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        if (needsDriveSignIn) {
                            TextButton(onClick = {
                                driveSignInLauncher.launch(contactsViewModel.getDriveSignInIntent())
                                contactsViewModel.clearSyncMessage()
                            }) {
                                Text("SIGN IN", color = Brand)
                            }
                        } else {
                            TextButton(onClick = { contactsViewModel.clearSyncMessage() }) {
                                Text("OK", color = Color.White)
                            }
                        }
                    }
                ) {
                    Text(syncMessage!!)
                }
            }

            // ── Device status card ───────────────────────────────────
            AnimatedVisibility(
                visible = showDevice,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(350))
            ) {
                if (userDevice != null) {
                    val device = userDevice!!
                    val statusColor = when (device.status) {
                        DeviceStatus.ACTIVE -> Success
                        DeviceStatus.STOLEN -> Danger
                        DeviceStatus.INACTIVE -> Inactive
                        DeviceStatus.FLAGGED -> Warning
                    }
                    val isStolenOrFlagged = device.status == DeviceStatus.STOLEN || device.status == DeviceStatus.FLAGGED

                    if (isStolenOrFlagged) {
                        // Compact oval stolen/flagged badge
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0000)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(statusColor.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning, null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.manufacturer} ${device.model}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        device.status.name,
                                        color = statusColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.25f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        device.status.name,
                                        color = statusColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    } else {
                        // Normal active device card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PhoneAndroid, null,
                                        tint = statusColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.manufacturer} ${device.model}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        device.status.name,
                                        color = statusColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        device.status.name,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── Quick Actions ────────────────────────────────────────
            AnimatedVisibility(
                visible = showActions,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(400))
            ) {
                Column {
                    Text(
                        "Quick Actions",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Report,
                            label = "Report\nStolen",
                            color = Danger,
                            onClick = { rootNavController.navigate("report_stolen") }
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Sync,
                            label = "Sync\nContacts",
                            color = BrandGold,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                                    == PackageManager.PERMISSION_GRANTED) {
                                    contactsViewModel.syncContacts(user.id)
                                } else {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.LocationOn,
                            label = "View\nIP Log",
                            color = Success,
                            onClick = { rootNavController.navigate("remote_actions") }
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Alarm,
                            label = "Trigger\nAlarm",
                            color = Accent,
                            onClick = { rootNavController.navigate("remote_actions") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Recent Activity ──────────────────────────────────────
            AnimatedVisibility(
                visible = showActivity,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(450))
            ) {
                Column {
                    Text(
                        "Recent Activity",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (activities.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Text(
                                "No recent activity",
                                color = TextSecondary,
                                modifier = Modifier.padding(24.dp),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column {
                                activities.forEachIndexed { index, activity ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(activity.color.copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val icon = when (activity.type) {
                                                "ip" -> Icons.Default.LocationOn
                                                "stolen" -> Icons.Default.Warning
                                                "alarm" -> Icons.Default.Alarm
                                                else -> Icons.Default.Shield
                                            }
                                            Icon(icon, null, tint = activity.color, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                activity.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                activity.subtitle,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            formatRelativeTime(activity.timestamp),
                                            fontSize = 11.sp,
                                            color = Inactive
                                        )
                                    }
                                    if (index < activities.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 70.dp)
                                                .height(0.5.dp)
                                                .background(Color(0xFF333333))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.1.sp
            )
        }
    }
}
