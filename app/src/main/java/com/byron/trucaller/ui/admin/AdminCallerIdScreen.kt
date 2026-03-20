package com.byron.trucaller.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.getSpamScoreColor
import com.byron.trucaller.viewmodel.CallerIdViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdminCallerIdScreen(navController: NavController, callerIdViewModel: CallerIdViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val allEntries by callerIdViewModel.allEntries.collectAsState(initial = emptyList())
    var isInitialLoad by remember { mutableStateOf(true) }

    if (allEntries.isNotEmpty() && isInitialLoad) {
        isInitialLoad = false
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CallerIdEntry?>(null) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }

    val isBulkOperationInProgress by callerIdViewModel.isBulkOperationInProgress.collectAsState()

    // Exit multi-select when no items remain selected
    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            val updated = selectedIds - id
            if (updated.isEmpty()) {
                isMultiSelectMode = false
            }
            updated
        } else {
            selectedIds + id
        }
    }

    val filtered by remember(searchQuery, allEntries) {
        derivedStateOf {
            if (searchQuery.isBlank()) allEntries
            else {
                val q = searchQuery.lowercase()
                allEntries.filter { it.phoneNumber.contains(q) || it.name.lowercase().contains(q) }
            }
        }
    }

    // Clean up selectedIds when entries change (e.g., after bulk delete)
    LaunchedEffect(allEntries) {
        if (isMultiSelectMode) {
            val validIds = allEntries.map { it.id }.toSet()
            val cleaned = selectedIds.intersect(validIds)
            selectedIds = cleaned
            if (cleaned.isEmpty()) {
                isMultiSelectMode = false
            }
        }
    }

    // Pagination state
    var currentPage by remember { mutableIntStateOf(1) }
    val lazyListState = rememberLazyListState()

    // Reset page when filter changes
    LaunchedEffect(searchQuery) {
        currentPage = 1
    }

    val paginatedItems by remember(filtered, currentPage) {
        derivedStateOf { filtered.take(PAGE_SIZE * currentPage) }
    }
    val hasMoreItems by remember(paginatedItems, filtered) {
        derivedStateOf { paginatedItems.size < filtered.size }
    }

    // Detect scroll near bottom to load next page
    LaunchedEffect(lazyListState, hasMoreItems) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to totalItems
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3 && hasMoreItems) {
                currentPage++
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    // Snackbar for sync feedback
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actionMessage by callerIdViewModel.actionMessage.collectAsState()

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                callerIdViewModel.clearActionMessage()
            }
        }
    }

    // Add Entry Dialog
    if (showAddDialog) {
        EntryDialog(
            title = "Add Caller ID Entry",
            entry = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                callerIdViewModel.addEntry(entry)
                showAddDialog = false
            }
        )
    }

    // Edit Entry Dialog
    if (showEditDialog && selectedEntry != null) {
        EntryDialog(
            title = "Edit Caller ID Entry",
            entry = selectedEntry,
            onDismiss = { showEditDialog = false; selectedEntry = null },
            onConfirm = { entry ->
                callerIdViewModel.updateEntry(entry)
                showEditDialog = false
                selectedEntry = null
            }
        )
    }

    // Single Delete Confirmation Dialog
    if (showDeleteDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedEntry = null },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete ${selectedEntry!!.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    callerIdViewModel.deleteEntry(selectedEntry!!)
                    showDeleteDialog = false
                    selectedEntry = null
                }) { Text("Delete", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedEntry = null }) { Text("Cancel") }
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} Entries") },
            text = {
                Text("Are you sure you want to delete ${selectedIds.size} selected entries? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                        callerIdViewModel.bulkDeleteEntries(selectedIds) {
                            exitMultiSelect()
                        }
                    },
                    enabled = !isBulkOperationInProgress
                ) { Text("Delete All", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Category Change Dialog
    if (showBulkCategoryDialog) {
        BulkCategoryDialog(
            selectedCount = selectedIds.size,
            onDismiss = { showBulkCategoryDialog = false },
            onConfirm = { category ->
                showBulkCategoryDialog = false
                callerIdViewModel.bulkUpdateCategory(selectedIds, category) {
                    exitMultiSelect()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                // Multi-select top bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.onPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${selectedIds.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Selected",
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitMultiSelect() }) {
                            Icon(Icons.Default.Close, "Exit selection", tint = colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        val allFilteredIds = filtered.map { it.id }.toSet()
                        val allSelected = allFilteredIds.isNotEmpty() && allFilteredIds.all { it in selectedIds }

                        TextButton(onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                allFilteredIds
                            }
                            if (selectedIds.isEmpty()) {
                                isMultiSelectMode = false
                            }
                        }) {
                            Text(
                                if (allSelected) "Deselect All" else "Select All",
                                color = colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = {
                        Text(
                            "Caller ID (${filtered.size})",
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
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "Add entry")
                }
            }
        },
        bottomBar = {
            // Bulk action bar
            AnimatedVisibility(
                visible = isMultiSelectMode && selectedIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomAppBar(
                    containerColor = colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBulkOperationInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Processing...",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        } else {
                            Button(
                                onClick = { showBulkDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.error,
                                    contentColor = colorScheme.onError
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete (${selectedIds.size})")
                            }
                            Button(
                                onClick = { showBulkCategoryDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Category (${selectedIds.size})")
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Progress indicator during bulk operations
            if (isBulkOperationInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.primary
                )
            }

            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search by phone or name...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedContainerColor = colorScheme.surfaceVariant,
                    unfocusedContainerColor = colorScheme.surfaceVariant
                )
            )

            when {
                isInitialLoad && allEntries.isEmpty() -> {
                    ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
                }
                filtered.isEmpty() -> {
                    EmptyStateView(
                        title = "No Caller ID Entries",
                        subtitle = if (searchQuery.isNotBlank()) "No entries match \"$searchQuery\""
                        else "Add entries to the caller ID database",
                        icon = EmptyStateIcon.CALLS,
                        actionLabel = "Add Entry",
                        onAction = { showAddDialog = true }
                    )
                }
                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)
                    ) {
                        items(paginatedItems, key = { it.id }) { entry ->
                            val isSelected = entry.id in selectedIds
                            val catBadgeType = when (entry.category) {
                                SpamCategory.SAFE -> BadgeType.Success
                                SpamCategory.SUSPECTED_SPAM -> BadgeType.Warning
                                SpamCategory.SPAM -> BadgeType.Spam
                                SpamCategory.FRAUD -> BadgeType.Spam
                            }
                            val catLabel = when (entry.category) {
                                SpamCategory.SAFE -> "Safe"
                                SpamCategory.SUSPECTED_SPAM -> "Suspected"
                                SpamCategory.SPAM -> "Spam"
                                SpamCategory.FRAUD -> "Fraud"
                            }

                            TruCallerCard(
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                toggleSelection(entry.id)
                                            } else {
                                                selectedEntry = entry
                                                showEditDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            if (!isMultiSelectMode) {
                                                isMultiSelectMode = true
                                                selectedIds = setOf(entry.id)
                                            } else {
                                                toggleSelection(entry.id)
                                            }
                                        }
                                    ),
                                elevation = if (isSelected) 2.dp else 0.5.dp
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Selection checkbox in multi-select mode
                                    if (isMultiSelectMode) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle
                                            else Icons.Outlined.Circle,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            tint = if (isSelected) colorScheme.primary
                                            else colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            entry.phoneNumber,
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    TruCallerBadge(
                                        text = catLabel,
                                        type = catBadgeType
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LinearProgressIndicator(
                                        progress = { entry.spamScore / 100f },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = getSpamScoreColor(entry.spamScore),
                                        trackColor = colorScheme.outlineVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "${entry.spamScore}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = getSpamScoreColor(entry.spamScore)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "${entry.reportCount} reports",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        // Pagination footer
                        item {
                            if (hasMoreItems) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.primary
                                    )
                                }
                            } else if (paginatedItems.size > PAGE_SIZE) {
                                Text(
                                    "All items loaded",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkCategoryDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (SpamCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(SpamCategory.SAFE) }

    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Category") },
        text = {
            Column {
                Text(
                    "Update category for $selectedCount selected entries:",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SpamCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategory = cat; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedCategory) }) {
                Text("Apply", color = colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDialog(
    title: String,
    entry: CallerIdEntry?,
    onDismiss: () -> Unit,
    onConfirm: (CallerIdEntry) -> Unit
) {
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var phoneNumber by remember { mutableStateOf(entry?.phoneNumber ?: "") }
    var spamScore by remember { mutableStateOf(entry?.spamScore?.toString() ?: "0") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(entry?.category ?: SpamCategory.SAFE) }

    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber, onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = spamScore, onValueChange = { spamScore = it },
                    label = { Text("Spam Score (0-100)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SpamCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategory = cat; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val score = spamScore.toIntOrNull()?.coerceIn(0, 100) ?: 0
                val newEntry = entry?.copy(
                    name = name,
                    phoneNumber = phoneNumber,
                    spamScore = score,
                    category = selectedCategory,
                    lastUpdated = now
                ) ?: CallerIdEntry(
                    id = "cid-${System.currentTimeMillis()}",
                    phoneNumber = phoneNumber,
                    name = name,
                    spamScore = score,
                    reportCount = 0,
                    category = selectedCategory,
                    lastUpdated = now
                )
                onConfirm(newEntry)
            }) { Text("Save", color = colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
