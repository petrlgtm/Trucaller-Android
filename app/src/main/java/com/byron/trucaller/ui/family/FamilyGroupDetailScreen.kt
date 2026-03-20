package com.byron.trucaller.ui.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.FamilyGroupMember
import com.byron.trucaller.data.model.FamilyGroupRole
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.FamilyGroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupDetailScreen(
    navController: NavController,
    groupId: String,
    authViewModel: AuthViewModel,
    familyGroupViewModel: FamilyGroupViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return
    val uiState by familyGroupViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showMenuExpanded by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<FamilyGroupMember?>(null) }

    LaunchedEffect(groupId) {
        familyGroupViewModel.loadGroupDetail(groupId)
    }

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

    val group = uiState.selectedGroup
    val isOwner = group?.ownerId == user.id
    val isAdmin = isOwner || group?.members?.any {
        it.userId == user.id && it.role == FamilyGroupRole.ADMIN
    } == true

    // Rename dialog
    if (showRenameDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Group", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameValue.trim()
                        if (trimmed.isNotBlank() && trimmed.length >= 2) {
                            familyGroupViewModel.renameGroup(groupId, trimmed, user.id)
                            showRenameDialog = false
                        }
                    },
                    enabled = !uiState.isLoading
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirm dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently remove the group and all member associations. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        familyGroupViewModel.deleteGroup(groupId) {
                            navController.popBackStack()
                        }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Leave confirm dialog
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Group?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You will no longer receive device alerts from this group.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        familyGroupViewModel.leaveGroup(groupId, user.id) {
                            navController.popBackStack()
                        }
                        showLeaveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Warning)
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Remove member dialog
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove ${member.fullName}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${member.fullName} will be removed from this group and will no longer receive alerts.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        familyGroupViewModel.removeMember(groupId, member.userId, user.id)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        group?.name ?: "Group Detail",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Overflow menu
                    Box {
                        IconButton(onClick = { showMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenuExpanded,
                            onDismissRequest = { showMenuExpanded = false }
                        ) {
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        renameValue = group?.name ?: ""
                                        showRenameDialog = true
                                        showMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Regenerate Invite Code") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = {
                                        familyGroupViewModel.regenerateInviteCode(groupId)
                                        showMenuExpanded = false
                                    }
                                )
                            }
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Delete Group", color = Danger) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Danger) },
                                    onClick = {
                                        showDeleteConfirm = true
                                        showMenuExpanded = false
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Leave Group", color = Warning) },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = Warning) },
                                    onClick = {
                                        showLeaveConfirm = true
                                        showMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading && group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else if (group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Group not found", color = colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item { Spacer(modifier = Modifier.height(Spacing.sm)) }

                // View Device Map button
                item {
                    Button(
                        onClick = {
                            navController.navigate("family_device_map/$groupId")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("view_device_map_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "View Device Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Invite code card
                item {
                    TruCallerCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 1.dp,
                        containerColor = colorScheme.surfaceVariant
                    ) {
                        Text(
                            "Invite Code",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                group.inviteCode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                letterSpacing = 2.sp
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Invite Code", group.inviteCode)
                                    )
                                },
                                modifier = Modifier.testTag("copy_invite_code")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy invite code",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "Share this code for others to join the group",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Members header
                item {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "Members (${group.members.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colorScheme.onBackground
                    )
                }

                // Member list
                items(group.members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        isOwner = member.userId == group.ownerId,
                        canManage = isAdmin && member.userId != user.id,
                        onPromote = { familyGroupViewModel.promoteMember(groupId, member.userId) },
                        onRemove = { memberToRemove = member }
                    )
                }

                item { Spacer(modifier = Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: FamilyGroupMember,
    isOwner: Boolean,
    canManage: Boolean,
    onPromote: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            val initials = member.fullName.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("")
                .ifBlank { "?" }
            Text(
                initials,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.fullName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isOwner) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Owner",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (member.role == FamilyGroupRole.ADMIN) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Admin",
                        tint = Success,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                member.phoneNumber,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        // Admin actions
        if (canManage) {
            if (member.role != FamilyGroupRole.ADMIN) {
                IconButton(
                    onClick = onPromote,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Promote to admin",
                        tint = Success,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Remove member",
                    tint = Danger,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.2f))
}
