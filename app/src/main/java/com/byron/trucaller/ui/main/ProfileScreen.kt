package com.byron.trucaller.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.CardGradientEnd
import com.byron.trucaller.ui.theme.CardGradientStart
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.ui.theme.ThemeMode
import com.byron.trucaller.service.LocationService
import com.byron.trucaller.util.DeviceAdminHelper
import com.byron.trucaller.util.ThemePreferences
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(rootNavController: NavController, authViewModel: AuthViewModel, deviceViewModel: DeviceViewModel) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val colorScheme = MaterialTheme.colorScheme
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

    val roleManager = remember { context.getSystemService(Context.ROLE_SERVICE) as RoleManager }
    var isDefaultDialer by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) }

    // Background location permission state
    var hasBackgroundLocation by remember {
        mutableStateOf(LocationService.hasBackgroundLocationPermission(context))
    }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    // Theme preferences
    val themePreferences = remember { ThemePreferences(context.applicationContext) }
    val currentThemeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val themeScope = rememberCoroutineScope()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) authViewModel.updateAvatar(uri)
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

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
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showProtectionDialog = false }) {
                    Text("OK", color = colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProtectionDialog = false
                    showDisableProtectionDialog = true
                    disablePassword = ""
                    disableError = null
                }) { Text("Disable Protection", color = colorScheme.error) }
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
                        color = colorScheme.onSurfaceVariant
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        ),
                        isError = disableError != null
                    )
                    if (disableError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(disableError!!, color = colorScheme.error, fontSize = 12.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text("Disable Protection", color = colorScheme.onError)
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
                }) { Text("Logout", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Profile header with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CardGradientStart,
                            CardGradientEnd,
                            colorScheme.background
                        )
                    )
                )
                .padding(vertical = Spacing.lg, horizontal = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClickLabel = "Change profile photo") {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("profile_avatar"),
                    contentAlignment = Alignment.Center
                ) {
                    TruCallerAvatar(
                        name = user.fullName,
                        size = 80.dp,
                        imageUri = user.avatarUrl,
                        contentDesc = "Profile photo"
                    )
                    // Camera icon overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change photo",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (user.avatarUrl != null) {
                    TextButton(onClick = { authViewModel.removeAvatar() }) {
                        Text(
                            "Remove Photo",
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    user.fullName,
                    color = colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatPhoneNumber(user.phoneNumber),
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Column(modifier = Modifier.padding(Spacing.md)) {
            // Appearance section — theme toggle
            Text(
                "Appearance",
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
                    .testTag("appearance_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.SettingsSuggest
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minHeight = 48.dp)
                                .clickable {
                                    themeScope.launch { themePreferences.setThemeMode(mode) }
                                }
                                .testTag("theme_toggle_$label")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = "$label theme",
                                    tint = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Trust Level card
            Text(
                "Community Trust",
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
                    .testTag("trust_level_card"),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trust shield icon
                    val trustColor = when (user.trustLevel) {
                        com.byron.trucaller.data.model.TrustLevel.NEW -> colorScheme.onSurfaceVariant
                        com.byron.trucaller.data.model.TrustLevel.BASIC -> Color(0xFF2196F3)
                        com.byron.trucaller.data.model.TrustLevel.TRUSTED -> Color(0xFF4CAF50)
                        com.byron.trucaller.data.model.TrustLevel.VERIFIED -> Color(0xFFFF9800)
                        com.byron.trucaller.data.model.TrustLevel.AUTHORITY -> Color(0xFF9C27B0)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(trustColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Trust level",
                            tint = trustColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            user.trustLevel.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "Trust Score: ${user.trustScore}/100",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Trust progress bar
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { user.trustScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (user.trustLevel) {
                        com.byron.trucaller.data.model.TrustLevel.NEW -> colorScheme.onSurfaceVariant
                        com.byron.trucaller.data.model.TrustLevel.BASIC -> Color(0xFF2196F3)
                        com.byron.trucaller.data.model.TrustLevel.TRUSTED -> Color(0xFF4CAF50)
                        com.byron.trucaller.data.model.TrustLevel.VERIFIED -> Color(0xFFFF9800)
                        com.byron.trucaller.data.model.TrustLevel.AUTHORITY -> Color(0xFF9C27B0)
                    },
                    trackColor = colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (user.trustLevel) {
                        com.byron.trucaller.data.model.TrustLevel.NEW -> "New member. Report spam and verify numbers to build trust."
                        com.byron.trucaller.data.model.TrustLevel.BASIC -> "Basic trust. Keep contributing to level up."
                        com.byron.trucaller.data.model.TrustLevel.TRUSTED -> "Trusted member. Your reports carry extra weight."
                        com.byron.trucaller.data.model.TrustLevel.VERIFIED -> "Verified contributor. High impact on spam scoring."
                        com.byron.trucaller.data.model.TrustLevel.AUTHORITY -> "Authority. Top-tier community member."
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Settings section heading
            Text(
                "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(bottom = Spacing.sm, start = 4.dp)
                    .semantics { heading() }
            )

            // Menu card using TruCallerCard
            TruCallerCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                containerColor = colorScheme.surfaceVariant
            ) {
                // Device Info (expandable)
                ProfileMenuItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Device Info",
                    iconColor = colorScheme.primary,
                    isExpandable = true,
                    isExpanded = showDeviceInfo,
                    onClick = { showDeviceInfo = !showDeviceInfo }
                )
                AnimatedVisibility(
                    visible = showDeviceInfo && userDevice != null,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                ) {
                    val device = userDevice!!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.background.copy(alpha = 0.5f))
                            .padding(vertical = 12.dp)
                    ) {
                        InfoRow("Model", "${device.manufacturer} ${device.model}")
                        InfoRow("OS", device.osVersion)
                        InfoRow("Device ID", device.deviceId)
                        InfoRow("Status", device.status.name)
                        InfoRow("Last IP", device.lastIp)
                        InfoRow("Last Seen", formatRelativeTime(device.lastSeen))
                    }
                }
                HorizontalDivider(color = colorScheme.outlineVariant)

                // IP History (expandable)
                ProfileMenuItem(
                    icon = Icons.Default.LocationOn,
                    title = "IP History",
                    iconColor = colorScheme.primary,
                    isExpandable = true,
                    isExpanded = showIpHistory,
                    onClick = { showIpHistory = !showIpHistory }
                )
                AnimatedVisibility(
                    visible = showIpHistory && sortedIpLogs.isNotEmpty(),
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.background.copy(alpha = 0.5f))
                            .padding(vertical = Spacing.sm)
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
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        "${log.isp} - ${log.city}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatRelativeTime(log.timestamp),
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        if (log.networkType == "wifi") "WiFi" else "Mobile",
                                        fontSize = 11.sp,
                                        color = if (log.networkType == "wifi") {
                                            Color(0xFF4CAF50)
                                        } else {
                                            colorScheme.primary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Security
                ProfileMenuItem(
                    icon = Icons.Default.Lock,
                    title = "Security",
                    iconColor = colorScheme.primary,
                    onClick = { rootNavController.navigate("security") }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Device Protection
                ProfileMenuItem(
                    icon = Icons.Default.Shield,
                    title = if (isProtectionActive) "Device Protection (ON)" else "Device Protection (OFF)",
                    iconColor = if (isProtectionActive) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    onClick = {
                        isProtectionActive = DeviceAdminHelper.isAdminActive(context)
                        if (isProtectionActive) {
                            showProtectionDialog = true
                        } else {
                            activity?.let {
                                DeviceAdminHelper.requestAdminPermission(it)
                            }
                        }
                    }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Default Phone App
                ProfileMenuItem(
                    icon = Icons.Default.Phone,
                    title = if (isDefaultDialer) "Default Phone App (Active)" else "Set as Default Phone App",
                    iconColor = if (isDefaultDialer) Color(0xFF4CAF50) else colorScheme.primary,
                    onClick = {
                        if (!isDefaultDialer) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            dialerRoleLauncher.launch(intent)
                        }
                    }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Background Location Permission
                ProfileMenuItem(
                    icon = Icons.Default.LocationOn,
                    title = if (hasBackgroundLocation) "Background Location (Granted)" else "Background Location (Denied)",
                    iconColor = if (hasBackgroundLocation) Color(0xFF4CAF50) else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    onClick = {
                        hasBackgroundLocation = LocationService.hasBackgroundLocationPermission(context)
                        if (!hasBackgroundLocation && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Blocking Schedules
                ProfileMenuItem(
                    icon = Icons.Default.DoNotDisturb,
                    title = "Blocking Schedules",
                    iconColor = colorScheme.primary,
                    onClick = { rootNavController.navigate("blocking_schedules") }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Recording Settings
                ProfileMenuItem(
                    icon = Icons.Default.FiberManualRecord,
                    title = "Recording Settings",
                    iconColor = colorScheme.error,
                    onClick = { rootNavController.navigate("recording_settings") }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Call Recordings
                ProfileMenuItem(
                    icon = Icons.Default.GraphicEq,
                    title = "Call Recordings",
                    iconColor = colorScheme.primary,
                    onClick = { rootNavController.navigate("call_recordings") }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Report stolen
                ProfileMenuItem(
                    icon = Icons.Default.Warning,
                    title = "Report Device Stolen",
                    iconColor = colorScheme.error,
                    titleColor = colorScheme.error,
                    onClick = { rootNavController.navigate("report_stolen") }
                )
                HorizontalDivider(color = colorScheme.outlineVariant)

                // About (expandable)
                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    iconColor = colorScheme.primary,
                    isExpandable = true,
                    isExpanded = showAbout,
                    onClick = { showAbout = !showAbout }
                )
                AnimatedVisibility(
                    visible = showAbout,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.background.copy(alpha = 0.5f))
                            .padding(vertical = Spacing.md)
                    ) {
                        Text(
                            "TruCaller v1.0.0",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "A Truecaller clone with anti-theft features. Secure your device, identify callers, and protect your data.",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
                HorizontalDivider(color = colorScheme.outlineVariant)

                // Logout
                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Logout",
                    iconColor = colorScheme.error,
                    titleColor = colorScheme.error,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Version badge and check for updates
            TruCallerCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Version 1.0.0",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "Build 1 - Stable",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .clickable(onClickLabel = "Check for updates") { /* check for updates action */ }
                            .testTag("check_for_updates_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = "Check for updates",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Check for Updates",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Footer text
            Text(
                "Made with care in Uganda",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpandable && isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "chevronRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClickLabel = title) { onClick() }
            .padding(horizontal = Spacing.md, vertical = 14.dp)
            .testTag("profile_menu_${title.lowercase().replace(" ", "_").replace("(", "").replace(")", "")}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpandable) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (isExpandable) {
                if (isExpanded) "Collapse $title" else "Expand $title"
            } else "Navigate to $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
