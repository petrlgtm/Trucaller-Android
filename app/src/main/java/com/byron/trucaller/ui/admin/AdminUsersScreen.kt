package com.byron.trucaller.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.AdminUserSummary
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ADMIN_USERS_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdminUsersScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    val users = remember { mutableStateListOf<AdminUserSummary>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var skip by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isInSearchMode by remember { mutableStateOf(false) }

    // Select mode state
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var isBulkDeleting by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    // Exit select mode on back
    BackHandler(enabled = isSelectMode) {
        isSelectMode = false
        selectedIds.clear()
    }

    suspend fun loadPage(pageSkip: Int) {
        val result = ApiClient.getAdminUsers(skip = pageSkip, limit = ADMIN_USERS_PAGE_SIZE)
        if (result.success && result.data != null) {
            if (pageSkip == 0) { users.clear(); users.addAll(result.data) }
            else users.addAll(result.data)
            hasMore = result.data.size >= ADMIN_USERS_PAGE_SIZE
            skip = pageSkip + result.data.size
            loadError = null
        } else {
            loadError = result.error ?: "Failed to load users"
        }
    }

    // Initial load
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
            if (!isInSearchMode && total > 0 && last >= total - 3 && hasMore && !isLoadingMore && !isLoading) {
                isLoadingMore = true
                loadPage(skip)
                isLoadingMore = false
            }
        }
    }

    // Debounced server search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(400)
            isSearching = true
            isInSearchMode = true
            val result = ApiClient.searchAdminUsers(searchQuery)
            if (result.success && result.data != null) {
                users.clear()
                users.addAll(result.data)
                hasMore = false
            }
            isSearching = false
        } else if (searchQuery.isEmpty() && isInSearchMode) {
            // Return to full server list
            isInSearchMode = false
            isLoading = true
            skip = 0
            hasMore = true
            loadPage(0)
            isLoading = false
        }
    }

    // Bulk delete handler
    val onBulkDelete: () -> Unit = {
        coroutineScope.launch {
            isBulkDeleting = true
            val ids = selectedIds.toList()
            val result = ApiClient.bulkDeleteAdminUsers(ids)
            if (result.success) {
                ids.forEach { id -> users.removeAll { it.id == id } }
                selectedIds.clear()
                isSelectMode = false
            }
            isBulkDeleting = false
            showBulkDeleteDialog = false
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBulkDeleting) showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} user${if (selectedIds.size != 1) "s" else ""}?") },
            text = { Text("This will permanently delete the selected user(s) and all their associated data (devices, contacts, reports). This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = onBulkDelete, enabled = !isBulkDeleting) {
                    if (isBulkDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete", color = colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }, enabled = !isBulkDeleting) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                if (isSelectMode) {
                    Text(
                        if (selectedIds.isEmpty()) "Select users" else "${selectedIds.size} selected",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Users (${users.size}${if (hasMore && !isInSearchMode) "+" else ""})",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                }
            },
            navigationIcon = {
                if (isSelectMode) {
                    IconButton(onClick = { isSelectMode = false; selectedIds.clear() }) {
                        Icon(Icons.Default.Close, "Cancel selection", tint = colorScheme.onPrimary)
                    }
                } else {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                    }
                }
            },
            actions = {
                if (isSelectMode) {
                    // Select all / deselect all
                    IconButton(onClick = {
                        if (selectedIds.size == users.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(users.map { it.id })
                        }
                    }) {
                        Icon(
                            if (selectedIds.size == users.size) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            "Select all",
                            tint = colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = { if (selectedIds.isNotEmpty()) showBulkDeleteDialog = true },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete selected",
                            tint = if (selectedIds.isNotEmpty()) colorScheme.onPrimary else colorScheme.onPrimary.copy(alpha = 0.4f)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name or phone...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.primary) },
            singleLine = true,
            enabled = !isSelectMode,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant
            )
        )

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        loadError?.let {
            Text(
                it,
                color = colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = 4.dp)
            )
        }

        when {
            isLoading -> ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            users.isEmpty() -> EmptyStateView(
                title = "No Users Found",
                subtitle = if (searchQuery.isNotBlank()) "No users match \"$searchQuery\""
                else "No users have registered yet",
                icon = EmptyStateIcon.CONTACTS
            )
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)
                ) {
                    items(users, key = { it.id }) { user ->
                        TruCallerCard(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .combinedClickable(
                                    onLongClick = {
                                        if (!isSelectMode) {
                                            isSelectMode = true
                                            selectedIds.add(user.id)
                                        }
                                    },
                                    onClick = {
                                        if (isSelectMode) {
                                            if (user.id in selectedIds) selectedIds.remove(user.id)
                                            else selectedIds.add(user.id)
                                        } else {
                                            navController.navigate("admin_user_detail/${user.id}")
                                        }
                                    }
                                ),
                            elevation = 0.5.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectMode) {
                                    Checkbox(
                                        checked = user.id in selectedIds,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIds.add(user.id)
                                            else selectedIds.remove(user.id)
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                TruCallerAvatar(name = user.fullName, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.fullName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TruCallerBadge(
                                            text = if (user.isActive) "Active" else "Inactive",
                                            type = if (user.isActive) BadgeType.Success else BadgeType.Info
                                        )
                                    }
                                    Text(
                                        user.phoneNumber,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    user.lastLogin?.let {
                                        Text(
                                            "Last: ${formatRelativeTime(it)}",
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (isLoadingMore) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                            }
                        } else if (!hasMore && users.size > ADMIN_USERS_PAGE_SIZE) {
                            Text(
                                "All ${users.size} users loaded",
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
}
