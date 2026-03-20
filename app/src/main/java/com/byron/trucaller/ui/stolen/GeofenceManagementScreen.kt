package com.byron.trucaller.ui.stolen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.byron.trucaller.data.model.Geofence
import com.byron.trucaller.data.model.GeofenceEvent
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.GeofenceViewModel
import kotlin.math.roundToInt

private val EVENT_TYPE_COLORS = mapOf(
    "ENTER" to Color(0xFF43A047),
    "EXIT" to Color(0xFFE53935),
    "DWELL" to Color(0xFFFB8C00)
)

private val TIMELINE_DOT_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF5E35B1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceManagementScreen(
    navController: NavController,
    deviceId: String,
    geofenceViewModel: GeofenceViewModel
) {
    val colorScheme = MaterialTheme.colorScheme

    val geofences by geofenceViewModel.getGeofences(deviceId).collectAsState(initial = emptyList())
    val events by geofenceViewModel.getEvents(deviceId).collectAsState(initial = emptyList())
    val isLoading by geofenceViewModel.isLoading.collectAsState()
    val error by geofenceViewModel.error.collectAsState()
    val statusMessage by geofenceViewModel.statusMessage.collectAsState()
    val currentLocation by geofenceViewModel.currentLocation.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var mapVisible by remember { mutableStateOf(false) }

    // Fetch current location on screen load
    LaunchedEffect(Unit) {
        geofenceViewModel.fetchCurrentLocation()
    }

    // Animate map in
    LaunchedEffect(geofences) {
        kotlinx.coroutines.delay(300)
        mapVisible = true
    }

    // Auto-clear status message
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(3000)
            geofenceViewModel.clearStatusMessage()
        }
    }

    // Add Geofence Dialog
    if (showAddDialog) {
        AddGeofenceDialog(
            currentLocation = currentLocation,
            onDismiss = { showAddDialog = false },
            onConfirm = { label, radius ->
                val loc = currentLocation
                if (loc != null) {
                    geofenceViewModel.addGeofence(
                        deviceId = deviceId,
                        label = label,
                        latitude = loc.first,
                        longitude = loc.second,
                        radiusMeters = radius,
                        onSuccess = { showAddDialog = false }
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Geofence Alerts",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (currentLocation != null) {
                        showAddDialog = true
                    } else {
                        geofenceViewModel.fetchCurrentLocation()
                    }
                },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, "Add Geofence") },
                text = { Text("Add Geofence", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // ── Map Section ──────────────────────────────────────────
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Brand.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Place,
                                null,
                                tint = Brand,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Geofence Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    AnimatedVisibility(
                        visible = mapVisible,
                        enter = fadeIn(tween(600)) + scaleIn(
                            initialScale = 0.95f,
                            animationSpec = tween(600)
                        )
                    ) {
                        GeofenceMapView(
                            geofences = geofences,
                            currentLocation = currentLocation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(BorderRadius.md))
                        )
                    }

                    if (geofences.isEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            "No geofences set. Tap \"Add Geofence\" to create one.",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Active Geofences List ────────────────────────────────
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    colorScheme.primary.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FmdGood,
                                null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Active Geofences",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "${geofences.size} configured",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (geofences.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    colorScheme.background,
                                    RoundedCornerShape(BorderRadius.md)
                                )
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No geofences configured yet",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        geofences.forEachIndexed { index, geofence ->
                            GeofenceListItem(
                                geofence = geofence,
                                onToggle = { active ->
                                    geofenceViewModel.toggleGeofenceActive(
                                        geofence.id,
                                        active
                                    )
                                },
                                onDelete = { geofenceViewModel.deleteGeofence(geofence.id) }
                            )
                            if (index < geofences.lastIndex) {
                                HorizontalDivider(
                                    color = colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Event Timeline ───────────────────────────────────────
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Brand.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                null,
                                tint = Brand,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Event Timeline",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (events.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    colorScheme.background,
                                    RoundedCornerShape(BorderRadius.md)
                                )
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Notifications,
                                    null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    "No geofence events yet",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Events will appear here when the device enters or exits a geofence",
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        val recentEvents = events.take(20)
                        recentEvents.forEachIndexed { index, event ->
                            GeofenceEventTimelineItem(
                                event = event,
                                geofenceLabel = geofences.find { it.id == event.geofenceId }?.label
                                    ?: "Unknown",
                                index = index,
                                isLast = index == recentEvents.lastIndex
                            )
                        }
                    }
                }

                // Bottom padding for FAB clearance
                Spacer(modifier = Modifier.height(80.dp))
            }

            // Error display
            error?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md)
                        .padding(bottom = 72.dp),
                    containerColor = Danger,
                    action = {
                        TextButton(onClick = { geofenceViewModel.clearError() }) {
                            Text("OK", color = Color.White)
                        }
                    }
                ) {
                    Text(msg, color = Color.White)
                }
            }

            // Status message
            if (error == null) {
                statusMessage?.let { msg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.md)
                            .padding(bottom = 72.dp),
                        action = {
                            TextButton(onClick = { geofenceViewModel.clearStatusMessage() }) {
                                Text("OK", color = colorScheme.onError)
                            }
                        }
                    ) {
                        Text(msg)
                    }
                }
            }
        }
    }
}

