package com.byron.trucaller.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import kotlinx.coroutines.launch

private val defaultSegments = listOf("Family", "Work", "Friends", "VIP")

@Composable
fun ContactsScreen(authViewModel: AuthViewModel, contactsViewModel: ContactsViewModel) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return

    val colorScheme = MaterialTheme.colorScheme

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=All, 1=Favourites
    var selectedSegment by remember { mutableStateOf<String?>(null) } // null=All favourites

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
    var showNoteDialog by remember { mutableStateOf(false) }
    var showFavouriteDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var isSelectedBlocked by remember { mutableStateOf(false) }
    var photoPickerContactId by remember { mutableStateOf<String?>(null) }

    // Animation state for staggered entry
    var hasAnimated by remember { mutableStateOf(false) }

    val contactPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && photoPickerContactId != null) {
            contactsViewModel.updateContactPhoto(photoPickerContactId!!, uri)
            photoPickerContactId = null
        }
    }

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

    val favouriteContacts by remember(user.id) {
        contactsViewModel.getFavouritesByUser(user.id)
    }.collectAsState(initial = emptyList())

    val segments by remember(user.id) {
        contactsViewModel.getSegmentsByUser(user.id)
    }.collectAsState(initial = emptyList())

    // Combined segment list: default + user-created
    val allSegments = remember(segments) {
        (defaultSegments + segments).distinct()
    }

    // Determine which contacts to show based on tab + segment + search
    val baseContacts = if (selectedTab == 0) userContacts else {
        if (selectedSegment != null) {
            favouriteContacts.filter { it.favouriteSegment == selectedSegment }
        } else {
            favouriteContacts
        }
    }

    // Improved search: normalize query, match across name, phone, email, note, segment
    val filteredContacts = remember(searchQuery, baseContacts) {
        if (searchQuery.isBlank()) baseContacts
        else {
            val query = searchQuery.trim().lowercase()
            val queryDigits = query.filter { it.isDigit() }
            baseContacts.filter { contact ->
                contact.name.lowercase().contains(query) ||
                        contact.phoneNumber.contains(query) ||
                        (queryDigits.isNotEmpty() && contact.phoneNumber.filter { it.isDigit() }.contains(queryDigits)) ||
                        (contact.email?.lowercase()?.contains(query) == true) ||
                        (contact.note?.lowercase()?.contains(query) == true) ||
                        (contact.favouriteSegment?.lowercase()?.contains(query) == true)
            }
        }
    }

    val grouped = remember(filteredContacts) {
        filteredContacts.sortedBy { it.name.lowercase() }.groupBy {
            val first = it.name.firstOrNull()
            if (first != null && first.isLetter()) first.uppercaseChar() else '#'
        }
    }

    val backedUpCount = remember(userContacts) { userContacts.count { it.isBackedUp } }
    val lastSynced = remember(userContacts) { userContacts.maxByOrNull { it.syncedAt }?.syncedAt }

    // Trigger staggered animation after first data load
    LaunchedEffect(filteredContacts) {
        if (filteredContacts.isNotEmpty() && !hasAnimated) {
            hasAnimated = true
        }
    }

    // Build flat list of section keys for fast-scroll sidebar
    val sectionKeys = remember(grouped) { grouped.keys.toList() }

    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        // Header using TruCallerHeader
        TruCallerHeader(
            title = "Contacts",
            subtitle = if (lastSynced != null) "Last synced: ${formatRelativeTime(lastSynced)}" else null,
            trailingContent = {
                Box(
                    modifier = Modifier
                        .background(colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${userContacts.size}",
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        )

        // Tab row: All | Favourites
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.primary,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; selectedSegment = null },
                text = { Text("All", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                selectedContentColor = colorScheme.primary,
                unselectedContentColor = colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Favourites", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                    }
                },
                selectedContentColor = colorScheme.primary,
                unselectedContentColor = colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Segment chips (visible only on Favourites tab)
        if (selectedTab == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSegment == null,
                    onClick = { selectedSegment = null },
                    label = { Text("All", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary
                    )
                )
                allSegments.forEach { segment ->
                    FilterChip(
                        selected = selectedSegment == segment,
                        onClick = { selectedSegment = if (selectedSegment == segment) null else segment },
                        label = { Text(segment, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary,
                            selectedLabelColor = colorScheme.onPrimary
                        )
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
                        Text("OK", color = colorScheme.inversePrimary)
                    }
                }
            ) {
                Text(syncMessage!!)
            }
        }

        // Search + Auto backup toggle
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TruCallerTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search name, phone, email, notes...",
                isSearch = true,
                showClearButton = true,
                onClear = { searchQuery = "" },
                contentDesc = "Search contacts"
            )

            Spacer(modifier = Modifier.height(8.dp))

            TruCallerCard(
                elevation = 0.5.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto Backup",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "$backedUpCount/${userContacts.size} backed up",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = autoBackup,
                        onCheckedChange = { contactsViewModel.setAutoBackup(it, user.id) },
                        colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Google Drive Sync Card with enhanced visual hierarchy
            TruCallerCard(
                elevation = 1.dp,
                gradientColors = if (isDriveConnected) listOf(
                    colorScheme.primary.copy(alpha = 0.08f),
                    colorScheme.surfaceVariant
                ) else null,
                gradientHeaderContent = if (isDriveConnected) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Cloud sync enabled",
                                fontSize = 11.sp,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = "Google Drive",
                        tint = if (isDriveConnected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isDriveConnected) "Connected to Google Drive" else "Google Drive Backup",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            if (isDriveConnected) "$backedUpCount contacts backed up" else "Sign in to enable cloud backup",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colorScheme.primary,
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary)
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text("Connect", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Contact list
        if (grouped.isEmpty()) {
            EmptyStateView(
                title = if (selectedTab == 1 && searchQuery.isBlank()) "No favourites yet"
                       else if (searchQuery.isNotBlank()) "No results found"
                       else "No contacts found",
                subtitle = if (selectedTab == 1 && searchQuery.isBlank()) "Star contacts to add them here."
                          else if (searchQuery.isNotBlank()) "Try a different search term."
                          else "Sync your contacts to get started.",
                icon = if (searchQuery.isNotBlank()) EmptyStateIcon.SEARCH else EmptyStateIcon.CONTACTS
            )
        } else {
            // Box overlay for fast-scroll sidebar
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 32.dp) // extra end padding for sidebar
                ) {
                    var globalIndex = 0
                    grouped.forEach { (letter, contacts) ->
                        stickyHeader(key = "header_$letter") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colorScheme.background)
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    letter.toString(),
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        items(contacts, key = { it.id }) { contact ->
                            val itemIndex = globalIndex++
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = if (!hasAnimated) 0 else (itemIndex * 30).coerceAtMost(600)
                                    )
                                ) + slideInVertically(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = if (!hasAnimated) 0 else (itemIndex * 30).coerceAtMost(600)
                                    ),
                                    initialOffsetY = { it / 4 }
                                )
                            ) {
                                ContactCard(
                                    contact = contact,
                                    onCardClick = {
                                        selectedContact = contact
                                        scope.launch {
                                            isSelectedBlocked = contactsViewModel.isContactBlocked(user.id, contact.phoneNumber)
                                        }
                                        showActionsDialog = true
                                    },
                                    onStarClick = {
                                        if (contact.isFavourite) {
                                            contactsViewModel.removeFromFavourites(contact)
                                        } else {
                                            selectedContact = contact
                                            showFavouriteDialog = true
                                        }
                                    },
                                    onCallClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${contact.phoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Fast-scroll alphabet sidebar
                if (sectionKeys.isNotEmpty()) {
                    var sidebarHeightPx by remember { mutableStateOf(0f) }

                    // Build index mapping: for each section key, compute its item index in the LazyColumn
                    val sectionItemIndices = remember(grouped) {
                        val indices = mutableMapOf<Char, Int>()
                        var itemIdx = 0
                        grouped.forEach { (letter, contacts) ->
                            indices[letter] = itemIdx
                            itemIdx += 1 + contacts.size // 1 for sticky header + contacts
                        }
                        indices
                    }

                    val alphabet = ('A'..'Z').toList() + listOf('#')

                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .width(20.dp)
                            .onGloballyPositioned { coordinates ->
                                sidebarHeightPx = coordinates.size.height.toFloat()
                            }
                            .pointerInput(sectionKeys, sectionItemIndices) {
                                detectTapGestures { offset ->
                                    val letterIndex = ((offset.y / sidebarHeightPx) * alphabet.size)
                                        .toInt()
                                        .coerceIn(0, alphabet.lastIndex)
                                    val targetLetter = alphabet[letterIndex]
                                    val itemIndex = sectionItemIndices[targetLetter]
                                    if (itemIndex != null) {
                                        scope.launch {
                                            listState.scrollToItem(itemIndex)
                                        }
                                    }
                                }
                            }
                            .pointerInput(sectionKeys, sectionItemIndices) {
                                detectVerticalDragGestures { change, _ ->
                                    change.consume()
                                    val letterIndex = ((change.position.y / sidebarHeightPx) * alphabet.size)
                                        .toInt()
                                        .coerceIn(0, alphabet.lastIndex)
                                    val targetLetter = alphabet[letterIndex]
                                    val itemIndex = sectionItemIndices[targetLetter]
                                    if (itemIndex != null) {
                                        scope.launch {
                                            listState.scrollToItem(itemIndex)
                                        }
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabet.forEach { letter ->
                            val isActive = letter in sectionKeys
                            Text(
                                text = letter.toString(),
                                fontSize = 9.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (contact.isFavourite) {
                            Icon(Icons.Default.Star, "Favourite", tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                            if (contact.favouriteSegment != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(contact.favouriteSegment, fontSize = 11.sp, color = colorScheme.primary)
                            }
                        }
                    }
                    Text(formatPhoneNumber(contact.phoneNumber), fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                    if (!contact.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            contact.note,
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Call
                    ActionRow(
                        icon = Icons.Default.Call,
                        label = "Call",
                        tint = colorScheme.tertiary,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${contact.phoneNumber}")
                            }
                            context.startActivity(intent)
                            showActionsDialog = false
                        }
                    )

                    // WhatsApp
                    ActionRow(
                        icon = Icons.Default.Call,
                        label = "WhatsApp",
                        tint = Color(0xFF25D366),
                        onClick = {
                            val phone = contact.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/$phone")
                            }
                            context.startActivity(intent)
                            showActionsDialog = false
                        }
                    )

                    // Favourite / Unfavourite
                    ActionRow(
                        icon = if (contact.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                        label = if (contact.isFavourite) "Remove from Favourites" else "Add to Favourites",
                        tint = colorScheme.primary,
                        onClick = {
                            showActionsDialog = false
                            if (contact.isFavourite) {
                                contactsViewModel.removeFromFavourites(contact)
                            } else {
                                showFavouriteDialog = true
                            }
                        }
                    )

                    // Add/Edit Note
                    ActionRow(
                        icon = Icons.Default.NoteAlt,
                        label = if (contact.note.isNullOrBlank()) "Add Note" else "Edit Note",
                        tint = Color(0xFF42A5F5),
                        onClick = {
                            editNote = contact.note ?: ""
                            showActionsDialog = false
                            showNoteDialog = true
                        }
                    )

                    // Set/Change Photo
                    ActionRow(
                        icon = Icons.Default.AddAPhoto,
                        label = if (contact.photoUri != null) "Change Photo" else "Set Photo",
                        tint = colorScheme.primary,
                        onClick = {
                            photoPickerContactId = contact.id
                            showActionsDialog = false
                            contactPhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // Remove Photo (only if photo exists)
                    if (contact.photoUri != null) {
                        ActionRow(
                            icon = Icons.Default.Delete,
                            label = "Remove Photo",
                            tint = colorScheme.error,
                            labelColor = colorScheme.error,
                            onClick = {
                                contactsViewModel.removeContactPhoto(contact.id)
                                showActionsDialog = false
                            }
                        )
                    }

                    // Edit
                    ActionRow(
                        icon = Icons.Default.Edit,
                        label = "Edit Contact",
                        tint = colorScheme.primary,
                        onClick = {
                            editName = contact.name
                            editPhone = contact.phoneNumber
                            showActionsDialog = false
                            showEditDialog = true
                        }
                    )

                    // Block/Unblock
                    ActionRow(
                        icon = if (isSelectedBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                        label = if (isSelectedBlocked) "Unblock Number" else "Block Number",
                        tint = if (isSelectedBlocked) colorScheme.tertiary else colorScheme.error,
                        labelColor = if (isSelectedBlocked) colorScheme.tertiary else colorScheme.error,
                        onClick = {
                            if (isSelectedBlocked) {
                                contactsViewModel.unblockContact(contact.phoneNumber, user.id, contact.name)
                            } else {
                                contactsViewModel.blockContact(contact, user.id)
                            }
                            showActionsDialog = false
                        }
                    )

                    // Delete
                    ActionRow(
                        icon = Icons.Default.Delete,
                        label = "Delete Contact",
                        tint = colorScheme.error,
                        labelColor = colorScheme.error,
                        onClick = {
                            showActionsDialog = false
                            showDeleteConfirm = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionsDialog = false }) {
                    Text("Close", color = colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Favourite Segment Picker Dialog
    if (showFavouriteDialog && selectedContact != null) {
        var chosenSegment by remember { mutableStateOf(selectedContact!!.favouriteSegment) }
        var customSegment by remember { mutableStateOf("") }
        var useCustom by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showFavouriteDialog = false },
            title = { Text("Add to Favourites", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose a segment:", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    allSegments.forEach { segment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosenSegment = segment; useCustom = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (chosenSegment == segment && !useCustom) colorScheme.primary else colorScheme.outline,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(segment, fontSize = 15.sp, color = colorScheme.onSurface)
                        }
                    }
                    // Custom segment option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useCustom = true; chosenSegment = null }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (useCustom) colorScheme.primary else colorScheme.outline,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Custom...", fontSize = 15.sp, color = colorScheme.onSurface)
                    }
                    if (useCustom) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TruCallerTextField(
                            value = customSegment,
                            onValueChange = { customSegment = it },
                            placeholder = "Segment name",
                            singleLine = true,
                            contentDesc = "Custom segment name"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val segment = if (useCustom) customSegment.trim().ifEmpty { null } else chosenSegment
                        contactsViewModel.toggleFavourite(selectedContact!!, segment)
                        showFavouriteDialog = false
                        selectedContact = null
                    }
                ) {
                    Text("Add", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavouriteDialog = false }) {
                    Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Note Edit Dialog
    if (showNoteDialog && selectedContact != null) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = {
                Text(
                    if (selectedContact!!.note.isNullOrBlank()) "Add Note" else "Edit Note",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Note for ${selectedContact!!.name}", fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    TruCallerTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        placeholder = "Write a note...",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        contentDesc = "Contact note"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactsViewModel.updateNote(selectedContact!!.id, editNote)
                        showNoteDialog = false
                        selectedContact = null
                    }
                ) {
                    Text("Save", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (!selectedContact!!.note.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                contactsViewModel.updateNote(selectedContact!!.id, null)
                                showNoteDialog = false
                                selectedContact = null
                            }
                        ) {
                            Text("Remove", color = colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showNoteDialog = false }) {
                        Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.6f))
                    }
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
                    TruCallerTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = "Name",
                        singleLine = true,
                        contentDesc = "Contact name"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TruCallerTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = "Phone Number",
                        singleLine = true,
                        keyboardType = KeyboardType.Phone,
                        contentDesc = "Phone number"
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
                    Text("Save", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.6f))
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
                    Text("Delete", color = colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onCardClick: () -> Unit,
    onStarClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    TruCallerCard(
        modifier = Modifier.padding(vertical = 2.dp),
        elevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar using TruCallerAvatar
            TruCallerAvatar(
                name = contact.name,
                imageUri = contact.photoUri,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            // Name, phone, note
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colorScheme.onSurface
                    )
                    if (contact.isFavourite && contact.favouriteSegment != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(contact.favouriteSegment, fontSize = 10.sp, color = colorScheme.primary)
                        }
                    }
                }
                Text(
                    formatPhoneNumber(contact.phoneNumber),
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!contact.note.isNullOrBlank()) {
                    Text(
                        contact.note,
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Star toggle
            IconButton(
                onClick = { onStarClick() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (contact.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (contact.isFavourite) "Remove favourite" else "Add favourite",
                    tint = if (contact.isFavourite) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            // Note indicator
            if (!contact.note.isNullOrBlank()) {
                Icon(
                    Icons.Default.NoteAlt,
                    contentDescription = "Has note",
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            // Quick call button
            IconButton(
                onClick = { onCallClick() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Call,
                    "Call",
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = labelColor)
    }
}
