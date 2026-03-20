package com.byron.trucaller.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.CallLogEntry
import com.byron.trucaller.data.model.CallType
import com.byron.trucaller.ui.components.AvatarIndicator
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.CallLogFilter
import com.byron.trucaller.viewmodel.CallLogViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallLogScreen(
    rootNavController: NavController,
    callLogViewModel: CallLogViewModel
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

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
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Action sheet state
    var selectedEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Show Snackbar when actionMessage changes
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage!!)
            callLogViewModel.clearActionMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("call_log_screen")
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // -- Premium Header with gradient --
        val missedCount by remember { derivedStateOf { callLogEntries.count { it.callType == CallType.MISSED } } }

        TruCallerHeader(
            title = "Call Log",
            subtitle = "${callLogEntries.size} calls",
            gradientColors = listOf(colorScheme.surface, colorScheme.background),
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (missedCount > 0) {
                        TruCallerBadge(
                            text = "$missedCount missed",
                            type = BadgeType.Spam,
                            icon = Icons.AutoMirrored.Filled.PhoneMissed
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Recordings",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    rootNavController.navigate("call_recordings")
                                }
                            )
                        }
                    }
                }
            }
        )

        // -- Search bar below header --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            TruCallerTextField(
                value = searchQuery,
                onValueChange = { callLogViewModel.setSearchQuery(it) },
                placeholder = "Search calls...",
                isSearch = true,
                showClearButton = true,
                onClear = { callLogViewModel.setSearchQuery("") },
                contentDesc = "Search call log"
            )
        }

        // -- Filter chips --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CallLogFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val chipColor = when (filter) {
                    CallLogFilter.ALL -> colorScheme.primary
                    CallLogFilter.INCOMING -> Color(0xFF4CAF50)
                    CallLogFilter.OUTGOING -> Color(0xFF42A5F5)
                    CallLogFilter.MISSED -> colorScheme.error
                    CallLogFilter.BLOCKED -> Color(0xFF757575)
                }
                val count = when (filter) {
                    CallLogFilter.ALL -> callLogEntries.size
                    CallLogFilter.INCOMING -> callLogEntries.count { it.callType == CallType.INCOMING }
                    CallLogFilter.OUTGOING -> callLogEntries.count { it.callType == CallType.OUTGOING }
                    CallLogFilter.MISSED -> callLogEntries.count { it.callType == CallType.MISSED }
                    CallLogFilter.BLOCKED -> callLogEntries.count {
                        it.callType == CallType.BLOCKED || it.callType == CallType.REJECTED || it.isBlocked
                    }
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
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = if (filter == CallLogFilter.MISSED || filter == CallLogFilter.BLOCKED) colorScheme.onError else colorScheme.onPrimary,
                        containerColor = colorScheme.surfaceVariant,
                        labelColor = colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colorScheme.outline,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        // -- Content --
        if (!hasCallLogPermission) {
            CallLogPermissionPrompt(onGrant = {
                permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            })
        } else if (isLoading) {
            ShimmerLoadingList()
        } else {
            val filtered by remember(callLogEntries, selectedFilter, searchQuery) {
                derivedStateOf { callLogViewModel.getFilteredEntries() }
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(300))
            ) {
                if (filtered.isEmpty()) {
                    val (emptyTitle, emptySubtitle) = when (selectedFilter) {
                        CallLogFilter.ALL -> Pair("No calls found", "Your call history will appear here once you start making or receiving calls.")
                        CallLogFilter.INCOMING -> Pair("No incoming calls", "Incoming calls will appear here.")
                        CallLogFilter.OUTGOING -> Pair("No outgoing calls", "Outgoing calls will appear here.")
                        CallLogFilter.MISSED -> Pair("No missed calls", "You haven't missed any calls. Nice!")
                        CallLogFilter.BLOCKED -> Pair("No blocked calls", "Calls from blocked numbers will appear here.")
                    }
                    EmptyStateView(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        icon = EmptyStateIcon.CALLS
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            filtered,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> "call_log_entry" }
                        ) { index, entry ->
                            // Date separator
                            val showDateHeader = index == 0 || !isSameDayCallLog(
                                filtered[index - 1].timestamp,
                                entry.timestamp
                            )
                            if (showDateHeader) {
                                CallLogDateHeader(entry.timestamp)
                            }

                            // Staggered slide-in animation
                            var itemVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(entry.id) {
                                itemVisible = true
                            }

                            AnimatedVisibility(
                                visible = itemVisible,
                                enter = slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetY = { it / 2 }
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = (index * 30).coerceAtMost(300)
                                    )
                                )
                            ) {
                                SwipeableCallLogItem(
                                    entry = entry,
                                    onClick = {
                                        val encoded = java.net.URLEncoder.encode(entry.phoneNumber, "UTF-8")
                                        rootNavController.navigate("caller_id_lookup/$encoded")
                                    },
                                    onLongPress = {
                                        selectedEntry = entry
                                        showActionSheet = true
                                    },
                                    onCall = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${entry.phoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    onBlock = {
                                        val encoded = java.net.URLEncoder.encode(entry.phoneNumber, "UTF-8")
                                        rootNavController.navigate("caller_id_lookup/$encoded")
                                    }
                                )
                            }
                        }
                        // Bottom padding
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    } // end Column

        // -- Snackbar at bottom --
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurface,
                    actionColor = colorScheme.primary
                )
            }
        )

    } // end Box

    // -- Action Bottom Sheet --
    if (showActionSheet && selectedEntry != null) {
        val entry = selectedEntry!!
        val displayName = entry.name ?: entry.phoneNumber

        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedEntry = null
            },
            sheetState = sheetState,
            containerColor = colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                if (entry.name != null) {
                    Text(
                        text = entry.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider(color = colorScheme.outlineVariant)

                // Block / Unblock
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (entry.isBlocked) "Unblock number" else "Block number",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (entry.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = null,
                            tint = if (entry.isBlocked) Color(0xFF4CAF50) else colorScheme.error
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showActionSheet = false
                            selectedEntry = null
                        }
                        if (entry.isBlocked) {
                            callLogViewModel.unblockNumber(entry.phoneNumber, entry.name, context)
                        } else {
                            callLogViewModel.blockNumber(entry.phoneNumber, entry.name, context)
                        }
                    }
                )

                // Spam / Remove from spam
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (entry.isSpam) "Remove from spam" else "Report as spam",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (entry.isSpam) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (entry.isSpam) Color(0xFF4CAF50) else Color(0xFFFFA000)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showActionSheet = false
                            selectedEntry = null
                        }
                        if (entry.isSpam) {
                            callLogViewModel.removeFromSpam(entry.phoneNumber, entry.name)
                        } else {
                            callLogViewModel.reportSpam(entry.phoneNumber, entry.name)
                        }
                    }
                )

                // View caller ID
                ListItem(
                    headlineContent = {
                        Text(
                            text = "View caller ID",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showActionSheet = false
                            selectedEntry = null
                        }
                        val encoded = java.net.URLEncoder.encode(entry.phoneNumber, "UTF-8")
                        rootNavController.navigate("caller_id_lookup/$encoded")
                    }
                )
            }
        }
    }
}

