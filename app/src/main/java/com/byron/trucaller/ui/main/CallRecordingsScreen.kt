package com.byron.trucaller.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.LogoBlueLight
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.CallRecording
import com.byron.trucaller.viewmodel.CallRecordingsViewModel
import com.byron.trucaller.viewmodel.RecordingDirection
import com.byron.trucaller.viewmodel.RecordingFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallRecordingsScreen(
    navController: NavController,
    callRecordingsViewModel: CallRecordingsViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val recordings by callRecordingsViewModel.recordings.collectAsState()
    val isLoading by callRecordingsViewModel.isLoading.collectAsState()
    val selectedFilter by callRecordingsViewModel.selectedFilter.collectAsState()
    val searchQuery by callRecordingsViewModel.searchQuery.collectAsState()
    val playbackState by callRecordingsViewModel.playbackState.collectAsState()
    val storageInfo by callRecordingsViewModel.storageInfo.collectAsState()
    val actionMessage by callRecordingsViewModel.actionMessage.collectAsState()
    val pendingDelete by callRecordingsViewModel.pendingDelete.collectAsState()

    var showStorageDialog by remember { mutableStateOf(false) }
    var expandedRecordingId by remember { mutableStateOf<String?>(null) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    // Auto-confirm delete after snackbar timeout
    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            kotlinx.coroutines.delay(5000L)
            callRecordingsViewModel.confirmDelete()
        }
    }

    // Stop playback when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            callRecordingsViewModel.stopPlayback()
        }
    }

    // Manage Storage Dialog
    if (showStorageDialog) {
        ManageStorageDialog(
            storageInfo = storageInfo,
            onDismiss = { showStorageDialog = false },
            onDeleteOld = { days ->
                callRecordingsViewModel.deleteOldRecordings(days)
                showStorageDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Header with back button
        TruCallerHeader(
            title = "Recordings",
            subtitle = "${recordings.size} recordings",
            trailingContent = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onSurface
                    )
                }
            }
        )

        // Storage Summary Card
        StorageSummaryCard(
            storageInfo = storageInfo,
            onManageStorage = { showStorageDialog = true }
        )

        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            TruCallerTextField(
                value = searchQuery,
                onValueChange = { callRecordingsViewModel.setSearchQuery(it) },
                placeholder = "Search recordings...",
                isSearch = true,
                showClearButton = true,
                onClear = { callRecordingsViewModel.setSearchQuery("") },
                contentDesc = "Search call recordings"
            )
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val chipColor = when (filter) {
                    RecordingFilter.ALL -> colorScheme.primary
                    RecordingFilter.INCOMING -> Color(0xFF4CAF50)
                    RecordingFilter.OUTGOING -> LogoBlueLight
                    RecordingFilter.STARRED -> BrandGold
                }
                val count = when (filter) {
                    RecordingFilter.ALL -> recordings.size
                    RecordingFilter.INCOMING -> recordings.count { it.direction == RecordingDirection.INCOMING }
                    RecordingFilter.OUTGOING -> recordings.count { it.direction == RecordingDirection.OUTGOING }
                    RecordingFilter.STARRED -> recordings.count { it.isStarred }
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { callRecordingsViewModel.setFilter(filter) },
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
                        selectedLabelColor = colorScheme.onPrimary,
                        containerColor = colorScheme.surface,
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

        // Action message / undo snackbar
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = colorScheme.surfaceVariant,
                action = {
                    if (pendingDelete != null) {
                        TextButton(onClick = { callRecordingsViewModel.undoDelete() }) {
                            Text("UNDO", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { callRecordingsViewModel.clearActionMessage() }) {
                            Text("OK", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) { Text(actionMessage!!, color = colorScheme.onSurface) }
        }

        // Content
        if (isLoading) {
            ShimmerLoadingList()
        } else {
            val filtered = callRecordingsViewModel.getFilteredRecordings()

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(300))
            ) {
                if (filtered.isEmpty()) {
                    val (emptyTitle, emptySubtitle) = when (selectedFilter) {
                        RecordingFilter.ALL -> Pair(
                            "No recordings yet",
                            "Call recordings will appear here when you record calls."
                        )
                        RecordingFilter.INCOMING -> Pair(
                            "No incoming recordings",
                            "Recordings of incoming calls will appear here."
                        )
                        RecordingFilter.OUTGOING -> Pair(
                            "No outgoing recordings",
                            "Recordings of outgoing calls will appear here."
                        )
                        RecordingFilter.STARRED -> Pair(
                            "No starred recordings",
                            "Long press a recording to star it for quick access."
                        )
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
                            key = { _, it -> it.id }
                        ) { index, recording ->
                            // Date separator
                            val showDateHeader = index == 0 || !isSameDay(
                                filtered[index - 1].timestamp,
                                recording.timestamp
                            )
                            if (showDateHeader) {
                                RecordingDateHeader(recording.timestamp)
                            }

                            // Staggered animation
                            var itemVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(recording.id) {
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
                                val isExpanded = expandedRecordingId == recording.id
                                val isCurrentlyPlaying = playbackState.recordingId == recording.id

                                SwipeableRecordingItem(
                                    recording = recording,
                                    isExpanded = isExpanded,
                                    isCurrentlyPlaying = isCurrentlyPlaying,
                                    playbackState = if (isCurrentlyPlaying) playbackState else null,
                                    onClick = {
                                        expandedRecordingId = if (isExpanded) null else recording.id
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        callRecordingsViewModel.toggleStar(recording)
                                    },
                                    onPlayPause = {
                                        callRecordingsViewModel.togglePlayback(recording)
                                    },
                                    onStop = {
                                        callRecordingsViewModel.stopPlayback()
                                    },
                                    onSeek = { posMs ->
                                        callRecordingsViewModel.seekTo(posMs.toLong())
                                    },
                                    onSpeedChange = { speed ->
                                        callRecordingsViewModel.setPlaybackSpeed(speed)
                                    },
                                    onDelete = {
                                        callRecordingsViewModel.markForDeletion(recording)
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
    }
}

// -- Storage Summary Card --

@Composable
private fun StorageSummaryCard(
    storageInfo: com.byron.trucaller.viewmodel.StorageInfo,
    onManageStorage: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val usedPercent = if (storageInfo.deviceTotalSpace > 0) {
        (storageInfo.totalRecordingsSize.toFloat() / storageInfo.deviceTotalSpace * 100)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .background(colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable { onManageStorage() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = "Storage",
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${CallRecordingsViewModel.formatFileSize(storageInfo.totalRecordingsSize)} used",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colorScheme.onSurface
            )
            Text(
                "${storageInfo.recordingCount} recordings" +
                    " \u2022 ${CallRecordingsViewModel.formatFileSize(storageInfo.deviceFreeSpace)} free",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Manage",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary
        )
    }
}

// -- Date Header --

@Composable
private fun RecordingDateHeader(timestamp: Long) {
    val colorScheme = MaterialTheme.colorScheme
    val label = CallRecordingsViewModel.formatDateHeader(timestamp)
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

// -- Swipeable Recording Item --

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRecordingItem(
    recording: CallRecording,
    isExpanded: Boolean,
    isCurrentlyPlaying: Boolean,
    playbackState: com.byron.trucaller.viewmodel.PlaybackState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // Reset after action
                }
                else -> false
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
                            SwipeToDismissBoxValue.EndToStart -> colorScheme.error
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Delete",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
        ) {
            RecordingItem(
                recording = recording,
                isCurrentlyPlaying = isCurrentlyPlaying,
                onClick = onClick,
                onLongClick = onLongClick
            )

            // Expandable inline player
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
            ) {
                InlinePlayer(
                    recording = recording,
                    isPlaying = playbackState?.isPlaying ?: false,
                    currentPositionMs = playbackState?.currentPositionMs ?: 0L,
                    totalDurationMs = playbackState?.totalDurationMs ?: recording.durationMs,
                    playbackSpeed = playbackState?.playbackSpeed ?: 1.0f,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onSeek = onSeek,
                    onSpeedChange = onSpeedChange
                )
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
    }
}

// -- Recording Item --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    recording: CallRecording,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val directionColor = when (recording.direction) {
        RecordingDirection.INCOMING -> Color(0xFF4CAF50)
        RecordingDirection.OUTGOING -> LogoBlueLight
    }

    val directionIcon = when (recording.direction) {
        RecordingDirection.INCOMING -> Icons.Default.CallReceived
        RecordingDirection.OUTGOING -> Icons.Default.CallMade
    }

    val directionLabel = when (recording.direction) {
        RecordingDirection.INCOMING -> "Incoming"
        RecordingDirection.OUTGOING -> "Outgoing"
    }

    val displayName = recording.contactName ?: recording.phoneNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (isCurrentlyPlaying) colorScheme.primary.copy(alpha = 0.05f)
                else colorScheme.background
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Recording icon
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (isCurrentlyPlaying) colorScheme.primary.copy(alpha = 0.15f)
                    else directionColor.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isCurrentlyPlaying) Icons.Default.GraphicEq else Icons.Default.GraphicEq,
                contentDescription = "$directionLabel recording",
                tint = if (isCurrentlyPlaying) colorScheme.primary else directionColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (recording.isStarred) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Starred",
                        tint = BrandGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CallRecordingsViewModel.formatTimestamp(recording.timestamp),
                    fontSize = 11.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    directionIcon, null,
                    modifier = Modifier.size(14.dp),
                    tint = directionColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = directionLabel,
                    fontSize = 12.sp,
                    color = directionColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CallRecordingsViewModel.formatDuration(recording.durationMs),
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = CallRecordingsViewModel.formatFileSize(recording.fileSize),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Show number beneath name if name is available
            if (recording.contactName != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recording.phoneNumber,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -- Inline Audio Player --

@Composable
private fun InlinePlayer(
    recording: CallRecording,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var showSpeedSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Seek bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                CallRecordingsViewModel.formatDuration(currentPositionMs),
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f,
                onValueChange = { fraction ->
                    onSeek(fraction * totalDurationMs)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.outlineVariant
                )
            )
            Text(
                CallRecordingsViewModel.formatDuration(totalDurationMs),
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed control
            Box {
                IconButton(onClick = { showSpeedSelector = !showSpeedSelector }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "Playback speed",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "${playbackSpeed}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play/Pause
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(colorScheme.primary, CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stop
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Speed selector row
        AnimatedVisibility(
            visible = showSpeedSelector,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                speeds.forEach { speed ->
                    val isSelected = playbackSpeed == speed
                    Text(
                        "${speed}x",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) colorScheme.primary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .clickable {
                                onSpeedChange(speed)
                                showSpeedSelector = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// -- Manage Storage Dialog --

@Composable
private fun ManageStorageDialog(
    storageInfo: com.byron.trucaller.viewmodel.StorageInfo,
    onDismiss: () -> Unit,
    onDeleteOld: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedDays by remember { mutableStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Storage",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                "Manage Storage",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                // Storage breakdown
                Text(
                    "Recordings: ${CallRecordingsViewModel.formatFileSize(storageInfo.totalRecordingsSize)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${storageInfo.recordingCount} files",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Storage bar
                val usedFraction = if (storageInfo.deviceTotalSpace > 0) {
                    (storageInfo.totalRecordingsSize.toFloat() / storageInfo.deviceTotalSpace).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Used",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${CallRecordingsViewModel.formatFileSize(storageInfo.deviceFreeSpace)} free",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Delete recordings older than:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7, 30, 90).forEach { days ->
                        val isSelected = selectedDays == days
                        Text(
                            "$days days",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) colorScheme.primary
                                    else colorScheme.surface
                                )
                                .clickable { selectedDays = days }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDeleteOld(selectedDays) },
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
            ) {
                Text("Delete Old Recordings", color = colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -- Helpers --

private fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val dayMs = 24 * 60 * 60 * 1000L
    return (ts1 / dayMs) == (ts2 / dayMs)
}
