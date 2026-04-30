package com.byron.trucaller.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsRule
import com.byron.trucaller.data.model.SmsRuleType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsRulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsRulesScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    smsRulesViewModel: SmsRulesViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val rules by smsRulesViewModel.rules.collectAsState()
    val isLoading by smsRulesViewModel.isLoading.collectAsState()
    val statusMessage by smsRulesViewModel.statusMessage.collectAsState()

    val colorScheme = MaterialTheme.colorScheme

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user.id) {
        smsRulesViewModel.loadRules(user.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SMS Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "${rules.size} rule${if (rules.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Rule", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status message
            if (statusMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    containerColor = colorScheme.surfaceVariant,
                    action = {
                        TextButton(onClick = { smsRulesViewModel.clearStatusMessage() }) {
                            Text("OK", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                ) { Text(statusMessage!!, color = colorScheme.onSurface) }
            }

            if (isLoading) {
                ShimmerLoadingList()
            } else if (rules.isEmpty()) {
                EmptyStateView(
                    title = "No SMS rules",
                    subtitle = "Create custom rules to automatically classify messages by sender, keyword, or regex pattern.",
                    icon = EmptyStateIcon.MESSAGES
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        SmsRuleItem(
                            rule = rule,
                            onToggle = { isActive ->
                                smsRulesViewModel.toggleRule(rule.id, isActive)
                            },
                            onDelete = {
                                smsRulesViewModel.deleteRule(rule.id)
                            }
                        )
                    }
                    // Bottom padding for FAB clearance
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Add rule dialog
    if (showAddDialog) {
        AddSmsRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { ruleType, pattern, category, priority ->
                smsRulesViewModel.addRule(user.id, ruleType, pattern, category, priority)
                showAddDialog = false
            }
        )
    }
}

// -- Rule Item with swipe-to-delete --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsRuleItem(
    rule: SmsRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    colorScheme.error else Color.Transparent,
                label = "swipeColor"
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.5f,
                label = "iconScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size((24 * scale).dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surface)
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rule type icon
            val typeColor = when (rule.ruleType) {
                SmsRuleType.SENDER_MATCH -> Color(0xFF4CAF50)
                SmsRuleType.KEYWORD_MATCH -> colorScheme.primary
                SmsRuleType.REGEX_MATCH -> BrandGold
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (rule.ruleType) {
                        SmsRuleType.SENDER_MATCH -> Icons.Default.FilterList
                        SmsRuleType.KEYWORD_MATCH -> Icons.Default.Rule
                        SmsRuleType.REGEX_MATCH -> Icons.Default.Rule
                    },
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Rule details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.pattern,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (rule.isActive) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typeLabel = when (rule.ruleType) {
                        SmsRuleType.SENDER_MATCH -> "Sender"
                        SmsRuleType.KEYWORD_MATCH -> "Keyword"
                        SmsRuleType.REGEX_MATCH -> "Regex"
                    }
                    Text(
                        text = typeLabel,
                        fontSize = 11.sp,
                        color = typeColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  ->  ",
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    val categoryColor = when (rule.targetCategory) {
                        SmsCategory.SPAM -> colorScheme.error
                        SmsCategory.PROMOTIONAL -> colorScheme.primary
                        SmsCategory.TRANSACTIONAL -> Color(0xFF4CAF50)
                        SmsCategory.PERSONAL -> Color(0xFFFFB300)
                    }
                    Text(
                        text = rule.targetCategory.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "P${rule.priority}",
                        fontSize = 10.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Toggle switch
            Switch(
                checked = rule.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.3f),
                    uncheckedTrackColor = colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = colorScheme.outline.copy(alpha = 0.15f)
    )
}

// -- Add Rule Dialog --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSmsRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (SmsRuleType, String, SmsCategory, Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    var selectedRuleType by remember { mutableStateOf(SmsRuleType.KEYWORD_MATCH) }
    var pattern by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SmsCategory.SPAM) }
    var priority by remember { mutableFloatStateOf(5f) }

    var ruleTypeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add SMS Rule", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Rule type dropdown
                Text("Rule type", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                ExposedDropdownMenuBox(
                    expanded = ruleTypeExpanded,
                    onExpandedChange = { ruleTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedRuleType) {
                            SmsRuleType.SENDER_MATCH -> "Sender Match"
                            SmsRuleType.KEYWORD_MATCH -> "Keyword Match"
                            SmsRuleType.REGEX_MATCH -> "Regex Match"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ruleTypeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = ruleTypeExpanded,
                        onDismissRequest = { ruleTypeExpanded = false }
                    ) {
                        SmsRuleType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (type) {
                                        SmsRuleType.SENDER_MATCH -> "Sender Match"
                                        SmsRuleType.KEYWORD_MATCH -> "Keyword Match"
                                        SmsRuleType.REGEX_MATCH -> "Regex Match"
                                    })
                                },
                                onClick = {
                                    selectedRuleType = type
                                    ruleTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Pattern input
                val patternHint = when (selectedRuleType) {
                    SmsRuleType.SENDER_MATCH -> "e.g. MTN, +256700..."
                    SmsRuleType.KEYWORD_MATCH -> "e.g. lottery, free data"
                    SmsRuleType.REGEX_MATCH -> "e.g. \\bwin\\b.*\\bfree\\b"
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    placeholder = { Text(patternHint, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline
                    )
                )

                // Target category dropdown
                Text("Classify as", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        SmsCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Text(cat.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Priority slider
                Text(
                    "Priority: ${priority.toInt()}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Slider(
                    value = priority,
                    onValueChange = { priority = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Low", fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("High", fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onConfirm(selectedRuleType, pattern, selectedCategory, priority.toInt())
                    }
                },
                enabled = pattern.isNotBlank()
            ) {
                Text("Add", color = if (pattern.isNotBlank()) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    )
}
