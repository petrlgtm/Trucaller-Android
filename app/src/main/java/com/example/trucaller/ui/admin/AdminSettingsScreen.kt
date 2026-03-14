package com.example.trucaller.ui.admin

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.trucaller.ui.theme.Background
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.BrandDark
import com.example.trucaller.ui.theme.TextPrimary
import com.example.trucaller.ui.theme.TextSecondary
import com.example.trucaller.viewmodel.AdminSettingsViewModel
import com.example.trucaller.viewmodel.AuthViewModel

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Background)) {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
            )

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // Profile section
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Admin Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.background(Color(0xFF6A1B9A).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Super Admin", color = Color(0xFF6A1B9A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, label = { Text("Name") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email, onValueChange = { email = it }, label = { Text("Email") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { adminSettingsViewModel.saveProfile(name, email) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Save Profile", fontWeight = FontWeight.Bold) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notifications
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, tint = Brand, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        NotifToggle("Email on new stolen report", stolenReportNotifState) {
                            adminSettingsViewModel.setStolenReportNotif(it)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        NotifToggle("Email on alarm trigger", alarmNotifState) {
                            adminSettingsViewModel.setAlarmNotif(it)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        NotifToggle("Daily activity digest", dailyDigestState) {
                            adminSettingsViewModel.setDailyDigest(it)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        NotifToggle("Weekly summary report", weeklyReportState) {
                            adminSettingsViewModel.setWeeklyReport(it)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Security", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = currentPassword, onValueChange = { currentPassword = it },
                            label = { Text("Current Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPassword, onValueChange = { newPassword = it },
                            label = { Text("New Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val admin = adminUser
                                if (admin != null) {
                                    adminSettingsViewModel.updatePassword(admin.id, currentPassword, newPassword)
                                    currentPassword = ""
                                    newPassword = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Update Password", fontWeight = FontWeight.Bold) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Snackbar
        saveMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(checkedTrackColor = Brand)
        )
    }
}
