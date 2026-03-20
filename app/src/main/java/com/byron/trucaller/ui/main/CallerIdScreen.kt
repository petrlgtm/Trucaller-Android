package com.byron.trucaller.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerButtonStyle
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Divider
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.SurfaceElevated
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.Warning
import com.byron.trucaller.util.formatPhoneNumber
import com.byron.trucaller.util.getSpamScoreColor
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.CallerIdViewModel

@Composable
fun CallerIdScreen(callerIdViewModel: CallerIdViewModel, authViewModel: AuthViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    val lookupResult by callerIdViewModel.lookupResult.collectAsState()
    val recentLookups by callerIdViewModel.recentLookups.collectAsState()
    val notFound by callerIdViewModel.notFound.collectAsState()
    val actionMessage by callerIdViewModel.actionMessage.collectAsState()
    val isNumberBlocked by callerIdViewModel.isNumberBlocked.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val userId = authState.user?.id ?: ""

    val entry = lookupResult?.callerIdEntry
    val context = LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }

    // Animated spam score: starts at 0 and animates to actual value
    var targetSpamScore by remember { mutableFloatStateOf(0f) }
    val animatedSpamScore by animateFloatAsState(
        targetValue = targetSpamScore,
        animationSpec = tween(durationMillis = 800, delayMillis = 200),
        label = "spamScoreAnimation"
    )

    // Animate spam score when entry changes
    LaunchedEffect(entry?.phoneNumber, entry?.spamScore) {
        targetSpamScore = if (entry != null) entry.spamScore / 100f else 0f
    }

    // Reset spam score animation target when entry is cleared
    LaunchedEffect(entry) {
        if (entry == null) targetSpamScore = 0f
    }

    // Check blocked status when entry changes
    LaunchedEffect(entry?.phoneNumber) {
        if (entry != null && userId.isNotEmpty()) {
            callerIdViewModel.checkIfBlocked(entry.phoneNumber, userId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header - using TruCallerHeader component
        TruCallerHeader(
            title = "Caller ID",
            subtitle = "Search any phone number",
            gradientColors = listOf(BrandDark, Background),
            titleColor = Color.White,
            subtitleColor = Color.White.copy(alpha = 0.8f)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // Action message snackbar
            if (actionMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    containerColor = SurfaceCard,
                    action = {
                        TextButton(onClick = { callerIdViewModel.clearActionMessage() }) {
                            Text("OK", color = Brand)
                        }
                    }
                ) {
                    Text(actionMessage!!, color = TextPrimary)
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; if (notFound) callerIdViewModel.clearLookup() },
                placeholder = { Text("Enter phone number...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Brand) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { callerIdViewModel.lookup(searchQuery) }) {
                            Text("Search", color = Brand, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                keyboardActions = KeyboardActions(onDone = { callerIdViewModel.lookup(searchQuery) }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Divider,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated
                )
            )

            // Not-found state with EmptyStateView illustration
            if (notFound) {
                EmptyStateView(
                    title = "No Results Found",
                    subtitle = "We couldn't find any information for this phone number. Try a different number or check the format.",
                    icon = EmptyStateIcon.SEARCH,
                    modifier = Modifier.height(280.dp)
                )
            }

            // Result card
            if (entry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Source indicator using TruCallerBadge
                        val sourceLabel = when (lookupResult?.source) {
                            "registered_user" -> "Verified User"
                            "central_drive" -> "Known Contact"
                            "caller_id_db" -> "Caller ID Database"
                            else -> "Lookup Result"
                        }
                        val sourceBadgeType = when (lookupResult?.source) {
                            "registered_user" -> BadgeType.Success
                            "central_drive" -> BadgeType.Warning
                            else -> BadgeType.Info
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TruCallerBadge(
                                text = sourceLabel,
                                type = sourceBadgeType
                            )
                            if (isNumberBlocked) {
                                TruCallerBadge(
                                    text = "BLOCKED",
                                    type = BadgeType.Spam
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Caller info with TruCallerAvatar
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TruCallerAvatar(
                                name = entry.name,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                                Text(formatPhoneNumber(entry.phoneNumber), fontSize = 14.sp, color = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick action row: Call, WhatsApp, Edit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Call button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${entry.phoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Success.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, "Call", tint = Success, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Call", fontSize = 12.sp, color = TextSecondary)
                            }

                            // WhatsApp button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val phone = entry.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("https://wa.me/$phone")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF25D366).copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        "WhatsApp",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("WhatsApp", fontSize = 12.sp, color = TextSecondary)
                            }

                            // Edit button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        editName = entry.name
                                        showEditDialog = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Brand.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit", tint = Brand, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Edit", fontSize = 12.sp, color = TextSecondary)
                            }

                            // Block/Unblock button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        if (isNumberBlocked) {
                                            callerIdViewModel.unblockNumber(entry.phoneNumber, userId, entry.name)
                                        } else {
                                            callerIdViewModel.blockNumber(entry, userId)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isNumberBlocked) Success.copy(alpha = 0.1f) else Danger.copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        if (isNumberBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                        if (isNumberBlocked) "Unblock" else "Block",
                                        tint = if (isNumberBlocked) Success else Danger,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isNumberBlocked) "Unblock" else "Block",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Spam score with animated progress bar
                        Text("Spam Score", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { animatedSpamScore },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = getSpamScoreColor(entry.spamScore),
                                trackColor = Divider
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "${entry.spamScore}/100",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = getSpamScoreColor(entry.spamScore)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category badge using TruCallerBadge
                        val (catLabel, catBadgeType, catColor) = when (entry.category) {
                            SpamCategory.SAFE -> Triple("Safe", BadgeType.Success, Success)
                            SpamCategory.SUSPECTED_SPAM -> Triple("Suspected Spam", BadgeType.Warning, Warning)
                            SpamCategory.SPAM -> Triple("Spam", BadgeType.Spam, Color(0xFFF4511E))
                            SpamCategory.FRAUD -> Triple("Fraud", BadgeType.Spam, Danger)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TruCallerBadge(
                                text = catLabel,
                                type = catBadgeType,
                                color = catColor,
                                backgroundColor = catColor.copy(alpha = 0.12f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${entry.reportCount} reports", color = TextSecondary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons row using TruCallerButton
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TruCallerButton(
                                text = "Report",
                                onClick = { callerIdViewModel.reportNumber(entry) },
                                modifier = Modifier.weight(1f),
                                style = TruCallerButtonStyle.Primary,
                                leadingIcon = Icons.Default.Report
                            )
                            TruCallerButton(
                                text = "Save",
                                onClick = { callerIdViewModel.saveToContacts(entry, userId) },
                                modifier = Modifier.weight(1f),
                                style = TruCallerButtonStyle.Secondary,
                                leadingIcon = Icons.Default.PersonAdd
                            )
                        }
                    }
                }
            }

            // Recent lookups
            if (recentLookups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Recent Lookups", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column {
                        recentLookups.forEach { recentEntry ->
                            val catColor = when (recentEntry.category) {
                                SpamCategory.SAFE -> Success
                                SpamCategory.SUSPECTED_SPAM -> Warning
                                SpamCategory.SPAM -> Color(0xFFF4511E)
                                SpamCategory.FRAUD -> Danger
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        callerIdViewModel.selectRecentLookup(recentEntry)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TruCallerAvatar(
                                    name = recentEntry.name,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recentEntry.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                    Text(
                                        formatPhoneNumber(recentEntry.phoneNumber),
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                TruCallerBadge(
                                    text = "${recentEntry.spamScore}",
                                    type = BadgeType.Custom,
                                    color = catColor,
                                    backgroundColor = catColor.copy(alpha = 0.12f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit dialog
    if (showEditDialog && entry != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Name", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Phone: ${formatPhoneNumber(entry.phoneNumber)}", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Brand,
                            unfocusedBorderColor = Divider,
                            focusedContainerColor = SurfaceElevated,
                            unfocusedContainerColor = SurfaceElevated
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            callerIdViewModel.updateEntryName(entry, editName.trim())
                            showEditDialog = false
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
}
