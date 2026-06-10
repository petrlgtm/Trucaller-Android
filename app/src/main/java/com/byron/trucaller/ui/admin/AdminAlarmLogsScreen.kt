package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val ALARM_PAGE_SIZE = 50

private val alarmIsoFormats = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ssXXX"
)

private fun parseAlarmIsoToMillis(dateStr: String): Long? {
    for (fmt in alarmIsoFormats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US).also { it.timeZone = TimeZone.getTimeZone("UTC") }
            return sdf.parse(dateStr)?.time
        } catch (_: Exception) { }
    }
    return null
}

private fun formatAlarmShortDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.US).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAlarmLogsScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme

    val allLogs = remember { mutableStateListOf<Map<String, Any>>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(0) }

    val lazyListState = rememberLazyListState()

    // Filters (client-side on loaded data)
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedResult by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    suspend fun loadPage(pageSkip: Int) {
        val result = ApiClient.getAdminAlarmLogs(skip = pageSkip, limit = ALARM_PAGE_SIZE)
        if (result.success && result.data != null) {
            if (pageSkip == 0) { allLogs.clear(); allLogs.addAll(result.data) }
            else allLogs.addAll(result.data)
            hasMore = result.data.size >= ALARM_PAGE_SIZE
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

    val filteredLogs by remember(allLogs.toList(), selectedType, selectedResult, startDate, endDate) {
        derivedStateOf {
            allLogs
                .let { logs -> if (selectedType != null) logs.filter { it["type"]?.toString() == selectedType } else logs }
                .let { logs -> if (selectedResult != null) logs.filter { it["result"]?.toString() == selectedResult } else logs }
                .let { logs ->
                    if (startDate != null || endDate != null) {
                        logs.filter { log ->
                            val ts = log["triggeredAt"]?.toString() ?: return@filter false
                            val ms = parseAlarmIsoToMillis(ts) ?: return@filter false
                            val afterStart = startDate?.let { ms >= it } ?: true
                            val beforeEnd = endDate?.let { ms < it + 86_400_000L } ?: true
                            afterStart && beforeEnd
                        }
                    } else logs
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Alarm Logs (${filteredLogs.size}${if (hasMore && selectedType == null && selectedResult == null) "+" else ""})",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
        )

        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text("Type", fontSize = 12.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeOptions = listOf(null to "All", "REMOTE_ALARM" to "Remote Alarm", "LOCATION_REQUEST" to "Location", "LOCK_DEVICE" to "Lock Device")
                typeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedType == value,
                        onClick = { selectedType = value },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f), selectedLabelColor = colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Result", fontSize = 12.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val resultOptions = listOf(
                    null to ("All" to colorScheme.primary),
                    "SUCCESS" to ("SUCCESS" to Color(0xFF4CAF50)),
                    "FAILED" to ("FAILED" to colorScheme.error),
                    "PENDING" to ("PENDING" to colorScheme.primary)
                )
                resultOptions.forEach { (value, pair) ->
                    val (label, color) = pair
                    FilterChip(
                        selected = selectedResult == value,
                        onClick = { selectedResult = value },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.15f), selectedLabelColor = color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Date Range", fontSize = 12.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val hasDateFilter = startDate != null || endDate != null
                val dateLabel = if (hasDateFilter) "${startDate?.let { formatAlarmShortDate(it) } ?: "..."} - ${endDate?.let { formatAlarmShortDate(it) } ?: "..."}" else "Select dates"
                FilterChip(
                    selected = hasDateFilter,
                    onClick = { showDateRangePicker = true },
                    label = { Text(dateLabel) },
                    leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f), selectedLabelColor = colorScheme.primary)
                )
                if (hasDateFilter) {
                    FilterChip(selected = false, onClick = { startDate = null; endDate = null }, label = { Text("Clear") },
                        leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) })
                }
            }
        }

        if (showDateRangePicker) {
            val datePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = startDate, initialSelectedEndDateMillis = endDate)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { showDateRangePicker = false }, sheetState = sheetState, containerColor = colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
                        Text("Select Date Range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { startDate = datePickerState.selectedStartDateMillis; endDate = datePickerState.selectedEndDateMillis; showDateRangePicker = false }, enabled = datePickerState.selectedStartDateMillis != null) { Text("Apply") }
                    }
                    DateRangePicker(state = datePickerState, modifier = Modifier.fillMaxWidth().height(500.dp), title = null, showModeToggle = true)
                }
            }
        }

        when {
            isLoading -> ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            filteredLogs.isEmpty() -> EmptyStateView(title = "No Alarm Logs", subtitle = "No alarm activity recorded yet", icon = EmptyStateIcon.GENERIC)
            else -> LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)
            ) {
                items(filteredLogs, key = { it["_id"]?.toString() ?: it.hashCode().toString() }) { log ->
                    val deviceId = log["deviceId"]?.toString() ?: "Unknown"
                    val typeStr = log["type"]?.toString() ?: ""
                    val resultStr = log["result"]?.toString() ?: ""
                    val triggeredByName = log["triggeredByName"]?.toString() ?: "Unknown"
                    val triggeredByRole = log["triggeredByRole"]?.toString() ?: "user"
                    val notes = log["notes"]?.toString()
                    val triggeredAt = log["triggeredAt"]?.toString() ?: ""

                    val (typeLabel, typeBadgeType) = when (typeStr) {
                        "REMOTE_ALARM" -> "Remote Alarm" to BadgeType.Spam
                        "LOCATION_REQUEST" -> "Location Request" to BadgeType.Info
                        "LOCK_DEVICE" -> "Lock Device" to BadgeType.Warning
                        else -> typeStr to BadgeType.Info
                    }
                    val resultBadgeType = when (resultStr) {
                        "SUCCESS" -> BadgeType.Success
                        "FAILED" -> BadgeType.Spam
                        else -> BadgeType.Warning
                    }

                    TruCallerCard(modifier = Modifier.padding(vertical = 3.dp), elevation = 0.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Device: $deviceId", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorScheme.onSurface)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(triggeredByName, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                                    TruCallerBadge(
                                        text = if (triggeredByRole == "admin") "Admin" else "User",
                                        type = if (triggeredByRole == "admin") BadgeType.Custom else BadgeType.Info,
                                        color = if (triggeredByRole == "admin") Brand else null,
                                        backgroundColor = if (triggeredByRole == "admin") Brand.copy(alpha = 0.1f) else null
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TruCallerBadge(text = typeLabel, type = typeBadgeType)
                                Spacer(modifier = Modifier.height(4.dp))
                                TruCallerBadge(text = resultStr.ifBlank { "PENDING" }, type = resultBadgeType)
                            }
                        }
                        if (!notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(notes, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        if (triggeredAt.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formatRelativeTime(triggeredAt), fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    if (isLoadingMore) {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                        }
                    } else if (!hasMore && allLogs.size > ALARM_PAGE_SIZE) {
                        Text("All ${allLogs.size} logs loaded", modifier = Modifier.fillMaxWidth().padding(Spacing.md), textAlign = TextAlign.Center, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    }
                }
                item { Spacer(modifier = Modifier.height(Spacing.md)) }
            }
        }
    }
}
