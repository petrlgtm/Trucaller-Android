package com.byron.trucaller.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.data.model.StolenReport
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AdminDashboardViewModel
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Activity Type ────────────────────────────────────────────────────────────

/** Represents the type of an admin dashboard activity event. */
enum class AdminActivityType {
    STOLEN_REPORT,
    ALARM_TRIGGERED,
    LOCATION_REQUEST,
    DEVICE_LOCKED
}

/**
 * A unified activity item that merges different event sources (stolen reports,
 * alarm logs) into a single sortable model for the Recent Activity feed.
 */
private data class AdminActivityItem(
    val id: String,
    val type: AdminActivityType,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val navigationRoute: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    dashboardViewModel: AdminDashboardViewModel,
    stolenReportViewModel: StolenReportViewModel,
    alarmViewModel: AlarmViewModel
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    val stats by dashboardViewModel.stats.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val error by dashboardViewModel.error.collectAsState()

    val reports by stolenReportViewModel.allReports.collectAsState(initial = emptyList())
    val alarmLogs by alarmViewModel.allLogs.collectAsState(initial = emptyList())

    // Build unified activity feed from stolen reports + alarm logs
    val recentActivity = remember(reports, alarmLogs) {
        buildRecentActivityList(reports, alarmLogs)
    }

    // Staggered entrance animation state
    var statsVisible by remember { mutableStateOf(false) }
    var activityVisible by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        statsVisible = true
        delay(200)
        activityVisible = true
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
        val adminUser by authViewModel.adminUser.collectAsState()
        TruCallerHeader(
            title = "Admin Dashboard",
            subtitle = adminUser?.name?.let { "Logged in as $it" } ?: "System overview & management"
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { dashboardViewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // Live-update status bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (stats.lastUpdated > 0L) {
                        val secondsAgo = ((System.currentTimeMillis() - stats.lastUpdated) / 1_000).toInt()
                        val timeText = when {
                            secondsAgo < 10 -> "Live"
                            secondsAgo < 60 -> "${secondsAgo}s ago"
                            secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
                            else -> "${secondsAgo / 3600}h ago"
                        }
                        Text(
                            "Realtime · $timeText · auto-refresh every 30s",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Connecting...", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                }

                error?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Stats row 1: Users & Active Devices
                AnimatedVisibility(
                    visible = statsVisible,
                    enter = fadeIn(animationSpec = tween(400)) +
                            slideInVertically(
                                animationSpec = tween(400),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    if (isLoading && stats.lastUpdated == 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Total Users",
                                targetValue = stats.userCount,
                                accentColor = colorScheme.onSurface
                            )
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Active Devices",
                                targetValue = stats.activeDevices,
                                accentColor = Color(0xFF4CAF50)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row 2: All devices & Pending reports
                AnimatedVisibility(
                    visible = statsVisible,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                            slideInVertically(
                                animationSpec = tween(400, delayMillis = 100),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    if (isLoading && stats.lastUpdated == 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "All Devices",
                                targetValue = stats.deviceCount,
                                accentColor = colorScheme.primary
                            )
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Pending Reports",
                                targetValue = stats.pendingReports,
                                accentColor = colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row 3: Stolen Reports & Alarms
                AnimatedVisibility(
                    visible = statsVisible,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 150)) +
                            slideInVertically(
                                animationSpec = tween(400, delayMillis = 150),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    if (isLoading && stats.lastUpdated == 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Stolen Reports",
                                targetValue = stats.reportCount,
                                accentColor = colorScheme.error
                            )
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Alarms Fired",
                                targetValue = stats.alarmCount,
                                accentColor = colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row 4: Caller IDs & SMS Spam Reports
                AnimatedVisibility(
                    visible = statsVisible,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 300)) +
                            slideInVertically(
                                animationSpec = tween(400, delayMillis = 300),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    if (isLoading && stats.lastUpdated == 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                            ShimmerStatCard(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Caller IDs",
                                targetValue = stats.callerIdCount,
                                accentColor = Brand
                            )
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "SMS Reports",
                                targetValue = stats.smsSpamReportCount,
                                accentColor = BrandGold
                            )
                        }
                    }
                }

                // Stats row 5: Verified & Resolved stolen reports
                if (stats.verifiedReports > 0 || stats.resolvedReports > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AnimatedVisibility(
                        visible = statsVisible,
                        enter = fadeIn(animationSpec = tween(400, delayMillis = 400)) +
                                slideInVertically(
                                    animationSpec = tween(400, delayMillis = 400),
                                    initialOffsetY = { it / 4 }
                                )
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Verified Cases",
                                targetValue = stats.verifiedReports,
                                accentColor = Color(0xFFFFA000)
                            )
                            AnimatedStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Resolved Cases",
                                targetValue = stats.resolvedReports,
                                accentColor = Color(0xFF4CAF50)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Recent Activity
            AnimatedVisibility(
                visible = activityVisible,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 50)) +
                        slideInVertically(
                            animationSpec = tween(400, delayMillis = 50),
                            initialOffsetY = { it / 6 }
                        )
            ) {
                Column {
                    Text(
                        "Recent Activity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (recentActivity.isEmpty()) {
                        TruCallerCard {
                            EmptyStateView(
                                title = "No activity yet",
                                subtitle = "Recent stolen reports, alarm triggers, and other events will appear here.",
                                icon = EmptyStateIcon.GENERIC,
                                modifier = Modifier.height(200.dp)
                            )
                        }
                    } else {
                        TruCallerCard {
                            Column {
                                recentActivity.forEachIndexed { index, item ->
                                    RecentActivityRow(
                                        item = item,
                                        onClick = {
                                            navController.navigate(item.navigationRoute)
                                        }
                                    )
                                    if (index < recentActivity.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = Spacing.md),
                                            color = colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                                "${stats.activeDevices} active · ${stats.deviceCount} total",
                                colorScheme.onSurface
                            ) {
                                navController.navigate("admin_devices")
                            }
                            AdminMenuItem(
                                Icons.Default.People,
                                "Users",
                                "${stats.userCount} registered",
                                Color(0xFF4CAF50)
                            ) {
                                navController.navigate("admin_users")
                            }
                            AdminMenuItem(
                                Icons.Default.Warning,
                                "Stolen Reports",
                                if (stats.pendingReports > 0) "${stats.pendingReports} pending · ${stats.reportCount} total" else "${stats.reportCount} total",
                                colorScheme.error
                            ) {
                                navController.navigate("admin_stolen_reports")
                            }
                            AdminMenuItem(
                                Icons.Default.Phone,
                                "Caller ID Database",
                                "${stats.callerIdCount} entries",
                                Brand
                            ) {
                                navController.navigate("admin_caller_id")
                            }
                            AdminMenuItem(
                                Icons.Default.Alarm,
                                "Alarm Logs",
                                "${stats.alarmCount} logs",
                                colorScheme.primary
                            ) {
                                navController.navigate("admin_alarm_logs")
                            }
                            AdminMenuItem(
                                Icons.Default.MarkEmailUnread,
                                "SMS Spam Reports",
                                "${stats.smsSpamReportCount} reports",
                                BrandGold
                            ) {
                                navController.navigate("admin_caller_id")
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
}

@Composable
private fun ShimmerStatCard(modifier: Modifier) {
    val shimmerAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "shimmer"
    )
    val colorScheme = MaterialTheme.colorScheme
    val shimmerColor = colorScheme.onSurface.copy(alpha = 0.08f * shimmerAlpha)

    TruCallerCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(shimmerColor, shimmerColor.copy(alpha = 0.15f), shimmerColor),
                        start = Offset.Zero,
                        end = Offset(300f, 0f)
                    )
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(shimmerColor, shimmerColor.copy(alpha = 0.15f), shimmerColor),
                        start = Offset.Zero,
                        end = Offset(200f, 0f)
                    )
                )
        )
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

// ── Recent Activity Row ──────────────────────────────────────────────────────

@Composable
private fun RecentActivityRow(
    item: AdminActivityItem,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val (icon, iconColor) = when (item.type) {
        AdminActivityType.STOLEN_REPORT -> Icons.Default.Warning to colorScheme.error
        AdminActivityType.ALARM_TRIGGERED -> Icons.Default.NotificationsActive to BrandGold
        AdminActivityType.LOCATION_REQUEST -> Icons.Default.LocationOn to Brand
        AdminActivityType.DEVICE_LOCKED -> Icons.Default.Lock to Color(0xFF9C27B0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            formatRelativeTime(item.timestamp),
            fontSize = 11.sp,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ── Activity List Builder ────────────────────────────────────────────────────

/**
 * Merges stolen reports and alarm logs into a unified activity feed,
 * sorted by timestamp descending, limited to the 10 most recent items.
 */
private fun buildRecentActivityList(
    reports: List<StolenReport>,
    alarmLogs: List<AlarmLog>
): List<AdminActivityItem> {
    val reportItems = reports.map { report ->
        AdminActivityItem(
            id = report.id,
            type = AdminActivityType.STOLEN_REPORT,
            title = "Stolen device reported",
            subtitle = "Device ${report.deviceId.take(12)}... - ${report.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
            timestamp = report.reportedAt,
            navigationRoute = "admin_stolen_reports"
        )
    }

    val alarmItems = alarmLogs.map { log ->
        val (type, title) = when (log.type) {
            AlarmType.REMOTE_ALARM -> AdminActivityType.ALARM_TRIGGERED to "Alarm triggered"
            AlarmType.LOCATION_REQUEST -> AdminActivityType.LOCATION_REQUEST to "Location requested"
            AlarmType.LOCK_DEVICE -> AdminActivityType.DEVICE_LOCKED to "Device lock triggered"
        }
        AdminActivityItem(
            id = log.id,
            type = type,
            title = title,
            subtitle = "By ${log.triggeredByName} - ${log.result.name.lowercase().replaceFirstChar { it.uppercase() }}",
            timestamp = log.triggeredAt,
            navigationRoute = "admin_alarm_logs"
        )
    }

    return (reportItems + alarmItems)
        .sortedByDescending { it.timestamp }
        .take(10)
}

// ── Relative Time Formatter ──────────────────────────────────────────────────

/**
 * Converts an ISO-8601 timestamp string to a human-readable relative time
 * (e.g., "2h ago", "3d ago", "Just now").
 */
private fun formatRelativeTime(isoTimestamp: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val date = format.parse(isoTimestamp) ?: return isoTimestamp
        val now = Date()
        val diffMs = now.time - date.time

        if (diffMs < 0) return "Just now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        val weeks = days / 7

        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            weeks < 4 -> "${weeks}w ago"
            else -> {
                val displayFormat = SimpleDateFormat("MMM d", Locale.US)
                displayFormat.format(date)
            }
        }
    } catch (_: Exception) {
        isoTimestamp
    }
}
