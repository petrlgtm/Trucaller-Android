package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
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

private const val SPAM_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSmsSpamScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val reports = remember { mutableStateListOf<Map<String, Any>>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(0) }
    val lazyListState = rememberLazyListState()

    suspend fun loadPage(pageSkip: Int) {
        val result = ApiClient.getAdminSmsSpamReports(skip = pageSkip, limit = SPAM_PAGE_SIZE)
        if (result.success && result.data != null) {
            if (pageSkip == 0) {
                reports.clear()
                reports.addAll(result.data)
            } else {
                reports.addAll(result.data)
            }
            hasMore = result.data.size >= SPAM_PAGE_SIZE
            skip = pageSkip + result.data.size
        }
    }

    LaunchedEffect(Unit) {
        loadPage(0)
        isLoading = false
    }

    // Infinite scroll
    LaunchedEffect(lazyListState, hasMore) {
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3 && hasMore && !isLoadingMore) {
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
                        "SMS Spam Reports (${reports.size})",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> ShimmerLoadingList(modifier = Modifier.padding(innerPadding).padding(Spacing.md))
            reports.isEmpty() -> EmptyStateView(
                title = "No SMS Spam Reports",
                subtitle = "No spam SMS reports have been submitted yet",
                icon = EmptyStateIcon.GENERIC
            )
            else -> LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.md)
            ) {
                itemsIndexed(reports, key = { _, r -> r["_id"]?.toString() ?: r.hashCode().toString() }) { _, report ->
                    val reportId = report["_id"]?.toString() ?: ""
                    val sender = report["senderNumber"]?.toString() ?: report["phoneNumber"]?.toString() ?: "Unknown"
                    val body = report["messageBody"]?.toString() ?: report["message"]?.toString() ?: ""
                    val reason = report["reason"]?.toString() ?: ""
                    val status = report["status"]?.toString() ?: "PENDING"
                    val reportedAt = report["reportedAt"]?.toString() ?: report["createdAt"]?.toString() ?: ""

                    val badgeType = when (status) {
                        "REVIEWED" -> BadgeType.Success
                        "DISMISSED" -> BadgeType.Info
                        else -> BadgeType.Warning
                    }

                    TruCallerCard(modifier = Modifier.padding(vertical = 4.dp), elevation = 1.dp) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sender, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorScheme.onSurface)
                                    if (reportedAt.isNotBlank()) {
                                        Text(formatRelativeTime(reportedAt), fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                }
                                TruCallerBadge(text = status, type = badgeType)
                            }
                            if (body.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (body.length > 120) body.take(120) + "…" else body,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            if (reason.isNotBlank()) {
                                Text("Reason: $reason", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                            }
                            if (status == "PENDING") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TruCallerButton(
                                        text = "Review",
                                        onClick = {
                                            coroutineScope.launch {
                                                val r = ApiClient.updateSmsSpamStatus(reportId, "REVIEWED")
                                                if (r.success) {
                                                    val idx = reports.indexOfFirst { it["_id"]?.toString() == reportId }
                                                    if (idx >= 0) reports[idx] = reports[idx].toMutableMap().also { it["status"] = "REVIEWED" }
                                                    snackbarHostState.showSnackbar("Marked as reviewed")
                                                } else snackbarHostState.showSnackbar(r.error ?: "Failed")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        style = TruCallerButtonStyle.Primary
                                    )
                                    TruCallerButton(
                                        text = "Dismiss",
                                        onClick = {
                                            coroutineScope.launch {
                                                val r = ApiClient.updateSmsSpamStatus(reportId, "DISMISSED")
                                                if (r.success) {
                                                    val idx = reports.indexOfFirst { it["_id"]?.toString() == reportId }
                                                    if (idx >= 0) reports[idx] = reports[idx].toMutableMap().also { it["status"] = "DISMISSED" }
                                                    snackbarHostState.showSnackbar("Dismissed")
                                                } else snackbarHostState.showSnackbar(r.error ?: "Failed")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        style = TruCallerButtonStyle.Secondary
                                    )
                                }
                            } else if (status == "REVIEWED") {
                                Spacer(modifier = Modifier.height(8.dp))
                                TruCallerButton(
                                    text = "Promote to Caller ID",
                                    onClick = {
                                        coroutineScope.launch {
                                            val r = ApiClient.promoteSmsSpamToCallerId(reportId)
                                            if (r.success) {
                                                snackbarHostState.showSnackbar("$sender added to spam caller ID list")
                                            } else snackbarHostState.showSnackbar(r.error ?: "Failed")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TruCallerButtonStyle.Danger
                                )
                            }
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.Center) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}
