package com.byron.trucaller.ui.stolen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.byron.trucaller.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.BorderRadius
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.NetworkForensicsViewModel

private val TIMELINE_DOT_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1565C0), Color(0xFFFB8C00), Color(0xFF43A047),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF5E35B1)
)

private val SuccessGreen = Color(0xFF4CAF50)
private val WarningOrange = BrandGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkForensicsScreen(
    navController: NavController,
    deviceId: String,
    networkForensicsViewModel: NetworkForensicsViewModel
) {
    val state by networkForensicsViewModel.state.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(deviceId) {
        networkForensicsViewModel.loadForensics(deviceId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Network Forensics",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { networkForensicsViewModel.loadForensics(deviceId) },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh forensics")
            }
        },
        containerColor = colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            // Shimmer skeleton loading state
            ShimmerSkeletonLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.md)
            )
            return@Scaffold
        }

        if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Error",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        state.error ?: "Unknown error",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
            return@Scaffold
        }

        val uniqueIps = state.timeline.map { it.ipAddress }.toSet().size
        val uniqueIsps = state.ispAnalysis.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Custom header with network image + stats ──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=800&h=360&q=80",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colorScheme.primary.copy(alpha = 0.55f),
                                    colorScheme.primary.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colorScheme.primary,
                                colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    colorScheme.onPrimary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text(
                                "Network Analysis",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = colorScheme.onPrimary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                "Device: ${
                                    if (deviceId.length > 16) deviceId.take(16) + "..."
                                    else deviceId
                                }",
                                fontSize = 12.sp,
                                color = colorScheme.onPrimary.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Stats pills row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPillBadge(
                            label = "Events",
                            value = "${state.timeline.size}",
                            backgroundColor = colorScheme.onPrimary.copy(alpha = 0.2f),
                            textColor = colorScheme.onPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        StatPillBadge(
                            label = "IPs",
                            value = "$uniqueIps",
                            backgroundColor = colorScheme.onPrimary.copy(alpha = 0.2f),
                            textColor = colorScheme.onPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        StatPillBadge(
                            label = "ISPs",
                            value = "$uniqueIsps",
                            backgroundColor = colorScheme.onPrimary.copy(alpha = 0.2f),
                            textColor = colorScheme.onPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                // ── Last Seen card with green left border ──────────────────
                state.lastSeenLog?.let { lastLog ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BorderRadius.lg))
                    ) {
                        TruCallerCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pulsing green dot
                                PulsingDot(color = SuccessGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Last Seen",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TruCallerBadge(
                                    text = if (lastLog.networkType == "wifi") "WiFi" else "Mobile",
                                    type = if (lastLog.networkType == "wifi") BadgeType.Success else BadgeType.Warning,
                                    icon = if (lastLog.networkType == "wifi") Icons.Default.Wifi else Icons.Default.CellTower
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            // Prominent time display
                            Text(
                                formatRelativeTime(lastLog.timestamp),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = SuccessGreen
                            )

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            // Info box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        colorScheme.background,
                                        RoundedCornerShape(BorderRadius.md)
                                    )
                                    .padding(Spacing.sm)
                            ) {
                                Column {
                                    ForensicsInfoRow(
                                        "IP Address",
                                        lastLog.ipAddress,
                                        mono = true
                                    )
                                    ForensicsInfoRow("ISP", lastLog.isp)
                                    ForensicsInfoRow(
                                        "Location",
                                        "${lastLog.city}, ${lastLog.country}"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            // Coordinates monospace box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        colorScheme.primary.copy(alpha = 0.08f),
                                        RoundedCornerShape(BorderRadius.md)
                                    )
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lat: ${
                                            String.format(
                                                "%.6f",
                                                lastLog.latitude
                                            )
                                        }  Lon: ${
                                            String.format(
                                                "%.6f",
                                                lastLog.longitude
                                            )
                                        }",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Nearby Fingerprints (Bluetooth)
                            if (lastLog.nearbyDevices.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    "Nearby Fingerprints",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                lastLog.nearbyDevices.take(5).forEach { device ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_caller_id), // Generic icon for BT
                                            contentDescription = null,
                                            tint = colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${device.name} (${device.type})",
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${device.rssi} dBm",
                                            fontSize = 10.sp,
                                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                if (lastLog.nearbyDevices.size > 5) {
                                    Text(
                                        "+ ${lastLog.nearbyDevices.size - 5} more devices",
                                        fontSize = 9.sp,
                                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // Green left border strip
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .matchParentSize()
                                .background(
                                    SuccessGreen,
                                    RoundedCornerShape(
                                        topStart = BorderRadius.lg,
                                        bottomStart = BorderRadius.lg
                                    )
                                )
                                .align(Alignment.CenterStart)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Network Timeline ───────────────────────────────────────
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
                                Icons.Default.Timeline,
                                null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Network Timeline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "${state.timeline.size} entries",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (state.timeline.isEmpty()) {
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
                                "No network data available",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val context = LocalContext.current
                        val displayLogs = state.timeline.take(15)
                        displayLogs.forEachIndexed { index, log ->
                            val dotColor =
                                TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]
                            val nextDotColor = if (index < displayLogs.lastIndex) {
                                TIMELINE_DOT_COLORS[(index + 1) % TIMELINE_DOT_COLORS.size]
                            } else dotColor
                            val isLast = index == displayLogs.lastIndex

                            val dotAlpha = remember { Animatable(0f) }
                            LaunchedEffect(log.id) {
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
                                // Timeline left column with numbered dots
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        // Numbered dot (16.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(dotColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${index + 1}",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                lineHeight = 8.sp
                                            )
                                        }
                                        if (!isLast) {
                                            // Gradient connecting line
                                            Canvas(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(80.dp)
                                            ) {
                                                drawLine(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            dotColor,
                                                            nextDotColor
                                                        )
                                                    ),
                                                    start = Offset(
                                                        size.width / 2,
                                                        0f
                                                    ),
                                                    end = Offset(
                                                        size.width / 2,
                                                        size.height
                                                    ),
                                                    strokeWidth = 3.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Content card for each entry
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(bottom = Spacing.sm)
                                        .background(
                                            colorScheme.background,
                                            RoundedCornerShape(BorderRadius.md)
                                        )
                                        .padding(Spacing.sm)
                                ) {
                                    Column {
                                        // IP address chip + copy button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        dotColor.copy(alpha = 0.15f),
                                                        RoundedCornerShape(BorderRadius.full)
                                                    )
                                                    .padding(
                                                        horizontal = 10.dp,
                                                        vertical = 3.dp
                                                    )
                                            ) {
                                                Text(
                                                    log.ipAddress,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = dotColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    val clipboard =
                                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            "IP",
                                                            log.ipAddress
                                                        )
                                                    )
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy IP",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            "${log.isp} \u2022 ${log.city}, ${log.country}",
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                formatRelativeTime(log.timestamp),
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                                )
                                            )
                                            val isWifi = log.networkType == "wifi"
                                            TruCallerBadge(
                                                text = if (isWifi) "WiFi" else "Mobile",
                                                type = if (isWifi) BadgeType.Success else BadgeType.Warning,
                                                icon = if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.timeline.size > 15) {
                            Text(
                                "Showing 15 of ${state.timeline.size} entries",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── ISP Analysis with custom rounded bars ──────────────────
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
                                Icons.Default.Router,
                                null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "ISP Analysis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "${state.ispAnalysis.size} unique ISPs detected",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (state.ispAnalysis.isEmpty()) {
                        Text(
                            "No ISP data available",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else {
                        state.ispAnalysis.forEachIndexed { index, isp ->
                            val barColor =
                                TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]

                            val animatedProgress by animateFloatAsState(
                                targetValue = isp.percentage / 100f,
                                animationSpec = tween(
                                    durationMillis = 800,
                                    delayMillis = index * 150
                                ),
                                label = "isp_bar_$index"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ISP icon with unique color
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            barColor.copy(alpha = 0.12f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Router,
                                        contentDescription = null,
                                        tint = barColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            isp.isp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${
                                                String.format(
                                                    "%.1f",
                                                    isp.percentage
                                                )
                                            }%",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = barColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Custom rounded bar chart
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(barColor.copy(alpha = 0.1f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedProgress)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            barColor,
                                                            barColor.copy(alpha = 0.7f)
                                                        )
                                                    ),
                                                    RoundedCornerShape(5.dp)
                                                )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${isp.count} connections",
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            if (index < state.ispAnalysis.lastIndex) {
                                HorizontalDivider(
                                    color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Geographic Summary with map-like grid ──────────────────
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    colorScheme.error.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Public,
                                null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Geographic Summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "${state.geoSummary.size} locations detected",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (state.geoSummary.isEmpty()) {
                        Text(
                            "No geographic data available",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else {
                        // Map-like grid background container
                        val gridColor = colorScheme.outlineVariant.copy(alpha = 0.15f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BorderRadius.md))
                                .drawBehind {
                                    // Draw a subtle grid pattern
                                    val gridSpacing = 24.dp.toPx()
                                    val lineWidth = 1.dp.toPx()
                                    // Vertical lines
                                    var x = 0f
                                    while (x < size.width) {
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(x, 0f),
                                            end = Offset(x, size.height),
                                            strokeWidth = lineWidth
                                        )
                                        x += gridSpacing
                                    }
                                    // Horizontal lines
                                    var y = 0f
                                    while (y < size.height) {
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = lineWidth
                                        )
                                        y += gridSpacing
                                    }
                                }
                                .background(colorScheme.background.copy(alpha = 0.5f))
                                .padding(Spacing.sm)
                        ) {
                            Column {
                                state.geoSummary.forEachIndexed { index, geo ->
                                    val geoColor =
                                        TIMELINE_DOT_COLORS[index % TIMELINE_DOT_COLORS.size]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Flag-like colored circle
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(
                                                    geoColor.copy(alpha = 0.15f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                null,
                                                tint = geoColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${geo.city}, ${geo.country}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = colorScheme.onSurface
                                            )
                                            Text(
                                                "${
                                                    String.format(
                                                        "%.1f",
                                                        geo.percentage
                                                    )
                                                }% of connections",
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                                )
                                            )
                                        }

                                        // Prominent count badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    geoColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(BorderRadius.full)
                                                )
                                                .padding(
                                                    horizontal = 12.dp,
                                                    vertical = 4.dp
                                                )
                                        ) {
                                            Text(
                                                "${geo.count}x",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = geoColor
                                            )
                                        }
                                    }

                                    if (index < state.geoSummary.lastIndex) {
                                        HorizontalDivider(
                                            color = colorScheme.outlineVariant.copy(
                                                alpha = 0.3f
                                            ),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Network Changes with visual flow ───────────────────────
                TruCallerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    WarningOrange.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CompareArrows,
                                null,
                                tint = WarningOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Network Changes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "${state.networkChanges.size} switches detected",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (state.networkChanges.isEmpty()) {
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
                                "No network switches detected",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val displayChanges = state.networkChanges.take(10)
                        displayChanges.forEachIndexed { index, change ->
                            NetworkChangeCard(
                                change = change,
                                colorScheme = colorScheme,
                                fromColor = TIMELINE_DOT_COLORS[(index * 2) % TIMELINE_DOT_COLORS.size],
                                toColor = TIMELINE_DOT_COLORS[(index * 2 + 1) % TIMELINE_DOT_COLORS.size]
                            )

                            if (index < displayChanges.lastIndex) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                            }
                        }

                        if (state.networkChanges.size > 10) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                "Showing 10 of ${state.networkChanges.size} changes",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Bottom spacing for FAB
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// ── Helper composables ─────────────────────────────────────────────────────

@Composable
private fun StatPillBadge(
    label: String,
    value: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(BorderRadius.lg))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = textColor
            )
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsing_alpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

@Composable
private fun NetworkChangeCard(
    change: com.byron.trucaller.viewmodel.NetworkChange,
    colorScheme: androidx.compose.material3.ColorScheme,
    fromColor: Color,
    toColor: Color
) {
    // Animated arrow offset
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_offset"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colorScheme.background,
                RoundedCornerShape(BorderRadius.md)
            )
    ) {
        // Timestamp bar at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    WarningOrange.copy(alpha = 0.1f),
                    RoundedCornerShape(
                        topStart = BorderRadius.md,
                        topEnd = BorderRadius.md
                    )
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            Text(
                formatRelativeTime(change.timestamp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WarningOrange
            )
        }

        // Visual flow: From -> Arrow -> To
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // From side
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(fromColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(fromColor, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "FROM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    change.fromIsp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    change.fromCity,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Animated arrow in center
            Icon(
                Icons.Default.SwapHoriz,
                null,
                tint = WarningOrange,
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = arrowOffset.dp)
                    .padding(horizontal = 2.dp)
            )

            // To side
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(toColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(toColor, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "TO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    change.toIsp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    change.toCity,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Network type change badge
        if (change.fromNetworkType != change.toNetworkType) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm)
            ) {
                TruCallerBadge(
                    text = "${change.fromNetworkType.replaceFirstChar { it.uppercase() }} \u2192 ${change.toNetworkType.replaceFirstChar { it.uppercase() }}",
                    type = BadgeType.Warning
                )
            }
        }
    }
}

@Composable
private fun ShimmerSkeletonLayout(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val shimmerColor = colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.1f)

    Column(modifier = modifier) {
        // Header skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // Last seen card skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // Timeline skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // ISP analysis skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // Geo summary skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(BorderRadius.lg))
                .background(shimmerColor)
        )
    }
}

@Composable
private fun ForensicsInfoRow(label: String, value: String, mono: Boolean = false) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
