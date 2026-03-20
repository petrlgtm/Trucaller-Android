package com.byron.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsConversation
import com.byron.trucaller.ui.components.AvatarIndicator
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsFilter
import com.byron.trucaller.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val colorScheme = MaterialTheme.colorScheme

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
    var contextMenuConversation by remember { mutableStateOf<SmsConversation?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // -- Header with gradient --
        TruCallerHeader(
            title = "Messages",
            subtitle = "${conversations.size} conversations",
            trailingContent = {
                val spamCount = conversations.count { it.category == SmsCategory.SPAM }
                if (spamCount > 0) {
                    TruCallerBadge(
                        text = "$spamCount spam",
                        type = BadgeType.Spam,
                        icon = Icons.Default.Shield
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { rootNavController.navigate("sms_rules") }) {
                    Icon(
                        Icons.Default.Rule,
                        contentDescription = "SMS Rules",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )

        // -- Search bar --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            TruCallerTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search messages...",
                isSearch = true,
                showClearButton = true,
                onClear = { searchQuery = "" },
                contentDesc = "Search messages"
            )
        }

        // -- Filter chips with scrollable row --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SmsFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val chipColor = when (filter) {
                    SmsFilter.ALL -> colorScheme.primary
                    SmsFilter.PERSONAL -> Color(0xFFFFB300)
                    SmsFilter.TRANSACTIONAL -> Color(0xFF4CAF50)
                    SmsFilter.PROMOTIONAL -> colorScheme.primary
                    SmsFilter.SPAM -> colorScheme.error
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
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = if (filter == SmsFilter.SPAM) Color.White else colorScheme.onPrimary,
                        containerColor = colorScheme.surfaceVariant,
                        labelColor = colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colorScheme.outline,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        // -- Action message --
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = colorScheme.surfaceVariant,
                action = {
                    TextButton(onClick = { smsViewModel.clearActionMessage() }) {
                        Text("OK", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            ) { Text(actionMessage!!, color = colorScheme.onSurface) }
        }

        // -- Content --
        if (!hasSmsPermission) {
            PermissionPrompt(onGrant = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            })
        } else if (isLoading) {
            ShimmerLoadingList()
        } else {
            val filtered by remember(searchQuery, selectedFilter, conversations) {
                derivedStateOf {
                    smsViewModel.getFilteredConversations().filter { conv ->
                        if (searchQuery.isBlank()) true
                        else {
                            val q = searchQuery.lowercase()
                            (conv.contactName?.lowercase()?.contains(q) == true) ||
                                    conv.address.contains(q) ||
                                    conv.lastMessage.lowercase().contains(q)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(300))
            ) {
                if (filtered.isEmpty()) {
                    val (title, subtitle) = when (selectedFilter) {
                        SmsFilter.SPAM -> "No spam messages" to "Your inbox is clean — no spam detected."
                        SmsFilter.PERSONAL -> "No personal messages" to "Personal conversations will appear here."
                        SmsFilter.TRANSACTIONAL -> "No transactional messages" to "Bank and service notifications will appear here."
                        SmsFilter.PROMOTIONAL -> "No promotional messages" to "Marketing and promotional messages will appear here."
                        SmsFilter.ALL -> "No messages found" to "Your message inbox is empty."
                    }
                    EmptyStateView(
                        title = title,
                        subtitle = subtitle,
                        icon = EmptyStateIcon.MESSAGES
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            filtered,
                            key = { _, it -> it.address },
                            contentType = { _, _ -> "sms_conversation" }
                        ) { index, conversation ->
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
                                onLongClick = { contextMenuConversation = conversation }
                            )
                        }
                        // Bottom padding
                        item { Spacer(modifier = Modifier.height(Spacing.sm)) }
                    }
                }
            }
        }
    }

    // -- Context menu bottom sheet --
    if (contextMenuConversation != null) {
        val conv = contextMenuConversation!!
        ModalBottomSheet(
            onDismissRequest = { contextMenuConversation = null },
            sheetState = bottomSheetState,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg)
            ) {
                // Header with contact info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categoryColor = categoryColorFor(conv.category, colorScheme)
                    TruCallerAvatar(
                        name = conv.contactName ?: conv.address,
                        size = 40.dp,
                        indicator = AvatarIndicator.Category,
                        indicatorColor = categoryColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = conv.contactName ?: conv.address,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "${conv.messageCount} messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(
                    color = colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )

                // Pin action
                BottomSheetActionItem(
                    icon = Icons.Default.PushPin,
                    label = "Pin conversation",
                    iconTint = colorScheme.primary,
                    onClick = {
                        contextMenuConversation = null
                    }
                )

                // Report action
                BottomSheetActionItem(
                    icon = Icons.Default.Report,
                    label = "Report as spam",
                    iconTint = colorScheme.error,
                    onClick = {
                        showReportDialog = conv
                        contextMenuConversation = null
                    }
                )

                // Block action
                if (!conv.isBlocked) {
                    BottomSheetActionItem(
                        icon = Icons.Default.Block,
                        label = "Block sender",
                        iconTint = colorScheme.error,
                        onClick = {
                            smsViewModel.blockSmsNumber(conv.address, user.id, contentResolver)
                            contextMenuConversation = null
                        }
                    )
                }
            }
        }
    }

    // -- Report spam dialog --
    if (showReportDialog != null) {
        val conv = showReportDialog!!
        AlertDialog(
            onDismissRequest = { showReportDialog = null },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface.copy(alpha = 0.7f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = colorScheme.error, modifier = Modifier.size(22.dp))
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
                    Text("Report Spam", color = colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = null }) {
                    Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// -- Bottom Sheet Action Item --

@Composable
private fun BottomSheetActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// -- Date Header --

@Composable
private fun DateHeader(timestamp: Long) {
    val colorScheme = MaterialTheme.colorScheme
    val label = formatDateHeader(timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(colorScheme.outline.copy(alpha = 0.3f)))
        Text(
            text = label,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(colorScheme.outline.copy(alpha = 0.3f)))
    }
}

// -- Permission Prompt --

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
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
                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(40.dp), tint = colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "SMS Permission Required",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow SMS access to view and manage your messages with spam protection.",
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onGrant,
            modifier = Modifier
                .background(colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            Text("Grant Permission", color = colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// -- Conversation Item --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: SmsConversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryColor = categoryColorFor(conversation.category, colorScheme)

    val categoryIcon = when (conversation.category) {
        SmsCategory.SPAM -> Icons.Default.Warning
        SmsCategory.PROMOTIONAL -> Icons.Default.Campaign
        SmsCategory.TRANSACTIONAL -> Icons.Default.Business
        SmsCategory.PERSONAL -> Icons.Default.Person
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (conversation.unreadCount > 0) colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = Spacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar using TruCallerAvatar with category indicator
        TruCallerAvatar(
            name = conversation.contactName ?: conversation.address,
            size = 52.dp,
            indicator = AvatarIndicator.Category,
            indicatorColor = categoryColor,
            contentDesc = "${conversation.contactName ?: conversation.address} avatar"
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSmsTime(conversation.lastDate),
                    fontSize = 11.sp,
                    color = if (conversation.unreadCount > 0) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessage,
                    fontSize = 13.sp,
                    color = if (conversation.unreadCount > 0) colorScheme.onSurface.copy(alpha = 0.9f) else colorScheme.onSurface.copy(alpha = 0.6f),
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
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                                .size(22.dp)
                                .background(colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${conversation.unreadCount}",
                                color = colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (conversation.isBlocked) {
                        Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp), tint = colorScheme.error)
                    }
                }
            }

            // Category badge row (without inline action buttons)
            if (conversation.category != SmsCategory.PERSONAL || conversation.isBlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.category != SmsCategory.PERSONAL) {
                        val badgeType = when (conversation.category) {
                            SmsCategory.SPAM -> BadgeType.Spam
                            SmsCategory.PROMOTIONAL -> BadgeType.Warning
                            SmsCategory.TRANSACTIONAL -> BadgeType.Success
                            SmsCategory.PERSONAL -> BadgeType.Info
                        }
                        TruCallerBadge(
                            text = conversation.category.name.lowercase().replaceFirstChar { it.uppercase() },
                            type = badgeType,
                            icon = categoryIcon
                        )
                    }
                }
            }
        }
    }

    // Subtle divider
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        thickness = 0.5.dp,
        color = colorScheme.outline.copy(alpha = 0.2f)
    )
}

// -- Helpers --

private fun categoryColorFor(
    category: SmsCategory,
    colorScheme: androidx.compose.material3.ColorScheme
): Color {
    return when (category) {
        SmsCategory.SPAM -> colorScheme.error
        SmsCategory.PROMOTIONAL -> colorScheme.primary
        SmsCategory.TRANSACTIONAL -> Color(0xFF4CAF50)
        SmsCategory.PERSONAL -> colorScheme.primary
    }
}

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
