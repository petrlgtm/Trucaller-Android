package com.byron.trucaller.ui.admin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    val filtered by remember(searchQuery, allEntries) {
        derivedStateOf {
            if (searchQuery.isBlank()) allEntries
            else {
                val q = searchQuery.lowercase()
                allEntries.filter { it.phoneNumber.contains(q) || it.name.lowercase().contains(q) }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

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

    // Delete Confirmation Dialog
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

    Scaffold(
        topBar = {
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Add entry")
            }
        },
        containerColor = colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
                        items(filtered, key = { it.id }) { entry ->
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
                                            selectedEntry = entry
                                            showEditDialog = true
                                        },
                                        onLongClick = {
                                            selectedEntry = entry
                                            showDeleteDialog = true
                                        }
                                    ),
                                elevation = 0.5.dp
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
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