// -- Date Header --

@Composable
private fun CallLogDateHeader(timestamp: Long) {
    val colorScheme = MaterialTheme.colorScheme
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
                .background(colorScheme.outlineVariant)
        )
        Text(
            text = label,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(colorScheme.outlineVariant)
        )
    }
}

// -- Permission Prompt --

@Composable
private fun CallLogPermissionPrompt(onGrant: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
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
                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Phone, null,
                modifier = Modifier.size(40.dp), tint = colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Call Log Permission Required",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow call log access to view your call history with caller ID and spam protection.",
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onGrant,
            modifier = Modifier
                .background(colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Grant Permission",
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// -- Swipeable Call Log Item --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCallLogItem(
    entry: CallLogEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onCall()
                    false // Reset after action
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onBlock()
                    false // Reset after action
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                            SwipeToDismissBoxValue.EndToStart -> colorScheme.error
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Call",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Block",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Block",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        CallLogItem(
            entry = entry,
            onClick = onClick,
            onLongPress = onLongPress
        )
    }
}

// -- Call Log Item --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogItem(
    entry: CallLogEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val callTypeColor = when (entry.callType) {
        CallType.INCOMING -> Color(0xFF4CAF50)
        CallType.OUTGOING -> Color(0xFF42A5F5)
        CallType.MISSED -> colorScheme.error
        CallType.REJECTED -> colorScheme.error
        CallType.BLOCKED -> colorScheme.error
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

    val displayName = entry.name ?: entry.phoneNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .background(
                if (entry.callType == CallType.MISSED) colorScheme.surfaceVariant else colorScheme.background
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar using TruCallerAvatar
        TruCallerAvatar(
            name = displayName,
            size = 52.dp,
            indicator = AvatarIndicator.Category,
            indicatorColor = callTypeColor,
            contentDesc = "$displayName avatar"
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontWeight = if (entry.callType == CallType.MISSED) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (entry.isSpam) colorScheme.error else colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCallLogTime(entry.timestamp),
                    fontSize = 11.sp,
                    color = if (entry.callType == CallType.MISSED) colorScheme.error else colorScheme.onSurface.copy(alpha = 0.5f),
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
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Blocked badge
                if (entry.isBlocked) {
                    TruCallerBadge(
                        text = "Blocked",
                        type = BadgeType.Spam,
                        icon = Icons.Default.Block
                    )
                }

                // Spam badge using TruCallerBadge
                if (entry.isSpam && entry.spamScore > 30) {
                    TruCallerBadge(
                        text = "Spam",
                        type = BadgeType.Spam,
                        icon = Icons.Default.Warning
                    )
                }
            }

            // Show number beneath name if name is available
            if (entry.name != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.phoneNumber,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
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
            .background(colorScheme.outlineVariant)
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
