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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.User
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingCard
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
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

    val colorScheme = MaterialTheme.colorScheme

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
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status: ", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                            TruCallerBadge(
                                text = if (u.isActive) "Active" else "Inactive",
                                type = if (u.isActive) BadgeType.Success else BadgeType.Spam
                            )
                        }
                        Text(
                            "Joined: ${formatRelativeTime(u.createdAt)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        u.lastLogin?.let {
                            Text(
                                "Last Login: ${formatRelativeTime(it)}",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
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
                            modifier = Modifier.padding(vertical = 4.dp),
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
                                        "Last IP: ${device.lastIp}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                TruCallerBadge(
                                    text = device.status.name,
                                    type = statusBadgeType
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
