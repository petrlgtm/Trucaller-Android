package com.byron.trucaller.ui.stolen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val TIMELINE_DOT_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1565C0), Color(0xFFFB8C00), Color(0xFF43A047),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF5E35B1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteActionsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    stolenReportViewModel: StolenReportViewModel,
    alarmViewModel: AlarmViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarmLoading by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var lockLoading by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showAlarmConfirm by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showRecoverConfirm by remember { mutableStateOf(false) }
    var showRecoverSuccess by remember { mutableStateOf(false) }
    var recoverLoading by remember { mutableStateOf(false) }

    val alarmPlaying by alarmViewModel.alarmPlaying.collectAsState()
    val actionMessage by alarmViewModel.actionMessage.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val user = authState.user

    val device by deviceViewModel.userDevice.collectAsState()

    LaunchedEffect(user?.id) {
        user?.id?.let { deviceViewModel.loadUserDevice(it) }
    }

    val deviceIpLogs by remember(device?.id) {
        device?.id?.let { deviceViewModel.getIpLogs(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val sortedIpLogs = remember(deviceIpLogs) {
        deviceIpLogs.sortedByDescending { it.timestamp }.take(8)
    }
    val lastIpLog = sortedIpLogs.firstOrNull()

    val stolenReports by remember(device?.id) {
        device?.id?.let { stolenReportViewModel.getReportsByDevice(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val stolenReport = stolenReports.firstOrNull()

    val reportDate = stolenReport?.let {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(it.reportedAt)
            SimpleDateFormat("d MMM yyyy", Locale.US).format(date!!)
        } catch (_: Exception) { "Unknown" }
    } ?: "Unknown"

    // Auto-clear action message and stop location loading when request completes
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            locationLoading = false
            kotlinx.coroutines.delay(3000)
            alarmViewModel.clearActionMessage()
        }
    }

    // Dialogs
    if (showAlarmConfirm) {
        AlertDialog(
            onDismissRequest = { showAlarmConfirm = false },
            title = { Text("Trigger Remote Alarm") },
            text = { Text("This will sound a loud alarm on the device, even if it is on silent mode.") },
            confirmButton = {
                TextButton(onClick = {
                    showAlarmConfirm = false
                    val currentUser = user ?: return@TextButton
                    val currentDevice = device ?: return@TextButton
                    alarmLoading = true
                    scope.launch {
                        alarmViewModel.triggerAlarm(
                            deviceId = currentDevice.id,
                            triggeredBy = currentUser.id,
                            triggeredByName = currentUser.fullName,
                            triggeredByRole = "owner"
                        )
                        alarmLoading = false
                        showAlarmDialog = true
                    }
                }) { Text("Trigger Alarm", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Alarm Triggered") },
            text = { Text("Alarm triggered successfully! The device should now be sounding an alert.") },
            confirmButton = {
                TextButton(onClick = { showAlarmDialog = false }) { Text("OK") }
            }
        )
    }
    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("Lock Device") },
            text = { Text("This will immediately lock the device screen. Device Admin permission is required.") },
            confirmButton = {
                TextButton(onClick = {
                    showLockConfirm = false
                    val currentUser = user ?: return@TextButton
                    val currentDevice = device ?: return@TextButton
                    lockLoading = true
                    alarmViewModel.lockDevice(
                        deviceId = currentDevice.id,
                        triggeredBy = currentUser.id,
                        triggeredByName = currentUser.fullName,
                        triggeredByRole = "owner"
                    )
                    lockLoading = false
                }) { Text("Lock Now", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showRecoverConfirm) {
        AlertDialog(
            onDismissRequest = { showRecoverConfirm = false },
            title = { Text("Mark as Recovered") },
            text = { Text("Are you sure? This will remove the stolen flag and stop tracking escalation.") },
            confirmButton = {
                TextButton(onClick = {
                    showRecoverConfirm = false
                    val currentDevice = device ?: return@TextButton
                    recoverLoading = true
                    scope.launch {
                        stolenReportViewModel.markDeviceRecovered(currentDevice.id)
                        recoverLoading = false
                        showRecoverSuccess = true
                    }
                }) { Text("Mark Recovered", color = Success) }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showRecoverSuccess) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Device Recovered") },
            text = { Text("Your device has been marked as recovered. Glad you got it back!") },
            confirmButton = {
                TextButton(onClick = {
                    showRecoverSuccess = false
                    navController.popBackStack()
                }) { Text("OK") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            // Banner
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Danger)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("STOLEN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        device?.let { "${it.manufacturer} ${it.model}" } ?: "Loading...",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Reported $reportDate", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Alarm active banner
                if (alarmPlaying) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Danger)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Alarm, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ALARM ACTIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Alarm is sounding on device", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            Button(
                                onClick = { alarmViewModel.stopAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                Text("STOP", color = Danger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Remote Actions Grid: Alarm + Lock + Location
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Remote Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger Alarm
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Danger.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Alarm, null, tint = Danger, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trigger Alarm", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                Text("Sound a loud alarm, even on silent", fontSize = 12.sp, color = TextSecondary)
                            }
                            Button(
                                onClick = { showAlarmConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !alarmLoading && device != null && user != null
                            ) {
                                if (alarmLoading) {
                                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Trigger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 14.dp))

                        // Lock Device
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Warning.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lock Device", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                Text("Immediately lock the screen", fontSize = 12.sp, color = TextSecondary)
                            }
                            Button(
                                onClick = { showLockConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !lockLoading && device != null && user != null
                            ) {
                                if (lockLoading) {
                                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Lock", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 14.dp))

                        // Request Location
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Brand.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.GpsFixed, null, tint = Brand, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Request Location", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                Text("Refresh IP-based location data", fontSize = 12.sp, color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    val currentUser = user ?: return@Button
                                    val currentDevice = device ?: return@Button
                                    locationLoading = true
                                    alarmViewModel.requestLocation(
                                        deviceId = currentDevice.id,
                                        triggeredBy = currentUser.id,
                                        triggeredByName = currentUser.fullName,
                                        triggeredByRole = "owner"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !locationLoading && device != null && user != null
                            ) {
                                if (locationLoading) {
                                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Refresh", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Last Known Location card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Brand.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Brand, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Last Known Location", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        }

                        if (lastIpLog != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Background, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    LocationRow("City", "${lastIpLog.city}, ${lastIpLog.country}")
                                    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                                    LocationRow("ISP", lastIpLog.isp)
                                    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                                    LocationRow("IP Address", lastIpLog.ipAddress, mono = true)
                                    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                                    LocationRow("Last Seen", formatRelativeTime(lastIpLog.timestamp))
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No IP data available", color = TextSecondary, fontSize = 14.sp)
                        }

                        // Embedded Map View
                        Spacer(modifier = Modifier.height(16.dp))
                        if (lastIpLog != null && lastIpLog.latitude != 0.0 && lastIpLog.longitude != 0.0) {
                            val lat = lastIpLog.latitude
                            val lon = lastIpLog.longitude

                            // osmdroid MapView
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                factory = { ctx ->
                                    org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                                    org.osmdroid.views.MapView(ctx).apply {
                                        setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        controller.setZoom(14.0)
                                        controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                                        // Add marker
                                        val marker = org.osmdroid.views.overlay.Marker(this)
                                        marker.position = org.osmdroid.util.GeoPoint(lat, lon)
                                        marker.setAnchor(
                                            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                                        )
                                        marker.title = "Last Known Location"
                                        marker.snippet = "${lastIpLog.city}, ${lastIpLog.country}"
                                        overlays.add(marker)
                                    }
                                },
                                update = { mapView ->
                                    mapView.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                                    mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>()
                                        .firstOrNull()?.position = org.osmdroid.util.GeoPoint(lat, lon)
                                    mapView.invalidate()
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Open in Google Maps button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brand.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val geoUri = Uri.parse(
                                            "geo:$lat,$lon?q=$lat,$lon(Last+Known+Location)"
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        } else {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.google.com/maps?q=$lat,$lon")
                                                )
                                            )
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                @Suppress("DEPRECATION")
                                Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Google Maps", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}",
                                    color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(Background, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No coordinates available", color = Inactive, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // IP Trail card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Brand.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Timeline, null, tint = Brand, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("IP Trail Since Report", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (sortedIpLogs.isEmpty()) {
                            Text("No IP trail data available", color = TextSecondary, fontSize = 14.sp)
                        } else {
                            sortedIpLogs.forEachIndexed { index, log ->
                                val dotColor = TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]
                                val isLast = index == sortedIpLogs.lastIndex

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    // Timeline left
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(dotColor, CircleShape)
                                        )
                                        if (!isLast) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(56.dp)
                                                    .background(Brand)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    // Content
                                    Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
                                        Text(
                                            log.ipAddress,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "${log.isp} \u2022 ${log.city}",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(formatRelativeTime(log.timestamp), fontSize = 12.sp, color = Inactive)
                                            val isWifi = log.networkType == "wifi"
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isWifi) Success.copy(alpha = 0.1f) else Warning.copy(alpha = 0.1f),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower,
                                                        null,
                                                        tint = if (isWifi) Success else Warning,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        if (isWifi) "WiFi" else "Mobile",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isWifi) Success else Warning
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mark as Recovered button
                Button(
                    onClick = { showRecoverConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !recoverLoading && device != null
                ) {
                    if (recoverLoading) {
                        CircularProgressIndicator(color = Brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark as Recovered", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Snackbar for action messages
        actionMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { alarmViewModel.clearActionMessage() }) {
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
private fun LocationRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
