package com.byron.trucaller.ui.stolen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportStolenScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel
) {
    var confirmed by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val authState by authViewModel.authState.collectAsState()
    val user = authState.user
    val reportError by stolenReportViewModel.reportError.collectAsState()

    val device by deviceViewModel.userDevice.collectAsState()

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(user?.id) {
        user?.id?.let { deviceViewModel.loadUserDevice(it) }
    }

    val hasPin = authViewModel.hasSecurityPin()
    val canReport = confirmed && pin.length == 4 && pin.all { it.isDigit() }

    // Red pulse animation for the report confirmation overlay
    val infiniteTransition = rememberInfiniteTransition(label = "reportPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Device Reported Stolen") },
            text = { Text("Your device has been flagged as stolen. You can now use remote actions to trigger an alarm, request location, or lock the device.") },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    navController.navigate("remote_actions") {
                        popUpTo("report_stolen") { inclusive = true }
                    }
                }) { Text("Remote Actions", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    navController.popBackStack()
                }) { Text("Done") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Report Stolen", fontWeight = FontWeight.Bold, color = colorScheme.onError) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onError)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.error)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // Warning banner with pulse effect when confirmed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (confirmed) {
                            drawRect(
                                color = Color.Red.copy(alpha = pulseAlpha)
                            )
                        }
                    }
                    .background(colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(BorderRadius.md))
                    .padding(Spacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = colorScheme.error, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "This action will flag your device as stolen in our system",
                        color = colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // What happens next card
            TruCallerCard {
                Text(
                    "What happens next",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                InfoItem(Icons.Default.Warning, "Device flagged as stolen in system")
                InfoItem(Icons.Default.Alarm, "Remote alarm triggering enabled")
                InfoItem(Icons.Default.GpsFixed, "IP location logged on each access")
                InfoItem(Icons.Default.Notifications, "Report visible on admin dashboard")
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Confirmation checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmed = !confirmed }
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            2.dp,
                            if (confirmed) colorScheme.error else colorScheme.outline,
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (confirmed) colorScheme.error else colorScheme.background,
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (confirmed) {
                        Icon(Icons.Default.Check, null, tint = colorScheme.onError, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "I confirm that my device has been stolen and I want to flag it",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // No PIN warning
            if (!hasPin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(BorderRadius.md))
                        .padding(Spacing.md)
                ) {
                    Text(
                        "You must set a Security PIN in Profile \u2192 Security before reporting. This PIN verifies your identity.",
                        color = colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // PIN error from ViewModel
            if (reportError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(BorderRadius.md))
                        .padding(Spacing.md)
                ) {
                    Text(reportError!!, color = colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // PIN entry
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "ENTER YOUR SECURITY PIN",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                BasicTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.focusRequester(focusRequester),
                    decorationBox = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                val char = pin.getOrNull(index)?.toString() ?: ""
                                val isFocused = pin.length == index
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .border(
                                            if (isFocused) 2.dp else 1.5.dp,
                                            if (isFocused || char.isNotEmpty()) colorScheme.error else colorScheme.outline,
                                            RoundedCornerShape(BorderRadius.md)
                                        )
                                        .background(
                                            if (char.isNotEmpty()) colorScheme.surfaceVariant else colorScheme.background,
                                            RoundedCornerShape(BorderRadius.md)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (char.isNotEmpty()) "\u2022" else "",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Report button with red pulse overlay on confirmation
            Box(modifier = Modifier.fillMaxWidth()) {
                TruCallerButton(
                    text = "Report Stolen",
                    onClick = {
                        val currentUser = user ?: return@TruCallerButton
                        val currentDevice = device ?: return@TruCallerButton
                        isLoading = true
                        stolenReportViewModel.clearReportError()
                        stolenReportViewModel.reportStolen(
                            userId = currentUser.id,
                            deviceId = currentDevice.id,
                            description = "Device reported stolen by owner",
                            pin = pin,
                            onSuccess = {
                                isLoading = false
                                showSuccessDialog = true
                            }
                        )
                        // If reportError gets set, loading stops
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            if (!showSuccessDialog) isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = TruCallerButtonStyle.Danger,
                    enabled = canReport && !isLoading && user != null && device != null,
                    isLoading = isLoading,
                    leadingIcon = Icons.Default.Warning,
                    iconSize = 20.dp
                )

                // Dramatic red pulse overlay on the button when confirmed
                if (confirmed && canReport) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Color.Red.copy(alpha = pulseAlpha),
                                RoundedCornerShape(BorderRadius.md)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Cancel
            TruCallerButton(
                text = "Cancel",
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
                style = TruCallerButtonStyle.Secondary
            )

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colorScheme.error.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, fontSize = 14.sp, color = colorScheme.onSurface)
    }
}