// ── Map View (osmdroid) ──────────────────────────────────────────────────

@Composable
private fun GeofenceMapView(
    geofences: List<Geofence>,
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    // Determine center: first geofence, or current location, or fallback
    val center = when {
        geofences.isNotEmpty() -> org.osmdroid.util.GeoPoint(
            geofences.first().latitude,
            geofences.first().longitude
        )
        currentLocation != null -> org.osmdroid.util.GeoPoint(
            currentLocation.first,
            currentLocation.second
        )
        else -> org.osmdroid.util.GeoPoint(0.3476, 32.5825) // Kampala fallback
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                controller.setCenter(center)
            }
        },
        update = { mapView ->
            mapView.controller.setCenter(center)

            // Clear existing overlays and re-add
            mapView.overlays.clear()

            // Draw geofence circles and markers
            for (geofence in geofences) {
                val geoPoint = org.osmdroid.util.GeoPoint(
                    geofence.latitude,
                    geofence.longitude
                )

                // Draw circle polygon for radius
                val circlePoints = buildCirclePoints(
                    geoPoint,
                    geofence.radiusMeters.toDouble()
                )
                val polygon = org.osmdroid.views.overlay.Polygon(mapView).apply {
                    points = circlePoints
                    fillPaint.color = if (geofence.isActive) {
                        android.graphics.Color.argb(40, 33, 150, 243) // Blue with alpha
                    } else {
                        android.graphics.Color.argb(20, 158, 158, 158) // Gray with alpha
                    }
                    outlinePaint.color = if (geofence.isActive) {
                        android.graphics.Color.argb(180, 33, 150, 243)
                    } else {
                        android.graphics.Color.argb(100, 158, 158, 158)
                    }
                    outlinePaint.strokeWidth = 3f
                    title = geofence.label
                    snippet = "${geofence.radiusMeters}m radius"
                }
                mapView.overlays.add(polygon)

                // Add center marker
                val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = geoPoint
                    setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                    title = geofence.label
                    snippet = if (geofence.isActive) "Active" else "Disabled"
                }
                mapView.overlays.add(marker)
            }

            // Add current location marker if available
            if (currentLocation != null) {
                val myLocationPoint = org.osmdroid.util.GeoPoint(
                    currentLocation.first,
                    currentLocation.second
                )
                val myMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = myLocationPoint
                    setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER
                    )
                    title = "Current Location"
                }
                mapView.overlays.add(myMarker)
            }

            mapView.invalidate()
        }
    )
}

/** Build a list of GeoPoints approximating a circle for the polygon overlay. */
private fun buildCirclePoints(
    center: org.osmdroid.util.GeoPoint,
    radiusMeters: Double,
    points: Int = 64
): List<org.osmdroid.util.GeoPoint> {
    val result = mutableListOf<org.osmdroid.util.GeoPoint>()
    val earthRadius = 6371000.0 // meters
    val lat = Math.toRadians(center.latitude)
    val lon = Math.toRadians(center.longitude)
    val angularRadius = radiusMeters / earthRadius

    for (i in 0..points) {
        val bearing = Math.toRadians(i.toDouble() * 360.0 / points)
        val newLat = Math.asin(
            Math.sin(lat) * Math.cos(angularRadius) +
                    Math.cos(lat) * Math.sin(angularRadius) * Math.cos(bearing)
        )
        val newLon = lon + Math.atan2(
            Math.sin(bearing) * Math.sin(angularRadius) * Math.cos(lat),
            Math.cos(angularRadius) - Math.sin(lat) * Math.sin(newLat)
        )
        result.add(
            org.osmdroid.util.GeoPoint(
                Math.toDegrees(newLat),
                Math.toDegrees(newLon)
            )
        )
    }
    return result
}

