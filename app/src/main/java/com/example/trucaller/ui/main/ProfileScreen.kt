package com.example.trucaller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trucaller.ui.theme.Background
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.BrandDark
import com.example.trucaller.ui.theme.Danger
import com.example.trucaller.ui.theme.Inactive
import com.example.trucaller.ui.theme.TextPrimary
import com.example.trucaller.ui.theme.TextSecondary
import com.example.trucaller.util.DeviceAdminHelper
import com.example.trucaller.util.formatPhoneNumber
import com.example.trucaller.util.formatRelativeTime
import com.example.trucaller.util.getInitials
import com.example.trucaller.viewmodel.AuthViewModel
import com.example.trucaller.viewmodel.DeviceViewModel

@Composable
fun ProfileScreen(rootNavController: NavController, authViewModel: AuthViewModel, deviceViewModel: DeviceViewModel) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val context = LocalContext.current
    val activity = context as? Activity

    var showDeviceInfo by remember { mutableStateOf(false) }
    var showIpHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showProtectionDialog by remember { mutableStateOf(false) }
    var showDisableProtectionDialog by remember { mutableStateOf(false) }
    var disablePassword by remember { mutableStateOf("") }
    var disableError by remember { mutableStateOf<String?>(null) }
    var isProtectionActive by remember { mutableStateOf(DeviceAdminHelper.isAdminActive(context)) }

    LaunchedEffect(user.id) {
        deviceViewModel.loadUserDevice(user.id)
    }

    val userDevice by deviceViewModel.userDevice.collectAsState()

    val userIpLogs by remember(userDevice?.id) {
        userDevice?.let { deviceViewModel.getIpLogs(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val sortedIpLogs = remember(userIpLogs) {
        userIpLogs.sortedByDescending { it.timestamp }.take(5)
    }

    if (showProtectionDialog) {
        AlertDialog(
            onDismissRequest = { showProtectionDialog = false },
            title = { Text("Device Protection Active", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Anti-uninstall protection is ON. Your device is protected against unauthorized app removal.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "To disable protection, you must enter your account password.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showProtectionDialog = false }) { Text("OK", color = Brand) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProtectionDialog = false
                    showDisableProtectionDialog = true
                    disablePassword = ""
                    disableError = null
                }) { Text("Disable Protection", color = Danger) }
            }
        )
    }

    if (showDisableProtectionDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisableProtectionDialog = false
                disablePassword = ""
                disableError = null
            },
            title = { Text("Enter Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter your account password to disable device protection. " +
                                "This will allow the app to be uninstalled.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = disablePassword,
                        onValueChange = { disablePassword = it; disableError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand),
                        isError = disableError != null
                    )
                    if (disableError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(disableError!!, color = Danger, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (disablePassword.isBlank()) {
                            disableError = "Password is required"
                        } else if (!authViewModel.verifyPassword(disablePassword)) {
                            disableError = "Incorrect password"
                        } else {
                            DeviceAdminHelper.removeAdmin(context)
                            isProtectionActive = false
                            showDisableProtectionDialog = false
                            disablePassword = ""
                            disableError = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) {
                    Text("Disable Protection", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableProtectionDialog = false
                    disablePassword = ""
                    disableError = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.logout()
                    rootNavController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Logout", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Profile header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        getInitials(user.fullName),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(user.fullName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(formatPhoneNumber(user.phoneNumber), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Menu card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    // Device Info
                    ProfileMenuItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Device Info",
                        iconColor = Brand,
                        onClick = { showDeviceInfo = !showDeviceInfo }
                    )
                    if (showDeviceInfo && userDevice != null) {
                        val device = userDevice!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Background)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            InfoRow("Model", "${device.manufacturer} ${device.model}")
                            InfoRow("OS", device.osVersion)
                            InfoRow("Device ID", device.deviceId)
                            InfoRow("Status", device.status.name)
                            InfoRow("Last IP", device.lastIp)
                            InfoRow("Last Seen", formatRelativeTime(device.lastSeen))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // IP History
                    ProfileMenuItem(
                        icon = Icons.Default.LocationOn,
                        title = "IP History",
                        iconColor = Brand,
                        onClick = { showIpHistory = !showIpHistory }
                    )
                    if (showIpHistory && sortedIpLogs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Background)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            sortedIpLogs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            log.ipAddress,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "${log.isp} - ${log.city}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            formatRelativeTime(log.timestamp),
                                            fontSize = 11.sp,
                                            color = Inactive
                                        )
                                        Text(
                                            if (log.networkType == "wifi") "WiFi" else "Mobile",
                                            fontSize = 11.sp,
                                            color = if (log.networkType == "wifi") Brand else com.example.trucaller.ui.theme.Warning
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // Security
                    ProfileMenuItem(
                        icon = Icons.Default.Lock,
                        title = "Security",
                        iconColor = Brand,
                        onClick = { rootNavController.navigate("security") }
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // Device Protection
                    ProfileMenuItem(
                        icon = Icons.Default.Shield,
                        title = if (isProtectionActive) "Device Protection (ON)" else "Device Protection (OFF)",
                        iconColor = if (isProtectionActive) Brand else Inactive,
                        onClick = {
                            isProtectionActive = DeviceAdminHelper.isAdminActive(context)
                            if (isProtectionActive) {
                                showProtectionDialog = true
                            } else {
                                activity?.let {
                                    DeviceAdminHelper.requestAdminPermission(it)
                                    // Will be refreshed next time user taps
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // Report stolen
                    ProfileMenuItem(
                        icon = Icons.Default.Warning,
                        title = "Report Device Stolen",
                        iconColor = Danger,
                        titleColor = Danger,
                        onClick = { rootNavController.navigate("report_stolen") }
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // About
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        title = "About",
                        iconColor = Brand,
                        onClick = { showAbout = !showAbout }
                    )
                    if (showAbout) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Background)
                                .padding(16.dp)
                        ) {
                            Text("TruCaller v1.0.0", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "A Truecaller clone with anti-theft features. Secure your device, identify callers, and protect your data.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // Logout
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Logout",
                        iconColor = Danger,
                        titleColor = Danger,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    iconColor: Color = Brand,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, null, tint = Inactive, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
