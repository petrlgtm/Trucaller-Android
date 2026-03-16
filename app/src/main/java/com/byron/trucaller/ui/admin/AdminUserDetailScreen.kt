package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.User
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.util.getInitials
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    navController: NavController,
    userId: String,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel
) {
    val app = LocalContext.current.applicationContext as TruCallerApplication
    var user by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(userId) {
        user = app.container.userRepository.getUserById(userId)
    }

    val devices by deviceViewModel.getDevicesByUser(userId).collectAsState(initial = emptyList())
    val reports by stolenReportViewModel.getReportsByUser(userId).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("User Detail", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        val u = user
        if (u == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // User profile card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(Brand.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getInitials(u.fullName), color = Brand, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(u.fullName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                        Text(formatPhoneNumber(u.phoneNumber), fontSize = 14.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Text("Status: ", fontSize = 13.sp, color = TextSecondary)
                            Text(
                                if (u.isActive) "Active" else "Inactive",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (u.isActive) Success else Danger
                            )
                        }
                        Text("Joined: ${formatRelativeTime(u.createdAt)}", fontSize = 12.sp, color = Inactive)
                        u.lastLogin?.let {
                            Text("Last Login: ${formatRelativeTime(it)}", fontSize = 12.sp, color = Inactive)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Devices
                Text("Devices (${devices.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text("No devices registered", color = TextSecondary, fontSize = 14.sp)
                } else {
                    devices.forEach { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${device.manufacturer} ${device.model}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                    Text("OS: ${device.osVersion}", fontSize = 12.sp, color = TextSecondary)
                                    Text("Last IP: ${device.lastIp}", fontSize = 12.sp, color = TextSecondary)
                                }
                                val statusColor = when (device.status.name) {
                                    "ACTIVE" -> Success
                                    "STOLEN" -> Danger
                                    "FLAGGED" -> Warning
                                    else -> Inactive
                                }
                                Text(device.status.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stolen Reports
                Text("Stolen Reports (${reports.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text("No stolen reports", color = TextSecondary, fontSize = 14.sp)
                } else {
                    reports.forEach { report ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text("Device: ${report.deviceId}", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                    val statusColor = when (report.status.name) {
                                        "PENDING" -> Warning
                                        "VERIFIED" -> Brand
                                        "RESOLVED" -> Success
                                        "ESCALATED" -> Danger
                                        else -> Inactive
                                    }
                                    Text(report.status.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                                Text(report.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                                Text("Reported: ${formatRelativeTime(report.reportedAt)}", fontSize = 11.sp, color = Inactive)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
