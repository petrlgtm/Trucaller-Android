package com.byron.trucaller.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.R
import com.byron.trucaller.data.model.Contact
import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.byron.trucaller.viewmodel.DialPadViewModel
import com.byron.trucaller.util.PhoneUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPadScreen(
    rootNavController: NavController,
    viewModel: DialPadViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    
    // Default Dialer Check
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null
    
    val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
        roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else true // Simplified for older versions
    
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val matchingContacts by viewModel.matchingContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching = searchQuery.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { rootNavController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colorScheme.onBackground
                )
            }

            // Search contacts by name or number
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Search contacts", fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (isSearching) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = colorScheme.onSurfaceVariant)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
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
                            "Experience Premium Calling",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "Set Trucaller as your default phone app to enable the full caller ID and safety features.",
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 15.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = {
                            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            if (intent != null) roleLauncher.launch(intent)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.primary
                        )
                    ) {
                        Text("SET", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Search Results Area — shows free-text search results while searching,
        // otherwise the dial-as-you-type matches.
        val resultsToShow = if (isSearching) searchResults else matchingContacts
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedVisibility(
                visible = resultsToShow.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(resultsToShow) { contact ->
                        ContactSearchResultItem(contact = contact) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dialWithSim(context, contact.phoneNumber, 0)
                        }
                    }
                }
            }

            val emptyMessage = when {
                isSearching && searchResults.isEmpty() -> "No contacts found"
                !isSearching && matchingContacts.isEmpty() && phoneNumber.isNotEmpty() -> "Unknown Number"
                else -> null
            }
            if (emptyMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = emptyMessage,
                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Phone number display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (phoneNumber.isNotEmpty()) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        createNewContact(context, phoneNumber)
                    },
                    color = colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Contact",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = phoneNumber.ifEmpty { " " },
                fontSize = if (phoneNumber.length > 12) 32.sp else 40.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dial pad grid
            DialPadGrid(
                onDigitClick = { digit ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onDigitPressed(digit)
                },
                onBackspaceClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onBackspace()
                },
                onBackspaceLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onBackspaceLongClick()
                },
                onZeroLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onLongPressZero()
                },
                showBackspace = phoneNumber.isNotEmpty()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons row: SIM1 | WhatsApp | SIM2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SMS button (only if number exists)
            ActionShortcutButton(
                icon = Icons.Default.Chat,
                label = "SMS",
                color = colorScheme.secondary,
                enabled = phoneNumber.isNotEmpty(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sendSms(context, phoneNumber)
                }
            )

            // Primary Call Button (SIM 1)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (phoneNumber.isNotEmpty()) colorScheme.primary else colorScheme.surfaceVariant)
                    .clickable(
                        enabled = phoneNumber.isNotEmpty(),
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple()
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dialWithSim(context, phoneNumber, 0)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // WhatsApp button
            ActionShortcutButton(
                painter = painterResource(id = R.drawable.ic_whatsapp),
                label = "WhatsApp",
                color = Color(0xFF25D366),
                enabled = phoneNumber.isNotEmpty(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    openWhatsApp(context, phoneNumber)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialPadGrid(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBackspaceLongClick: () -> Unit,
    onZeroLongClick: () -> Unit,
    showBackspace: Boolean
) {
    val keys = listOf(
        Triple("1", "", null),
        Triple("2", "ABC", null),
        Triple("3", "DEF", null),
        Triple("4", "GHI", null),
        Triple("5", "JKL", null),
        Triple("6", "MNO", null),
        Triple("7", "PQRS", null),
        Triple("8", "TUV", null),
        Triple("9", "WXYZ", null),
        Triple("*", "", null),
        Triple("0", "+", onZeroLongClick),
        Triple("#", "", null)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { (digit, sub, onLong) ->
                    DialKey(
                        digit = digit,
                        sub = sub,
                        onClick = { onDigitClick(digit) },
                        onLongClick = onLong
                    )
                }
            }
        }
        
        // Backspace row
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (showBackspace) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onBackspaceClick,
                            onLongClick = onBackspaceLongClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    sub: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 30.sp,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface.copy(alpha = 0.4f),
                    letterSpacing = 1.sp
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ContactSearchResultItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = contact.phoneNumber,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ActionShortcutButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) color.copy(alpha = 0.15f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(22.dp)
                )
            } else if (painter != null) {
                Icon(
                    painter,
                    contentDescription = label,
                    tint = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}

private fun dialWithSim(context: Context, number: String, simSlot: Int) {
    try {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val accounts = telecomManager?.callCapablePhoneAccounts
            if (accounts != null && accounts.size > simSlot) {
                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", accounts[simSlot])
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: SecurityException) {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
        }
        dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(dialIntent)
    }
}

private fun openWhatsApp(context: Context, number: String) {
    val normalizedNumber = PhoneUtils.normalizePhone(number)
    val cleanNumber = normalizedNumber.replace(Regex("[^+\\d]"), "")
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanNumber")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

private fun sendSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${Uri.encode(number)}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun createNewContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
        putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
