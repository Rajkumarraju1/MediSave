package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.SettingsViewModel
import com.pralayakaveri.medisave.viewmodel.DeletionStage

enum class DeleteDialogStage {
    NONE,
    WARNING,
    VERIFICATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToLinkedAccounts: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val primaryUser by viewModel.primaryUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? ProfileUiState.Success
    
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var deleteDialogStage by remember { mutableStateOf(DeleteDialogStage.NONE) }
    var passwordInput by remember { mutableStateOf("") }

    // Redirect to login when deletion is completed successfully
    LaunchedEffect(settingsUiState.isAccountDeleted) {
        if (settingsUiState.isAccountDeleted) {
            onLogout()
        }
    }

    // Lock navigation during critical database / auth deletion transactions
    BackHandler(enabled = settingsUiState.deletionStage == DeletionStage.PURGING || settingsUiState.deletionStage == DeletionStage.AUTH_DELETE) {
        // Block hardware back gesture
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Account", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = BrandingGreen.copy(alpha = 0.1f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = primaryUser?.name?.getOrNull(0)?.toString()?.uppercase() ?: "?",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandingGreen
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryUser?.name ?: "User",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = primaryUser?.email ?: "",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                            
                            Text(
                                text = "Edit profile",
                                color = BrandingGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToEditProfile() }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AccountStatItem(
                                label = "Medicines", 
                                value = successState?.activeMedicineCount?.toString() ?: "0", 
                                modifier = Modifier.weight(1f)
                            )
                            AccountStatItem(
                                label = "Adherence", 
                                value = "${successState?.primaryAdherence ?: 0}%", 
                                modifier = Modifier.weight(1f)
                            )
                            AccountStatItem(
                                label = "Members", 
                                value = successState?.connectedMemberCount?.toString() ?: "0", 
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ACCOUNT Section
            item { SectionHeader("ACCOUNT") }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.Lock,
                    iconColor = Color(0xFFE3F2FD),
                    title = "Change password",
                    subtitle = "Update your login password",
                    onClick = { onNavigateToChangePassword() }
                )
            }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.Link,
                    iconColor = Color(0xFFE8F5E9),
                    title = "Linked accounts",
                    subtitle = "Google · ${primaryUser?.email ?: ""}",
                    onClick = { onNavigateToLinkedAccounts() }
                )
            }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.StarOutline,
                    iconColor = Color(0xFFFFF8E1),
                    title = "Upgrade to Premium",
                    subtitle = "Unlimited · Reports · No ads",
                    badgeText = "₹49/mo",
                    onClick = { /* Upgrade intent */ }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // SUPPORT Section
            item { SectionHeader("SUPPORT") }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.HelpOutline,
                    iconColor = Color(0xFFF5F5F5),
                    title = "Help & FAQ",
                    onClick = { /* Help intent */ }
                )
            }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    iconColor = Color(0xFFF5F5F5),
                    title = "Send feedback",
                    onClick = { /* Feedback intent */ }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // DANGER ZONE Section
            item { SectionHeader("DANGER ZONE") }
            item {
                AccountActionItem(
                    icon = Icons.Outlined.DeleteOutline,
                    iconColor = Color(0xFFFFEBEE),
                    title = "Delete account",
                    subtitle = "Permanently removes all your data",
                    titleColor = Color.Red.copy(alpha = 0.7f),
                    onClick = { deleteDialogStage = DeleteDialogStage.WARNING }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // Sign out Button
            item {
                Button(
                    onClick = { settingsViewModel.logout(onLogout) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Icon(
                        Icons.Default.Logout, 
                        contentDescription = null, 
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Sign out",
                        color = Color.Red.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "MediSave v1.0.0 · Made with care in India",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }

    // --- ARCHITECTURAL ACCOUNT DELETION WORKFLOWS ---

    // Dialog Step 1: Warning and Irreversible Impact Info
    // Dialog Step 1: Warning and Irreversible Impact Info (Only mounts if stage is WARNING and VM is IDLE)
    if (deleteDialogStage == DeleteDialogStage.WARNING && settingsUiState.deletionStage == DeletionStage.IDLE) {
        AlertDialog(
            onDismissRequest = { deleteDialogStage = DeleteDialogStage.NONE },
            title = { Text("Delete Account?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to stop using MediSave? This action is permanent and will immediately disconnect you from all family connections.") },
            confirmButton = {
                TextButton(onClick = { 
                    deleteDialogStage = DeleteDialogStage.VERIFICATION
                }) {
                    Text("NEXT", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogStage = DeleteDialogStage.NONE }) {
                    Text("CANCEL", color = TextSecondary)
                }
            }
        )
    }

    // Dialog Step 2: Provider-gated Re-authentication (Only mounts if stage is VERIFICATION and VM is IDLE)
    if (deleteDialogStage == DeleteDialogStage.VERIFICATION && settingsUiState.deletionStage == DeletionStage.IDLE) {
        val providers = settingsViewModel.getSignInProviders()
        val isGoogle = providers.contains("google.com")
        
        AlertDialog(
            onDismissRequest = { 
                if (settingsUiState.deletionStage == DeletionStage.IDLE) {
                    deleteDialogStage = DeleteDialogStage.NONE 
                }
            },
            title = { Text("Identity Verification", fontWeight = FontWeight.Bold) },
            text = { 
                Column {
                    Text("This will permanently delete ALL your local & cloud health data. This action is completely irreversible.")
                    Spacer(Modifier.height(16.dp))
                    
                    if (isGoogle) {
                        Surface(
                            color = Color(0xFFFFF8E1),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Please re-authenticate your Google Account to complete the deletion transaction.",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Enter Login Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = settingsUiState.error != null && settingsUiState.deletionStage == DeletionStage.IDLE,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandingGreen, focusedLabelColor = BrandingGreen)
                        )
                    }
                    
                    if (settingsUiState.error != null && settingsUiState.deletionStage == DeletionStage.IDLE) {
                        Text(
                            text = settingsUiState.error!!, 
                            color = Color.Red, 
                            fontSize = 12.sp, 
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (isGoogle) {
                            settingsViewModel.startDeletionFlow(null) 
                        } else {
                            settingsViewModel.startDeletionFlow(passwordInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = (passwordInput.isNotBlank() || isGoogle) && !settingsUiState.isLoading
                ) {
                    Text(if (isGoogle) "VERIFY & DELETE" else "DELETE PERMANENTLY", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (settingsUiState.deletionStage == DeletionStage.IDLE) {
                    TextButton(onClick = { deleteDialogStage = DeleteDialogStage.WARNING }) {
                        Text("GO BACK", color = TextSecondary)
                    }
                }
            }
        )
    }

    // Fullscreen Deletion Transaction Progress & Error Handler (Only mounts if VM has active transaction/error)
    if (settingsUiState.deletionStage != DeletionStage.IDLE && settingsUiState.deletionStage != DeletionStage.COMPLETED) {
        val isLocked = settingsUiState.deletionStage == DeletionStage.PURGING || settingsUiState.deletionStage == DeletionStage.AUTH_DELETE
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (settingsUiState.error == null) {
                        CircularProgressIndicator(color = BrandingGreen)
                        Spacer(Modifier.height(24.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text = settingsUiState.deletionStage.name.replace("_", " "),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = settingsUiState.deletionProgress,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    if (settingsUiState.error != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = settingsUiState.error!!, 
                            color = Color.Red, 
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Retry Button
                            Button(
                                onClick = { 
                                    if (settingsUiState.deletionStage == DeletionStage.AUTH_DELETE) {
                                        settingsViewModel.retryAuthDelete()
                                    } else {
                                        settingsViewModel.startDeletionFlow(passwordInput, isResume = true)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandingGreen)
                            ) {
                                Text("RETRY", fontWeight = FontWeight.Bold)
                            }
                            
                            // Cancel Button - resets dialog state and signs out safely
                            OutlinedButton(
                                onClick = { 
                                    settingsViewModel.logout {
                                        deleteDialogStage = DeleteDialogStage.NONE
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCEL", color = TextSecondary)
                            }
                        }
                    } else if (!isLocked) {
                        // Allow cancellation during cancellable stages (like REAUTH)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = {
                                deleteDialogStage = DeleteDialogStage.NONE
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ABORT DELETION", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountStatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF1F1F1)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AccountActionItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = BrandingGreen)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        
        if (badgeText != null) {
            Surface(
                color = Color(0xFFE67E22).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    badgeText,
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
    }
    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
}
