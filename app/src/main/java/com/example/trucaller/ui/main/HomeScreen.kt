package com.example.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
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
import com.example.trucaller.viewmodel.ContactsViewModel
import com.example.trucaller.viewmodel.DeviceViewModel
import com.example.trucaller.viewmodel.StolenReportViewModel

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
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Sync regardless — if permission denied, it just won't import phone contacts
        contactsViewModel.syncContacts(user.id)
    }

    val activities = remember(ipLogs, alarmLogs, stolenReports) {
        val items = mutableListOf<ActivityItem>()
        ipLogs.take(3).forEach { log ->
            items.add(ActivityItem(log.id, "ip", "IP Location Update", "${log.city}, ${log.country} - ${log.isp}", log.timestamp, Brand))
        }
        stolenReports.take(2).forEach { report ->
            items.add(ActivityItem(report.id, "stolen", "Stolen Report", report.description.take(60) + "...", report.reportedAt, Danger))
        }
        alarmLogs.take(2).forEach { alarm ->
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
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark)
                .padding(24.dp)
        ) {
            Column {
                Text("Welcome back,", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text(user.fullName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Sync message snackbar
            if (syncMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { contactsViewModel.clearSyncMessage() }) {
                            Text("OK", color = Color.White)
                        }
                    }
                ) {
                    Text(syncMessage!!)
                }
            }

            // Device status card
            if (userDevice != null) {
                val device = userDevice!!
                val statusColor = when (device.status) {
                    DeviceStatus.ACTIVE -> Success
                    DeviceStatus.STOLEN -> Danger
                    DeviceStatus.INACTIVE -> Inactive
                    DeviceStatus.FLAGGED -> Warning
                }
                val statusBg = when (device.status) {
                    DeviceStatus.STOLEN -> Color(0x1AE53935)
                    else -> Color(0x1A43A047)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(statusBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (device.status == DeviceStatus.STOLEN) Icons.Default.Warning else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${device.manufacturer} ${device.model}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
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
                                .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                device.status.name,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick actions
            Text("Quick Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    color = Brand,
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
                    color = Warning,
                    onClick = { rootNavController.navigate("remote_actions") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent activity
            Text("Recent Activity", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            if (activities.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        activities.forEachIndexed { index, activity ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(activity.color.copy(alpha = 0.1f), CircleShape),
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
                                    Text(activity.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                    Text(activity.subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
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
                                        .padding(start = 68.dp)
                                        .height(0.5.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
