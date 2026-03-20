package com.byron.trucaller.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.ui.components.BadgeType
import com.byron.trucaller.ui.components.EmptyStateIcon
import com.byron.trucaller.ui.components.EmptyStateView
import com.byron.trucaller.ui.components.ShimmerLoadingList
import com.byron.trucaller.ui.components.TruCallerAvatar
import com.byron.trucaller.ui.components.TruCallerBadge
import com.byron.trucaller.ui.components.TruCallerCard
import com.byron.trucaller.ui.components.TruCallerHeader
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.util.getInitials

private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var isInitialLoad by remember { mutableStateOf(true) }

    val app = LocalContext.current.applicationContext as TruCallerApplication
    val users by app.container.userRepository.getAllUsers().collectAsState(initial = emptyList())

    // Mark initial load complete once we have data
    if (users.isNotEmpty() && isInitialLoad) {
        isInitialLoad = false
    }

    val filtered by remember(searchQuery, users) {
        derivedStateOf {
            if (searchQuery.isBlank()) users
            else {
                val q = searchQuery.lowercase()
                users.filter { it.fullName.lowercase().contains(q) || it.phoneNumber.contains(q) }
            }
        }
    }

    // Pagination state
    var currentPage by remember { mutableIntStateOf(1) }
    val lazyListState = rememberLazyListState()

    // Reset page when filter changes
    LaunchedEffect(searchQuery) {
        currentPage = 1
    }

    val paginatedItems by remember(filtered, currentPage) {
        derivedStateOf { filtered.take(PAGE_SIZE * currentPage) }
    }
    val hasMoreItems by remember(paginatedItems, filtered) {
        derivedStateOf { paginatedItems.size < filtered.size }
    }

    // Detect scroll near bottom to load next page
    LaunchedEffect(lazyListState, hasMoreItems) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to totalItems
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3 && hasMoreItems) {
                currentPage++
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    "Users (${filtered.size})",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.primary)
        )

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name or phone...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.primary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant
            )
        )

        when {
            isInitialLoad && users.isEmpty() -> {
                ShimmerLoadingList(modifier = Modifier.padding(horizontal = Spacing.md))
            }
            filtered.isEmpty() -> {
                EmptyStateView(
                    title = "No Users Found",
                    subtitle = if (searchQuery.isNotBlank()) "No users match \"$searchQuery\""
                    else "No users have registered yet",
                    icon = EmptyStateIcon.CONTACTS
                )
            }
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md)
                ) {
                    items(paginatedItems, key = { it.id }) { user ->
                        TruCallerCard(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .clickable { navController.navigate("admin_user_detail/${user.id}") },
                            elevation = 0.5.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TruCallerAvatar(name = user.fullName, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.fullName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TruCallerBadge(
                                            text = if (user.isActive) "Active" else "Inactive",
                                            type = if (user.isActive) BadgeType.Success else BadgeType.Info
                                        )
                                    }
                                    Text(
                                        user.phoneNumber,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Row {
                                        user.lastLogin?.let {
                                            Text(
                                                "Last: ${formatRelativeTime(it)}",
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Pagination footer
                    item {
                        if (hasMoreItems) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary
                                )
                            }
                        } else if (paginatedItems.size > PAGE_SIZE) {
                            Text(
                                "All items loaded",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.md)) }
                }
            }
        }
    }
}
