package com.example.trucaller.ui.admin

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trucaller.ui.theme.Background
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.BrandDark
import com.example.trucaller.ui.theme.Danger
import com.example.trucaller.ui.theme.Inactive
import com.example.trucaller.ui.theme.Success
import com.example.trucaller.ui.theme.TextPrimary
import com.example.trucaller.ui.theme.TextSecondary
import com.example.trucaller.ui.theme.Warning
import com.example.trucaller.viewmodel.AlarmViewModel
import com.example.trucaller.viewmodel.AuthViewModel
import com.example.trucaller.viewmodel.ContactsViewModel
import com.example.trucaller.viewmodel.DeviceViewModel
import com.example.trucaller.viewmodel.StolenReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel,
    alarmViewModel: AlarmViewModel,
    contactsViewModel: ContactsViewModel
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    val devices by deviceViewModel.allDevices.collectAsState(initial = emptyList())
    val reports by stolenReportViewModel.allReports.collectAsState(initial = emptyList())
    val alarmLogs by alarmViewModel.allLogs.collectAsState(initial = emptyList())
    val pendingAlarms by alarmViewModel.pendingCount.collectAsState(initial = 0)
    val userCount by deviceViewModel.userCount.collectAsState(initial = 0)

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Admin Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.adminLogout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Logout", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Admin Dashboard", fontWeight = FontWeight.Bold, color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Stats cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "Users", userCount, Brand)
                StatCard(Modifier.weight(1f), "Devices", devices.size, Success)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "Stolen", reports.size, Danger)
                StatCard(Modifier.weight(1f), "Alarms", pendingAlarms, Warning)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Menu items
            Text("Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    AdminMenuItem(Icons.Default.PhoneAndroid, "Devices", "${devices.size} registered", Brand) {
                        navController.navigate("admin_devices")
                    }
                    AdminMenuItem(Icons.Default.People, "Users", "$userCount users", Success) {
                        navController.navigate("admin_users")
                    }
                    AdminMenuItem(Icons.Default.Warning, "Stolen Reports", "${reports.size} reports", Danger) {
                        navController.navigate("admin_stolen_reports")
                    }
                    AdminMenuItem(Icons.Default.Phone, "Caller ID Database", "Manage spam database", Brand) {
                        navController.navigate("admin_caller_id")
                    }
                    AdminMenuItem(Icons.Default.Alarm, "Alarm Logs", "${alarmLogs.size} logs", Warning) {
                        navController.navigate("admin_alarm_logs")
                    }
                    AdminMenuItem(Icons.Default.Settings, "Settings", "Profile & preferences", Inactive) {
                        navController.navigate("admin_settings")
                    }
                    AdminMenuItem(Icons.AutoMirrored.Filled.Logout, "Logout", "Sign out of admin", Danger) {
                        showLogoutDialog = true
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: Int, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun AdminMenuItem(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Inactive, modifier = Modifier.size(20.dp))
    }
}
