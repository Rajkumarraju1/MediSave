package com.pralayakaveri.medisave.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.model.DoseStatus
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.util.formatTime
import com.pralayakaveri.medisave.viewmodel.DayAdherence
import com.pralayakaveri.medisave.viewmodel.HomeUiState
import com.pralayakaveri.medisave.viewmodel.HomeViewModel
import com.pralayakaveri.medisave.viewmodel.ScheduleItem
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToGenerics: () -> Unit,
    onAddMedicine: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isUpdating by viewModel.isUpdating.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.pralayakaveri.medisave.data.PreferenceManager(context) }
    val degradedBannerDismissed by prefManager.degradedBannerDismissed.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    val alarmManager = remember { context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager }
    val currentPermission = remember(alarmManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    val showDegradedBanner = !currentPermission && !degradedBannerDismissed

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    color = PrimaryGreen,
                    trackColor = TakenGreenBg
                )
            }
            
            when (val state = uiState) {
            is HomeUiState.Loading -> LoadingState()
            is HomeUiState.Empty -> EmptyState(onAddMedicine = onAddMedicine)
            is HomeUiState.Error -> ErrorState(state.message)
            is HomeUiState.Success -> {
                HomeScreenContent(
                    state = state,
                    userName = userName,
                    isUpdating = isUpdating,
                    onToggleTaken = { id, time, taken, status -> viewModel.markAsTaken(id, time, taken, status) },
                    onResetToday = { viewModel.resetToday() },
                    onDeleteMedicine = { viewModel.deleteMedicine(it) },
                    onNavigateToGenerics = onNavigateToGenerics,
                    onNavigateToProfile = onNavigateToProfile,
                    onDebugClick = { viewModel.showDebugInfo(it) },
                    onRefillStock = { medId, medName, qty -> viewModel.refillStock(medId, medName, qty) },
                    showDegradedBanner = showDegradedBanner,
                    onDismissDegradedBanner = {
                        coroutineScope.launch {
                            prefManager.saveDegradedBannerDismissed(true)
                        }
                    }
                )
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    state: HomeUiState.Success,
    userName: String,
    isUpdating: Boolean,
    onToggleTaken: (String, String, Boolean, DoseStatus?) -> Unit,
    onResetToday: (String) -> Unit,
    onDeleteMedicine: (String) -> Unit,
    onNavigateToGenerics: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onDebugClick: (String) -> Unit,
    onRefillStock: (medicineId: String, medicineName: String, quantity: Int) -> Unit = { _, _, _ -> },
    showDegradedBanner: Boolean = false,
    onDismissDegradedBanner: () -> Unit = {}
) {
    var showRefillSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // RefillBottomSheet: shown when the user taps the Refill Alert Card
    if (showRefillSheet && state.lowStockMedicine != null) {
        RefillBottomSheet(
            medicine = state.lowStockMedicine,
            sheetState = sheetState,
            onDismiss = { showRefillSheet = false },
            onConfirm = { qty ->
                onRefillStock(state.lowStockMedicine.id, state.lowStockMedicine.name, qty)
                showRefillSheet = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeHeader(
            userName = userName,
            adherencePercentage = state.weeklyAdherence,
            weeklyTaken = state.weeklyTaken,
            weeklyTotal = state.weeklyTotal,
            todayTaken = state.takenCount,
            todayTotal = state.totalCount,
            dailyStats = state.dailyStats,
            secondaryLabel = state.adherenceTheme.labelText,
            todayFractionLabel = state.todayAdherenceLabel,
            adherenceTheme = state.adherenceTheme,
            daysWithData = state.daysWithData,
            onNavigateToProfile = onNavigateToProfile,
            onDebugClick = onDebugClick,
            debugInfo = state.debugInfo
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (showDegradedBanner) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF9EB))
                    .border(1.dp, Color(0xFFFEF08A), RoundedCornerShape(16.dp))
                    .clickable {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        } else { null }
                        intent?.let { context.startActivity(it) }
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alarm Timing Degraded",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Exact timing is disabled. Medicine reminders may be delayed up to 30 minutes by system battery management. Tap to restore precision.",
                            color = Color(0xFFB45309).copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismissDegradedBanner,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.isStartingToday || state.startsTomorrow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (state.isStartingToday) Icons.Default.Info else Icons.Default.EventNote,
                        contentDescription = null,
                        tint = PrimaryGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.isStartingToday) "Started Today" else "Starts Tomorrow",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (state.isStartingToday)
                            "Scheduled doses have passed. Tracking starts fully tomorrow."
                            else "Your first doses are scheduled for tomorrow.",
                        color = PrimaryGreen.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Refill Alert Card — rendered only when a medicine is low on stock
        if (state.hasRefillAlert && state.lowStockMedicine != null) {
            RefillAlertCard(
                medicine = state.lowStockMedicine,
                onRefillClick = { showRefillSheet = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        StatsRow(state.totalCount, state.takenCount, state.pendingCount)

        Spacer(modifier = Modifier.height(32.dp))

        ScheduleSection(
            items = state.scheduleItems,
            isUpdating = isUpdating,
            onToggleTaken = onToggleTaken,
            onResetToday = onResetToday,
            onDeleteMedicine = onDeleteMedicine,
            onNavigateToGenerics = onNavigateToGenerics
        )

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeHeader(
    userName: String,
    adherencePercentage: Int?,
    weeklyTaken: Int,
    weeklyTotal: Int,
    todayTaken: Int,
    todayTotal: Int,
    dailyStats: List<DayAdherence>,
    secondaryLabel: String,
    todayFractionLabel: String,
    adherenceTheme: com.pralayakaveri.medisave.model.AdherenceThemeConfig,
    daysWithData: Int,
    onNavigateToProfile: () -> Unit,
    onDebugClick: (String) -> Unit,
    debugInfo: String
) {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    
    val dateStr = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())

    // Removed legacy cardBg logic - now handled by AdherenceThemeMapper
    val cardBg = adherenceTheme.cardBackground

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        cardBg,
                        cardBg.copy(alpha = 0.85f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting,", 
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), 
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userName.ifBlank { "User" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = dateStr, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = ripple(bounded = false, color = Color.White, radius = 24.dp),
                            onClick = onNavigateToProfile
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            // PREMIUM ADHERENCE CARD (Final Spec)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f)) // Soft tonal separation
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .then(
                        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { onDebugClick(debugInfo) }
                            )
                        } else Modifier
                    )
                    .padding(20.dp)
            ) {
                Column {
                    // Header Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "This week's adherence",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = dateStr,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (adherencePercentage != null) "${adherencePercentage}%" else "—",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = todayFractionLabel,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (adherencePercentage != null) adherencePercentage / 100f else 0f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Weekly Indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dailyStats.forEach { stat ->
                            val status = stat.status
                            val isToday = stat.date.isEqual(java.time.LocalDate.now())
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Day Rendering Logic (Simplified dots as requested)
                                val dotBg = when (status) {
                                    com.pralayakaveri.medisave.viewmodel.DayStatus.TAKEN -> Color.White.copy(alpha = 0.25f)
                                    com.pralayakaveri.medisave.viewmodel.DayStatus.MISSED -> Color.White.copy(alpha = 0.25f)
                                    com.pralayakaveri.medisave.viewmodel.DayStatus.BLUE -> Color.White.copy(alpha = 0.25f)
                                    com.pralayakaveri.medisave.viewmodel.DayStatus.TODAY,
                                    com.pralayakaveri.medisave.viewmodel.DayStatus.PARTIAL -> Color.White.copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.05f) // FUTURE / EMPTY
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(dotBg)
                                        .alpha(if (status == com.pralayakaveri.medisave.viewmodel.DayStatus.FUTURE) 0.3f else 1.0f)
                                        .then(
                                            if (isToday || status == com.pralayakaveri.medisave.viewmodel.DayStatus.PARTIAL)
                                                Modifier.border(1.5.dp, Color.White, CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        // 🔵 Rule: TODAY always shows fraction (0/2, 1/2, 2/2)
                                        isToday -> {
                                            Text(
                                                text = "${stat.taken}/${stat.total}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        // 🟢 Past: PERFECT
                                        status == com.pralayakaveri.medisave.viewmodel.DayStatus.TAKEN -> {
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        // 🔴 Past: MISSED
                                        status == com.pralayakaveri.medisave.viewmodel.DayStatus.MISSED -> {
                                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        // 🟡 Past: PARTIAL
                                        status == com.pralayakaveri.medisave.viewmodel.DayStatus.PARTIAL -> {
                                            Text(
                                                text = "${stat.taken}/${stat.total}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        else -> {}
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Day Name
                                Text(
                                    text = stat.date.dayOfWeek.toString().first().toString(),
                                    color = if (status == com.pralayakaveri.medisave.viewmodel.DayStatus.FUTURE) Color.White.copy(alpha = 0.3f) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Black else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Priority Footer Message
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            adherenceTheme.footerIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = adherenceTheme.footerMessage,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal // Less aggressive typography
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsRow(total: Int, taken: Int, pending: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(modifier = Modifier.weight(1f), number = total.toString(), label = "Today", bgColor = Color.White, textColor = TextPrimary)
        StatCard(modifier = Modifier.weight(1f), number = taken.toString(), label = "Taken", bgColor = TakenGreenBg, textColor = BrandingGreen)
        StatCard(modifier = Modifier.weight(1f), number = pending.toString(), label = "Pending", bgColor = Color.White, textColor = TextPrimary)
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, number: String, label: String, bgColor: Color, textColor: Color) {
    Surface(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (bgColor == Color.White) androidx.compose.foundation.BorderStroke(1.dp, DividerGray.copy(alpha = 0.5f)) else null,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = number, 
                fontSize = 28.sp, 
                fontWeight = FontWeight.Bold, 
                color = if (label == "Taken") BrandingGreen else TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label, 
                fontSize = 13.sp, 
                color = if (label == "Taken") BrandingGreen else TextSecondary, 
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ScheduleSection(
    items: List<ScheduleItem>,
    isUpdating: Boolean,
    onToggleTaken: (String, String, Boolean, DoseStatus?) -> Unit,
    onResetToday: (String) -> Unit,
    onDeleteMedicine: (String) -> Unit,
    onNavigateToGenerics: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Today's schedule", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = "View all", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        var hasShownPending = false
        var hasShownOverdue = false
        var hasShownCompleted = false

        items.forEachIndexed { index, item ->
            // Classify the item into one of three sections:
            //  • Completed: taken or explicitly skipped
            //  • Overdue:   grace period has passed and dose is unresolved
            //  • Pending:   future dose not yet due
            val isCompleted = item.isTaken
                || item.status == DoseStatus.SKIPPED_AUTO
                || item.status == DoseStatus.SKIPPED_NO_STOCK
            val isOverdue = item.isComputedOverdue || item.status == DoseStatus.MISSED

            when {
                // ── PENDING header ─────────────────────────────────────────
                !isCompleted && !isOverdue && !hasShownPending -> {
                    Text(
                        text = "Pending",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    hasShownPending = true
                }
                // ── OVERDUE header ─────────────────────────────────────────
                isOverdue && !hasShownOverdue -> {
                    if (hasShownPending) Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC2410C),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Overdue",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C)
                        )
                    }
                    hasShownOverdue = true
                }
                // ── COMPLETED header ───────────────────────────────────────
                isCompleted && !hasShownCompleted -> {
                    if (hasShownPending || hasShownOverdue) Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Completed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    hasShownCompleted = true
                }
                else -> {}
            }

            ScheduleItemCard(
                item = item,
                isNextUp = item.isNextUp,
                isUpdating = isUpdating,
                onToggleTaken = onToggleTaken,
                onResetToday = onResetToday,
                onDeleteMedicine = onDeleteMedicine,
                onNavigateToGenerics = onNavigateToGenerics
            )
            
            if (index < items.size - 1) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ScheduleItemCard(
    item: ScheduleItem,
    isNextUp: Boolean,
    isUpdating: Boolean,
    onToggleTaken: (String, String, Boolean, DoseStatus?) -> Unit,
    onResetToday: (String) -> Unit,
    onDeleteMedicine: (String) -> Unit,
    onNavigateToGenerics: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showLastPillConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete medicine?") },
            text = { Text("This will permanently remove ${item.medicine.name} and all its tracking data.") },
            confirmButton = {
                TextButton(onClick = { 
                    onDeleteMedicine(item.medicine.id)
                    showDeleteConfirm = false 
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset today?") },
            text = { Text("Clear all progress for ${item.medicine.name} for today only? This allows you to re-track today's doses.") },
            confirmButton = {
                TextButton(onClick = { 
                    onResetToday(item.medicine.id)
                    showResetConfirm = false 
                }, colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
    val medicine = item.medicine
    val isTaken = item.isTaken
    // Real-time stock check: insufficient pills to take a full dose (includes 0-pills case).
    // Uses actual pillsLeft rather than stored DoseStatus so it catches medicines that ran
    // out AFTER the status was last persisted (e.g. after a refill was not yet synced).
    val isOutOfStock = medicine.pillsLeft < medicine.doseQuantity && !isTaken
    val isAutoSkipped = item.status == DoseStatus.SKIPPED_AUTO
    // isLastPill: exactly enough for one dose — next take will exhaust stock.
    val isLastPill = medicine.pillsLeft == medicine.doseQuantity && !isTaken && !isOutOfStock && !isAutoSkipped

    if (showLastPillConfirm) {
        AlertDialog(
            onDismissRequest = { showLastPillConfirm = false },
            title = { Text("Consume Last Pill?", fontWeight = FontWeight.Bold, color = AlertOrangeText) },
            text = { Text("This will use the last available pill(s) for ${medicine.name}. Please ensure you have a refill ready.") },
            confirmButton = {
                TextButton(onClick = { 
                    onToggleTaken(item.medicine.id, item.time, true, null)
                    showLastPillConfirm = false 
                }, colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)) {
                    Text("Confirm Taken")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLastPillConfirm = false }) { Text("Cancel") }
            }
        )
    }
    
    // Priority: TAKEN > OUT_OF_STOCK > LAST_PILL > AUTO_SKIPPED > NORMAL
    val cardColor = when {
        isTaken -> MaterialTheme.colorScheme.surface
        isOutOfStock -> MaterialTheme.colorScheme.surface
        isLastPill -> MaterialTheme.colorScheme.errorContainer
        isAutoSkipped -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    
    val borderColor = when {
        isTaken -> DividerGray
        isOutOfStock -> Color.Red
        isLastPill -> DotOrange
        isAutoSkipped -> DividerGray.copy(alpha = 0.5f)
        item.isComputedOverdue -> Color(0xFFF59E0B) // amber — overdue but still actionable
        isNextUp -> PrimaryGreen
        else -> DividerGray
    }
    
    val dotColor = when {
        isTaken -> MaterialTheme.colorScheme.primary
        isOutOfStock -> MaterialTheme.colorScheme.error
        isAutoSkipped -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        isLastPill -> MaterialTheme.colorScheme.error
        else -> try { Color(android.graphics.Color.parseColor(medicine.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isTaken || isOutOfStock || isAutoSkipped) 0.75f else 1f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isTaken || isOutOfStock || isAutoSkipped) 0.dp else 2.dp,
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isNextUp || isOutOfStock || isLastPill) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTime(item.time), 
                        fontSize = 12.sp, 
                        color = if (isLastPill) AlertOrangeText else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = medicine.name, 
                        fontSize = 16.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = if (isOutOfStock) Color.Gray else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (isOutOfStock) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp)) // Reduced spacing
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                            text = when {
                                    isAutoSkipped -> "Skipped (added after time)"
                                    item.status == DoseStatus.MISSED -> "Missed ${formatDoseTimeAgo(item.time)}"
                                    item.status == DoseStatus.TAKEN_EARLY -> "Taken early — counts at ${item.time}"
                                    item.status == DoseStatus.TAKEN_ON_TIME -> "Taken on time \u2022 ${medicine.pillsLeft} left"
                                    item.status == DoseStatus.TAKEN_LATE -> "Taken late \u2022 ${medicine.pillsLeft} left"
                                    isTaken -> "Taken \u2022 ${medicine.pillsLeft} left"
                                    isOutOfStock -> if (medicine.pillsLeft == 0)
                                        "No pills left \u2014 refill needed"
                                    else
                                        "${medicine.pillsLeft} pill${if (medicine.pillsLeft != 1) "s" else ""} left \u2014 not enough for a dose"
                                    isLastPill -> "Last pill \u2014 refill now"
                                    // Overdue: grace period has passed, MissedDoseWorker hasn't confirmed yet.
                                    // User can still tap TAKE to log a late dose.
                                    item.isComputedOverdue -> "Overdue ${formatDoseTimeAgo(item.time)} \u2014 tap to log as taken"
                                    else -> "Take ${medicine.dose} \u2022 ${medicine.pillsLeft} left"
                                },
                                fontSize = 13.sp, 
                                color = when {
                                    isAutoSkipped -> Color.Gray.copy(alpha = 0.8f)
                                    isOutOfStock -> Color.Red
                                    isLastPill -> AlertOrangeText
                                    item.isComputedOverdue -> Color(0xFFC2410C) // amber-700
                                    else -> TextSecondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (medicine.isStockInferred && !isAutoSkipped) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AlertOrangeBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Verify Stock", 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = AlertOrangeText,
                                    maxLines = 1
                                )
                            }
                        } else if ((isOutOfStock || isLastPill) && !isAutoSkipped) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isOutOfStock) Color.Red.copy(alpha = 0.1f) else DotOrange.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isOutOfStock) "Out of stock" else "Refill alert", 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if (isOutOfStock) Color.Red else AlertOrangeText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    if (isNextUp && !isOutOfStock) {
                        Spacer(modifier = Modifier.height(8.dp)) // Reduced spacing
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(NextUpBlueBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "Next up", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NextUpBlueText)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = if (isLastPill) AlertOrangeText else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reset Today") },
                            onClick = {
                                showMenu = false
                                showResetConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                var showEarlyTakeDialog by remember { mutableStateOf(false) }
                
                if (showEarlyTakeDialog) {
                    val now = java.time.LocalTime.now()
                    val sched = java.time.LocalTime.parse(item.time)
                    val diff = java.time.Duration.between(now, sched)
                    val hours = diff.toHours()
                    val minutes = diff.toMinutes() % 60
                    
                    AlertDialog(
                        onDismissRequest = { showEarlyTakeDialog = false },
                        title = { Text("⏳ Too early", fontWeight = FontWeight.Bold) },
                        text = { Text("It's recommended to take this dose at ${item.time}. That's in ${hours}h ${minutes}m.") },
                        confirmButton = {
                            TextButton(onClick = { 
                                onToggleTaken(medicine.id, item.time, true, DoseStatus.TAKEN_EARLY)
                                showEarlyTakeDialog = false 
                            }, colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)) {
                                Text("Take anyway")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEarlyTakeDialog = false }) { Text("OK") }
                        }
                    )
                }

                IconButton(
                    onClick = { 
                        if (!isOutOfStock && !isUpdating && !isAutoSkipped) {
                            if (isLastPill && !isTaken) {
                                showLastPillConfirm = true
                            } else if (!isTaken) {
                                // Check if early
                                val now = java.time.LocalTime.now()
                                val sched = try { java.time.LocalTime.parse(item.time) } catch(e: Exception) { now }
                                if (now.isBefore(sched)) {
                                    showEarlyTakeDialog = true
                                } else {
                                    onToggleTaken(medicine.id, item.time, true, null)
                                }
                            } else {
                                // Untaking
                                onToggleTaken(medicine.id, item.time, false, null)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isTaken -> PrimaryGreen
                                isAutoSkipped -> Color.Gray.copy(alpha = 0.05f)
                                isOutOfStock || isUpdating -> Color.Gray.copy(alpha = 0.1f)
                                else -> TakenGreenBg
                            }
                        ),
                    enabled = !isOutOfStock && !isUpdating && !isAutoSkipped
                ) {
                    Icon(
                        imageVector = when {
                            isAutoSkipped -> Icons.Default.Block
                            isOutOfStock -> Icons.Default.Close
                            else -> Icons.Default.Check
                        },
                        contentDescription = "Status icon",
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            isTaken -> Color.White
                            isAutoSkipped -> Color.Gray.copy(alpha = 0.4f)
                            isOutOfStock -> Color.Red
                            isUpdating -> Color.Gray
                            else -> PrimaryGreen
                        }
                    )
                }
            }
            
            // Bottom warning strips
            if (isOutOfStock || isLastPill) {
                Divider(color = if (isOutOfStock) Color.Red.copy(alpha = 0.2f) else DotOrange.copy(alpha = 0.2f), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOutOfStock) Color.Red.copy(alpha = 0.05f) else DotOrange.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isOutOfStock) "Cannot take — no pills available" else "Taking this uses your last pill",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOutOfStock) Color.Red else AlertOrangeText
                    )
                    Text(
                        text = if (isOutOfStock) "Find pharmacy" else "Order refill",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOutOfStock) Color.Red else AlertOrangeText,
                        modifier = Modifier.clickable { 
                            onNavigateToGenerics() 
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Refill Alert Card — tapping opens RefillBottomSheet
// ---------------------------------------------------------------------------
@Composable
fun RefillAlertCard(medicine: Medicine, onRefillClick: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRefillClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = AlertOrangeBg,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DotOrange.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Refill alert",
                    tint = DotOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Refill alert: ${medicine.name} is low",
                    fontSize = 13.sp,
                    color = AlertOrangeText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${medicine.pillsLeft} pill${if (medicine.pillsLeft != 1) "s" else ""} remaining",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertOrangeText
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DotOrange.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Refill →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertOrangeText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Refill Bottom Sheet
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillBottomSheet(
    medicine: Medicine,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var customInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf("") }

    // Quick-add chip options
    val quickOptions = listOf(30, 60, 90)

    fun resolveQuantity(): Int? {
        val v = customInput.trim().toIntOrNull()
        return when {
            customInput.isBlank() -> null
            v == null -> null
            v <= 0 -> null
            v > 999 -> null
            else -> v
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E0))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AlertOrangeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = DotOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Refill Stock",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${medicine.name}  •  ${medicine.pillsLeft} pill${if (medicine.pillsLeft != 1) "s" else ""} remaining",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Quick-add chips ───────────────────────────────────────────
            Text(
                text = "Quick add",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                quickOptions.forEach { qty ->
                    val isSelected = customInput == qty.toString()
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                customInput = qty.toString()
                                inputError = ""
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryGreen else Color(0xFFF0FDF4),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (isSelected) PrimaryGreen else Color(0xFFBBF7D0)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "+$qty",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else PrimaryGreen
                            )
                            Text(
                                text = "pills",
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else PrimaryGreen.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Custom input ──────────────────────────────────────────────
            Text(
                text = "Or enter custom amount",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customInput,
                onValueChange = { raw ->
                    // Only allow digits, max 3 chars
                    val filtered = raw.filter { it.isDigit() }.take(3)
                    customInput = filtered
                    inputError = when {
                        filtered.isEmpty() -> ""
                        filtered.toIntOrNull() == null -> "Enter a valid number"
                        filtered.toInt() <= 0 -> "Must be at least 1"
                        filtered.toInt() > 999 -> "Max 999 pills at once"
                        else -> ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Number of pills") },
                placeholder = { Text("e.g. 45") },
                singleLine = true,
                isError = inputError.isNotEmpty(),
                supportingText = {
                    if (inputError.isNotEmpty()) {
                        Text(inputError, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    focusedLabelColor = PrimaryGreen
                ),
                trailingIcon = {
                    if (customInput.isNotEmpty()) {
                        IconButton(onClick = {
                            customInput = ""
                            inputError = ""
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp),
                                tint = TextSecondary
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Confirm button ────────────────────────────────────────────
            val resolvedQty = resolveQuantity()
            val canConfirm = resolvedQty != null && inputError.isEmpty()

            Button(
                onClick = {
                    val qty = resolvedQty ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(qty)
                },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (resolvedQty != null) "Add $resolvedQty pills" else "Add pills",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = PrimaryGreen)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Fetching your medicines...", color = TextSecondary)
    }
}

@Composable
fun EmptyState(onAddMedicine: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DividerGray
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "No medicines scheduled", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Add your first medicine to stay on track", color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAddMedicine,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text(
                text = "+ Add your first medicine",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Red
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Something went wrong", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
private fun formatDoseTimeAgo(doseTime: String): String {
    return try {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
        val scheduledTime = java.time.LocalTime.parse(doseTime)
        val scheduledDateTime = now.toLocalDate().atTime(scheduledTime).atZone(now.zone)
        val diff = java.time.Duration.between(scheduledDateTime, now)
        val minutes = diff.toMinutes()
        
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> if (minutes == 1L) "1 min ago" else "$minutes mins ago"
            minutes < 1440 -> {
                val hrs = minutes / 60
                if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
            }
            else -> doseTime
        }
    } catch (e: Exception) {
        "recently"
    }
}
