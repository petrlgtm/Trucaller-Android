package com.byron.trucaller.ui.stolen

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Divider
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
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

    LaunchedEffect(user?.id) {
        user?.id?.let { deviceViewModel.loadUserDevice(it) }
    }

    val hasPin = authViewModel.hasSecurityPin()
    val canReport = confirmed && pin.length == 4 && pin.all { it.isDigit() }

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
                }) { Text("Remote Actions", color = Danger) }
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
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Report Stolen", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Warning banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Danger.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Danger, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "This action will flag your device as stolen in our system",
                        color = Danger,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // What happens next card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("What happens next", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoItem(Icons.Default.Warning, "Device flagged as stolen in system")
                    InfoItem(Icons.Default.Alarm, "Remote alarm triggering enabled")
                    InfoItem(Icons.Default.GpsFixed, "IP location logged on each access")
                    InfoItem(Icons.Default.Notifications, "Report visible on admin dashboard")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirmation checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmed = !confirmed }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            2.dp,
                            if (confirmed) Danger else Divider,
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (confirmed) Danger else Background,
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (confirmed) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "I confirm that my device has been stolen and I want to flag it",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // No PIN warning
            if (!hasPin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3D2E00), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "You must set a Security PIN in Profile \u2192 Security before reporting. This PIN verifies your identity.",
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PIN error from ViewModel
            if (reportError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Danger.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(reportError!!, color = Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PIN entry
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "ENTER YOUR SECURITY PIN",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
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
                                            if (isFocused || char.isNotEmpty()) Danger else Divider,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                            if (char.isNotEmpty()) Color(0xFF252525) else Background,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (char.isNotEmpty()) "\u2022" else "",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Report button
            Button(
                onClick = {
                    val currentUser = user ?: return@Button
                    val currentDevice = device ?: return@Button
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                enabled = canReport && !isLoading && user != null && device != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report Stolen", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Danger.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Danger, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, fontSize = 14.sp, color = TextPrimary)
    }
}
