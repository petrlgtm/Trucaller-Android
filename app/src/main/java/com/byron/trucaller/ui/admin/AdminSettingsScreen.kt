package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AdminSettingsViewModel
import com.byron.trucaller.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    adminSettingsViewModel: AdminSettingsViewModel
) {
    val adminUser by authViewModel.adminUser.collectAsState()
    val savedName by adminSettingsViewModel.adminName.collectAsState(initial = "")
    val savedEmail by adminSettingsViewModel.adminEmail.collectAsState(initial = "")
    val stolenReportNotifState by adminSettingsViewModel.stolenReportNotif.collectAsState(initial = true)
    val alarmNotifState by adminSettingsViewModel.alarmNotif.collectAsState(initial = true)
    val dailyDigestState by adminSettingsViewModel.dailyDigest.collectAsState(initial = false)
    val weeklyReportState by adminSettingsViewModel.weeklyReport.collectAsState(initial = true)
    val saveMessage by adminSettingsViewModel.saveMessage.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    // Initialize fields from saved values
    LaunchedEffect(savedName, savedEmail) {
        if (name.isEmpty() && savedName.isNotEmpty()) name = savedName
        if (email.isEmpty() && savedEmail.isNotEmpty()) email = savedEmail
    }

    // Auto-clear snackbar
    LaunchedEffect(saveMessage) {
        if (saveMessage != null) {
            kotlinx.coroutines.delay(3000)
            adminSettingsViewModel.clearSaveMessage()
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // Profile section
                TruCallerCard {
                    Text(
                        "Admin Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TruCallerBadge(
                        text = "Super Admin",
                        type = BadgeType.Custom,
                        color = Color(0xFF6A1B9A),
                        backgroundColor = Color(0xFF6A1B9A).copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Email") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    TruCallerButton(
                        text = "Save Profile",
                        onClick = { adminSettingsViewModel.saveProfile(name, email) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Notifications
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    NotifToggle("Notify on new stolen report", stolenReportNotifState) {
                        adminSettingsViewModel.setStolenReportNotif(it)
                    }
                    HorizontalDivider(color = colorScheme.outlineVariant)
                    NotifToggle("Notify on alarm trigger", alarmNotifState) {
                        adminSettingsViewModel.setAlarmNotif(it)
                    }
                    HorizontalDivider(color = colorScheme.outlineVariant)
                    NotifToggle("Daily activity digest", dailyDigestState) {
                        adminSettingsViewModel.setDailyDigest(it)
                    }
                    HorizontalDivider(color = colorScheme.outlineVariant)
                    NotifToggle("Weekly summary report", weeklyReportState) {
                        adminSettingsViewModel.setWeeklyReport(it)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Security
                TruCallerCard {
                    Text(
                        "Security",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = currentPassword, onValueChange = { currentPassword = it },
                        label = { Text("Current Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("New Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    TruCallerButton(
                        text = "Update Password",
                        onClick = {
                            val admin = adminUser
                            if (admin != null) {
                                adminSettingsViewModel.updatePassword(admin.id, currentPassword, newPassword)
                                currentPassword = ""
                                newPassword = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }

        // Snackbar
        saveMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
                action = {
                    TextButton(onClick = { adminSettingsViewModel.clearSaveMessage() }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun NotifToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked, onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary)
        )
    }
}
