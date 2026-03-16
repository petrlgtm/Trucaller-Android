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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.StolenReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStolenReportsScreen(navController: NavController, stolenReportViewModel: StolenReportViewModel) {
    val reports by stolenReportViewModel.allReports.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Stolen Reports (${reports.size})", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger)
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(reports.sortedByDescending { it.reportedAt }, key = { it.id }) { report ->
                val statusColor = when (report.status) {
                    ReportStatus.PENDING -> Warning
                    ReportStatus.VERIFIED -> BrandDark
                    ReportStatus.ESCALATED -> Danger
                    ReportStatus.RESOLVED -> Success
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Device: ${report.deviceId}",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary
                                )
                                Text("Reported by: ${report.reportedBy}", fontSize = 13.sp, color = TextSecondary)
                            }
                            Box(
                                modifier = Modifier.background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(report.status.name, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(report.description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp, maxLines = 3)

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            report.lastKnownIp?.let {
                                Text(it, fontSize = 12.sp, color = Inactive, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            report.lastKnownLocation?.let {
                                Text(it.city, fontSize = 12.sp, color = Inactive)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(formatRelativeTime(report.reportedAt), fontSize = 12.sp, color = Inactive)
                        }

                        // Action buttons based on status
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (report.status) {
                                ReportStatus.PENDING -> {
                                    Button(
                                        onClick = { stolenReportViewModel.updateReportStatus(report.id, ReportStatus.VERIFIED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Verify", fontSize = 13.sp) }
                                    Button(
                                        onClick = { stolenReportViewModel.updateReportStatus(report.id, ReportStatus.RESOLVED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Reject", fontSize = 13.sp) }
                                }
                                ReportStatus.VERIFIED -> {
                                    Button(
                                        onClick = { stolenReportViewModel.updateReportStatus(report.id, ReportStatus.ESCALATED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Warning),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Escalate", fontSize = 13.sp) }
                                }
                                ReportStatus.ESCALATED -> {
                                    Button(
                                        onClick = { stolenReportViewModel.updateReportStatus(report.id, ReportStatus.RESOLVED) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Resolve", fontSize = 13.sp) }
                                }
                                ReportStatus.RESOLVED -> {
                                    Text("Resolved", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
