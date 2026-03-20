package com.byron.trucaller.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.delay

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

    // Staggered entrance animation state
    var statsVisible by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        statsVisible = true
        delay(200)
        menuVisible = true
    }

    val colorScheme = MaterialTheme.colorScheme

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
                }) { Text("Logout", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TruCallerHeader(
            title = "Admin Dashboard",
            subtitle = "System overview & management",
            gradientColors = listOf(colorScheme.surface, colorScheme.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // Stats cards row 1
            AnimatedVisibility(
                visible = statsVisible,
                enter = fadeIn(animationSpec = tween(400)) +
                        slideInVertically(
                            animationSpec = tween(400),
                            initialOffsetY = { it / 4 }
                        )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Users",
                        targetValue = userCount,
                        accentColor = colorScheme.onSurface
                    )
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Devices",
                        targetValue = devices.size,
                        accentColor = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats cards row 2
            AnimatedVisibility(
                visible = statsVisible,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 150)) +
                        slideInVertically(
                            animationSpec = tween(400, delayMillis = 150),
                            initialOffsetY = { it / 4 }
                        )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Stolen",
                        targetValue = reports.size,
                        accentColor = colorScheme.error
                    )
                    AnimatedStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Alarms",
                        targetValue = pendingAlarms,
                        accentColor = colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Menu items
            AnimatedVisibility(
                visible = menuVisible,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                        slideInVertically(
                            animationSpec = tween(400, delayMillis = 100),
                            initialOffsetY = { it / 6 }
                        )
            ) {
                Column {
                    Text(
                        "Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TruCallerCard {
                        Column {
                            AdminMenuItem(
                                Icons.Default.PhoneAndroid,
                                "Devices",
                                "${devices.size} registered",
                                colorScheme.onSurface
                            ) {
                                navController.navigate("admin_devices")
                            }
                            AdminMenuItem(
                                Icons.Default.People,
                                "Users",
                                "$userCount users",
                                Color(0xFF4CAF50)
                            ) {
                                navController.navigate("admin_users")
                            }
                            AdminMenuItem(
                                Icons.Default.Warning,
                                "Stolen Reports",
                                "${reports.size} reports",
                                colorScheme.error
                            ) {
                                navController.navigate("admin_stolen_reports")
                            }
                            AdminMenuItem(
                                Icons.Default.Phone,
                                "Caller ID Database",
                                "Manage spam database",
                                colorScheme.onSurface
                            ) {
                                navController.navigate("admin_caller_id")
                            }
                            AdminMenuItem(
                                Icons.Default.Alarm,
                                "Alarm Logs",
                                "${alarmLogs.size} logs",
                                colorScheme.primary
                            ) {
                                navController.navigate("admin_alarm_logs")
                            }
                            AdminMenuItem(
                                Icons.Default.Settings,
                                "Settings",
                                "Profile & preferences",
                                colorScheme.onSurfaceVariant
                            ) {
                                navController.navigate("admin_settings")
                            }
                            AdminMenuItem(
                                Icons.AutoMirrored.Filled.Logout,
                                "Logout",
                                "Sign out of admin",
                                colorScheme.error
                            ) {
                                showLogoutDialog = true
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun AnimatedStatCard(
    modifier: Modifier,
    title: String,
    targetValue: Int,
    accentColor: Color
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 800, delayMillis = 200),
        label = "statCounter"
    )
    val colorScheme = MaterialTheme.colorScheme

    TruCallerCard(modifier = modifier) {
        Text(title, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            animatedValue.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

@Composable
private fun AdminMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colorScheme.onSurface
            )
            Text(subtitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