// ── Geofence List Item with swipe-to-delete ──────────────────────────────

@Composable
private fun GeofenceListItem(
    geofence: Geofence,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var offsetX by remember { mutableFloatStateOf(0f) }
    val deleteThreshold = -200f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Delete background
        if (offsetX < -20f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Danger.copy(alpha = 0.1f), RoundedCornerShape(BorderRadius.md))
                    .padding(end = Spacing.md),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = Danger,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Foreground content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(geofence.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < deleteThreshold) {
                                onDelete()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-300f, 0f)
                        }
                    )
                }
                .background(colorScheme.surfaceVariant, RoundedCornerShape(BorderRadius.md))
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (geofence.isActive) Success else colorScheme.outline,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    geofence.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${geofence.radiusMeters}m radius",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
                if (geofence.triggeredCount > 0) {
                    Text(
                        "Triggered ${geofence.triggeredCount} time${if (geofence.triggeredCount != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = Warning
                    )
                }
            }

            // Toggle switch
            Switch(
                checked = geofence.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.outline.copy(alpha = 0.2f)
                )
            )
        }
    }
}

// ── Event Timeline Item ──────────────────────────────────────────────────

@Composable
private fun GeofenceEventTimelineItem(
    event: GeofenceEvent,
    geofenceLabel: String,
    index: Int,
    isLast: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val dotColor = TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]
    val typeColor = EVENT_TYPE_COLORS[event.transitionType] ?: Brand

    // Sequential dot fade-in animation
    val dotAlpha = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        kotlinx.coroutines.delay(index * 100L)
        dotAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(dotAlpha.value)
    ) {
        // Timeline left column
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(dotColor, CircleShape)
                )
                if (!isLast) {
                    Canvas(
                        modifier = Modifier
                            .width(2.dp)
                            .height(52.dp)
                    ) {
                        drawLine(
                            color = Brand.copy(alpha = 0.4f),
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Event content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    geofenceLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text(
                        event.transitionType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                formatRelativeTime(event.timestamp),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Add Geofence Dialog ──────────────────────────────────────────────────

@Composable
private fun AddGeofenceDialog(
    currentLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onConfirm: (label: String, radiusMeters: Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var label by remember { mutableStateOf("") }
    var radiusSlider by remember { mutableFloatStateOf(200f) }
    val radiusMeters = radiusSlider.roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MyLocation,
                    null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Add Geofence", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                // Label input
                TextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. Home, Office, School") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.background,
                        unfocusedContainerColor = colorScheme.background,
                        focusedIndicatorColor = colorScheme.primary,
                        focusedLabelColor = colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Radius slider
                Text(
                    "Radius: ${radiusMeters}m",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Slider(
                    value = radiusSlider,
                    onValueChange = { radiusSlider = it },
                    valueRange = 100f..1000f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100m", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                    Text("1000m", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Location info
                if (currentLocation != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Success.copy(alpha = 0.1f),
                                RoundedCornerShape(BorderRadius.md)
                            )
                            .padding(Spacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MyLocation,
                                null,
                                tint = Success,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                "Using device location: ${
                                    String.format("%.4f", currentLocation.first)
                                }, ${String.format("%.4f", currentLocation.second)}",
                                fontSize = 12.sp,
                                color = Success
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Warning.copy(alpha = 0.1f),
                                RoundedCornerShape(BorderRadius.md)
                            )
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            "Location unavailable. Enable GPS and try again.",
                            fontSize = 12.sp,
                            color = Warning
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim(), radiusMeters) },
                enabled = label.isNotBlank() && currentLocation != null
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
