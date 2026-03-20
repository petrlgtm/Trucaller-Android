package com.byron.trucaller.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.BlockingSchedule
import com.byron.trucaller.data.model.ScheduleBlockType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.BlockingScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockingSchedulesScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    blockingScheduleViewModel: BlockingScheduleViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val schedules by blockingScheduleViewModel.schedules.collectAsState()
    val isLoading by blockingScheduleViewModel.isLoading.collectAsState()
    val statusMessage by blockingScheduleViewModel.statusMessage.collectAsState()

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(user.id) {
        blockingScheduleViewModel.loadSchedules(user.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Blocking Schedules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "${schedules.size} schedule${if (schedules.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("edit_blocking_schedule/new") },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Schedule", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status message
            if (statusMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    containerColor = colorScheme.surfaceVariant,
                    action = {
                        TextButton(onClick = { blockingScheduleViewModel.clearStatusMessage() }) {
                            Text("OK", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                ) { Text(statusMessage!!, color = colorScheme.onSurface) }
            }

            if (isLoading) {
                ShimmerLoadingList()
            } else if (schedules.isEmpty()) {
                EmptyStateView(
                    title = "No blocking schedules",
                    subtitle = "Create schedules to automatically block calls during specific times, like overnight or during meetings.",
                    icon = EmptyStateIcon.CALLS
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleItem(
                            schedule = schedule,
                            onToggle = { isActive ->
                                blockingScheduleViewModel.toggleSchedule(schedule.id, isActive)
                            },
                            onDelete = {
                                blockingScheduleViewModel.deleteSchedule(schedule.id)
                            },
                            onClick = {
                                navController.navigate("edit_blocking_schedule/${schedule.id}")
                            }
                        )
                    }
                    // Bottom padding for FAB clearance
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleItem(
    schedule: BlockingSchedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isCurrentlyActive = remember(schedule) {
        BlockingScheduleViewModel.isCurrentlyActive(schedule)
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    colorScheme.error else Color.Transparent,
                label = "swipeColor"
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.5f,
                label = "iconScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size((24 * scale).dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surface)
                .clickable(onClickLabel = "Edit ${schedule.name}") { onClick() }
                .padding(horizontal = Spacing.md, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Schedule icon with DND indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (schedule.isActive)
                                colorScheme.primary.copy(alpha = 0.12f)
                            else
                                colorScheme.onSurface.copy(alpha = 0.06f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DoNotDisturb,
                        contentDescription = null,
                        tint = if (schedule.isActive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Schedule details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (schedule.isActive) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isCurrentlyActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFF4CAF50).copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Currently Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${BlockingScheduleViewModel.formatTime(schedule.startTimeMinutes)} - ${BlockingScheduleViewModel.formatTime(schedule.endTimeMinutes)}",
                            fontSize = 13.sp,
                            color = if (schedule.isActive) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time range progress bar (visual representation of the schedule window)
                    val startFraction = schedule.startTimeMinutes / 1440f
                    val endFraction = schedule.endTimeMinutes / 1440f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        if (schedule.startTimeMinutes <= schedule.endTimeMinutes) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(endFraction)
                                    .padding(start = (startFraction * 300).dp.coerceAtMost(280.dp))
                                    .height(4.dp)
                                    .background(
                                        if (schedule.isActive) colorScheme.primary.copy(alpha = 0.5f)
                                        else colorScheme.onSurface.copy(alpha = 0.15f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        } else {
                            // Overnight: show both ends
                            LinearProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = if (schedule.isActive) colorScheme.primary.copy(alpha = 0.35f)
                                else colorScheme.onSurface.copy(alpha = 0.1f),
                                trackColor = Color.Transparent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Day badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val allDays = listOf("S", "M", "T", "W", "T", "F", "S")
                        allDays.forEachIndexed { index, label ->
                            val isSet = schedule.daysOfWeek and (1 shl index) != 0
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        when {
                                            isSet && schedule.isActive -> colorScheme.primary
                                            isSet -> colorScheme.onSurface.copy(alpha = 0.2f)
                                            else -> colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSet) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSet && schedule.isActive -> colorScheme.onPrimary
                                        isSet -> colorScheme.onSurface.copy(alpha = 0.6f)
                                        else -> colorScheme.onSurface.copy(alpha = 0.25f)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Block type badge
                        val blockTypeLabel = when (schedule.blockType) {
                            ScheduleBlockType.ALL_UNKNOWN -> "Unknown"
                            ScheduleBlockType.ALL_SPAM -> "Spam"
                            ScheduleBlockType.ALL_EXCEPT_CONTACTS -> "Non-contacts"
                            ScheduleBlockType.ALL_EXCEPT_STARRED -> "Non-starred"
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    colorScheme.error.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                blockTypeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Active toggle
                Switch(
                    checked = schedule.isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.primary,
                        checkedTrackColor = colorScheme.primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.3f),
                        uncheckedTrackColor = colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                )
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = colorScheme.outline.copy(alpha = 0.15f)
    )
}
