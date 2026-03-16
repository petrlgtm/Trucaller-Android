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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.util.formatRelativeTime
import com.byron.trucaller.util.getInitials
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }

    val app = LocalContext.current.applicationContext as TruCallerApplication
    val users by app.container.userRepository.getAllUsers().collectAsState(initial = emptyList())

    val filtered by remember(searchQuery, users) {
        derivedStateOf {
            if (searchQuery.isBlank()) users
            else {
                val q = searchQuery.lowercase()
                users.filter { it.fullName.lowercase().contains(q) || it.phoneNumber.contains(q) }
            }
        }
    }

    val avatarColors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A),
        Color(0xFFE65100), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF37474F)
    )

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("Users (${filtered.size})", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name or phone...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Brand) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(filtered, key = { it.id }) { user ->
                val color = avatarColors[user.fullName.hashCode().absoluteValue % avatarColors.size]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { navController.navigate("admin_user_detail/${user.id}") },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).background(color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getInitials(user.fullName), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.fullName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.background(
                                        if (user.isActive) Success.copy(alpha = 0.1f) else Inactive.copy(alpha = 0.1f),
                                        RoundedCornerShape(6.dp)
                                    ).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (user.isActive) "Active" else "Inactive",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (user.isActive) Success else Inactive
                                    )
                                }
                            }
                            Text(user.phoneNumber, fontSize = 13.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            Row {
                                user.lastLogin?.let {
                                    Text("Last: ${formatRelativeTime(it)}", fontSize = 11.sp, color = Inactive)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
