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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsType
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.SurfaceLight
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.ui.theme.TextOnYellow
import com.byron.trucaller.ui.theme.YellowGradientEnd
import com.byron.trucaller.ui.theme.YellowGradientStart
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
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
            .background(Background)
    ) {
        // Top bar with consistent back button styling
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mini avatar using TruCallerAvatar component
                    TruCallerAvatar(
                        name = displayName,
                        size = 36.dp,
                        indicatorColor = if (isSpam) Danger else null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            "${messages.size} messages",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    val lastMsg = messages.lastOrNull()?.body ?: ""
                    smsViewModel.reportAsSpam(address, lastMsg, user.id, contentResolver)
                }) {
                    Icon(Icons.Default.Report, "Report", tint = Brand)
                }
                IconButton(onClick = {
                    smsViewModel.blockSmsNumber(address, user.id, contentResolver)
                }) {
                    Icon(Icons.Default.Block, "Block", tint = Accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
        )

        // Spam warning
        AnimatedVisibility(visible = isSpam) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Danger.copy(alpha = 0.08f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Danger.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = Danger, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Spam Detected",
                        color = Danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "This sender has been reported as spam",
                        color = Danger.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Action message
        if (actionMessage != null) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                containerColor = SurfaceCard,
                action = {
                    TextButton(onClick = { smsViewModel.clearActionMessage() }) {
                        Text("OK", color = Brand, fontWeight = FontWeight.Bold)
                    }
                }
            ) { Text(actionMessage!!, color = TextPrimary) }
        }

        // Messages with slide-up animation
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                    // Each message bubble slides up from the bottom with a fade
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight / 3 },
                            animationSpec = tween(
                                durationMillis = 300,
                                delayMillis = (index * 15).coerceAtMost(600)
                            )
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 250,
                                delayMillis = (index * 15).coerceAtMost(600)
                            )
                        )
                    ) {
                        MessageBubble(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    val isSent = message.type == SmsType.SENT

    val alignment = if (isSent) Alignment.End else Alignment.Start

    // Bubble with tail/pointer shape: tighter corner on the tail side
    val shape = if (isSent) {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 4.dp,    // Tail corner (tight)
            bottomEnd = 20.dp,
            bottomStart = 20.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,  // Tail corner (tight)
            topEnd = 20.dp,
            bottomEnd = 20.dp,
            bottomStart = 20.dp
        )
    }

    // Gradient backgrounds using theme tokens
    val bubbleGradient = if (isSent) {
        Brush.linearGradient(listOf(YellowGradientStart, YellowGradientEnd))
    } else {
        Brush.linearGradient(listOf(SurfaceCard, SurfaceLight))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    brush = bubbleGradient,
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.body,
                    color = if (isSent) TextOnYellow else TextPrimary,
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
                        color = if (isSent) TextOnYellow.copy(alpha = 0.6f) else Inactive,
                        fontSize = 10.sp
                    )
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.read) TextOnYellow.copy(alpha = 0.7f)
                            else TextOnYellow.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
