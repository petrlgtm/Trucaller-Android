package com.byron.trucaller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.ScheduleBlockType
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.BlockingScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBlockingScheduleScreen(
    navController: NavController,
    scheduleId: String,
    authViewModel: AuthViewModel,
    blockingScheduleViewModel: BlockingScheduleViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val isNew = scheduleId == "new"
    val editSchedule by blockingScheduleViewModel.editSchedule.collectAsState()

    val colorScheme = MaterialTheme.colorScheme

    // Form state
    var name by remember { mutableStateOf("") }
    var startMinutes by remember { mutableIntStateOf(22 * 60) } // Default 10:00 PM
    var endMinutes by remember { mutableIntStateOf(7 * 60) }    // Default 7:00 AM
    var daysOfWeek by remember { mutableIntStateOf(127) }       // All days
    var blockType by remember { mutableStateOf(ScheduleBlockType.ALL_UNKNOWN) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Load existing schedule data for editing
    LaunchedEffect(scheduleId) {
        if (!isNew) {
            blockingScheduleViewModel.loadScheduleForEdit(scheduleId)
        }
    }

    // Populate form when schedule is loaded
    LaunchedEffect(editSchedule) {
        editSchedule?.let { s ->
            name = s.name
            startMinutes = s.startTimeMinutes
            endMinutes = s.endTimeMinutes
            daysOfWeek = s.daysOfWeek
            blockType = s.blockType
        }
    }

    val startTimePickerState = rememberTimePickerState(
        initialHour = startMinutes / 60,
        initialMinute = startMinutes % 60,
        is24Hour = false
    )
    val endTimePickerState = rememberTimePickerState(
        initialHour = endMinutes / 60,
        initialMinute = endMinutes % 60,
        is24Hour = false
    )

    val isFormValid = name.isNotBlank() && daysOfWeek != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "New Blocking Schedule" else "Edit Schedule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        blockingScheduleViewModel.clearEditSchedule()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // Schedule name
            Text(
                "Schedule Name",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = Spacing.sm, start = 4.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Bedtime, Meeting hours") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedContainerColor = colorScheme.surfaceVariant,
                    unfocusedContainerColor = colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Time pickers
            Text(
                "Time Window",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = Spacing.sm, start = 4.dp)
            )

            if (showStartPicker) {
                TruCallerCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    containerColor = colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Start Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimePicker(
                            state = startTimePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = colorScheme.surface,
                                selectorColor = colorScheme.primary,
                                timeSelectorSelectedContainerColor = colorScheme.primary,
                                timeSelectorSelectedContentColor = colorScheme.onPrimary,
                                timeSelectorUnselectedContainerColor = colorScheme.surfaceVariant,
                                timeSelectorUnselectedContentColor = colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    startMinutes = startTimePickerState.hour * 60 + startTimePickerState.minute
                                    showStartPicker = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary
                                )
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            if (showEndPicker) {
                TruCallerCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    containerColor = colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "End Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimePicker(
                            state = endTimePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = colorScheme.surface,
                                selectorColor = colorScheme.primary,
                                timeSelectorSelectedContainerColor = colorScheme.primary,
                                timeSelectorSelectedContentColor = colorScheme.onPrimary,
                                timeSelectorUnselectedContainerColor = colorScheme.surfaceVariant,
                                timeSelectorUnselectedContentColor = colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    endMinutes = endTimePickerState.hour * 60 + endTimePickerState.minute
                                    showEndPicker = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary
                                )
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Time display buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start time button
                TruCallerCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartPicker = !showStartPicker; showEndPicker = false },
                    elevation = 1.dp,
                    containerColor = if (showStartPicker) colorScheme.primary.copy(alpha = 0.1f) else colorScheme.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Start",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                BlockingScheduleViewModel.formatTime(startMinutes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                // End time button
                TruCallerCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndPicker = !showEndPicker; showStartPicker = false },
                    elevation = 1.dp,
                    containerColor = if (showEndPicker) colorScheme.primary.copy(alpha = 0.1f) else colorScheme.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "End",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                BlockingScheduleViewModel.formatTime(endMinutes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Day toggles
            Text(
                "Active Days",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = Spacing.sm, start = 4.dp)
            )

            val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val isSet = daysOfWeek and (1 shl index) != 0
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSet) colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSet) colorScheme.primary else colorScheme.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                daysOfWeek = if (isSet) {
                                    daysOfWeek and (1 shl index).inv()
                                } else {
                                    daysOfWeek or (1 shl index)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSet) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick-select row
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Weekdays: Mon-Fri = bits 1-5 = 62
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { daysOfWeek = 62 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Weekdays", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariant)
                }
                // Weekends: Sun+Sat = bits 0+6 = 65
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { daysOfWeek = 65 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Weekends", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariant)
                }
                // Every day = 127
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { daysOfWeek = 127 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Every day", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Block type radio group
            Text(
                "Block Type",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = Spacing.sm, start = 4.dp)
            )

            TruCallerCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                ScheduleBlockType.entries.forEach { type ->
                    val label = when (type) {
                        ScheduleBlockType.ALL_UNKNOWN -> "All Unknown Numbers"
                        ScheduleBlockType.ALL_SPAM -> "All Spam-flagged Numbers"
                        ScheduleBlockType.ALL_EXCEPT_CONTACTS -> "All Except Contacts"
                        ScheduleBlockType.ALL_EXCEPT_STARRED -> "All Except Starred Contacts"
                    }
                    val description = when (type) {
                        ScheduleBlockType.ALL_UNKNOWN -> "Block calls from numbers not in the caller-ID database"
                        ScheduleBlockType.ALL_SPAM -> "Block only calls flagged as spam by the community"
                        ScheduleBlockType.ALL_EXCEPT_CONTACTS -> "Allow only calls from your saved contacts"
                        ScheduleBlockType.ALL_EXCEPT_STARRED -> "Allow only calls from your starred/favourite contacts"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { blockType = type }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = blockType == type,
                            onClick = { blockType = type },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary,
                                unselectedColor = colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (blockType == type) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                description,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Preview card
            Text(
                "Preview",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = Spacing.sm, start = 4.dp)
            )
            TruCallerCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                containerColor = colorScheme.primary.copy(alpha = 0.06f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DoNotDisturb,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "Untitled Schedule" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "${BlockingScheduleViewModel.formatTime(startMinutes)} - ${BlockingScheduleViewModel.formatTime(endMinutes)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        val activeDays = BlockingScheduleViewModel.daysToLabels(daysOfWeek)
                        Text(
                            if (activeDays.size == 7) "Every day" else activeDays.joinToString(", "),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        val blockLabel = when (blockType) {
                            ScheduleBlockType.ALL_UNKNOWN -> "Blocks unknown numbers"
                            ScheduleBlockType.ALL_SPAM -> "Blocks spam calls"
                            ScheduleBlockType.ALL_EXCEPT_CONTACTS -> "Allows contacts only"
                            ScheduleBlockType.ALL_EXCEPT_STARRED -> "Allows starred only"
                        }
                        Text(
                            blockLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Save button
            Button(
                onClick = {
                    if (isNew) {
                        blockingScheduleViewModel.addSchedule(
                            userId = user.id,
                            name = name,
                            startTimeMinutes = startMinutes,
                            endTimeMinutes = endMinutes,
                            daysOfWeek = daysOfWeek,
                            blockType = blockType
                        )
                    } else {
                        val existing = editSchedule ?: return@Button
                        blockingScheduleViewModel.updateSchedule(
                            scheduleId = existing.id,
                            userId = existing.userId,
                            name = name,
                            startTimeMinutes = startMinutes,
                            endTimeMinutes = endMinutes,
                            daysOfWeek = daysOfWeek,
                            blockType = blockType,
                            isActive = existing.isActive,
                            createdAt = existing.createdAt
                        )
                    }
                    blockingScheduleViewModel.clearEditSchedule()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isFormValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    if (isNew) Icons.Default.Check else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isNew) "Create Schedule" else "Save Changes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
