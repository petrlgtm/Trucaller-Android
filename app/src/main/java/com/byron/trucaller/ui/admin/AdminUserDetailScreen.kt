package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.AdminDeviceSummary
import com.byron.trucaller.service.AdminUserSummary
import com.byron.trucaller.service.ApiClient
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
import kotlinx.coroutines.launch

private fun trustLevelFromScore(score: Int): String = when {
    score >= 100 -> "AUTHORITY"
    score >= 80 -> "VERIFIED"
    score >= 50 -> "TRUSTED"
    score >= 20 -> "BASIC"
    else -> "NEW"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    navController: NavController,
    userId: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    var user by remember { mutableStateOf<AdminUserSummary?>(null) }
    val devices = remember { mutableStateListOf<AdminDeviceSummary>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showReactivateDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isActionLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        isLoading = true
        val result = ApiClient.getAdminUserDetail(userId)
        if (result.success && result.data != null) {
            user = result.data.user
            devices.clear()
            devices.addAll(result.data.devices)
        } else {
            loadError = result.error ?: "Failed to load user"
        }
        isLoading = false
    }

    // Dialogs
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Deactivate User") },
            text = { Text("This user will no longer be able to log in or use the app.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeactivateDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val r = ApiClient.banUser(userId, "INACTIVE")
                        isActionLoading = false
                        if (r.success) user = user?.copy(isActive = false)
                        else actionError = r.error ?: "Failed to deactivate"
                    }
                }) { Text("Deactivate", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeactivateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showReactivateDialog) {
        AlertDialog(
            onDismissRequest = { showReactivateDialog = false },
            title = { Text("Reactivate User") },
            text = { Text("This user will be able to log in and use the app again.") },
            confirmButton = {
                TextButton(onClick = {
                    showReactivateDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val r = ApiClient.banUser(userId, "ACTIVE")
                        isActionLoading = false
                        if (r.success) user = user?.copy(isActive = true)
                        else actionError = r.error ?: "Failed to reactivate"
                    }
                }) { Text("Reactivate") }
            },
            dismissButton = { TextButton(onClick = { showReactivateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text("Ban User") },
            text = { Text("This user will be permanently banned. You can unban by setting status to Active.") },
            confirmButton = {
                TextButton(onClick = {
                    showBanDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val r = ApiClient.banUser(userId, "BANNED")
                        isActionLoading = false
                        if (r.success) user = user?.copy(isActive = false)
                        else actionError = r.error ?: "Failed to ban user"
                    }
                }) { Text("Ban", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBanDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Permanently Delete User") },
            text = { Text("This will irreversibly delete the user and all their devices, contacts, and reports.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    coroutineScope.launch {
                        isActionLoading = true
                        val r = ApiClient.deleteAdminUser(userId)
                        isActionLoading = false
                        if (r.success) navController.popBackStack()
                        else actionError = r.error ?: "Failed to delete user"
                    }
                }) { Text("Delete Forever", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = { Text("User Detail", fontWeight = FontWeight.Bold, color = colorScheme.onPrimary) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
        )

        when {
            isLoading -> ShimmerLoadingCard(modifier = Modifier.padding(Spacing.md))
            loadError != null -> {
                Text(loadError!!, modifier = Modifier.padding(Spacing.md), color = colorScheme.error)
            }
            user == null -> {
                Text("User not found", modifier = Modifier.padding(Spacing.md), color = colorScheme.onSurfaceVariant)
            }
            else -> {
                val u = user!!
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)
                ) {
                    // Profile card
                    TruCallerCard {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TruCallerAvatar(name = u.fullName, size = 64.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(u.fullName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colorScheme.onSurface)
                            Text(formatPhoneNumber(u.phoneNumber), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                            u.email?.let { Text(it, fontSize = 13.sp, color = colorScheme.onSurfaceVariant) }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Status: ", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                                TruCallerBadge(text = if (u.isActive) "Active" else "Inactive", type = if (u.isActive) BadgeType.Success else BadgeType.Spam)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Joined: ${formatRelativeTime(u.createdAt)}", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    u.lastLogin?.let { lastLogin ->
                        TruCallerCard(containerColor = colorScheme.primaryContainer) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AccessTime, null, tint = colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Last Active", fontSize = 12.sp, color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    Text(formatRelativeTime(lastLogin), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Actions
                    if (u.isActive) {
                        TruCallerButton(text = "Deactivate User", onClick = { showDeactivateDialog = true }, modifier = Modifier.fillMaxWidth(), style = TruCallerButtonStyle.Danger, leadingIcon = Icons.Default.Block)
                    } else {
                        TruCallerButton(text = "Reactivate User", onClick = { showReactivateDialog = true }, modifier = Modifier.fillMaxWidth(), style = TruCallerButtonStyle.Primary, leadingIcon = Icons.Default.CheckCircle)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TruCallerButton(text = if (isActionLoading) "Processing…" else "Ban User", onClick = { if (!isActionLoading) showBanDialog = true }, modifier = Modifier.fillMaxWidth(), style = TruCallerButtonStyle.Danger)
                    Spacer(modifier = Modifier.height(8.dp))
                    TruCallerButton(text = if (isActionLoading) "Processing…" else "Delete User", onClick = { if (!isActionLoading) showDeleteDialog = true }, modifier = Modifier.fillMaxWidth(), style = TruCallerButtonStyle.Secondary)

                    actionError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Trust Level
                    Text("Trust Level", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    TruCallerCard(elevation = 1.dp) {
                        val trustColor = when (u.trustLevel) {
                            "BASIC" -> Color(0xFF2196F3)
                            "TRUSTED" -> Color(0xFF4CAF50)
                            "VERIFIED" -> Color(0xFFFF9800)
                            "AUTHORITY" -> Color(0xFF9C27B0)
                            else -> colorScheme.onSurfaceVariant
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = trustColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.trustLevel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = trustColor)
                                Text("Score: ${u.trustScore}/100", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                            }
                            TruCallerBadge(text = "${u.trustScore}", type = BadgeType.Custom, color = trustColor, backgroundColor = trustColor.copy(alpha = 0.12f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TruCallerButton(text = "-10", onClick = {
                                val newScore = maxOf(u.trustScore - 10, 0)
                                coroutineScope.launch {
                                    val r = ApiClient.updateUserTrust(userId, trustScore = newScore)
                                    if (r.success) user = u.copy(trustScore = newScore, trustLevel = trustLevelFromScore(newScore))
                                }
                            }, modifier = Modifier.weight(1f), style = TruCallerButtonStyle.Danger)
                            TruCallerButton(text = "+10", onClick = {
                                val newScore = minOf(u.trustScore + 10, 100)
                                coroutineScope.launch {
                                    val r = ApiClient.updateUserTrust(userId, trustScore = newScore)
                                    if (r.success) user = u.copy(trustScore = newScore, trustLevel = trustLevelFromScore(newScore))
                                }
                            }, modifier = Modifier.weight(1f), style = TruCallerButtonStyle.Primary)
                            TruCallerButton(text = "Authority", onClick = {
                                coroutineScope.launch {
                                    val r = ApiClient.updateUserTrust(userId, trustScore = 100, trustLevel = "AUTHORITY")
                                    if (r.success) user = u.copy(trustScore = 100, trustLevel = "AUTHORITY")
                                }
                            }, modifier = Modifier.weight(1f), style = TruCallerButtonStyle.Secondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Devices
                    Text("Devices (${devices.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (devices.isEmpty()) {
                        Text("No devices registered", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        devices.forEach { device ->
                            val statusBadgeType = when (device.status) {
                                "ACTIVE" -> BadgeType.Success
                                "STOLEN" -> BadgeType.Spam
                                "LOCKED" -> BadgeType.Warning
                                else -> BadgeType.Info
                            }
                            val deviceId = device.deviceId.ifBlank { device.id }
                            TruCallerCard(modifier = Modifier.padding(vertical = 4.dp).clickable { navController.navigate("admin_device_detail/$deviceId") }, elevation = 1.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${device.manufacturer} ${device.model}".trim(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                                        if (device.osVersion.isNotBlank()) Text("OS: ${device.osVersion}", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                        if (device.lastSeen.isNotBlank()) Text("Last Seen: ${formatRelativeTime(device.lastSeen)}", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                        if (device.lastIp.isNotBlank()) Text("Last IP: ${device.lastIp}", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    TruCallerBadge(text = device.status, type = statusBadgeType)
                                    Icon(Icons.Default.ChevronRight, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
            }
        }
    }
}
