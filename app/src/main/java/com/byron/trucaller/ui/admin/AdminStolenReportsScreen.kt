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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.ReportStatus
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
import com.byron.trucaller.viewmodel.StolenReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStolenReportsScreen(navController: NavController, stolenReportViewModel: StolenReportViewModel) {
    val reports by stolenReportViewModel.allReports.collectAsState(initial = emptyList())
    val isInitialLoad = remember { mutableStateOf(true) }

    if (reports.isNotEmpty() && isInitialLoad.value) {
        isInitialLoad.value = false
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Stolen Reports (${reports.size})",
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

        when {
            isInitialLoad.value && reports.isEmpty() -> {
                ShimmerLoadingList(modifier = Modifier.padding(Spacing.md))
            }
            reports.isEmpty() -> {
                EmptyStateView(
                    title = "No Stolen Reports",
                    subtitle = "No devices have been reported stolen yet",
                    icon = EmptyStateIcon.GENERIC
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                    items(
                        reports.sortedByDescending { it.reportedAt },
                        key = { it.id }
                    ) { report ->
                        val statusBadgeType = when (report.status) {
                            ReportStatus.PENDING -> BadgeType.Warning
                            ReportStatus.VERIFIED -> BadgeType.Info
                            ReportStatus.ESCALATED -> BadgeType.Spam
                            ReportStatus.RESOLVED -> BadgeType.Success
                        }

                        TruCallerCard(
                            modifier = Modifier.padding(vertical = 4.dp),
                            elevation = 1.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Device: ${report.deviceId}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        "Reported by: ${report.reportedBy}",
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                TruCallerBadge(
                                    text = report.status.name,
                                    type = statusBadgeType
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                report.description,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                report.lastKnownIp?.let {
                                    Text(
                                        it,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                report.lastKnownLocation?.let {
                                    Text(
                                        it.city,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    formatRelativeTime(report.reportedAt),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            // Action buttons based on status
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (report.status) {
                                    ReportStatus.PENDING -> {
                                        TruCallerButton(
                                            text = "Verify",
                                            onClick = {
                                                stolenReportViewModel.updateReportStatus(
                                                    report.id,
                                                    ReportStatus.VERIFIED
                                                )
                                            },
                                            style = TruCallerButtonStyle.Primary,
                                            modifier = Modifier.height(36.dp)
                                        )
                                        TruCallerButton(
                                            text = "Reject",
                                            onClick = {
                                                stolenReportViewModel.updateReportStatus(
                                                    report.id,
                                                    ReportStatus.RESOLVED
                                                )
                                            },
                                            style = TruCallerButtonStyle.Danger,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                    ReportStatus.VERIFIED -> {
                                        TruCallerButton(
                                            text = "Escalate",
                                            onClick = {
                                                stolenReportViewModel.updateReportStatus(
                                                    report.id,
                                                    ReportStatus.ESCALATED
                                                )
                                            },
                                            style = TruCallerButtonStyle.Primary,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                    ReportStatus.ESCALATED -> {
                                        TruCallerButton(
                                            text = "Resolve",
                                            onClick = {
                                                stolenReportViewModel.updateReportStatus(
                                                    report.id,
                                                    ReportStatus.RESOLVED
                                                )
                                            },
                                            style = TruCallerButtonStyle.Primary,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                    ReportStatus.RESOLVED -> {
                                        Text(
                                            "Resolved",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.md)) }
                }
            }
        }
    }
}
