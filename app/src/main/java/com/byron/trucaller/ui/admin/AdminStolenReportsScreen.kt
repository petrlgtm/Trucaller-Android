package com.byron.trucaller.ui.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import kotlinx.coroutines.launch

private const val REPORTS_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStolenReportsScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val reports = remember { mutableStateListOf<Map<String, Any>>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(0) }
    var updatingId by remember { mutableStateOf<String?>(null) }

    val lazyListState = rememberLazyListState()

    suspend fun loadPage(pageSkip: Int) {
        val result = ApiClient.getAdminStolenReports(skip = pageSkip, limit = REPORTS_PAGE_SIZE)
        if (result.success && result.data != null) {
            if (pageSkip == 0) { reports.clear(); reports.addAll(result.data) }
            else reports.addAll(result.data)
            hasMore = result.data.size >= REPORTS_PAGE_SIZE
            skip = pageSkip + result.data.size
        }
    }

    LaunchedEffect(Unit) {
        loadPage(0)
        isLoading = false
    }

    LaunchedEffect(lazyListState, hasMore) {
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3 && hasMore && !isLoadingMore && !isLoading) {
                isLoadingMore = true
                loadPage(skip)
                isLoadingMore = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Stolen Reports (${reports.size}${if (hasMore) "+" else ""})",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onError
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onError)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.error)
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> ShimmerLoadingList(modifier = Modifier.padding(innerPadding).padding(Spacing.md))
            reports.isEmpty() -> EmptyStateView(
                title = "No Stolen Reports",
                subtitle = "No devices have been reported stolen yet",
                icon = EmptyStateIcon.GENERIC
            )
            else -> LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().background(colorScheme.background)
                    .padding(innerPadding).padding(horizontal = Spacing.md)
            ) {
                itemsIndexed(reports, key = { _, r -> r["_id"]?.toString() ?: r.hashCode().toString() }) { _, report ->
                    val reportId = report["_id"]?.toString() ?: ""
                    val deviceId = report["deviceId"]?.toString() ?: "Unknown"
                    val reportedBy = report["reportedBy"]?.toString() ?: ""
                    val description = report["description"]?.toString() ?: ""
                    val status = report["status"]?.toString() ?: "PENDING"
                    val reportedAt = report["reportedAt"]?.toString() ?: report["createdAt"]?.toString() ?: ""
                    val lastKnownIp = report["lastKnownIp"]?.toString()
                    @Suppress("UNCHECKED_CAST")
                    val location = (report["lastKnownLocation"] as? Map<String, Any>)?.get("city")?.toString()
                    val isThisUpdating = updatingId == reportId

                    val statusBadgeType = when (status) {
                        "PENDING" -> BadgeType.Warning
                        "VERIFIED" -> BadgeType.Info
                        "ESCALATED" -> BadgeType.Spam
                        "RESOLVED" -> BadgeType.Success
                        else -> BadgeType.Info
                    }

                    TruCallerCard(modifier = Modifier.padding(vertical = 4.dp), elevation = 1.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Device: $deviceId", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onSurface)
                                if (reportedBy.isNotBlank()) {
                                    Text("Reported by: $reportedBy", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }
                            TruCallerBadge(text = status, type = statusBadgeType)
                        }

                        if (description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(description, fontSize = 13.sp, color = colorScheme.onSurfaceVariant, lineHeight = 18.sp, maxLines = 3)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            lastKnownIp?.let {
                                Text(it, fontSize = 12.sp, color = colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            location?.let {
                                Text(it, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            if (reportedAt.isNotBlank()) {
                                Text(formatRelativeTime(reportedAt), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                            }
                        }

                        if (status != "RESOLVED") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val (nextStatus, btnLabel, btnStyle) = when (status) {
                                    "PENDING" -> Triple("VERIFIED", "Verify", TruCallerButtonStyle.Primary)
                                    "VERIFIED" -> Triple("ESCALATED", "Escalate", TruCallerButtonStyle.Primary)
                                    "ESCALATED" -> Triple("RESOLVED", "Resolve", TruCallerButtonStyle.Primary)
                                    else -> Triple("RESOLVED", "Resolve", TruCallerButtonStyle.Primary)
                                }
                                TruCallerButton(
                                    text = btnLabel,
                                    onClick = {
                                        coroutineScope.launch {
                                            updatingId = reportId
                                            val r = ApiClient.updateAdminStolenReportStatus(reportId, nextStatus)
                                            if (r.success) {
                                                val idx = reports.indexOfFirst { it["_id"]?.toString() == reportId }
                                                if (idx >= 0) reports[idx] = reports[idx].toMutableMap().also { it["status"] = nextStatus }
                                                snackbarHostState.showSnackbar("Status updated to $nextStatus")
                                            } else {
                                                snackbarHostState.showSnackbar(r.error ?: "Failed to update")
                                            }
                                            updatingId = null
                                        }
                                    },
                                    style = btnStyle,
                                    modifier = Modifier.height(36.dp),
                                    isLoading = isThisUpdating,
                                    enabled = updatingId == null
                                )
                                if (status == "PENDING") {
                                    TruCallerButton(
                                        text = "Reject",
                                        onClick = {
                                            coroutineScope.launch {
                                                updatingId = reportId
                                                val r = ApiClient.updateAdminStolenReportStatus(reportId, "RESOLVED")
                                                if (r.success) {
                                                    val idx = reports.indexOfFirst { it["_id"]?.toString() == reportId }
                                                    if (idx >= 0) reports[idx] = reports[idx].toMutableMap().also { it["status"] = "RESOLVED" }
                                                    snackbarHostState.showSnackbar("Report rejected")
                                                } else {
                                                    snackbarHostState.showSnackbar(r.error ?: "Failed")
                                                }
                                                updatingId = null
                                            }
                                        },
                                        style = TruCallerButtonStyle.Danger,
                                        modifier = Modifier.height(36.dp),
                                        isLoading = isThisUpdating,
                                        enabled = updatingId == null
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    if (isLoadingMore) {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colorScheme.error)
                        }
                    } else if (!hasMore && reports.size > REPORTS_PAGE_SIZE) {
                        Text(
                            "All ${reports.size} reports loaded",
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(Spacing.md)) }
            }
        }
    }
}
