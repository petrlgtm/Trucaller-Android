package com.byron.trucaller.ui.main

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import java.time.Duration
import java.time.Instant

/**
 * Formats the duration between startTime and lastSeen into human-readable
 * "time spent" like "2d 5h", "3h 20m", "45m".
 */
private fun formatDuration(startTime: String, lastSeen: String): String {
    return try {
        val start = Instant.parse(startTime)
        val end = Instant.parse(lastSeen)
        val duration = Duration.between(start, end)

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Just now"
        }
    } catch (_: Exception) {
        ""
    }
}

/**
 * Checks if this is the currently active location (lastSeen within last 10 minutes).
 */
private fun isCurrentLocation(lastSeen: String): Boolean {
    return try {
        val end = Instant.parse(lastSeen)
        Duration.between(end, Instant.now()).toMinutes() < 10
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpLogsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user

    LaunchedEffect(user?.id) {
        user?.id?.let { deviceViewModel.loadUserDevice(it) }
    }

    val device by deviceViewModel.userDevice.collectAsState()

    val ipLogs by remember(device?.id) {
        device?.id?.let { deviceViewModel.getIpLogs(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val sortedLogs = remember(ipLogs) {
        ipLogs.sortedByDescending { it.startTime.ifBlank { it.timestamp } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Location History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${sortedLogs.size} locations",
                            fontSize = 12.sp,
                            color = colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        },
        containerColor = colorScheme.background
    ) { innerPadding ->
        if (sortedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = EmptyStateIcon.GENERIC,
                    title = "No Location Data Yet",
                    subtitle = "Location sessions will appear here as your device moves between places"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(sortedLogs, key = { _, log -> log.id }) { _, log ->
                    val isWifi = log.networkType == "wifi"
                    val networkColor = if (isWifi) Color(0xFF4CAF50) else BrandGold
                    val startTime = log.startTime.ifBlank { log.timestamp }
                    val lastSeen = log.lastSeen.ifBlank { log.timestamp }
                    val duration = formatDuration(startTime, lastSeen)
                    val isCurrent = isCurrentLocation(lastSeen)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            // Location pin icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isCurrent) colorScheme.primary.copy(alpha = 0.15f)
                                        else colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    tint = if (isCurrent) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                // City, Country — prominent
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${log.city}, ${log.country}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colorScheme.onSurface
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    colorScheme.primary.copy(alpha = 0.15f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "CURRENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // ISP
                                Text(
                                    log.isp,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Time spent row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    if (duration.isNotEmpty()) {
                                        Text(
                                            duration,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.primary
                                        )
                                        Text(
                                            "spent here",
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Bottom row: relative time + network badge + IP
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        formatRelativeTime(startTime),
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                networkColor.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower,
                                                null,
                                                tint = networkColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                if (isWifi) "WiFi" else "Mobile",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = networkColor
                                            )
                                        }
                                    }

                                    // IP address
                                    Text(
                                        log.ipAddress,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
