package com.byron.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.CallLogEntry
import com.byron.trucaller.data.model.CallType
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.viewmodel.CallLogFilter
import com.byron.trucaller.viewmodel.CallLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    rootNavController: NavController,
    callLogViewModel: CallLogViewModel
) {
    val context = LocalContext.current

    val callLogEntries by callLogViewModel.callLogEntries.collectAsState()
    val isLoading by callLogViewModel.isLoading.collectAsState()
    val actionMessage by callLogViewModel.actionMessage.collectAsState()
    val selectedFilter by callLogViewModel.selectedFilter.collectAsState()
    val searchQuery by callLogViewModel.searchQuery.collectAsState()

    var hasCallLogPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var showContent by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLogPermission = granted
        if (granted) {
            callLogViewModel.loadCallLog(context)
        }
    }

    LaunchedEffect(hasCallLogPermission) {
        if (hasCallLogPermission) {
            callLogViewModel.loadCallLog(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
        showContent = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // -- Premium Header with gradient --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandDark, Color(0xFF0D0D0D))
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Call Log",
                            color = Brand,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "${callLogEntries.size} calls",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    // Missed calls badge
                    val missedCount = callLogEntries.count { it.callType == CallType.MISSED }
                    if (missedCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Danger.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PhoneMissed,
                                    null,
                                    tint = Danger,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$missedCount missed",
                                    color = Danger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { callLogViewModel.setSearchQuery(it) },
                    placeholder = { Text("Search calls...", color = Inactive, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search, null,
                            tint = Inactive, modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        cursorColor = Brand,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }
        }

        // -- Filter chips --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CallLogFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val chipColor = when (filter) {
                    CallLogFilter.ALL -> Brand
                    CallLogFilter.INCOMING -> Success
                    CallLogFilter.OUTGOING -> Color(0xFF42A5F5)
                    CallLogFilter.MISSED -> Danger
                }
                val count = when (filter) {
                    CallLogFilter.ALL -> callLogEntries.size
                    CallLogFilter.INCOMING -> callLogEntries.count { it.callType == CallType.INCOMING }
                    CallLogFilter.OUTGOING -> callLogEntries.count { it.callType == CallType.OUTGOING }
                    CallLogFilter.MISSED -> callLogEntries.count { it.callType == CallType.MISSED }
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { callLogViewModel.setFilter(filter) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                filter.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$count",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color(0xFF1A1A1A) else Inactive
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = if (filter == CallLogFilter.MISSED) Color.White else Color(0xFF1A1A1A),
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0xFF333333),
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        // -- Action message --
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = SurfaceCard,
                action = {
                    TextButton(onClick = { callLogViewModel.clearActionMessage() }) {
                        Text("OK", color = Brand, fontWeight = FontWeight.Bold)
                    }
                }
            ) { Text(actionMessage!!, color = TextPrimary) }
        }

        // -- Content --
        if (!hasCallLogPermission) {
            CallLogPermissionPrompt(onGrant = {
                permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            })
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading call log...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val filtered = callLogViewModel.getFilteredEntries()

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(300))
            ) {
                if (filtered.isEmpty()) {
                    CallLogEmptyState(selectedFilter)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { index, entry ->
                            // Date separator
                            val showDateHeader = index == 0 || !isSameDayCallLog(
                                filtered[index - 1].timestamp,
                                entry.timestamp
                            )
                            if (showDateHeader) {
                                CallLogDateHeader(entry.timestamp)
                            }

                            CallLogItem(
                                entry = entry,
                                onClick = {
                                    val encoded = java.net.URLEncoder.encode(entry.phoneNumber, "UTF-8")
                                    rootNavController.navigate("caller_id_lookup/$encoded")
                                }
                            )
                        }
                        // Bottom padding
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

// -- Date Header --

@Composable
private fun CallLogDateHeader(timestamp: Long) {
    val label = formatCallLogDateHeader(timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(Color(0xFF2A2A2A))
        )
        Text(
            text = label,
            color = Inactive,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(Color(0xFF2A2A2A))
        )
    }
}

// -- Permission Prompt --

@Composable
private fun CallLogPermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Brand.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Phone, null,
                modifier = Modifier.size(40.dp), tint = Brand
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Call Log Permission Required",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow call log access to view your call history with caller ID and spam protection.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onGrant,
            modifier = Modifier
                .background(Brand, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Grant Permission",
                color = Color(0xFF1A1A1A),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// -- Empty State --

@Composable
private fun CallLogEmptyState(filter: CallLogFilter) {
    val (icon, message) = when (filter) {
        CallLogFilter.ALL -> Pair(Icons.Default.Phone, "No calls found")
        CallLogFilter.INCOMING -> Pair(Icons.Default.CallReceived, "No incoming calls")
        CallLogFilter.OUTGOING -> Pair(Icons.Default.CallMade, "No outgoing calls")
        CallLogFilter.MISSED -> Pair(Icons.AutoMirrored.Filled.PhoneMissed, "No missed calls")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = Inactive)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = TextSecondary, fontSize = 15.sp)
    }
}

// -- Call Log Item --

@Composable
private fun CallLogItem(
    entry: CallLogEntry,
    onClick: () -> Unit
) {
    val callTypeColor = when (entry.callType) {
        CallType.INCOMING -> Success
        CallType.OUTGOING -> Color(0xFF42A5F5)
        CallType.MISSED -> Danger
        CallType.REJECTED -> Danger
        CallType.BLOCKED -> Danger
    }

    val callTypeIcon = when (entry.callType) {
        CallType.INCOMING -> Icons.Default.CallReceived
        CallType.OUTGOING -> Icons.Default.CallMade
        CallType.MISSED -> Icons.AutoMirrored.Filled.PhoneMissed
        CallType.REJECTED -> Icons.Default.Close
        CallType.BLOCKED -> Icons.Default.Block
    }

    val callTypeLabel = when (entry.callType) {
        CallType.INCOMING -> "Incoming"
        CallType.OUTGOING -> "Outgoing"
        CallType.MISSED -> "Missed"
        CallType.REJECTED -> "Rejected"
        CallType.BLOCKED -> "Blocked"
    }

    // Initials for avatar
    val displayName = entry.name ?: entry.phoneNumber
    val initials = displayName
        .take(2)
        .uppercase()
        .let {
            if (it.firstOrNull()?.isDigit() == true || it.firstOrNull() == '+') "#" else it.take(1)
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (entry.callType == CallType.MISSED) SurfaceCard else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(
                            colors = if (entry.isSpam) {
                                listOf(Danger.copy(alpha = 0.2f), Danger.copy(alpha = 0.1f))
                            } else {
                                listOf(callTypeColor.copy(alpha = 0.2f), callTypeColor.copy(alpha = 0.1f))
                            }
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = if (entry.isSpam) Danger else callTypeColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Call type indicator dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = 18.dp, y = 18.dp)
                    .background(Background, CircleShape)
                    .padding(2.dp)
                    .background(callTypeColor, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontWeight = if (entry.callType == CallType.MISSED) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (entry.isSpam) Danger else TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCallLogTime(entry.timestamp),
                    fontSize = 11.sp,
                    color = if (entry.callType == CallType.MISSED) Danger else Inactive,
                    fontWeight = if (entry.callType == CallType.MISSED) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    callTypeIcon, null,
                    modifier = Modifier.size(14.dp),
                    tint = callTypeColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = callTypeLabel,
                    fontSize = 12.sp,
                    color = callTypeColor,
                    fontWeight = FontWeight.Medium
                )

                if (entry.duration > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDuration(entry.duration),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Spam badge
                if (entry.isSpam && entry.spamScore > 30) {
                    Box(
                        modifier = Modifier
                            .background(Danger.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning, null,
                                modifier = Modifier.size(11.dp),
                                tint = Danger
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Spam",
                                fontSize = 10.sp,
                                color = Danger,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            // Show number beneath name if name is available
            if (entry.name != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.phoneNumber,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Subtle divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 82.dp)
            .height(0.5.dp)
            .background(Color(0xFF222222))
    )
}

// -- Helpers --

private fun formatCallLogTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
        diff < TimeUnit.HOURS.toMillis(24) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale.US).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM", Locale.US).format(Date(timestamp))
    }
}

private fun formatCallLogDateHeader(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDayCalendar(cal, today) -> "Today"
        isSameDayCalendar(cal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(timestamp))
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private fun isSameDayCallLog(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return isSameDayCalendar(c1, c2)
}

private fun isSameDayCalendar(c1: Calendar, c2: Calendar): Boolean {
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
