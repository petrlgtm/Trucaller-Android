package com.byron.trucaller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Recording direction filter options.
 */
enum class RecordingDirection(val label: String) {
    ALL("All calls"),
    INCOMING("Incoming only"),
    OUTGOING("Outgoing only")
}

/**
 * Consent mode options for call recording.
 */
enum class ConsentMode(val label: String) {
    ONE_PARTY("One-party (Uganda default)"),
    TWO_PARTY("Two-party")
}

/**
 * Auto-delete period options.
 */
enum class AutoDeletePeriod(val label: String, val days: Int) {
    NEVER("Never", 0),
    DAYS_30("30 days", 30),
    DAYS_60("60 days", 60),
    DAYS_90("90 days", 90)
}

/**
 * Storage limit options for call recordings.
 */
enum class StorageLimit(val label: String, val megabytes: Long) {
    MB_200("200 MB", 200),
    MB_500("500 MB", 500),
    GB_1("1 GB", 1024),
    UNLIMITED("Unlimited", Long.MAX_VALUE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    // Read persisted preferences
    val consentAccepted by userPreferences.recordingConsentAccepted.collectAsState(initial = false)
    val recordingEnabled by userPreferences.recordingEnabled.collectAsState(initial = false)
    val autoRecord by userPreferences.recordingAutoRecord.collectAsState(initial = false)
    val playBeepTone by userPreferences.recordingPlayBeep.collectAsState(initial = true)
    val directionStr by userPreferences.recordingDirection.collectAsState(initial = RecordingDirection.ALL.name)
    val consentModeStr by userPreferences.recordingConsentMode.collectAsState(initial = ConsentMode.ONE_PARTY.name)
    val autoDeleteStr by userPreferences.recordingAutoDelete.collectAsState(initial = AutoDeletePeriod.NEVER.name)
    val storageLimitStr by userPreferences.recordingStorageLimit.collectAsState(initial = StorageLimit.MB_500.name)

    val direction = RecordingDirection.entries.find { it.name == directionStr } ?: RecordingDirection.ALL
    val consentMode = ConsentMode.entries.find { it.name == consentModeStr } ?: ConsentMode.ONE_PARTY
    val autoDelete = AutoDeletePeriod.entries.find { it.name == autoDeleteStr } ?: AutoDeletePeriod.NEVER
    val storageLimit = StorageLimit.entries.find { it.name == storageLimitStr } ?: StorageLimit.MB_500

    // Show consent dialog on first visit when consent not yet accepted
    var showConsentDialog by remember { mutableStateOf(!consentAccepted) }

    // Consent dialog
    if (showConsentDialog && !consentAccepted) {
        RecordingConsentDialog(
            onAccepted = {
                scope.launch { userPreferences.setRecordingConsentAccepted(true) }
                showConsentDialog = false
            },
            onDeclined = {
                showConsentDialog = false
                navController.popBackStack()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text("Recording Settings", fontWeight = FontWeight.Bold, color = Color.White)
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // ── Master Switch ──────────────────────────────────────────────
            Text(
                "General",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(bottom = Spacing.sm, start = 4.dp)
                    .semantics { heading() }
            )

            TruCallerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_general_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                // Enable Call Recording toggle
                SettingsToggleRow(
                    icon = Icons.Default.FiberManualRecord,
                    iconColor = if (recordingEnabled) colorScheme.error else colorScheme.onSurfaceVariant,
                    title = "Enable Call Recording",
                    subtitle = if (recordingEnabled) "Recording is active" else "Recording is disabled",
                    checked = recordingEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { userPreferences.setRecordingEnabled(enabled) }
                    },
                    testTag = "toggle_recording_enabled"
                )

                HorizontalDivider(color = colorScheme.outlineVariant)

                // Auto-record toggle
                SettingsToggleRow(
                    icon = Icons.Default.FiberManualRecord,
                    iconColor = colorScheme.primary,
                    title = "Auto-record Calls",
                    subtitle = if (autoRecord) "Automatically records all calls" else "Manual recording only",
                    checked = autoRecord,
                    enabled = recordingEnabled,
                    onCheckedChange = { auto ->
                        scope.launch { userPreferences.setRecordingAutoRecord(auto) }
                    },
                    testTag = "toggle_auto_record"
                )

                HorizontalDivider(color = colorScheme.outlineVariant)

                // Beep tone toggle
                SettingsToggleRow(
                    icon = Icons.Default.MusicNote,
                    iconColor = colorScheme.primary,
                    title = "Play Beep Tone",
                    subtitle = "Audible beep when recording starts",
                    checked = playBeepTone,
                    enabled = recordingEnabled,
                    onCheckedChange = { beep ->
                        scope.launch { userPreferences.setRecordingPlayBeep(beep) }
                    },
                    testTag = "toggle_play_beep"
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Call Direction ──────────────────────────────────────────────
            Text(
                "Call Direction",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(bottom = Spacing.sm, start = 4.dp)
                    .semantics { heading() }
            )

            TruCallerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_direction_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                RecordingDirection.entries.forEach { dir ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = recordingEnabled) {
                                scope.launch { userPreferences.setRecordingDirection(dir.name) }
                            }
                            .padding(horizontal = Spacing.sm, vertical = 10.dp)
                            .testTag("radio_direction_${dir.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = direction == dir,
                            onClick = {
                                scope.launch { userPreferences.setRecordingDirection(dir.name) }
                            },
                            enabled = recordingEnabled,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary,
                                unselectedColor = colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = if (recordingEnabled) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            dir.label,
                            fontSize = 14.sp,
                            fontWeight = if (direction == dir) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (recordingEnabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    if (dir != RecordingDirection.entries.last()) {
                        HorizontalDivider(color = colorScheme.outlineVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Legal / Consent Mode ──────────────────────────────────────
            Text(
                "Legal",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(bottom = Spacing.sm, start = 4.dp)
                    .semantics { heading() }
            )

            TruCallerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_legal_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                SettingsDropdownRow(
                    icon = Icons.Default.Gavel,
                    iconColor = colorScheme.primary,
                    title = "Consent Mode",
                    currentValue = consentMode.label,
                    enabled = recordingEnabled,
                    options = ConsentMode.entries.map { it.label },
                    onSelected = { index ->
                        scope.launch {
                            userPreferences.setRecordingConsentMode(ConsentMode.entries[index].name)
                        }
                    },
                    testTag = "dropdown_consent_mode"
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Storage & Retention ────────────────────────────────────────
            Text(
                "Storage & Retention",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(bottom = Spacing.sm, start = 4.dp)
                    .semantics { heading() }
            )

            TruCallerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_storage_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                SettingsDropdownRow(
                    icon = Icons.Default.Timer,
                    iconColor = colorScheme.primary,
                    title = "Auto-delete Recordings",
                    currentValue = autoDelete.label,
                    enabled = recordingEnabled,
                    options = AutoDeletePeriod.entries.map { it.label },
                    onSelected = { index ->
                        scope.launch {
                            userPreferences.setRecordingAutoDelete(AutoDeletePeriod.entries[index].name)
                        }
                    },
                    testTag = "dropdown_auto_delete"
                )

                HorizontalDivider(color = colorScheme.outlineVariant)

                SettingsDropdownRow(
                    icon = Icons.Default.Storage,
                    iconColor = colorScheme.primary,
                    title = "Storage Limit",
                    currentValue = storageLimit.label,
                    enabled = recordingEnabled,
                    options = StorageLimit.entries.map { it.label },
                    onSelected = { index ->
                        scope.launch {
                            userPreferences.setRecordingStorageLimit(StorageLimit.entries[index].name)
                        }
                    },
                    testTag = "dropdown_storage_limit"
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Legal disclaimer
            Text(
                "Call recording laws vary by jurisdiction. In Uganda, one-party consent " +
                    "is generally sufficient, but you are responsible for complying with " +
                    "applicable laws. Two-party consent mode notifies all parties.",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

// ── Consent Dialog ─────────────────────────────────────────────────────────

@Composable
private fun RecordingConsentDialog(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var understandLegal by remember { mutableStateOf(false) }
    var agreeResponsibility by remember { mutableStateOf(false) }
    val bothChecked = understandLegal && agreeResponsibility

    AlertDialog(
        onDismissRequest = onDeclined,
        title = {
            Text(
                "Call Recording Consent",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    "Before enabling call recording, please review and acknowledge " +
                        "the following legal requirements:",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Recording phone calls may be subject to local, state, and " +
                        "national laws. In Uganda, one-party consent is generally " +
                        "sufficient, meaning you may record calls you participate in. " +
                        "However, some jurisdictions require all parties to consent " +
                        "before a call can be recorded.",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Unauthorized recording may violate privacy laws and result " +
                        "in civil or criminal liability. Recorded content must not be " +
                        "shared without proper authorization.",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Checkbox 1: Understand legal implications
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { understandLegal = !understandLegal }
                        .padding(vertical = 4.dp)
                        .testTag("consent_checkbox_legal"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = understandLegal,
                        onCheckedChange = { understandLegal = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colorScheme.primary,
                            uncheckedColor = colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "I understand the legal implications of call recording in my jurisdiction.",
                        fontSize = 13.sp,
                        color = colorScheme.onSurface,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Checkbox 2: Accept responsibility
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreeResponsibility = !agreeResponsibility }
                        .padding(vertical = 4.dp)
                        .testTag("consent_checkbox_responsibility"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreeResponsibility,
                        onCheckedChange = { agreeResponsibility = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colorScheme.primary,
                            uncheckedColor = colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "I accept full responsibility for ensuring compliance with all applicable recording laws.",
                        fontSize = 13.sp,
                        color = colorScheme.onSurface,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccepted,
                enabled = bothChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("consent_accept_button")
            ) {
                Text(
                    "I Agree",
                    fontWeight = FontWeight.Bold,
                    color = if (bothChecked) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDeclined,
                modifier = Modifier.testTag("consent_decline_button")
            ) {
                Text("Decline", color = colorScheme.error)
            }
        }
    )
}

// ── Reusable Setting Row Components ────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.sm, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsDropdownRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    currentValue: String,
    enabled: Boolean = true,
    options: List<String>,
    onSelected: (Int) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.4f
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = Spacing.sm, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                currentValue,
                fontSize = 12.sp,
                color = colorScheme.primary.copy(alpha = alpha),
                fontWeight = FontWeight.Medium
            )
        }

        Box {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                fontWeight = if (option == currentValue) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == currentValue) colorScheme.primary else colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                        modifier = Modifier.testTag("${testTag}_option_$index")
                    )
                }
            }
        }
    }
}
