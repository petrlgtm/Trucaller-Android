package com.byron.trucaller.ui.admin

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDeviceDetailScreen(
    navController: NavController,
    deviceId: String,
    deviceViewModel: DeviceViewModel,
    alarmViewModel: AlarmViewModel,
    authViewModel: AuthViewModel
) {
    var device by remember { mutableStateOf<Device?>(null) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    val alarmPlaying by alarmViewModel.alarmPlaying.collectAsState()
    val adminUser by authViewModel.adminUser.collectAsState()
    val ipLogs by deviceViewModel.getIpLogs(deviceId).collectAsState(initial = emptyList())
    val alarmLogs by alarmViewModel.getLogsByDevice(deviceId).collectAsState(initial = emptyList())

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(deviceId) {
        device = deviceViewModel.getDeviceById(deviceId)
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

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
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

            // Actions
            TruCallerButton(
                text = "Trigger Alarm",
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
                modifier = Modifier.fillMaxWidth(),
                style = TruCallerButtonStyle.Danger,
                leadingIcon = Icons.Default.Alarm
            )

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
