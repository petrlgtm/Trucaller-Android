package com.byron.trucaller.ui.main

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.byron.trucaller.ui.components.PermissionPromptView
import com.byron.trucaller.ui.components.SectionDateHeader
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.ui.components.formatRelativeTimestamp
import com.byron.trucaller.ui.components.isSameTimestampDay
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsFilter
import com.byron.trucaller.viewmodel.SmsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    // Default SMS Check
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null

    fun checkIsDefault() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
        roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    } else true

    var isDefaultSms by remember { mutableStateOf(checkIsDefault()) }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { isDefaultSms = checkIsDefault() }

    // Re-check each time the screen comes back into view (e.g. after granting via Settings)
    val lifecycleOwner2 = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner2) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefaultSms = checkIsDefault()
        }
        lifecycleOwner2.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner2.lifecycle.removeObserver(observer) }
    }

    val conversations by smsViewModel.conversations.collectAsState()
    val filteredConversations by smsViewModel.filteredConversations.collectAsState()
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

    // Reload when returning from SMS rules screen so new rules apply immediately
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasSmsPermission) {
                smsViewModel.loadConversations(contentResolver, user.id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Local search filter combined with VM's filtered conversations
    val displayedConversations = remember(filteredConversations, searchQuery) {
        if (searchQuery.isBlank()) filteredConversations
        else filteredConversations.filter { 
            (it.contactName?.contains(searchQuery, ignoreCase = true) == true) || 
            it.address.contains(searchQuery) ||
            it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Redesigned Header with Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(colorScheme.primary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        ) {
            TruCallerHeader(
                title = "Messages",
                subtitle = if (conversations.isNotEmpty()) "${conversations.size} Conversations" else null,
                trailingContent = {
                    IconButton(onClick = { rootNavController.navigate("sms_rules") }) {
                        Icon(
                            Icons.Default.Rule,
                            contentDescription = "SMS Rules",
                            tint = colorScheme.primary
                        )
                    }
                }
            )
        }

        // -- Search bar --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
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

        if (!isDefaultSms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Secure Your Inbox",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "Filter spam and identify unknown senders automatically.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            if (intent != null) roleLauncher.launch(intent)
                        }
                    ) {
                        Text("SET AS DEFAULT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // -- Filter chips --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmsFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { smsViewModel.setFilter(filter) },
                    label = { 
                        Text(
                            text = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary,
                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        labelColor = colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // -- Content --
        if (!hasSmsPermission) {
            Box(modifier = Modifier.weight(1f)) {
                PermissionPromptView(
                    title = "SMS Access Required",
                    description = "Trucaller needs SMS permissions to identify spam messages and organize your inbox.",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                        )
                    }
                )
            }
        } else {
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoading && conversations.isEmpty(),
                onRefresh = { smsViewModel.loadConversations(contentResolver, user.id) },
                state = refreshState,
                modifier = Modifier.weight(1f)
            ) {
                if (displayedConversations.isEmpty() && !isLoading) {
                    EmptyStateView(
                        title = if (searchQuery.isEmpty()) "No messages yet" else "No results found",
                        subtitle = if (searchQuery.isEmpty()) "Your SMS conversations will appear here." else "Try a different search term.",
                        icon = EmptyStateIcon.MESSAGES
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                    ) {
                        var lastDay: Long = 0
                        itemsIndexed(displayedConversations, key = { _, conv -> conv.address }) { _, conversation ->
                            val currentDay = conversation.lastDate
                            if (!isSameTimestampDay(currentDay, lastDay)) {
                                SectionDateHeader(timestamp = currentDay)
                                lastDay = currentDay
                            }

                            SmsConversationItem(
                                conversation = conversation,
                                onClick = {
                                    rootNavController.navigate("sms_conversation/${conversation.address}")
                                }
                            )
                        }
                    }
                }
            }
        }

        // Action message snackbar
        if (actionMessage != null) {
            LaunchedEffect(actionMessage) {
                delay(3000)
                smsViewModel.clearActionMessage()
            }
            Snackbar(
                modifier = Modifier.padding(Spacing.md),
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant
            ) { Text(actionMessage!!) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmsConversationItem(
    conversation: SmsConversation,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSpam = conversation.category == SmsCategory.SPAM

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* Options */ }
            )
            .padding(horizontal = Spacing.lg, vertical = 12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TruCallerAvatar(
            name = conversation.contactName ?: conversation.address,
            size = 54.dp,
            indicator = if (isSpam) AvatarIndicator.Category else null,
            indicatorColor = if (isSpam) Color.Red else null
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isSpam) colorScheme.error else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTimestamp(conversation.lastDate),
                    fontSize = 12.sp,
                    color = if (conversation.unreadCount > 0) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessage,
                    fontSize = 14.sp,
                    color = if (conversation.unreadCount > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                )
                
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (conversation.category != SmsCategory.PERSONAL) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TruCallerBadge(
                        text = conversation.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        type = when(conversation.category) {
                            SmsCategory.SPAM -> BadgeType.Spam
                            else -> BadgeType.Success
                        },
                        backgroundColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
