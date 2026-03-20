package com.byron.trucaller.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.data.model.FamilyGroupMember
import com.byron.trucaller.data.model.FamilyGroupRole
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.theme.CardGradientEnd
import com.byron.trucaller.ui.theme.CardGradientStart
import com.byron.trucaller.ui.theme.Spacing
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
    val group = uiState.selectedGroup
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRemoveMemberDialog by remember { mutableStateOf<FamilyGroupMember?>(null) }
    var showPromoteMemberDialog by remember { mutableStateOf<FamilyGroupMember?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

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

    val isAdmin = group?.members?.any { it.userId == user.id && it.role == FamilyGroupRole.ADMIN } == true
    val isCreator = group?.ownerId == user.id

    // Delete group dialog
    if (showDeleteDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Group", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete \"${group.name}\"? All members will be removed. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        familyGroupViewModel.deleteGroup(groupId) {
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text("Delete", color = colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Leave group dialog
    if (showLeaveDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to leave \"${group.name}\"? You will no longer receive alerts from this group.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        familyGroupViewModel.leaveGroup(groupId, user.id) {
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text("Leave", color = colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog && group != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameText = ""
            },
            title = { Text("Rename Group", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") },
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
                        val trimmed = renameText.trim()
                        if (trimmed.length >= 2) {
                            familyGroupViewModel.renameGroup(groupId, trimmed, user.id)
                            showRenameDialog = false
                            renameText = ""
                        }
                    },
                    enabled = renameText.trim().length >= 2
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    renameText = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Remove member dialog
    showRemoveMemberDialog?.let { member ->
        AlertDialog(
            onDismissRequest = { showRemoveMemberDialog = null },
            title = { Text("Remove Member", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove ${member.fullName} from this group?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        familyGroupViewModel.removeMember(groupId, member.userId, user.id)
                        showRemoveMemberDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) { Text("Remove", color = colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveMemberDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Promote member dialog
    showPromoteMemberDialog?.let { member ->
        AlertDialog(
            onDismissRequest = { showPromoteMemberDialog = null },
            title = { Text("Promote to Admin", fontWeight = FontWeight.Bold) },
            text = {
                Text("Promote ${member.fullName} to admin? They will be able to manage members and group settings.")
            },
            confirmButton = {
                Button(onClick = {
                    familyGroupViewModel.promoteMember(groupId, member.userId)
                    showPromoteMemberDialog = null
                }) { Text("Promote") }
            },
            dismissButton = {
                TextButton(onClick = { showPromoteMemberDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isAdmin) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename Group") },
                                    onClick = {
                                        showOverflowMenu = false
                                        renameText = group?.name ?: ""
                                        showRenameDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Regenerate Invite Code") },
                                    onClick = {
                                        showOverflowMenu = false
                                        familyGroupViewModel.regenerateInviteCode(groupId)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                if (isCreator) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Group", color = colorScheme.error) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(20.dp)) }
                                    )
                                }
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
        when {
            uiState.isLoading && group == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }
            group == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Group not found", color = colorScheme.onSurfaceVariant)
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
                    // Group header card
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TruCallerCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            gradientColors = listOf(CardGradientStart, CardGradientEnd),
                            gradientHeaderContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            group.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            "${group.members.size}/${group.maxMembers} members",
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        ) {
                            // Invite code section
                            Text(
                                "Invite Code",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    group.inviteCode,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    letterSpacing = 3.sp,
                                    color = colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", group.inviteCode))
                                    },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                                        .testTag("copy_invite_code")
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy invite code",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val shareText = "Join my family group \"${group.name}\" on TruCaller! Use invite code: ${group.inviteCode}"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share invite code"))
                                    },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                                        .testTag("share_invite_code")
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share invite code",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Members header
                    item {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            "Members (${group.members.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Member list
                    items(group.members, key = { it.userId }) { member ->
                        MemberRow(
                            member = member,
                            isCurrentUser = member.userId == user.id,
                            isViewerAdmin = isAdmin,
                            onRemove = { showRemoveMemberDialog = member },
                            onPromote = { showPromoteMemberDialog = member }
                        )
                    }

                    // Leave / Delete section
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        if (!isCreator) {
                            Button(
                                onClick = { showLeaveDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("leave_group_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Leave Group", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("delete_group_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Group", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: FamilyGroupMember,
    isCurrentUser: Boolean,
    isViewerAdmin: Boolean,
    onRemove: () -> Unit,
    onPromote: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    TruCallerCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("member_row_${member.userId}"),
        elevation = 1.dp,
        containerColor = colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TruCallerAvatar(
                name = member.fullName,
                size = 42.dp,
                imageUri = member.avatarUrl,
                contentDesc = "${member.fullName} avatar"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCurrentUser) "${member.fullName} (You)" else member.fullName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (member.role == FamilyGroupRole.ADMIN) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Admin",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    member.phoneNumber,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
            // Admin actions on non-self, non-admin members
            if (isViewerAdmin && !isCurrentUser && member.role != FamilyGroupRole.ADMIN) {
                IconButton(
                    onClick = onPromote,
                    modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Promote ${member.fullName}",
                        tint = colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                ) {
                    Icon(
                        Icons.Default.PersonRemove,
                        contentDescription = "Remove ${member.fullName}",
                        tint = colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
