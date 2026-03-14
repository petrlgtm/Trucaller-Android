package com.example.trucaller.ui.admin

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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.trucaller.data.model.Device
import com.example.trucaller.data.model.DeviceStatus
import com.example.trucaller.ui.theme.Background
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.BrandDark
import com.example.trucaller.ui.theme.Danger
import com.example.trucaller.ui.theme.Inactive
import com.example.trucaller.ui.theme.Success
import com.example.trucaller.ui.theme.TextPrimary
import com.example.trucaller.ui.theme.TextSecondary
import com.example.trucaller.ui.theme.Warning
import com.example.trucaller.util.formatRelativeTime
import com.example.trucaller.viewmodel.AlarmViewModel
import com.example.trucaller.viewmodel.AuthViewModel
import com.example.trucaller.viewmodel.DeviceViewModel

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

    LaunchedEffect(deviceId) {
        device = deviceViewModel.getDeviceById(deviceId)
    }

    if (device == null) {
        Column(modifier = Modifier.fillMaxSize().background(Background)) {
            TopAppBar(
                title = { Text("Device Detail", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", fontSize = 18.sp, color = TextSecondary)
            }
        }
        return
    }

    val dev = device!!
    val statusColor = when (dev.status) {
        DeviceStatus.ACTIVE -> Success; DeviceStatus.STOLEN -> Danger
        DeviceStatus.INACTIVE -> Inactive; DeviceStatus.FLAGGED -> Warning
    }

    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Alarm Triggered") },
            text = { Text("Alarm triggered successfully! The device will ring at maximum volume.") },
            confirmButton = { TextButton(onClick = { showAlarmDialog = false }) { Text("OK") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("${dev.manufacturer} ${dev.model}", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // Alarm active banner
            if (alarmPlaying) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Danger)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ALARM ACTIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Alarm is sounding on device", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { alarmViewModel.stopAlarm() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("STOP", color = Danger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Device info card
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow("Model", "${dev.manufacturer} ${dev.model}")
                    InfoRow("OS", dev.osVersion)
                    InfoRow("Device ID", dev.deviceId)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Status", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(100.dp))
                        Box(
                            modifier = Modifier.background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(dev.status.name, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    InfoRow("Last IP", dev.lastIp)
                    InfoRow("Last Seen", formatRelativeTime(dev.lastSeen))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Button(
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
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Alarm, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Trigger Alarm", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // IP History
            Text("IP History (${ipLogs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            if (ipLogs.isEmpty()) {
                Text("No IP logs available", color = TextSecondary, fontSize = 14.sp)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        val sortedLogs = ipLogs.sortedByDescending { it.timestamp }
                        sortedLogs.forEachIndexed { index, log ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.ipAddress, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    Text("${log.isp} - ${log.city}, ${log.country}", fontSize = 12.sp, color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatRelativeTime(log.timestamp), fontSize = 11.sp, color = Inactive)
                                    val isWifi = log.networkType == "wifi"
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower, null,
                                            tint = if (isWifi) Brand else Warning, modifier = Modifier.size(12.dp)
                                        )
                                        Text(if (isWifi) "WiFi" else "Mobile", fontSize = 11.sp, color = if (isWifi) Brand else Warning)
                                    }
                                }
                            }
                            if (index < sortedLogs.lastIndex) HorizontalDivider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Alarm Logs
            Text("Alarm Logs (${alarmLogs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            if (alarmLogs.isEmpty()) {
                Text("No alarm logs", color = TextSecondary, fontSize = 14.sp)
            } else {
                alarmLogs.sortedByDescending { it.triggeredAt }.forEach { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(log.type.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                Text(log.result.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = when (log.result.name) {
                                    "SUCCESS" -> Success; "FAILED" -> Danger; else -> Warning
                                })
                            }
                            Text("By: ${log.triggeredByName} (${log.triggeredByRole})", fontSize = 12.sp, color = TextSecondary)
                            log.notes?.let { Text(it, fontSize = 11.sp, color = Inactive) }
                            Text(formatRelativeTime(log.triggeredAt), fontSize = 11.sp, color = Inactive)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
