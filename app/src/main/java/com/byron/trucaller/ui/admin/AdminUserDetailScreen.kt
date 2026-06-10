package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
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
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.ShimmerLoadingCard
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.launch

private fun trustLevelFromScore(score: Int): TrustLevel = when {
    score >= 100 -> TrustLevel.AUTHORITY
    score >= 80 -> TrustLevel.VERIFIED
    score >= 50 -> TrustLevel.TRUSTED
    score >= 20 -> TrustLevel.BASIC
    else -> TrustLevel.NEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    navController: NavController,
    userId: String,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel
) {
    val app = LocalContext.current.applicationContext as TruCallerApplication
    val coroutineScope = rememberCoroutineScope()
    var user by remember { mutableStateOf<User?>(null) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showReactivateDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isActionLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        user = app.container.userRepository.getUserById(userId)
    }

    val devices by deviceViewModel.getDevicesByUser(userId).collectAsState(initial = emptyList())
    val reports by stolenReportViewModel.getReportsByUser(userId).collectAsState(initial = emptyList())

    val colorScheme = MaterialTheme.colorScheme

    // Deactivate confirmation dialog
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Deactivate User") },
            text = {
                Text("Are you sure you want to deactivate this user? They will no longer be able to log in or use the app.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeactivateDialog = false
                    coroutineScope.launch {
                        app.container.userRepository.deactivateUser(userId)
                        user = app.container.userRepository.getUserById(userId)
                    }
                }) {
                    Text("Deactivate", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Ban confirmation dialog
    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text("Ban User") },
            text = { Text("This user will be permanently banned and will not be able to log in. You can unban by setting status to Active.") },
            confirmButton = {
                TextButton(onClick = {
                    showBanDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val result = ApiClient.banUser(userId, "BANNED")
                        isActionLoading = false
                        if (result.success) {
                            user = user?.copy(isActive = false)
                        } else {
                            actionError = result.error ?: "Failed to ban user"
                        }
                    }
                }) { Text("Ban", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBanDialog = false }) { Text("Cancel") } }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Permanently Delete User") },
            text = { Text("This will irreversibly delete the user and all their devices, contacts, and reports. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val result = ApiClient.deleteAdminUser(userId)
                        isActionLoading = false
                        if (result.success) {
                            navController.popBackStack()
                        } else {
                            actionError = result.error ?: "Failed to delete user"
                        }
                    }
                }) { Text("Delete Forever", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // Reactivate confirmation dialog
    if (showReactivateDialog) {
        AlertDialog(
            onDismissRequest = { showReactivateDialog = false },
            title = { Text("Reactivate User") },
            text = {
                Text("Are you sure you want to reactivate this user? They will be able to log in and use the app again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReactivateDialog = false
                    coroutineScope.launch {
                        app.container.userRepository.reactivateUser(userId)
                        user = app.container.userRepository.getUserById(userId)
                    }
                }) {
                    Text("Reactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReactivateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "User Detail",
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

        val u = user
        if (u == null) {
            ShimmerLoadingCard(modifier = Modifier.padding(Spacing.md))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // User profile card
                TruCallerCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TruCallerAvatar(
                            name = u.fullName,
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            u.fullName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            formatPhoneNumber(u.phoneNumber),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        u.email?.let { email ->
                            Text(
                                email,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status: ", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                            TruCallerBadge(
                                text = if (u.isActive) "Active" else "Inactive",
                                type = if (u.isActive) BadgeType.Success else BadgeType.Spam
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Joined: ${formatRelativeTime(u.createdAt)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Last Active card — prominent display
                u.lastLogin?.let { lastLogin ->
                    TruCallerCard(
                        containerColor = colorScheme.primaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Last Active",
                                    fontSize = 12.sp,
                                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    formatRelativeTime(lastLogin),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Admin action: Deactivate / Reactivate
                if (u.isActive) {
                    TruCallerButton(
                        text = "Deactivate User",
                        onClick = { showDeactivateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = TruCallerButtonStyle.Danger,
                        leadingIcon = Icons.Default.Block
                    )
                } else {
                    TruCallerButton(
                        text = "Reactivate User",
                        onClick = { showReactivateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = TruCallerButtonStyle.Primary,
                        leadingIcon = Icons.Default.CheckCircle
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TruCallerButton(
                    text = if (isActionLoading) "Processing…" else "Ban User",
                    onClick = { if (!isActionLoading) showBanDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    style = TruCallerButtonStyle.Danger
                )

                Spacer(modifier = Modifier.height(8.dp))

                TruCallerButton(
                    text = if (isActionLoading) "Processing…" else "Delete User",
                    onClick = { if (!isActionLoading) showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    style = TruCallerButtonStyle.Secondary
                )

                actionError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, color = colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trust Level management card
                Text(
                    "Trust Level",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                TruCallerCard(
                    elevation = 1.dp
                ) {
                    val trustColor = when (u.trustLevel) {
                        com.byron.trucaller.data.model.TrustLevel.NEW -> colorScheme.onSurfaceVariant
                        com.byron.trucaller.data.model.TrustLevel.BASIC -> Color(0xFF2196F3)
                        com.byron.trucaller.data.model.TrustLevel.TRUSTED -> Color(0xFF4CAF50)
                        com.byron.trucaller.data.model.TrustLevel.VERIFIED -> Color(0xFFFF9800)
                        com.byron.trucaller.data.model.TrustLevel.AUTHORITY -> Color(0xFF9C27B0)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Trust level",
                            tint = trustColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                u.trustLevel.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = trustColor
                            )
                            Text(
                                "Score: ${u.trustScore}/100",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        TruCallerBadge(
                            text = "${u.trustScore}",
                            type = BadgeType.Custom,
                            color = trustColor,
                            backgroundColor = trustColor.copy(alpha = 0.12f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Admin trust adjustment buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TruCallerButton(
                            text = "-10",
                            onClick = {
                                val newScore = maxOf(u.trustScore - 10, 0)
                                coroutineScope.launch {
                                    val updated = u.copy(
                                        trustScore = newScore,
                                        trustLevel = trustLevelFromScore(newScore)
                                    )
                                    app.container.userRepository.updateUser(updated)
                                    user = updated
                                    try {
                                        ApiClient.updateUserTrust(userId, trustScore = newScore)
                                    } catch (_: Exception) { }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            style = TruCallerButtonStyle.Danger
                        )
                        TruCallerButton(
                            text = "+10",
                            onClick = {
                                val newScore = minOf(u.trustScore + 10, 100)
                                coroutineScope.launch {
                                    val updated = u.copy(
                                        trustScore = newScore,
                                        trustLevel = trustLevelFromScore(newScore)
                                    )
                                    app.container.userRepository.updateUser(updated)
                                    user = updated
                                    try {
                                        ApiClient.updateUserTrust(userId, trustScore = newScore)
                                    } catch (_: Exception) { }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            style = TruCallerButtonStyle.Primary
                        )
                        TruCallerButton(
                            text = "Authority",
                            onClick = {
                                coroutineScope.launch {
                                    val updated = u.copy(
                                        trustScore = 100,
                                        trustLevel = com.byron.trucaller.data.model.TrustLevel.AUTHORITY
                                    )
                                    app.container.userRepository.updateUser(updated)
                                    user = updated
                                    try {
                                        ApiClient.updateUserTrust(userId, trustScore = 100, trustLevel = "AUTHORITY")
                                    } catch (_: Exception) { }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            style = TruCallerButtonStyle.Secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Devices
                Text(
                    "Devices (${devices.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text(
                        "No devices registered",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    devices.forEach { device ->
                        val statusBadgeType = when (device.status.name) {
                            "ACTIVE" -> BadgeType.Success
                            "STOLEN" -> BadgeType.Spam
                            "FLAGGED" -> BadgeType.Warning
                            else -> BadgeType.Info
                        }

                        TruCallerCard(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    navController.navigate("admin_device_detail/${device.id}")
                                },
                            elevation = 1.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.manufacturer} ${device.model}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        "OS: ${device.osVersion}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Last Seen: ${formatRelativeTime(device.lastSeen)}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Last IP: ${device.lastIp}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                TruCallerBadge(
                                    text = device.status.name,
                                    type = statusBadgeType
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "View device details",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stolen Reports
                Text(
                    "Stolen Reports (${reports.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text(
                        "No stolen reports",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    reports.forEach { report ->
                        val statusBadgeType = when (report.status.name) {
                            "PENDING" -> BadgeType.Warning
                            "VERIFIED" -> BadgeType.Info
                            "RESOLVED" -> BadgeType.Success
                            "ESCALATED" -> BadgeType.Spam
                            else -> BadgeType.Info
                        }

                        TruCallerCard(
                            modifier = Modifier.padding(vertical = 4.dp),
                            elevation = 1.dp
                        ) {
                            Row {
                                Text(
                                    "Device: ${report.deviceId}",
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TruCallerBadge(
                                    text = report.status.name,
                                    type = statusBadgeType
                                )
                            }
                            Text(
                                report.description,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                            Text(
                                "Reported: ${formatRelativeTime(report.reportedAt)}",
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
}
