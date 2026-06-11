package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.AdminDeviceSummary
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime

private const val DEVICES_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevicesScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme

    val devices = remember { mutableStateListOf<AdminDeviceSummary>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    suspend fun loadPage(pageSkip: Int) {
        val result = ApiClient.getAdminDevices(skip = pageSkip, limit = DEVICES_PAGE_SIZE)
        if (result.success && result.data != null) {
            if (pageSkip == 0) { devices.clear(); devices.addAll(result.data) }
            else devices.addAll(result.data)
            hasMore = result.data.size >= DEVICES_PAGE_SIZE
            skip = pageSkip + result.data.size
        }
    }

    LaunchedEffect(Unit) {
        loadPage(0)
        isLoading = false
    }

    LaunchedEffect(lazyListState, hasMore) {
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3 && hasMore && !isLoadingMore && !isLoading) {
                isLoadingMore = true
                loadPage(skip)
                isLoadingMore = false
            }
        }
    }

    val filtered by remember(searchQuery, devices) {
        derivedStateOf {
            if (searchQuery.isBlank()) devices
            else {
                val q = searchQuery.lowercase()
                devices.filter { d ->
                    d.model.lowercase().contains(q) ||
                    d.manufacturer.lowercase().contains(q) ||
                    d.deviceId.lowercase().contains(q) ||
                    d.lastIp.contains(q)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Devices (${filtered.size}${if (hasMore && searchQuery.isBlank()) "+" else ""})",
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
            placeholder = { Text("Search device, model, IP...") },
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

        if (isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        when {
            isLoading -> ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            filtered.isEmpty() -> EmptyStateView(
                title = "No Devices Found",
                subtitle = if (searchQuery.isNotBlank()) "No devices match \"$searchQuery\""
                else "No devices have been registered yet",
                icon = EmptyStateIcon.GENERIC
            )
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)
                ) {
                    items(filtered, key = { it.deviceId.ifBlank { it.id } }) { device ->
                        val deviceId = device.deviceId.ifBlank { device.id }
                        val model = device.model.ifBlank { "Unknown" }
                        val manufacturer = device.manufacturer
                        val status = device.status
                        val lastIp = device.lastIp
                        val lastSeen = device.lastSeen

                        val badgeType = when (status) {
                            "ACTIVE" -> BadgeType.Success
                            "STOLEN" -> BadgeType.Spam
                            "LOCKED" -> BadgeType.Warning
                            else -> BadgeType.Info
                        }

                        TruCallerCard(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable { navController.navigate("admin_device_detail/$deviceId") },
                            elevation = 1.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "$manufacturer $model".trim(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (lastIp.isNotBlank()) {
                                            Text(
                                                lastIp,
                                                fontSize = 12.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        if (lastSeen.isNotBlank()) {
                                            Text(
                                                formatRelativeTime(lastSeen),
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        deviceId.take(20),
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                TruCallerBadge(text = status, type = badgeType)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ChevronRight, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    item {
                        if (isLoadingMore) {
                            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                            }
                        } else if (!hasMore && filtered.size > DEVICES_PAGE_SIZE) {
                            Text(
                                "All ${filtered.size} devices loaded",
                                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.md)) }
                }
            }
        }
    }
}
