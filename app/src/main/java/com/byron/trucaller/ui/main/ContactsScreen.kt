package com.byron.trucaller.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.util.getInitials
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun ContactsScreen(authViewModel: AuthViewModel, contactsViewModel: ContactsViewModel) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    var searchQuery by remember { mutableStateOf("") }

    val autoBackup by contactsViewModel.autoBackup.collectAsState(initial = true)
    val syncMessage by contactsViewModel.syncMessage.collectAsState()
    val isSyncing by contactsViewModel.isSyncing.collectAsState()
    var isDriveConnected by remember { mutableStateOf(contactsViewModel.isDriveSignedIn()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dialog states
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showActionsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var isSelectedBlocked by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsViewModel.syncContacts(user.id)
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isDriveConnected = true
            contactsViewModel.syncToGoogleDrive()
        }
    }

    // Auto-sync contacts on first load
    LaunchedEffect(user.id) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            contactsViewModel.syncContacts(user.id)
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val userContacts by remember(user.id) {
        contactsViewModel.getContactsByUser(user.id)
    }.collectAsState(initial = emptyList())

    val filteredContacts = remember(searchQuery, userContacts) {
        if (searchQuery.isBlank()) userContacts
        else userContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery)
        }
    }

    val grouped = remember(filteredContacts) {
        filteredContacts.sortedBy { it.name }.groupBy { it.name.first().uppercaseChar() }
    }

    val backedUpCount = remember(userContacts) { userContacts.count { it.isBackedUp } }
    val lastSynced = remember(userContacts) { userContacts.maxByOrNull { it.syncedAt }?.syncedAt }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark)
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contacts", color = Brand, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Brand.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${userContacts.size}", color = Brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (lastSynced != null) {
                    Text(
                        "Last synced: ${formatRelativeTime(lastSynced)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Sync message snackbar
        if (syncMessage != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                action = {
                    TextButton(onClick = { contactsViewModel.clearSyncMessage() }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) {
                Text(syncMessage!!)
            }
        }

        // Search + Auto backup toggle
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Inactive) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedContainerColor = Color(0xFF252525),
                    unfocusedContainerColor = Color(0xFF252525)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Backup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("$backedUpCount/${userContacts.size} backed up", fontSize = 12.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = autoBackup,
                        onCheckedChange = { contactsViewModel.setAutoBackup(it, user.id) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Google Drive Sync Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = "Google Drive",
                        tint = if (isDriveConnected) Success else Inactive,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isDriveConnected) "Connected to Google Drive" else "Google Drive Backup",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            if (isDriveConnected) "Cloud sync enabled" else "Sign in to enable cloud backup",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Brand,
                            strokeWidth = 2.dp
                        )
                    } else if (isDriveConnected) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                                    == PackageManager.PERMISSION_GRANTED) {
                                    contactsViewModel.syncContacts(user.id)
                                } else {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                driveSignInLauncher.launch(contactsViewModel.getDriveSignInIntent())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Text("Connect", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Contact list
        if (grouped.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No contacts found", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                grouped.forEach { (letter, contacts) ->
                    item {
                        Text(
                            letter.toString(),
                            color = Brand,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                    items(contacts, key = { it.id }) { contact ->
                        val avatarColors = remember(contact.name) {
                            val bgColors = listOf(
                                Color(0xFF1A3A5C), Color(0xFF5C1A1A), Color(0xFF1A4D1A),
                                Color(0xFF5C3A00), Color(0xFF2E1A5C), Color(0xFF004D4D),
                                Color(0xFF5C4D00), Color(0xFF4D1A5C)
                            )
                            val fgColors = listOf(
                                Color(0xFF64B5F6), Color(0xFFEF5350), Color(0xFF66BB6A),
                                Color(0xFFFFB74D), Color(0xFFAB47BC), Color(0xFF4DD0E1),
                                Color(0xFFFFCD00), Color(0xFFCE93D8)
                            )
                            val idx = contact.name.hashCode().absoluteValue % bgColors.size
                            Pair(bgColors[idx], fgColors[idx])
                        }
                        val avatarColor = avatarColors.first
                        val textColor = avatarColors.second

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    selectedContact = contact
                                    scope.launch {
                                        isSelectedBlocked = contactsViewModel.isContactBlocked(user.id, contact.phoneNumber)
                                    }
                                    showActionsDialog = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(avatarColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        getInitials(contact.name),
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                    Text(formatPhoneNumber(contact.phoneNumber), fontSize = 12.sp, color = TextSecondary)
                                }
                                // Quick call button
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${contact.phoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Call, "Call", tint = Success, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Contact Actions Dialog
    if (showActionsDialog && selectedContact != null) {
        val contact = selectedContact!!
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = {
                Column {
                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(formatPhoneNumber(contact.phoneNumber), fontSize = 13.sp, color = TextSecondary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Call
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${contact.phoneNumber}")
                                }
                                context.startActivity(intent)
                                showActionsDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, "Call", tint = Success, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Call", fontSize = 16.sp, color = TextPrimary)
                    }

                    // WhatsApp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val phone = contact.phoneNumber.replace("+", "").replace(" ", "")
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://wa.me/$phone")
                                }
                                context.startActivity(intent)
                                showActionsDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("WhatsApp", fontSize = 16.sp, color = TextPrimary)
                    }

                    // Edit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editName = contact.name
                                editPhone = contact.phoneNumber
                                showActionsDialog = false
                                showEditDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, "Edit", tint = Brand, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Edit Contact", fontSize = 16.sp, color = TextPrimary)
                    }

                    // Block/Unblock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelectedBlocked) {
                                    contactsViewModel.unblockContact(contact.phoneNumber, user.id, contact.name)
                                } else {
                                    contactsViewModel.blockContact(contact, user.id)
                                }
                                showActionsDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSelectedBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                            if (isSelectedBlocked) "Unblock" else "Block",
                            tint = if (isSelectedBlocked) Success else Danger,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isSelectedBlocked) "Unblock Number" else "Block Number",
                            fontSize = 16.sp,
                            color = if (isSelectedBlocked) Success else Danger
                        )
                    }

                    // Delete
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showActionsDialog = false
                                showDeleteConfirm = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = Danger, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Delete Contact", fontSize = 16.sp, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionsDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    // Edit Contact Dialog
    if (showEditDialog && selectedContact != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank() && editPhone.isNotBlank()) {
                            val updated = selectedContact!!.copy(
                                name = editName.trim(),
                                phoneNumber = editPhone.trim()
                            )
                            contactsViewModel.updateContact(updated)
                            showEditDialog = false
                            selectedContact = null
                        }
                    }
                ) {
                    Text("Save", color = Brand, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && selectedContact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Contact", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete ${selectedContact!!.name}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactsViewModel.deleteContact(selectedContact!!)
                        showDeleteConfirm = false
                        selectedContact = null
                    }
                ) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
