package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
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
import com.byron.trucaller.viewmodel.AlarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAlarmLogsScreen(navController: NavController, alarmViewModel: AlarmViewModel) {
    val allLogs by alarmViewModel.allLogs.collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf<AlarmType?>(null) }
    var selectedResult by remember { mutableStateOf<AlarmResult?>(null) }

    val filteredLogs by remember(allLogs, selectedType, selectedResult) {
        derivedStateOf {
            allLogs
                .let { logs -> if (selectedType != null) logs.filter { it.type == selectedType } else logs }
                .let { logs -> if (selectedResult != null) logs.filter { it.result == selectedResult } else logs }
                .sortedByDescending { it.triggeredAt }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Alarm Logs (${filteredLogs.size})", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        // Filter chips - Type
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Type", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Brand.copy(alpha = 0.15f),
                        selectedLabelColor = BrandDark
                    )
                )
                AlarmType.entries.forEach { type ->
                    val label = when (type) {
                        AlarmType.REMOTE_ALARM -> "Remote Alarm"
                        AlarmType.LOCATION_REQUEST -> "Location"
                        AlarmType.LOCK_DEVICE -> "Lock Device"
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Brand.copy(alpha = 0.15f),
                            selectedLabelColor = BrandDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Result", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedResult == null,
                    onClick = { selectedResult = null },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Brand.copy(alpha = 0.15f),
                        selectedLabelColor = BrandDark
                    )
                )
                AlarmResult.entries.forEach { result ->
                    val resultColor = when (result) {
                        AlarmResult.SUCCESS -> Success
                        AlarmResult.FAILED -> Danger
                        AlarmResult.PENDING -> Warning
                    }
                    FilterChip(
                        selected = selectedResult == result,
                        onClick = { selectedResult = if (selectedResult == result) null else result },
                        label = { Text(result.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = resultColor.copy(alpha = 0.15f),
                            selectedLabelColor = resultColor
                        )
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(filteredLogs, key = { it.id }) { log ->
                val typeColor = when (log.type) {
                    AlarmType.REMOTE_ALARM -> Danger
                    AlarmType.LOCATION_REQUEST -> BrandDark
                    AlarmType.LOCK_DEVICE -> Warning
                }
                val typeLabel = when (log.type) {
                    AlarmType.REMOTE_ALARM -> "Remote Alarm"
                    AlarmType.LOCATION_REQUEST -> "Location Request"
                    AlarmType.LOCK_DEVICE -> "Lock Device"
                }
                val resultColor = when (log.result) {
                    AlarmResult.SUCCESS -> Success
                    AlarmResult.FAILED -> Danger
                    AlarmResult.PENDING -> Warning
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Device: ${log.deviceId}",
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(log.triggeredByName, fontSize = 13.sp, color = TextSecondary)
                                    Box(
                                        modifier = Modifier.background(
                                            if (log.triggeredByRole == "admin") Color(0xFF6A1B9A).copy(alpha = 0.1f)
                                            else BrandDark.copy(alpha = 0.1f), RoundedCornerShape(4.dp)
                                        ).padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            if (log.triggeredByRole == "admin") "Admin" else "User",
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (log.triggeredByRole == "admin") Color(0xFF6A1B9A) else BrandDark
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier.background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(typeLabel, color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier.background(resultColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(log.result.name, color = resultColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (log.notes != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(log.notes, fontSize = 12.sp, color = Inactive)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatRelativeTime(log.triggeredAt), fontSize = 11.sp, color = Inactive)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
