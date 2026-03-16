package com.byron.trucaller.ui.main

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Success
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
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandDark)
                .padding(24.dp)
        ) {
            Column {
                Text("Caller ID", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Search any phone number", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Action message snackbar
            if (actionMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { callerIdViewModel.clearActionMessage() }) {
                            Text("OK", color = Color.White)
                        }
                    }
                ) {
                    Text(actionMessage!!)
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
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedContainerColor = Color(0xFF252525),
                    unfocusedContainerColor = Color(0xFF252525)
                )
            )

            if (notFound) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("No results found for this number", color = TextSecondary, fontSize = 14.sp)
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
                        // Source indicator
                        val sourceLabel = when (lookupResult?.source) {
                            "registered_user" -> "Verified User"
                            "central_drive" -> "Known Contact"
                            "caller_id_db" -> "Caller ID Database"
                            else -> "Lookup Result"
                        }
                        val sourceColor = when (lookupResult?.source) {
                            "registered_user" -> Success
                            "central_drive" -> Brand
                            else -> TextSecondary
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(sourceColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(sourceLabel, color = sourceColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            if (isNumberBlocked) {
                                Box(
                                    modifier = Modifier
                                        .background(Danger.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("BLOCKED", color = Danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Caller info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Brand.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    entry.name.first().toString(),
                                    color = Brand,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
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
                                        val phone = entry.phoneNumber.replace("+", "").replace(" ", "")
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

                        // Spam score
                        Text("Spam Score", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { entry.spamScore / 100f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = getSpamScoreColor(entry.spamScore),
                                trackColor = Color(0xFF333333)
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

                        // Category badge
                        val (catLabel, catColor) = when (entry.category) {
                            SpamCategory.SAFE -> "Safe" to Success
                            SpamCategory.SUSPECTED_SPAM -> "Suspected Spam" to Warning
                            SpamCategory.SPAM -> "Spam" to Color(0xFFF4511E)
                            SpamCategory.FRAUD -> "Fraud" to Danger
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(catColor.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(catLabel, color = catColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${entry.reportCount} reports", color = TextSecondary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { callerIdViewModel.reportNumber(entry) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Report, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Report", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { callerIdViewModel.saveToContacts(entry, userId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 13.sp)
                            }
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recentEntry.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                    Text(
                                        formatPhoneNumber(recentEntry.phoneNumber),
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(catColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${recentEntry.spamScore}", color = catColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
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
            title = { Text("Edit Name", fontWeight = FontWeight.Bold) },
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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
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
