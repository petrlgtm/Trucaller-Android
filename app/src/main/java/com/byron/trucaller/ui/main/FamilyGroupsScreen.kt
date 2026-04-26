package com.byron.trucaller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.FamilyGroup
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.FamilyGroupViewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    familyGroupViewModel: FamilyGroupViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return
    val uiState by familyGroupViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var createNameError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user.id) {
        familyGroupViewModel.loadGroups(user.id)
    }

    // Show success / error messages as snackbar
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            familyGroupViewModel.clearSuccessMessage()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            familyGroupViewModel.clearError()
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newGroupName = ""
                createNameError = null
            },
            title = { Text("Create Family Group", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Create a group to share device locations and stolen-device alerts with family members.",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it; createNameError = null },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        ),
                        isError = createNameError != null
                    )
                    if (createNameError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(createNameError!!, color = colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newGroupName.trim()
                        if (trimmed.isBlank()) {
                            createNameError = "Group name is required"
                        } else if (trimmed.length < 2) {
                            createNameError = "Name must be at least 2 characters"
                        } else {
                            familyGroupViewModel.createGroup(trimmed, user.id)
                            showCreateDialog = false
                            newGroupName = ""
                            createNameError = null
                        }
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newGroupName = ""
                    createNameError = null
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family & Groups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Join group button
                    IconButton(
                        onClick = { navController.navigate("join_family_group") },
                        modifier = Modifier.testTag("join_group_button")
                    ) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = "Join a group",
                            tint = colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier.testTag("create_group_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.groups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }
            uiState.groups.isEmpty() -> {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1511895426328-dc8714191011?auto=format&fit=crop&w=800&h=400&q=80",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            colorScheme.background.copy(alpha = 0f),
                                            colorScheme.background.copy(alpha = 0.6f),
                                            colorScheme.background
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(Spacing.xl)
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            "No Family Groups",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            "Create a group or join one with an invite code to share device alerts with your family.",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.lg))
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Group")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    items(uiState.groups, key = { it.id }) { group ->
                        FamilyGroupCard(
                            group = group,
                            currentUserId = user.id,
                            onClick = { navController.navigate("family_group_detail/${group.id}") }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyGroupCard(
    group: FamilyGroup,
    currentUserId: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isAdmin = group.members.any { it.userId == currentUserId && it.role == com.byron.trucaller.data.model.FamilyGroupRole.ADMIN }

    TruCallerCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ${group.name}") { onClick() }
            .testTag("family_group_card_${group.id}"),
        elevation = 1.dp,
        containerColor = colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Admin",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    colorScheme.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${group.members.size} member${if (group.members.size != 1) "s" else ""}",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open group",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
