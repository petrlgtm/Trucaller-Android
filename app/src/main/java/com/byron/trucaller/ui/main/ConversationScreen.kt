package com.byron.trucaller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val contactName = messages.firstOrNull()?.contactName
    val isSpam = messages.any { it.category == SmsCategory.SPAM }
    val displayName = contactName ?: address

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TruCallerAvatar(
                        name = displayName,
                        size = 36.dp,
                        indicatorColor = if (isSpam) colorScheme.error else null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            "${messages.size} messages",
                            fontSize = 11.sp,
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
                    // Remove from spam action
                    IconButton(onClick = {
                        smsViewModel.removeFromSpam(address, user.id, contentResolver)
                    }) {
                        Icon(Icons.Default.CheckCircle, "Not spam", tint = Color(0xFF4CAF50))
                    }
                } else {
                    // Report as spam action
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface)
        )

        // Spam warning banner
        AnimatedVisibility(visible = isSpam) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.error.copy(alpha = 0.08f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colorScheme.error.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Spam Detected", color = colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("This sender has been reported as spam", color = colorScheme.error.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        // Action message snackbar
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                containerColor = colorScheme.surfaceVariant,
                action = {
                    TextButton(onClick = { smsViewModel.clearActionMessage() }) {
                        Text("OK", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            ) { Text(actionMessage!!, color = colorScheme.onSurface) }
        }

        // Messages
        if (isLoading) {
            ShimmerLoadingList()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Group messages by date and add date headers
                var lastDateLabel = ""
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                    val dateLabel = formatMessageDate(message.date)
                    if (dateLabel != lastDateLabel) {
                        lastDateLabel = dateLabel
                        // Date separator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dateLabel,
                                modifier = Modifier
                                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Animated message bubble
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(250, delayMillis = (index * 10).coerceAtMost(300))
                        ) + fadeIn(tween(200, delayMillis = (index * 10).coerceAtMost(300)))
                    ) {
                        MessageBubble(message, colorScheme)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: SmsMessage,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    val isSent = message.type == SmsType.SENT

    val alignment = if (isSent) Alignment.End else Alignment.Start

    val shape = if (isSent) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    }

    val bubbleColor = if (isSent) {
        colorScheme.primary
    } else {
        colorScheme.surfaceVariant
    }

    val textColor = if (isSent) colorScheme.onPrimary else colorScheme.onSurface
    val timeColor = if (isSent) colorScheme.onPrimary.copy(alpha = 0.6f) else colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.body,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
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
                        fontSize = 10.sp
                    )
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.read) colorScheme.onPrimary.copy(alpha = 0.7f)
                            else colorScheme.onPrimary.copy(alpha = 0.4f)
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
