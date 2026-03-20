package com.byron.trucaller.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.ShimmerLoadingCard
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDeviceDetailScreen(
    navController: NavController,
    deviceId: String,
    deviceViewModel: DeviceViewModel,
    alarmViewModel: AlarmViewModel,
    authViewModel: AuthViewModel,
    stolenReportViewModel: StolenReportViewModel
) {
    var device by remember { mutableStateOf<Device?>(null) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showLockConfirmDialog by remember { mutableStateOf(false) }
    var showStolenPromptDialog by remember { mutableStateOf(false) }
    var showAutoResolvedDialog by remember { mutableStateOf(false) }

    val alarmPlaying by alarmViewModel.alarmPlaying.collectAsState()
    val adminUser by authViewModel.adminUser.collectAsState()
    val actionMessage by alarmViewModel.actionMessage.collectAsState()
    val statusUpdating by deviceViewModel.statusUpdating.collectAsState()
    val deviceStatusMessage by deviceViewModel.statusMessage.collectAsState()
    val ipLogs by deviceViewModel.getIpLogs(deviceId).collectAsState(initial = emptyList())
    val alarmLogs by alarmViewModel.getLogsByDevice(deviceId).collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme

    // Show alarm action result via snackbar
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            alarmViewModel.clearActionMessage()
        }
    }

    // Show device status change result via snackbar
    LaunchedEffect(deviceStatusMessage) {
        deviceStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            deviceViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(deviceId) {
        device = deviceViewModel.getDeviceById(deviceId)
    }

    // Refresh device after status update completes
    LaunchedEffect(statusUpdating) {
        if (!statusUpdating) {
            device = deviceViewModel.getDeviceById(deviceId)
        }
    }

    if (device == null) {
        Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
            TopAppBar(
                title = {
                    Text(
                        "Device Detail",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
            )
            ShimmerLoadingCard(modifier = Modifier.padding(Spacing.md))
        }
        return
    }

    val dev = device!!
    val statusBadgeType = when (dev.status) {
        DeviceStatus.ACTIVE -> BadgeType.Success
        DeviceStatus.STOLEN -> BadgeType.Spam
        DeviceStatus.INACTIVE -> BadgeType.Info
        DeviceStatus.FLAGGED -> BadgeType.Warning
    }

    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Alarm Triggered") },
            text = { Text("Alarm triggered successfully! The device will ring at maximum volume.") },
            confirmButton = { TextButton(onClick = { showAlarmDialog = false }) { Text("OK") } }
        )
    }

    if (showLockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLockConfirmDialog = false },
            title = { Text("Lock Device?") },
            text = {
                Text(
                    "This will immediately lock the device screen. " +
                    "The device owner will need their PIN or password to unlock."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockConfirmDialog = false
                    val admin = adminUser
                    if (admin != null) {
                        alarmViewModel.lockDevice(
                            deviceId = deviceId,
                            triggeredBy = admin.id,
                            triggeredByName = admin.name,
                            triggeredByRole = "admin"
                        )
                    }
                }) {
                    Text("Lock", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Prompt: changing to STOLEN — optionally create a stolen report
    if (showStolenPromptDialog) {
        AlertDialog(
            onDismissRequest = { showStolenPromptDialog = false },
            title = { Text("Create Stolen Report?") },
            text = {
                Text(
                    "The device has been marked as STOLEN. " +
                    "Would you like to create a stolen report for this device?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStolenPromptDialog = false
                    navController.navigate("admin_stolen_reports")
                }) {
                    Text("Create Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStolenPromptDialog = false }) {
                    Text("Skip")
                }
            }
        )
    }

    // Info: auto-resolved pending stolen reports
    if (showAutoResolvedDialog) {
        AlertDialog(
            onDismissRequest = { showAutoResolvedDialog = false },
            title = { Text("Reports Auto-Resolved") },
            text = {
                Text(
                    "Device status changed to ACTIVE. All pending stolen reports " +
                    "for this device have been automatically resolved."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAutoResolvedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${dev.manufacturer} ${dev.model}",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
            )
        },
        containerColor = colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // Alarm active banner
            if (alarmPlaying) {
                TruCallerCard(
                    modifier = Modifier.padding(bottom = 12.dp),
                    containerColor = colorScheme.error
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm,
                            null,
                            tint = colorScheme.onError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ALARM ACTIVE",
                                color = colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Alarm is sounding on device",
                                color = colorScheme.onError.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        TruCallerButton(
                            text = "STOP",
                            onClick = { alarmViewModel.stopAlarm() },
                            style = TruCallerButtonStyle.Secondary
                        )
                    }
                }
            }

            // Device info card
            TruCallerCard {
                Text(
                    "Device Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("Model", "${dev.manufacturer} ${dev.model}")
                InfoRow("OS", dev.osVersion)
                InfoRow("Device ID", dev.deviceId)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Status",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.width(100.dp)
                    )
                    TruCallerBadge(
                        text = dev.status.name,
                        type = statusBadgeType
                    )
                }
                InfoRow("Last IP", dev.lastIp)
                InfoRow("Last Seen", formatRelativeTime(dev.lastSeen))
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Change Status Section ────────────────────────────────────
            TruCallerCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Change Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    if (statusUpdating) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Status button group — 2x2 grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DeviceStatusButton(
                            status = DeviceStatus.ACTIVE,
                            currentStatus = dev.status,
                            enabled = !statusUpdating,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val admin = adminUser ?: return@DeviceStatusButton
                                deviceViewModel.adminUpdateDeviceStatus(
                                    deviceId = deviceId,
                                    newStatus = DeviceStatus.ACTIVE,
                                    adminId = admin.id,
                                    adminName = admin.name,
                                    onAutoResolved = {
                                        stolenReportViewModel.markDeviceRecovered(deviceId)
                                        showAutoResolvedDialog = true
                                    }
                                )
                            }
                        )
                        DeviceStatusButton(
                            status = DeviceStatus.INACTIVE,
                            currentStatus = dev.status,
                            enabled = !statusUpdating,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val admin = adminUser ?: return@DeviceStatusButton
                                deviceViewModel.adminUpdateDeviceStatus(
                                    deviceId = deviceId,
                                    newStatus = DeviceStatus.INACTIVE,
                                    adminId = admin.id,
                                    adminName = admin.name
                                )
                            }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DeviceStatusButton(
                            status = DeviceStatus.FLAGGED,
                            currentStatus = dev.status,
                            enabled = !statusUpdating,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val admin = adminUser ?: return@DeviceStatusButton
                                deviceViewModel.adminUpdateDeviceStatus(
                                    deviceId = deviceId,
                                    newStatus = DeviceStatus.FLAGGED,
                                    adminId = admin.id,
                                    adminName = admin.name
                                )
                            }
                        )
                        DeviceStatusButton(
                            status = DeviceStatus.STOLEN,
                            currentStatus = dev.status,
                            enabled = !statusUpdating,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val admin = adminUser ?: return@DeviceStatusButton
                                deviceViewModel.adminUpdateDeviceStatus(
                                    deviceId = deviceId,
                                    newStatus = DeviceStatus.STOLEN,
                                    adminId = admin.id,
                                    adminName = admin.name,
                                    onStolenPrompt = { showStolenPromptDialog = true }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TruCallerButton(
                    text = "Alarm",
                    onClick = {
                        val admin = adminUser
                        if (admin != null) {
                            alarmViewModel.triggerAlarm(
                                deviceId = deviceId,
                                triggeredBy = admin.id,
                                triggeredByName = admin.name,
                                triggeredByRole = "admin"
                            )
                        }
                        showAlarmDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    style = TruCallerButtonStyle.Danger,
                    leadingIcon = Icons.Default.Alarm
                )
                TruCallerButton(
                    text = "Lock",
                    onClick = { showLockConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    style = TruCallerButtonStyle.Warning,
                    leadingIcon = Icons.Default.Lock
                )
                TruCallerButton(
                    text = "Location",
                    onClick = {
                        val admin = adminUser
                        if (admin != null) {
                            alarmViewModel.requestLocation(
                                deviceId = deviceId,
                                triggeredBy = admin.id,
                                triggeredByName = admin.name,
                                triggeredByRole = "admin"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = TruCallerButtonStyle.Primary,
                    leadingIcon = Icons.Default.LocationOn
                )
            }

            // Show testing note when device is not STOLEN
            if (dev.status != DeviceStatus.STOLEN) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Testing mode \u2014 device is not marked as stolen",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // IP History
            Text(
                "IP History (${ipLogs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (ipLogs.isEmpty()) {
                Text(
                    "No IP logs available",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                TruCallerCard(elevation = 0.dp) {
                    val sortedLogs = ipLogs.sortedByDescending { it.timestamp }
                    sortedLogs.forEachIndexed { index, log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    log.ipAddress,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    "${log.isp} - ${log.city}, ${log.country}",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatRelativeTime(log.timestamp),
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                                val isWifi = log.networkType == "wifi"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower,
                                        null,
                                        tint = if (isWifi) Color(0xFF4CAF50) else colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        if (isWifi) "WiFi" else "Mobile",
                                        fontSize = 11.sp,
                                        color = if (isWifi) Color(0xFF4CAF50) else colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (index < sortedLogs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Alarm Logs
            Text(
                "Alarm Logs (${alarmLogs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (alarmLogs.isEmpty()) {
                Text(
                    "No alarm logs",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                alarmLogs.sortedByDescending { it.triggeredAt }.forEach { log ->
                    val resultBadgeType = when (log.result.name) {
                        "SUCCESS" -> BadgeType.Success
                        "FAILED" -> BadgeType.Spam
                        else -> BadgeType.Warning
                    }

                    TruCallerCard(
                        modifier = Modifier.padding(vertical = 3.dp),
                        elevation = 0.5.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                log.type.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TruCallerBadge(
                                text = log.result.name,
                                type = resultBadgeType
                            )
                        }
                        Text(
                            "By: ${log.triggeredByName} (${log.triggeredByRole})",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        log.notes?.let {
                            Text(
                                it,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatRelativeTime(log.triggeredAt),
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

/**
 * A selectable status button used in the admin device status management grid.
 * Shows the status name with color coding. The currently active status is
 * rendered with a filled background; others use an outlined style.
 */
@Composable
private fun DeviceStatusButton(
    status: DeviceStatus,
    currentStatus: DeviceStatus,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSelected = status == currentStatus

    val statusColor = when (status) {
        DeviceStatus.ACTIVE -> Color(0xFF4CAF50)
        DeviceStatus.INACTIVE -> colorScheme.onSurfaceVariant
        DeviceStatus.FLAGGED -> Color(0xFFFF9800)
        DeviceStatus.STOLEN -> colorScheme.error
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isSelected,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) statusColor else statusColor.copy(alpha = 0.4f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) statusColor.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = statusColor,
            disabledContainerColor = if (isSelected) statusColor.copy(alpha = 0.15f) else Color.Transparent,
            disabledContentColor = if (isSelected) statusColor else statusColor.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = status.name,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(
            value,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
