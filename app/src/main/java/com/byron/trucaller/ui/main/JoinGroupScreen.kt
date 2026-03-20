package com.byron.trucaller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.Spacing
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.FamilyGroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    familyGroupViewModel: FamilyGroupViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val user = authState.user ?: return
    val uiState by familyGroupViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    var inviteCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            familyGroupViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                "Enter Invite Code",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                "Ask a family group admin to share their invite code with you.",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = inviteCode,
                onValueChange = {
                    inviteCode = it.uppercase().filter { c -> c.isLetterOrDigit() }
                    codeError = null
                },
                label = { Text("Invite Code") },
                placeholder = { Text("e.g. ABC12345") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("invite_code_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedContainerColor = colorScheme.surfaceVariant,
                    unfocusedContainerColor = colorScheme.surfaceVariant
                ),
                isError = codeError != null
            )

            if (codeError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    codeError!!,
                    color = colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = {
                    val trimmed = inviteCode.trim()
                    if (trimmed.isBlank()) {
                        codeError = "Please enter an invite code"
                    } else if (trimmed.length < 4) {
                        codeError = "Invite code is too short"
                    } else {
                        familyGroupViewModel.joinGroup(trimmed, user.id) {
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("join_group_submit"),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Join Group", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
