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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ActivityItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
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
    val colorScheme = MaterialTheme.colorScheme

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

    // Pull-to-refresh state
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

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

    // Location permission -- re-register device with GPS after granted
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
            items.add(ActivityItem(log.id, "ip", "IP Location Update", "${log.city}, ${log.country} - ${log.isp}", log.timestamp, colorScheme.primary))
        }
        stolenReports.forEach { report ->
            items.add(ActivityItem(report.id, "stolen", "Stolen Report", report.description.take(60) + "...", report.reportedAt, colorScheme.error))
        }
        alarmLogs.forEach { alarm ->
            items.add(ActivityItem(alarm.id, "alarm", "Alarm: ${alarm.type.name.replace("_", " ")}", "Result: ${alarm.result.name} - ${alarm.notes ?: ""}", alarm.triggeredAt, colorScheme.tertiary))
        }
        items.sortedByDescending { it.timestamp }.take(10)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                deviceViewModel.loadUserDevice(user.id)
                delay(800)
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // -- Gradient header using TruCallerHeader --
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(400))
            ) {
                TruCallerHeader(
                    title = user.fullName,
                    subtitle = "Welcome back,",
                    gradientColors = listOf(colorScheme.surface, colorScheme.background),
                    titleColor = colorScheme.onSurface,
                    subtitleColor = colorScheme.onSurface.copy(alpha = 0.7f),
                    horizontalPadding = Spacing.lg,
                    verticalPadding = Spacing.lg,
                    trailingContent = {
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
                )
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                // Sync message snackbar
                if (syncMessage != null) {
                    val needsDriveSignIn = !contactsViewModel.isDriveSignedIn()
                    Snackbar(
                        modifier = Modifier.padding(bottom = Spacing.sm),
                        action = {
                            if (needsDriveSignIn) {
                                TextButton(onClick = {
                                    driveSignInLauncher.launch(contactsViewModel.getDriveSignInIntent())
                                    contactsViewModel.clearSyncMessage()
                                }) {
                                    Text("SIGN IN", color = colorScheme.primary)
                                }
                            } else {
                                TextButton(onClick = { contactsViewModel.clearSyncMessage() }) {
                                    Text("OK", color = colorScheme.onSurface)
                                }
                            }
                        }
                    ) {
                        Text(syncMessage!!)
                    }
                }

                // -- Device status card using TruCallerCard --
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
                            DeviceStatus.ACTIVE -> colorScheme.primary
                            DeviceStatus.STOLEN -> colorScheme.error
                            DeviceStatus.INACTIVE -> colorScheme.onSurfaceVariant
                            DeviceStatus.FLAGGED -> colorScheme.tertiary
                        }
                        val isStolenOrFlagged = device.status == DeviceStatus.STOLEN || device.status == DeviceStatus.FLAGGED

                        if (isStolenOrFlagged) {
                            // Compact oval stolen/flagged badge
                            TruCallerCard(
                                cornerRadius = 50.dp,
                                containerColor = colorScheme.errorContainer,
                                elevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(statusColor.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Device status warning",
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
                                            color = colorScheme.onSurface
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
                            TruCallerCard(
                                cornerRadius = 20.dp,
                                containerColor = colorScheme.surface,
                                elevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(statusColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PhoneAndroid,
                                            contentDescription = "Device",
                                            tint = statusColor,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(Spacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${device.manufacturer} ${device.model}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = colorScheme.onSurface
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

                // -- Quick Actions --
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
                            color = colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_report_stolen"),
                                icon = Icons.Default.Report,
                                label = "Report\nStolen",
                                color = colorScheme.error,
                                onClick = { rootNavController.navigate("report_stolen") }
                            )
                            QuickActionCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_sync_contacts"),
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_view_ip_log"),
                                icon = Icons.Default.LocationOn,
                                label = "View\nIP Log",
                                color = colorScheme.primary,
                                onClick = { rootNavController.navigate("remote_actions") }
                            )
                            QuickActionCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_trigger_alarm"),
                                icon = Icons.Default.Alarm,
                                label = "Trigger\nAlarm",
                                color = colorScheme.secondary,
                                onClick = { rootNavController.navigate("remote_actions") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_analytics"),
                                icon = Icons.Default.BarChart,
                                label = "My\nAnalytics",
                                color = colorScheme.tertiary,
                                onClick = { rootNavController.navigate("analytics") }
                            )
                            // Spacer cards to keep layout consistent
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // -- Recent Activity --
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
                            color = colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (activities.isEmpty()) {
                            EmptyStateView(
                                title = "No recent activity",
                                subtitle = "Device events and reports will appear here as they occur.",
                                icon = EmptyStateIcon.GENERIC,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            )
                        } else {
                            TruCallerCard(
                                cornerRadius = 20.dp,
                                elevation = 2.dp
                            ) {
                                activities.forEachIndexed { index, activity ->
                                    // Staggered item animation
                                    var itemVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(activity.id) {
                                        delay(index * 60L)
                                        itemVisible = true
                                    }

                                    AnimatedVisibility(
                                        visible = itemVisible,
                                        enter = fadeIn(tween(300)) + slideInVertically(
                                            initialOffsetY = { it / 4 },
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        )
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Use TruCallerAvatar for activity items
                                                TruCallerAvatar(
                                                    name = activity.title,
                                                    size = 42.dp,
                                                    contentDesc = "${activity.title} icon"
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        activity.title,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        color = colorScheme.onSurface
                                                    )
                                                    Text(
                                                        activity.subtitle,
                                                        fontSize = 12.sp,
                                                        color = colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                                Text(
                                                    formatRelativeTime(activity.timestamp),
                                                    fontSize = 11.sp,
                                                    color = colorScheme.outline
                                                )
                                            }
                                            if (index < activities.lastIndex) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 54.dp)
                                                        .height(0.5.dp)
                                                        .background(colorScheme.outlineVariant)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
            }
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
    val colorScheme = MaterialTheme.colorScheme

    // Press scale animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "quickActionScale"
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (isPressed) 0f else 4f,
        animationSpec = tween(durationMillis = 120),
        label = "quickActionShadow"
    )

    TruCallerCard(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = shadowElevation.dp,
                shape = RoundedCornerShape(Spacing.md),
                clip = false
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                        onClick()
                    }
                )
            },
        cornerRadius = Spacing.md,
        containerColor = colorScheme.surfaceVariant,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label.replace("\n", " "), tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.1.sp
            )
        }
    }
}
