package com.pralayakaveri.medisave.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.SettingsViewModel
import com.pralayakaveri.medisave.viewmodel.DeletionStage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showProDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    
    // Redirect on completion
    LaunchedEffect(uiState.isAccountDeleted) {
        if (uiState.isAccountDeleted) {
            onLogout()
        }
    }

    // Prevent accidental back navigation during deletion
    BackHandler(enabled = uiState.deletionStage != DeletionStage.IDLE && uiState.deletionStage != DeletionStage.COMPLETED) {
        // Do nothing - block back
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSystemPermissionGranted by remember { 
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        )
    }

    // Refresh on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isSystemPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showDeleteStep1 by remember { mutableStateOf(false) }
    var showDeleteStep2 by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- NOTIFICATIONS SECTION ---
            item { SectionHeader("NOTIFICATIONS") }

            if (!isSystemPermissionGranted) {
                item {
                    SystemNotificationWarning(onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    })
                }
            }
            
            item {
                SettingToggleItem(
                    icon = Icons.Outlined.Notifications,
                    iconColor = Color(0xFFE8F5E9),
                    title = "Push notifications",
                    subtitle = "Sound + vibration",
                    checked = uiState.pushNotificationsEnabled,
                    onCheckedChange = { viewModel.togglePushNotifications(it) }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Outlined.AccessTime,
                    iconColor = Color(0xFFEDE7F6),
                    title = "Snooze duration",
                    subtitle = "Default ${uiState.snoozeDuration} minutes",
                    trailingText = "${uiState.snoozeDuration} min",
                    onClick = { showSnoozeDialog = true }
                )
            }

            item {
                val precisionText = if (uiState.isAlarmPrecisionDegraded) "DEGRADED (Tap to restore)" else "PRECISE"
                val precisionColor = if (uiState.isAlarmPrecisionDegraded) Color(0xFFD97706) else TextSecondary
                SettingClickableItem(
                    icon = Icons.Outlined.Timer,
                    iconColor = Color(0xFFFFFDE7),
                    title = "Alarm precision",
                    subtitle = "Ensure exact-time reminders",
                    trailingText = precisionText,
                    trailingTextColor = precisionColor,
                    onClick = {
                        if (uiState.isAlarmPrecisionDegraded) {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                            } else { null }
                            intent?.let { context.startActivity(it) }
                        }
                    }
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Outlined.ErrorOutline,
                    iconColor = Color(0xFFFFF3E0),
                    title = "Notify me if I miss a dose",
                    subtitle = "Caregiver alerts will still be sent",
                    checked = uiState.missedDoseAlertEnabled,
                    onCheckedChange = { viewModel.toggleMissedDoseAlert(it) }
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Outlined.Group,
                    iconColor = Color(0xFFE0F2F1),
                    title = "Family connection alerts",
                    subtitle = "Kamal misses a dose → notify me",
                    checked = uiState.familyAlertsEnabled,
                    onCheckedChange = { viewModel.toggleFamilyAlerts(it) }
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Outlined.ShoppingBag,
                    iconColor = Color(0xFFFFEBEE),
                    title = "Refill reminders",
                    subtitle = "Alert when pills running low",
                    checked = uiState.refillRemindersEnabled,
                    onCheckedChange = { viewModel.toggleRefillReminders(it) }
                )
            }

            item { Divider(Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE)) }

            // --- PREFERENCES SECTION ---
            item { SectionHeader("PREFERENCES") }

            item {
                SettingClickableItem(
                    icon = Icons.Outlined.AddCircleOutline,
                    iconColor = Color(0xFFE3F2FD),
                    title = "Language",
                    subtitle = "App display language",
                    trailingText = uiState.appLanguage,
                    onClick = { /* In-app language picker placeholder */ }
                )
            }

            item { Divider(Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE)) }

            // --- DATA & PRIVACY SECTION ---
            item { SectionHeader("DATA & PRIVACY") }

            item {
                SettingClickableItem(
                    icon = Icons.Outlined.Shield,
                    iconColor = Color(0xFFE3F2FD),
                    title = "Privacy policy",
                    subtitle = "How we protect your data",
                    onClick = { 
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://medisave-app.com/privacy"))
                        context.startActivity(intent)
                    }
                )
            }

            // --- ACCOUNT ACTIONS ---
            item { Spacer(Modifier.height(32.dp)) }
            
            item {
                TextButton(
                    onClick = { viewModel.logout(onLogout) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text("Logout", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            item {
                TextButton(
                    onClick = { showDeleteStep1 = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text("Delete Account", color = Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- DIALOGS & SHEETS ---

    if (showSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Snooze Duration") },
            text = {
                Column {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                viewModel.updateSnoozeDuration(mins)
                                showSnoozeDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.snoozeDuration == mins, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text("$mins minutes")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text("Select Theme", Modifier.padding(24.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                listOf("Light", "Dark", "System").forEach { theme ->
                    Row(
                        Modifier.fillMaxWidth().clickable { 
                            viewModel.updateTheme(theme)
                            showThemeSheet = false
                        }.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(theme, fontSize = 16.sp)
                        if (uiState.appTheme == theme) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryGreen)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteStep1 = false },
            title = { Text("Delete Account?") },
            text = { Text("Are you sure you want to stop using MediSave? This action will disconnect you from all family connections.") },
            confirmButton = {
                TextButton(onClick = { 
                    showDeleteStep1 = false
                    showDeleteStep2 = true 
                }) {
                    Text("NEXT", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStep1 = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showDeleteStep2) {
        AlertDialog(
            onDismissRequest = { if (uiState.deletionStage == DeletionStage.IDLE) showDeleteStep2 = false },
            title = { Text("Final Confirmation") },
            text = { 
                Column {
                    Text("This will permanently delete ALL your health data, medicines, and family connections. This action is irreversible.")
                    Spacer(Modifier.height(16.dp))
                    
                    if (viewModel.getSignInProviders().contains("google.com")) {
                        Text("Please verify your identity with Google to continue.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Enter Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.error != null && uiState.deletionStage == DeletionStage.IDLE
                        )
                    }
                    
                    if (uiState.error != null && uiState.deletionStage == DeletionStage.IDLE) {
                        Text(uiState.error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                val isGoogle = viewModel.getSignInProviders().contains("google.com")
                Button(
                    onClick = { 
                        if (isGoogle) {
                            // In a real app, trigger Google intent here. 
                            // For this MVP, we'll assume the user is re-authenticated if they click this.
                            // Ideally, we'd use a callback from the Activity.
                            viewModel.startDeletionFlow(null) 
                        } else {
                            viewModel.startDeletionFlow(passwordInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = (passwordInput.isNotBlank() || isGoogle) && !uiState.isLoading
                ) {
                    Text(if (isGoogle) "VERIFY & DELETE" else "DELETE PERMANENTLY")
                }
            },
            dismissButton = {
                if (uiState.deletionStage == DeletionStage.IDLE) {
                    TextButton(onClick = { showDeleteStep2 = false }) {
                        Text("GO BACK")
                    }
                }
            }
        )
    }

    // --- DELETION PROGRESS OVERLAY ---
    if (uiState.deletionStage != DeletionStage.IDLE && uiState.deletionStage != DeletionStage.COMPLETED) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {},
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
                    CircularProgressIndicator(color = PrimaryGreen)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        uiState.deletionStage.name.replace("_", " "),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        uiState.deletionProgress,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    if (uiState.error != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!, color = Color.Red, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { 
                            if (uiState.deletionStage == DeletionStage.AUTH_DELETE) viewModel.retryAuthDelete()
                            else viewModel.startDeletionFlow(passwordInput, isResume = true)
                        }) {
                            Text("RETRY")
                        }
                    }
                }
            }
        }
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text("MediSave Pro") },
            text = { Text("This feature is part of MediSave Pro. Start your 7-day free trial to export health reports.") },
            confirmButton = {
                Button(onClick = { showProDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) {
                    Text("Try it Free")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 1.sp
    )
}

@Composable
fun SettingToggleItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = PrimaryGreen)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFEEEEEE),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingClickableItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    trailingTextColor: Color = TextSecondary,
    isPro: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = PrimaryGreen)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        
        if (isPro) {
            Surface(
                color = Color(0xFFE67E22),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "PRO",
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else if (trailingText != null) {
            Text(
                trailingText, 
                fontSize = 14.sp, 
                color = trailingTextColor,
                fontWeight = if (trailingTextColor != TextSecondary) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SystemNotificationWarning(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { onClick() },
        color = Color(0xFFFEF2F2),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEE2E2))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsPaused,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Notifications Disabled",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF991B1B)
                )
                Text(
                    text = "You won't receive any family alerts. Tap to enable in system settings.",
                    fontSize = 12.sp,
                    color = Color(0xFF991B1B).copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color(0xFFDC2626).copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

