package com.byron.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.MontserratFontFamily
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    navController: NavController,
    address: String,
    authViewModel: AuthViewModel,
    smsViewModel: SmsViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return
    val colorScheme = MaterialTheme.colorScheme

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val messages by smsViewModel.currentMessages.collectAsState()
    val isLoading by smsViewModel.isLoading.collectAsState()
    val actionMessage by smsViewModel.actionMessage.collectAsState()

    val hasSmsPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(address) {
        if (hasSmsPermission) {
            smsViewModel.loadConversation(contentResolver, address, user.id)
        }
    }

    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    val sendPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            smsViewModel.sendSms(address, messageText, contentResolver)
            messageText = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val contactName = messages.firstOrNull()?.contactName
    val spamCount = messages.count { it.category == SmsCategory.SPAM }
    val isSpam = messages.isNotEmpty() && spamCount > messages.size / 2
    val displayName = contactName ?: address

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Premium top bar — flat background
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TruCallerAvatar(
                        name = displayName,
                        size = 40.dp,
                        indicatorColor = if (isSpam) colorScheme.error else null
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            displayName,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "${messages.size} messages",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onSurface
                    )
                }
            },
            actions = {
                if (isSpam) {
                    IconButton(onClick = {
                        smsViewModel.removeFromSpam(address, user.id, contentResolver)
                    }) {
                        Icon(Icons.Default.CheckCircle, "Not spam", tint = Color(0xFF4CAF50))
                    }
                } else {
                    IconButton(onClick = {
                        val lastMsg = messages.lastOrNull()?.body ?: ""
                        smsViewModel.reportAsSpam(address, lastMsg, user.id, contentResolver)
                    }) {
                        Icon(Icons.Default.Report, "Report", tint = colorScheme.primary)
                    }
                }
                IconButton(onClick = {
                    smsViewModel.blockSmsNumber(address, user.id, contentResolver)
                }) {
                    Icon(Icons.Default.Block, "Block", tint = colorScheme.error)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
        )

        HorizontalDivider(thickness = 0.5.dp, color = GlassBorder)

        // Spam warning banner
        AnimatedVisibility(visible = isSpam) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colorScheme.error.copy(alpha = 0.08f),
                                colorScheme.error.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colorScheme.error.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Spam Detected",
                        fontFamily = MontserratFontFamily,
                        color = colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "This sender has been flagged as spam",
                        color = colorScheme.error.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Action message snackbar
        if (actionMessage != null) {
            LaunchedEffect(actionMessage) {
                kotlinx.coroutines.delay(3000)
                smsViewModel.clearActionMessage()
            }
            Snackbar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = colorScheme.surfaceVariant
            ) { Text(actionMessage!!, color = colorScheme.onSurface) }
        }

        // Messages area
        Column(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                ShimmerLoadingList()
            } else if (messages.isEmpty()) {
                EmptyStateView(
                    title = "No messages",
                    subtitle = "Messages with this contact will appear here.",
                    icon = EmptyStateIcon.MESSAGES
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    var lastDateLabel = ""
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                        val dateLabel = formatMessageDate(message.date)
                        if (dateLabel != lastDateLabel) {
                            lastDateLabel = dateLabel
                            // Date pill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dateLabel,
                                    modifier = Modifier
                                        .background(
                                            colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    fontFamily = MontserratFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }

                        // Animate only the first 20 visible messages
                        if (index < 20) {
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { visible = true }
                            AnimatedVisibility(
                                visible = visible,
                                enter = slideInVertically(
                                    initialOffsetY = { it / 5 },
                                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(200))
                            ) {
                                PremiumMessageBubble(message, colorScheme)
                            }
                        } else {
                            PremiumMessageBubble(message, colorScheme)
                        }
                    }
                    // Bottom padding
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        // Message Input Bar
        HorizontalDivider(thickness = 0.5.dp, color = GlassBorder)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("Text message", fontSize = 15.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val hasSendPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.SEND_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasSendPermission) {
                                smsViewModel.sendSms(address, messageText, contentResolver)
                                messageText = ""
                            } else {
                                sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (messageText.isNotBlank()) colorScheme.primary else colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank()) Color.White else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumMessageBubble(
    message: SmsMessage,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    val isSent = message.type == SmsType.SENT

    val shape = if (isSent) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }

    val bubbleBrush = if (isSent) {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.primary,
                colorScheme.primary.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.surface,
                colorScheme.surface
            )
        )
    }

    val textColor = if (isSent) Color.White else colorScheme.onSurface
    val timeColor = if (isSent) Color.White.copy(alpha = 0.55f) else colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleBrush)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.body,
                    color = textColor,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.US).format(Date(message.date)),
                        color = timeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.read) Color.White.copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageDate(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L

    return when {
        timestamp >= todayStart -> "Today"
        timestamp >= yesterdayStart -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(timestamp))
    }
}
