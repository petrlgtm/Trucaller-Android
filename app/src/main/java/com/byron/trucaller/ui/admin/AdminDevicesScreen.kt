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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevicesScreen(navController: NavController, deviceViewModel: DeviceViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var isInitialLoad by remember { mutableStateOf(true) }
    val allDevices by deviceViewModel.allDevices.collectAsState(initial = emptyList())

    if (allDevices.isNotEmpty() && isInitialLoad) {
        isInitialLoad = false
    }

    val filtered by remember(searchQuery, allDevices) {
        derivedStateOf {
            if (searchQuery.isBlank()) allDevices
            else {
                val q = searchQuery.lowercase()
                allDevices.filter { d ->
                    d.model.lowercase().contains(q) ||
                            d.manufacturer.lowercase().contains(q) ||
                            d.deviceId.lowercase().contains(q) ||
                            d.lastIp.contains(q)
                }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Devices (${filtered.size})",
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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search device, model...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.primary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant
            )
        )

        when {
            isInitialLoad && allDevices.isEmpty() -> {
                ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            }
            filtered.isEmpty() -> {
                EmptyStateView(
                    title = "No Devices Found",
                    subtitle = if (searchQuery.isNotBlank()) "No devices match \"$searchQuery\""
                    else "No devices have been registered yet",
                    icon = EmptyStateIcon.GENERIC
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md)
                ) {
                    items(filtered, key = { it.id }) { device ->
                        val statusBadgeType = when (device.status) {
                            DeviceStatus.ACTIVE -> BadgeType.Success
                            DeviceStatus.STOLEN -> BadgeType.Spam
                            DeviceStatus.INACTIVE -> BadgeType.Info
                            DeviceStatus.FLAGGED -> BadgeType.Warning
                        }

                        TruCallerCard(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable { navController.navigate("admin_device_detail/${device.id}") },
                            elevation = 1.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.manufacturer} ${device.model}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            device.lastIp,
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            formatRelativeTime(device.lastSeen),
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TruCallerBadge(
                                    text = device.status.name,
                                    type = statusBadgeType
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.md)) }
                }
            }
        }
    }
}
