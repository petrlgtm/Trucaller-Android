package com.byron.trucaller.ui.family

import android.graphics.drawable.GradientDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.FamilyDeviceMapEntry
import com.byron.trucaller.viewmodel.FamilyGroupViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Status colors for device markers
private val StatusGreen = Color(0xFF43A047)
private val StatusYellow = Color(0xFFFFC107)
private val StatusRed = Color(0xFFE53935)
private val StatusGray = Color(0xFF9E9E9E)

/**
 * Returns the marker tint color for a given device status string.
 */
private fun statusColor(status: String): Color = when (status.uppercase()) {
    "ACTIVE" -> StatusGreen
    "INACTIVE" -> StatusYellow
    "STOLEN", "FLAGGED" -> StatusRed
    else -> StatusGray
}

/**
 * Returns a human-readable status label.
 */
private fun statusLabel(status: String): String = when (status.uppercase()) {
    "ACTIVE" -> "Online"
    "INACTIVE" -> "Idle"
    "STOLEN" -> "Stolen"
    "FLAGGED" -> "Flagged"
    else -> "Unknown"
}

/**
 * Creates a simple circular marker drawable with the given color.
 */
private fun createMarkerDrawable(context: android.content.Context, color: Color): android.graphics.drawable.Drawable {
    val size = (40 * context.resources.displayMetrics.density).toInt()
    val outer = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color.copy(alpha = 0.3f).toArgb())
        setSize(size, size)
    }
    val inner = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color.toArgb())
        setStroke(
            (2 * context.resources.displayMetrics.density).toInt(),
            Color.White.toArgb()
        )
        val innerSize = (24 * context.resources.displayMetrics.density).toInt()
        setSize(innerSize, innerSize)
    }
    val layers = android.graphics.drawable.LayerDrawable(arrayOf(outer, inner))
    val inset = ((size - (24 * context.resources.displayMetrics.density).toInt()) / 2)
    layers.setLayerInset(1, inset, inset, inset, inset)
    return layers
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDeviceMapScreen(
    navController: NavController,
    groupId: String,
    authViewModel: AuthViewModel,
    familyGroupViewModel: FamilyGroupViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return
    val uiState by familyGroupViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDevice by remember { mutableStateOf<FamilyDeviceMapEntry?>(null) }
    var mapVisible by remember { mutableStateOf(false) }

    // Fetch group detail and member devices on screen load
    LaunchedEffect(groupId) {
        familyGroupViewModel.loadGroupDetail(groupId)
    }

    // Once group is loaded, fetch member devices
    LaunchedEffect(uiState.selectedGroup?.id) {
        if (uiState.selectedGroup?.id == groupId) {
            familyGroupViewModel.fetchGroupMemberDevices(groupId)
        }
    }

    // Animate map in after data loads
    LaunchedEffect(uiState.memberDevices) {
        if (uiState.memberDevices.isNotEmpty()) {
            kotlinx.coroutines.delay(300)
            mapVisible = true
        }
    }

    // Show success / error messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            familyGroupViewModel.clearSuccessMessage()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            familyGroupViewModel.clearError()
        }
    }

    val groupName = uiState.selectedGroup?.name ?: "Device Map"
    val devices = uiState.memberDevices
    val devicesWithLocation = devices.filter { it.latitude != null && it.longitude != null }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 140.dp,
        sheetContainerColor = colorScheme.surface,
        sheetContentColor = colorScheme.onSurface,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetDragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Devices (${devices.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    Button(
                        onClick = { familyGroupViewModel.fetchGroupMemberDevices(groupId) },
                        enabled = !uiState.isLoadingDevices,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("refresh_all_button")
                    ) {
                        if (uiState.isLoadingDevices) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        },
        sheetContent = {
            // Bottom sheet device list
            if (devices.isEmpty() && !uiState.isLoadingDevices) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DeviceUnknown,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No devices found",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(devices, key = { it.deviceId }) { device ->
                        DeviceListItem(
                            device = device,
                            isSelected = selectedDevice?.deviceId == device.deviceId,
                            onClick = { selectedDevice = device },
                            onRing = {
                                familyGroupViewModel.ringFamilyDevice(
                                    device.deviceId, user.id, user.fullName
                                )
                            },
                            onLock = {
                                familyGroupViewModel.lockFamilyDevice(
                                    device.deviceId, user.id, user.fullName
                                )
                            },
                            onLocate = {
                                familyGroupViewModel.requestFamilyDeviceLocation(
                                    device.deviceId, user.id, user.fullName
                                )
                            },
                            isAdmin = uiState.selectedGroup?.members?.any {
                                it.userId == user.id && (it.role == com.byron.trucaller.data.model.FamilyGroupRole.ADMIN)
                            } == true || uiState.selectedGroup?.ownerId == user.id
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        groupName,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Loading state
            if (uiState.isLoading || uiState.isLoadingDevices && devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Loading device locations...",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (devicesWithLocation.isEmpty()) {
                // No location data — show placeholder map area
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No location data available",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap Refresh All or request location from a device",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // OSMDroid Map
                AnimatedVisibility(
                    visible = mapVisible,
                    enter = fadeIn() + scaleIn(initialScale = 0.95f)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("family_device_map"),
                        factory = { context ->
                            MapView(context).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(6.0)

                                // Default center (Africa / equator area)
                                controller.setCenter(GeoPoint(0.3476, 32.5825))
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.clear()

                            for (device in devicesWithLocation) {
                                val lat = device.latitude ?: continue
                                val lon = device.longitude ?: continue
                                val color = statusColor(device.status)

                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lon)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = device.memberName
                                    snippet = "${device.deviceModel} - ${statusLabel(device.status)}"
                                    subDescription = "Last seen: ${device.lastSeen}"
                                    icon = createMarkerDrawable(mapView.context, color)
                                }

                                mapView.overlays.add(marker)
                            }

                            // Zoom to fit all markers with padding
                            if (devicesWithLocation.size == 1) {
                                val d = devicesWithLocation.first()
                                mapView.controller.setCenter(
                                    GeoPoint(d.latitude!!, d.longitude!!)
                                )
                                mapView.controller.setZoom(14.0)
                            } else if (devicesWithLocation.size > 1) {
                                val lats = devicesWithLocation.mapNotNull { it.latitude }
                                val lons = devicesWithLocation.mapNotNull { it.longitude }
                                val boundingBox = BoundingBox(
                                    lats.max(), lons.max(),
                                    lats.min(), lons.min()
                                )
                                mapView.post {
                                    mapView.zoomToBoundingBox(boundingBox, true, 100)
                                }
                            }

                            mapView.invalidate()
                        }
                    )
                }
            }

            // Selected device info popup
            AnimatedVisibility(
                visible = selectedDevice != null && selectedDevice?.latitude != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedDevice?.let { device ->
                    DeviceInfoPopup(
                        device = device,
                        onDismiss = { selectedDevice = null },
                        onRing = {
                            familyGroupViewModel.ringFamilyDevice(
                                device.deviceId, user.id, user.fullName
                            )
                        },
                        onLock = {
                            familyGroupViewModel.lockFamilyDevice(
                                device.deviceId, user.id, user.fullName
                            )
                        },
                        onLocate = {
                            familyGroupViewModel.requestFamilyDeviceLocation(
                                device.deviceId, user.id, user.fullName
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * A compact popup showing device info after tapping a marker.
 */
@Composable
private fun DeviceInfoPopup(
    device: FamilyDeviceMapEntry,
    onDismiss: () -> Unit,
    onRing: () -> Unit,
    onLock: () -> Unit,
    onLocate: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val color = statusColor(device.status)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface)
            .clickable(onClickLabel = "Dismiss info popup") { onDismiss() }
            .padding(Spacing.md)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.memberName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colorScheme.onSurface
                    )
                    Text(
                        "${device.deviceModel} - ${statusLabel(device.status)}",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            if (device.lastSeen.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Last seen: ${device.lastSeen}",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(
                    label = "Ring",
                    icon = Icons.Default.NotificationsActive,
                    color = Warning,
                    onClick = onRing,
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    label = "Lock",
                    icon = Icons.Default.Lock,
                    color = Danger,
                    onClick = onLock,
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    label = "Locate",
                    icon = Icons.Default.MyLocation,
                    color = Success,
                    onClick = onLocate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Device list item shown in the bottom sheet.
 */
@Composable
private fun DeviceListItem(
    device: FamilyDeviceMapEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRing: () -> Unit,
    onLock: () -> Unit,
    onLocate: () -> Unit,
    isAdmin: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val color = statusColor(device.status)
    val bgColor = if (isSelected) colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClickLabel = "Select ${device.memberName}'s device") { onClick() }
            .padding(horizontal = Spacing.md, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Device icon with status ring
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.memberName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.deviceModel,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        statusLabel(device.status),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                }
            }
            // Location indicator
            if (device.latitude != null && device.longitude != null) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Has location",
                    tint = Success.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Admin action row — only show for admin / owner
        if (isAdmin && isSelected) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip(
                    label = "Ring",
                    icon = Icons.Default.NotificationsActive,
                    color = Warning,
                    onClick = onRing,
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    label = "Lock",
                    icon = Icons.Default.Lock,
                    color = Danger,
                    onClick = onLock,
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    label = "Locate",
                    icon = Icons.Default.MyLocation,
                    color = Success,
                    onClick = onLocate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.md),
        color = colorScheme.outlineVariant.copy(alpha = 0.2f)
    )
}

/**
 * A small action chip button for Ring / Lock / Locate actions.
 */
@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.12f),
            contentColor = color
        )
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
