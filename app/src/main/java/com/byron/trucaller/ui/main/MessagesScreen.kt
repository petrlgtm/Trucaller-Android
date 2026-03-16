package com.byron.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsConversation
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.SurfaceLight
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.ui.theme.YellowGradientEnd
import com.byron.trucaller.ui.theme.YellowGradientStart
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsFilter
import com.byron.trucaller.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    rootNavController: NavController,
    authViewModel: AuthViewModel,
    smsViewModel: SmsViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val conversations by smsViewModel.conversations.collectAsState()
    val isLoading by smsViewModel.isLoading.collectAsState()
    val actionMessage by smsViewModel.actionMessage.collectAsState()
    val selectedFilter by smsViewModel.selectedFilter.collectAsState()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var showContent by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.READ_SMS] == true
        if (hasSmsPermission) {
            smsViewModel.loadConversations(contentResolver, user.id)
        }
    }

    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) {
            smsViewModel.loadConversations(contentResolver, user.id)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
        showContent = true
    }

    var showReportDialog by remember { mutableStateOf<SmsConversation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Premium Header with gradient ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandDark, Color(0xFF0D0D0D))
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Messages",
                            color = Brand,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "${conversations.size} conversations",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    // Spam count badge
                    val spamCount = conversations.count { it.category == SmsCategory.SPAM }
                    if (spamCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Danger.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, null, tint = Danger, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$spamCount spam",
                                    color = Danger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search messages...", color = Inactive, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Inactive, modifier = Modifier.size(20.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        cursorColor = Brand,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }
        }

        // ── Filter chips with scrollable row ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmsFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val chipColor = when (filter) {
                    SmsFilter.ALL -> Brand
                    SmsFilter.PERSONAL -> BrandGold
                    SmsFilter.TRANSACTIONAL -> Success
                    SmsFilter.PROMOTIONAL -> Warning
                    SmsFilter.SPAM -> Danger
                }
                val count = when (filter) {
                    SmsFilter.ALL -> conversations.count { it.category != SmsCategory.SPAM }
                    SmsFilter.PERSONAL -> conversations.count { it.category == SmsCategory.PERSONAL }
                    SmsFilter.TRANSACTIONAL -> conversations.count { it.category == SmsCategory.TRANSACTIONAL }
                    SmsFilter.PROMOTIONAL -> conversations.count { it.category == SmsCategory.PROMOTIONAL }
                    SmsFilter.SPAM -> conversations.count { it.category == SmsCategory.SPAM }
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { smsViewModel.setFilter(filter) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                filter.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$count",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color(0xFF1A1A1A) else Inactive
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = if (filter == SmsFilter.SPAM) Color.White else Color(0xFF1A1A1A),
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0xFF333333),
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        // ── Action message ───────────────────────────────────────────
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = SurfaceCard,
                action = {
                    TextButton(onClick = { smsViewModel.clearActionMessage() }) {
                        Text("OK", color = Brand, fontWeight = FontWeight.Bold)
                    }
                }
            ) { Text(actionMessage!!, color = TextPrimary) }
        }

        // ── Content ──────────────────────────────────────────────────
        if (!hasSmsPermission) {
            PermissionPrompt(onGrant = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            })
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading messages...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val filtered = smsViewModel.getFilteredConversations().filter { conv ->
                if (searchQuery.isBlank()) true
                else {
                    val q = searchQuery.lowercase()
                    (conv.contactName?.lowercase()?.contains(q) == true) ||
                            conv.address.contains(q) ||
                            conv.lastMessage.lowercase().contains(q)
                }
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(300))
            ) {
                if (filtered.isEmpty()) {
                    EmptyState(selectedFilter)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, it -> it.address }) { index, conversation ->
                            // Date separator
                            val showDateHeader = index == 0 || !isSameDay(
                                filtered[index - 1].lastDate,
                                conversation.lastDate
                            )
                            if (showDateHeader) {
                                DateHeader(conversation.lastDate)
                            }

                            ConversationItem(
                                conversation = conversation,
                                onClick = {
                                    val encoded = java.net.URLEncoder.encode(conversation.address, "UTF-8")
                                    rootNavController.navigate("sms_conversation/$encoded")
                                },
                                onReport = { showReportDialog = conversation },
                                onBlock = {
                                    smsViewModel.blockSmsNumber(conversation.address, user.id, contentResolver)
                                }
                            )
                        }
                        // Bottom padding
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    // Report spam dialog
    if (showReportDialog != null) {
        val conv = showReportDialog!!
        AlertDialog(
            onDismissRequest = { showReportDialog = null },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Danger, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report as Spam?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Report ${conv.contactName ?: conv.address} as a spam sender? This helps protect other users.")
            },
            confirmButton = {
                TextButton(onClick = {
                    smsViewModel.reportAsSpam(conv.address, conv.lastMessage, user.id, contentResolver)
                    showReportDialog = null
                }) {
                    Text("Report Spam", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ── Date Header ──────────────────────────────────────────────────────────

@Composable
private fun DateHeader(timestamp: Long) {
    val label = formatDateHeader(timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(Color(0xFF2A2A2A)))
        Text(
            text = label,
            color = Inactive,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(Color(0xFF2A2A2A)))
    }
}

// ── Permission Prompt ────────────────────────────────────────────────────

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Brand.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(40.dp), tint = Brand)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "SMS Permission Required",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow SMS access to view and manage your messages with spam protection.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onGrant,
            modifier = Modifier
                .background(Brand, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            Text("Grant Permission", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────

@Composable
private fun EmptyState(filter: SmsFilter) {
    val (icon, message) = when (filter) {
        SmsFilter.SPAM -> Pair(Icons.Default.Shield, "No spam messages detected")
        SmsFilter.PERSONAL -> Pair(Icons.Default.Person, "No personal messages")
        SmsFilter.TRANSACTIONAL -> Pair(Icons.Default.Business, "No transactional messages")
        SmsFilter.PROMOTIONAL -> Pair(Icons.Default.Campaign, "No promotional messages")
        SmsFilter.ALL -> Pair(Icons.AutoMirrored.Filled.Chat, "No messages found")
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = Inactive)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = TextSecondary, fontSize = 15.sp)
    }
}

// ── Conversation Item ────────────────────────────────────────────────────

@Composable
private fun ConversationItem(
    conversation: SmsConversation,
    onClick: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    val categoryColor = when (conversation.category) {
        SmsCategory.SPAM -> Danger
        SmsCategory.PROMOTIONAL -> Warning
        SmsCategory.TRANSACTIONAL -> Success
        SmsCategory.PERSONAL -> Brand
    }

    val categoryIcon = when (conversation.category) {
        SmsCategory.SPAM -> Icons.Default.Warning
        SmsCategory.PROMOTIONAL -> Icons.Default.Campaign
        SmsCategory.TRANSACTIONAL -> Icons.Default.Business
        SmsCategory.PERSONAL -> Icons.Default.Person
    }

    // Initials for avatar
    val initials = (conversation.contactName ?: conversation.address)
        .take(2)
        .uppercase()
        .let { if (it.firstOrNull()?.isDigit() == true || it.firstOrNull() == '+') "#" else it.take(1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (conversation.unreadCount > 0) SurfaceCard else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(
                            colors = when (conversation.category) {
                                SmsCategory.SPAM -> listOf(Danger.copy(alpha = 0.2f), Danger.copy(alpha = 0.1f))
                                SmsCategory.PROMOTIONAL -> listOf(Warning.copy(alpha = 0.2f), Warning.copy(alpha = 0.1f))
                                SmsCategory.TRANSACTIONAL -> listOf(Success.copy(alpha = 0.2f), Success.copy(alpha = 0.1f))
                                SmsCategory.PERSONAL -> listOf(Brand.copy(alpha = 0.2f), Brand.copy(alpha = 0.1f))
                            }
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = categoryColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Category indicator dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = 18.dp, y = 18.dp)
                    .background(Background, CircleShape)
                    .padding(2.dp)
                    .background(categoryColor, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSmsTime(conversation.lastDate),
                    fontSize = 11.sp,
                    color = if (conversation.unreadCount > 0) Brand else Inactive,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessage,
                    fontSize = 13.sp,
                    color = if (conversation.unreadCount > 0) TextPrimary.copy(alpha = 0.9f) else TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Right side indicators
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (conversation.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Brand, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${conversation.unreadCount}",
                                color = Color(0xFF1A1A1A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (conversation.isBlocked) {
                        Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp), tint = Danger)
                    }
                }
            }

            // Category + actions row
            if (conversation.category != SmsCategory.PERSONAL || conversation.isBlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.category != SmsCategory.PERSONAL) {
                        Box(
                            modifier = Modifier
                                .background(categoryColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    categoryIcon, null,
                                    modifier = Modifier.size(11.dp),
                                    tint = categoryColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = conversation.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp,
                                    color = categoryColor,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Inline action buttons
                    IconButton(onClick = onReport, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Report, "Report", tint = Inactive, modifier = Modifier.size(16.dp))
                    }
                    if (!conversation.isBlocked) {
                        IconButton(onClick = onBlock, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Block, "Block", tint = Inactive, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Subtle divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 82.dp)
            .height(0.5.dp)
            .background(Color(0xFF222222))
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun formatSmsTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale.US).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM", Locale.US).format(Date(timestamp))
    }
}

private fun formatDateHeader(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(cal, today) -> "Today"
        isSameDay(cal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(timestamp))
    }
}

private fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return isSameDay(c1, c2)
}

private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
