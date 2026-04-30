package com.byron.trucaller.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.ErrorStateView
import com.byron.trucaller.ui.components.ShimmerLoadingCard
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AnalyticsViewModel
import com.byron.trucaller.viewmodel.MonthlyDataPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    analyticsViewModel: AnalyticsViewModel
) {
    val uiState by analyticsViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Staggered animations
    var showHeader by remember { mutableStateOf(false) }
    var showHero by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHeader = true
        delay(100)
        showHero = true
        delay(100)
        showGrid = true
        delay(100)
        showChart = true
        delay(100)
        showInsights = true
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                analyticsViewModel.refresh()
                delay(800)
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .testTag("analytics_screen")
    ) {
        if (uiState.isLoading && uiState.stats.callsBlocked == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TruCallerHeader(
                    title = "Analytics",
                    subtitle = "Your protection summary",
                    trailingContent = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
                )
                repeat(3) {
                    ShimmerLoadingCard(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
                }
            }
            return@PullToRefreshBox
        }

        if (uiState.error != null && uiState.stats.callsBlocked == 0 && uiState.stats.smsBlocked == 0) {
            ErrorStateView(
                message = uiState.error ?: "Something went wrong",
                onRetry = { analyticsViewModel.refresh() }
            )
            return@PullToRefreshBox
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header with back navigation
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(400))
            ) {
                TruCallerHeader(
                    title = "Analytics",
                    subtitle = "Your protection summary",
                    trailingContent = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
                )
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                // Protection Summary Hero Card
                AnimatedVisibility(
                    visible = showHero,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(350))
                ) {
                    ProtectionSummaryCard(
                        totalBlocked = uiState.stats.callsBlocked + uiState.stats.smsBlocked,
                        weeklyDelta = uiState.stats.weeklyBlockedDelta
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Stats Grid
                AnimatedVisibility(
                    visible = showGrid,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(400))
                ) {
                    Column {
                        Text(
                            "Statistics",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        StatsGrid(stats = uiState.stats)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Monthly Trends Bar Chart
                AnimatedVisibility(
                    visible = showChart,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(450))
                ) {
                    Column {
                        Text(
                            "Monthly Trends",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MonthlyTrendsChart(
                            data = uiState.history,
                            callsColor = colorScheme.primary,
                            smsColor = colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Insights
                AnimatedVisibility(
                    visible = showInsights,
                    enter = slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(500))
                ) {
                    Column {
                        Text(
                            "Insights",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.insights.forEachIndexed { index, insight ->
                            InsightCard(text = insight, index = index)
                            if (index < uiState.insights.lastIndex) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }
}

@Composable
private fun ProtectionSummaryCard(
    totalBlocked: Int,
    weeklyDelta: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    val trendIcon = if (weeklyDelta >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    val trendColor = if (weeklyDelta >= 0) Color(0xFF4CAF50) else colorScheme.error
    val trendText = if (weeklyDelta >= 0) "+$weeklyDelta this week" else "$weeklyDelta this week"

    TruCallerCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("protection_summary_card"),
        cornerRadius = 20.dp,
        elevation = 0.dp,
        containerColor = colorScheme.primary
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colorScheme.onPrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Protection shield",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$totalBlocked",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.onPrimary,
                letterSpacing = (-1).sp
            )
            Text(
                text = "threats rejected",
                fontSize = 14.sp,
                color = colorScheme.onPrimary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        colorScheme.onPrimary.copy(alpha = 0.15f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    trendIcon,
                    contentDescription = "Trend",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = trendText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: com.byron.trucaller.viewmodel.AnalyticsStats) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Block,
                label = "Calls Rejected",
                value = "${stats.callsBlocked}",
                color = colorScheme.error
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.MarkEmailRead,
                label = "SMS Rejected",
                value = "${stats.smsBlocked}",
                color = colorScheme.tertiary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                label = "Spam Identified",
                value = "${stats.spamIdentified}",
                color = Color(0xFF4CAF50)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Search,
                label = "Lookups",
                value = "${stats.numbersLookedUp}",
                color = colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ContactPhone,
                label = "Contacts Synced",
                value = "${stats.contactsSynced}",
                color = Brand
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                label = "Stolen Reports",
                value = "${stats.stolenReports}",
                color = BrandGold
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    val colorScheme = MaterialTheme.colorScheme

    TruCallerCard(
        modifier = modifier.testTag("stat_card_${label.lowercase().replace(" ", "_")}"),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        containerColor = colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    value,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    label,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MonthlyTrendsChart(
    data: List<MonthlyDataPoint>,
    callsColor: Color,
    smsColor: Color
) {
    val colorScheme = MaterialTheme.colorScheme

    if (data.isEmpty()) {
        TruCallerCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp
        ) {
            Text(
                "No trend data available yet.",
                color = colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(Spacing.md),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val maxValue = data.maxOfOrNull { maxOf(it.callsBlocked, it.smsBlocked) }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    // Animated bar height progress
    var animateChart by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animateChart) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "chartAnimation"
    )
    LaunchedEffect(data) {
        animateChart = false
        delay(100)
        animateChart = true
    }

    TruCallerCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_trends_chart"),
        elevation = 0.dp
    ) {
        Column {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(callsColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Calls", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(smsColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("SMS", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas bar chart
            val labelColor = colorScheme.onSurfaceVariant
            val gridLineColor = GlassBorder

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 30f // Reserve space for labels
                val barGroupWidth = chartWidth / data.size
                val barWidth = barGroupWidth * 0.3f
                val barGap = barGroupWidth * 0.05f

                // Draw horizontal grid lines with Y-axis labels
                val yAxisPaint = android.graphics.Paint().apply {
                    color = labelColor.hashCode()
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                }
                for (i in 0..3) {
                    val y = chartHeight * (1f - i / 3f)
                    drawLine(
                        color = gridLineColor.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                    val labelVal = (maxValue * i / 3f).toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        "$labelVal",
                        4f,
                        y - 4f,
                        yAxisPaint
                    )
                }

                data.forEachIndexed { index, point ->
                    val groupX = barGroupWidth * index + barGroupWidth * 0.15f

                    // Calls bar
                    val callsHeight = (point.callsBlocked / maxValue) * chartHeight * animatedProgress
                    drawRoundRect(
                        color = callsColor,
                        topLeft = Offset(groupX, chartHeight - callsHeight),
                        size = Size(barWidth, callsHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    // SMS bar
                    val smsHeight = (point.smsBlocked / maxValue) * chartHeight * animatedProgress
                    drawRoundRect(
                        color = smsColor,
                        topLeft = Offset(groupX + barWidth + barGap, chartHeight - smsHeight),
                        size = Size(barWidth, smsHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    // Month label
                    val textPaint = android.graphics.Paint().apply {
                        color = labelColor.hashCode()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        point.month,
                        barGroupWidth * index + barGroupWidth / 2,
                        size.height - 2f,
                        textPaint
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    text: String,
    index: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    val insightColors = listOf(
        colorScheme.primary,
        Color(0xFF4CAF50),
        Brand,
        BrandGold,
        colorScheme.tertiary,
        colorScheme.error
    )
    val color = insightColors[index % insightColors.size]

    TruCallerCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insight_card_$index"),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        containerColor = colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Insight",
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                color = colorScheme.onSurface,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
