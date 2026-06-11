package com.byron.trucaller.ui.main

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.telecom.TelecomManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.components.*
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val context = LocalContext.current

    // -- System Status Checks --
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null
    
    var isDefaultDialer by remember { mutableStateOf(true) }
    var isDefaultSms by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            isDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            isDefaultSms = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
            isDefaultSms = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            isDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            isDefaultSms = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        }
    }

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

    // Location permission
    val userPreferences = remember {
        (context.applicationContext as TruCallerApplication).container.userPreferences
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            deviceViewModel.refreshDeviceLocation(user.id)
        }
    }

    // Request location permission exactly once across all app launches.
    // Using a DataStore flag prevents the dialog from re-appearing every time
    // the user navigates back to Home (LaunchedEffect(Unit) fires on each
    // NavHost recomposition).
    LaunchedEffect(user.id) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            val alreadyRequested = userPreferences.locationPermissionRequested.first()
            if (!alreadyRequested) {
                userPreferences.setLocationPermissionRequested(true)
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) contactsViewModel.syncContacts(user.id)
    }

    LaunchedEffect(user.id) {
        val hasContacts = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasContacts) {
            contactsViewModel.syncContacts(user.id)
        }
    }

    val activities = remember(ipLogs, alarmLogs, stolenReports) {
        val items = mutableListOf<ActivityItem>()
        ipLogs.forEach { log ->
            items.add(ActivityItem(log.id, "ip", "IP Location Update", "${log.city}, ${log.country} - ${log.isp}", log.timestamp, colorScheme.primary))
        }
        stolenReports.forEach { report ->
            val desc = if (report.description.length > 60) report.description.take(60) + "…" else report.description
            items.add(ActivityItem(report.id, "stolen", "Stolen Report", desc, report.reportedAt, colorScheme.error))
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
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(400))
            ) {
                TruCallerHeader(
                    title = user.fullName,
                    subtitle = "Welcome back",
                    titleColor = colorScheme.onSurface.copy(alpha = 0.7f),
                    subtitleColor = colorScheme.primary,
                    horizontalPadding = Spacing.lg,
                    verticalPadding = Spacing.lg
                )
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                if (syncMessage != null) {
                    LaunchedEffect(syncMessage) {
                        delay(4000)
                        contactsViewModel.clearSyncMessage()
                    }
                    Snackbar(modifier = Modifier.padding(bottom = Spacing.sm)) {
                        Text(syncMessage!!)
                    }
                }

                AnimatedVisibility(
                    visible = showDevice,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(350))
                ) {
                    Column {
                        if (!isDefaultDialer || !isDefaultSms) {
                            TruCallerCard(
                                cornerRadius = 20.dp,
                                containerColor = colorScheme.primary.copy(alpha = 0.05f),
                                elevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(Spacing.sm)) {
                                    Text(
                                        "Security Configuration",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    StatusItem(
                                        label = "Call Protection",
                                        isActive = isDefaultDialer,
                                        onFix = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                                if (intent != null) roleLauncher.launch(intent)
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    StatusItem(
                                        label = "SMS Filtering",
                                        isActive = isDefaultSms,
                                        onFix = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                                if (intent != null) roleLauncher.launch(intent)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (userDevice != null) {
                            val device = userDevice!!
                            val statusColor = when (device.status) {
                                DeviceStatus.ACTIVE -> colorScheme.primary
                                DeviceStatus.STOLEN -> colorScheme.error
                                DeviceStatus.INACTIVE -> colorScheme.onSurfaceVariant
                                DeviceStatus.FLAGGED -> colorScheme.tertiary
                            }
                            val isStolenOrFlagged = device.status == DeviceStatus.STOLEN || device.status == DeviceStatus.FLAGGED

                            TruCallerCard(
                                cornerRadius = 20.dp,
                                containerColor = if (isStolenOrFlagged) colorScheme.errorContainer else colorScheme.surface,
                                elevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isStolenOrFlagged) 36.dp else 52.dp)
                                            .background(statusColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isStolenOrFlagged) Icons.Default.Warning else Icons.Default.PhoneAndroid,
                                            contentDescription = "Device",
                                            tint = statusColor,
                                            modifier = Modifier.size(if (isStolenOrFlagged) 18.dp else 26.dp)
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
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = showActions,
                    enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(350)) + fadeIn(tween(350))
                ) {
                    TruCallerCard(cornerRadius = 20.dp, containerColor = colorScheme.surface, elevation = 0.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ProtectionStat(stolenReports.size.toString(), "Reports", colorScheme.error)
                            ProtectionStat(alarmLogs.size.toString(), "Alarms", colorScheme.tertiary)
                            ProtectionStat(ipLogs.size.toString(), "IP Logs", colorScheme.primary)
                            val isProt = userDevice?.status == DeviceStatus.ACTIVE
                            ProtectionStat(if (isProt) "ON" else "OFF", "Protected", if (isProt) Color(0xFF4CAF50) else colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = showActions,
                    enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(400)) + fadeIn(tween(400))
                ) {
                    Column {
                        Text("Quick Actions", fontWeight = FontWeight.Black, fontSize = 18.sp, color = colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Report, "Report\nStolen", colorScheme.error) { rootNavController.navigate("report_stolen") }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Sync, "Sync\nContacts", BrandGold) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    contactsViewModel.syncContacts(user.id)
                                } else {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Alarm, "Remote\nAlarm", colorScheme.secondary) { rootNavController.navigate("remote_actions") }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.LocationOn, "IP\nTracker", colorScheme.primary) { rootNavController.navigate("ip_logs") }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionCard(Modifier.weight(1f), Icons.Default.BarChart, "My\nAnalytics", colorScheme.tertiary) { rootNavController.navigate("analytics") }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Shield, "Security\nCenter", Color(0xFF4CAF50)) { rootNavController.navigate("security") }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Schedule, "Block\nSchedules", Color(0xFF7E57C2)) { rootNavController.navigate("blocking_schedules") }
                            QuickActionCard(Modifier.weight(1f), Icons.Default.Groups, "Family\nGroups", Color(0xFF29B6F6)) { rootNavController.navigate("family_groups") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                AnimatedVisibility(
                    visible = showActivity,
                    enter = slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(450)) + fadeIn(tween(450))
                ) {
                    Column {
                        Text("Recent Activity", fontWeight = FontWeight.Black, fontSize = 18.sp, color = colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                        Spacer(modifier = Modifier.height(12.dp))
                        if (activities.isEmpty()) {
                            EmptyStateView(
                                title = "No recent activity",
                                subtitle = "Device events and reports will appear here.",
                                icon = EmptyStateIcon.GENERIC,
                                modifier = Modifier.fillMaxWidth().height(240.dp)
                            )
                        } else {
                            TruCallerCard(cornerRadius = 20.dp, elevation = 0.dp) {
                                activities.forEachIndexed { index, activity ->
                                    var itemVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(activity.id) { delay(index * 60L); itemVisible = true }
                                    AnimatedVisibility(visible = itemVisible, enter = fadeIn() + slideInVertically()) {
                                        Column {
                                            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                                TruCallerAvatar(
                                                    name = activity.title,
                                                    size = 42.dp,
                                                    imageUri = null
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(activity.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                                                    Text(activity.subtitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                                Text(formatRelativeTime(activity.timestamp), fontSize = 11.sp, color = colorScheme.outline)
                                            }
                                            if (index < activities.lastIndex) {
                                                Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(0.5.dp).background(GlassBorder))
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
private fun StatusItem(label: String, isActive: Boolean, onFix: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).background(if (isActive) Color(0xFF4CAF50).copy(alpha = 0.15f) else colorScheme.error.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (isActive) Icons.Default.GppGood else Icons.Default.Shield, null, tint = if (isActive) Color(0xFF4CAF50) else colorScheme.error, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        if (!isActive) {
            TextButton(onClick = onFix, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                Text("FIX", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun QuickActionCard(modifier: Modifier = Modifier, icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.93f else 1f, label = "scale")
    TruCallerCard(modifier = modifier.scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { isPressed = true; try { awaitRelease() } finally { isPressed = false }; onClick() }) }, cornerRadius = Spacing.md, containerColor = colorScheme.surface, elevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun ProtectionStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
