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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val PAGE_SIZE = 20

private val isoDateFormats = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ssXXX"
)

/** Parse an ISO date string to epoch millis, or null on failure. */
private fun parseIsoToMillis(dateStr: String): Long? {
    for (fmt in isoDateFormats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateStr)
            if (date != null) return date.time
        } catch (_: Exception) { }
    }
    return null
}

/** Format epoch millis to a short date label like "Mar 1". */
private fun formatShortDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.US)
    return sdf.format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAlarmLogsScreen(navController: NavController, alarmViewModel: AlarmViewModel) {
    val allLogs by alarmViewModel.allLogs.collectAsState(initial = emptyList())
    var isInitialLoad by remember { mutableStateOf(true) }

    if (allLogs.isNotEmpty() && isInitialLoad) {
        isInitialLoad = false
    }

    var selectedType by remember { mutableStateOf<AlarmType?>(null) }
    var selectedResult by remember { mutableStateOf<AlarmResult?>(null) }

    // Date range filter state
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val filteredLogs by remember(allLogs, selectedType, selectedResult, startDate, endDate) {
        derivedStateOf {
            allLogs
                .let { logs -> if (selectedType != null) logs.filter { it.type == selectedType } else logs }
                .let { logs -> if (selectedResult != null) logs.filter { it.result == selectedResult } else logs }
                .let { logs ->
                    if (startDate != null || endDate != null) {
                        logs.filter { log ->
                            val logMillis = parseIsoToMillis(log.triggeredAt) ?: return@filter false
                            val afterStart = startDate?.let { logMillis >= it } ?: true
                            // endDate selection represents the start of that day; include the full day
                            val beforeEnd = endDate?.let { logMillis < it + 86_400_000L } ?: true
                            afterStart && beforeEnd
                        }
                    } else logs
                }
                .sortedByDescending { it.triggeredAt }
        }
    }

    // Pagination state
    var currentPage by remember { mutableIntStateOf(1) }
    val lazyListState = rememberLazyListState()

    // Reset page when filters change
    LaunchedEffect(selectedType, selectedResult, startDate, endDate) {
        currentPage = 1
    }

    val paginatedItems by remember(filteredLogs, currentPage) {
        derivedStateOf { filteredLogs.take(PAGE_SIZE * currentPage) }
    }
    val hasMoreItems by remember(paginatedItems, filteredLogs) {
        derivedStateOf { paginatedItems.size < filteredLogs.size }
    }

    // Detect scroll near bottom to load next page
    LaunchedEffect(lazyListState, hasMoreItems) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to totalItems
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3 && hasMoreItems) {
                currentPage++
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Alarm Logs (${filteredLogs.size})",
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

        // Filter chips - Type
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(
                "Type",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = colorScheme.primary
                    )
                )
                AlarmType.entries.forEach { type ->
                    val label = when (type) {
                        AlarmType.REMOTE_ALARM -> "Remote Alarm"
                        AlarmType.LOCATION_REQUEST -> "Location"
                        AlarmType.LOCK_DEVICE -> "Lock Device"
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Result",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedResult == null,
                    onClick = { selectedResult = null },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = colorScheme.primary
                    )
                )
                AlarmResult.entries.forEach { result ->
                    val resultColor = when (result) {
                        AlarmResult.SUCCESS -> Color(0xFF4CAF50)
                        AlarmResult.FAILED -> colorScheme.error
                        AlarmResult.PENDING -> colorScheme.primary
                    }
                    FilterChip(
                        selected = selectedResult == result,
                        onClick = { selectedResult = if (selectedResult == result) null else result },
                        label = { Text(result.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = resultColor.copy(alpha = 0.15f),
                            selectedLabelColor = resultColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Date Range",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasDateFilter = startDate != null || endDate != null
                val dateLabel = if (hasDateFilter) {
                    val start = startDate?.let { formatShortDate(it) } ?: "..."
                    val end = endDate?.let { formatShortDate(it) } ?: "..."
                    "$start - $end"
                } else {
                    "Select dates"
                }

                FilterChip(
                    selected = hasDateFilter,
                    onClick = { showDateRangePicker = true },
                    label = { Text(dateLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Date range",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = colorScheme.primary
                    )
                )
                if (hasDateFilter) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            startDate = null
                            endDate = null
                        },
                        label = { Text("Clear") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear date filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Date range picker bottom sheet
        if (showDateRangePicker) {
            val dateRangePickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = startDate,
                initialSelectedEndDateMillis = endDate
            )
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showDateRangePicker = false },
                sheetState = sheetState,
                containerColor = colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDateRangePicker = false }) {
                            Text("Cancel")
                        }
                        Text(
                            "Select Date Range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                startDate = dateRangePickerState.selectedStartDateMillis
                                endDate = dateRangePickerState.selectedEndDateMillis
                                showDateRangePicker = false
                            },
                            enabled = dateRangePickerState.selectedStartDateMillis != null
                        ) {
                            Text("Apply")
                        }
                    }
                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp),
                        title = null,
                        showModeToggle = true
                    )
                }
            }
        }

        when {
            isInitialLoad && allLogs.isEmpty() -> {
                ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            }
            filteredLogs.isEmpty() -> {
                EmptyStateView(
                    title = "No Alarm Logs",
                    subtitle = "No alarm activity has been recorded yet",
                    icon = EmptyStateIcon.GENERIC
                )
            }
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)
                ) {
                    items(paginatedItems, key = { it.id }) { log ->
                        val typeBadgeType = when (log.type) {
                            AlarmType.REMOTE_ALARM -> BadgeType.Spam
                            AlarmType.LOCATION_REQUEST -> BadgeType.Info
                            AlarmType.LOCK_DEVICE -> BadgeType.Warning
                        }
                        val typeLabel = when (log.type) {
                            AlarmType.REMOTE_ALARM -> "Remote Alarm"
                            AlarmType.LOCATION_REQUEST -> "Location Request"
                            AlarmType.LOCK_DEVICE -> "Lock Device"
                        }
                        val resultBadgeType = when (log.result) {
                            AlarmResult.SUCCESS -> BadgeType.Success
                            AlarmResult.FAILED -> BadgeType.Spam
                            AlarmResult.PENDING -> BadgeType.Warning
                        }

                        TruCallerCard(
                            modifier = Modifier.padding(vertical = 3.dp),
                            elevation = 0.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Device: ${log.deviceId}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            log.triggeredByName,
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        TruCallerBadge(
                                            text = if (log.triggeredByRole == "admin") "Admin" else "User",
                                            type = if (log.triggeredByRole == "admin") BadgeType.Custom else BadgeType.Info,
                                            color = if (log.triggeredByRole == "admin") Brand else null,
                                            backgroundColor = if (log.triggeredByRole == "admin") Brand.copy(alpha = 0.1f) else null
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    TruCallerBadge(
                                        text = typeLabel,
                                        type = typeBadgeType
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TruCallerBadge(
                                        text = log.result.name,
                                        type = resultBadgeType
                                    )
                                }
                            }
                            if (log.notes != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    log.notes,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatRelativeTime(log.triggeredAt),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Pagination footer
                    item {
                        if (hasMoreItems) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary
                                )
                            }
                        } else if (paginatedItems.size > PAGE_SIZE) {
                            Text(
                                "All items loaded",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
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
