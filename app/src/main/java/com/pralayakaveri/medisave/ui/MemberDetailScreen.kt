package com.pralayakaveri.medisave.ui

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.FamilyConnectionRepository
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.model.*
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import com.pralayakaveri.medisave.model.MemberType
import java.text.SimpleDateFormat
import java.util.*

data class TimelineItem(
    val medicineName: String,
    val time: String,
    val status: String,
    val medId: String,
    val caregiverAlertEnabled: Boolean = true,
    val pillsLeft: Int = Int.MAX_VALUE,
    val doseQuantity: Int = 1
)

data class DailyAdherence(
    val label: String,
    val dateKey: String,
    val percentage: Float,
    val color: Color,
    val taken: Int = 0,
    val total: Int = 0
)

sealed class MemberDetailUiState {
    object Loading : MemberDetailUiState()
    data class Success(
        val name: String,
        val relation: String,
        val type: MemberType,
        val adherence: Int,
        val adherenceLabel: String = "",
        val todayDoses: Int,
        val missedWk: Int,
        val lastMissedDose: TimelineItem? = null,
        val isLive: Boolean = false,
        val isStartingToday: Boolean = false,
        val lastSyncedText: String = "",
        val lastActiveAt: com.google.firebase.Timestamp? = null,
        val todayTimeline: List<TimelineItem>,
        val weeklyAdherence: List<DailyAdherence> = emptyList(),
        val connectionId: String = "",
        val phone: String = "",
        val isGlobalAlertsEnabled: Boolean = true,
        // Phase 3: Stock visibility for caregivers
        val lowStockMedicines: List<com.pralayakaveri.medisave.model.Medicine> = emptyList(),
        val hasStockAlert: Boolean = false
    ) : MemberDetailUiState()
    data class Error(val message: String) : MemberDetailUiState()
}

class MemberDetailViewModel(
    application: Application,
    val memberId: String,
    val memberName: String,
    val connectionId: String
) : AndroidViewModel(application) {
    private val localDb = AppDatabase.getDatabase(application)
    private val connectionRepo = FamilyConnectionRepository()
    private val medRepo = MedicineRepository(application)
    private val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(application)
    
    private val _uiState = MutableStateFlow<MemberDetailUiState>(MemberDetailUiState.Loading)
    val uiState: StateFlow<MemberDetailUiState> = _uiState.asStateFlow()

    private fun getAuthenticatedUserFlow(): Flow<String> = callbackFlow {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                trySend(uid)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    init {
        loadMemberData()
    }

    private fun loadMemberData() {
        viewModelScope.launch {
            getAuthenticatedUserFlow().collect { currentUid ->
                if (memberId == currentUid) {
                    // PRIMARY USER CASE
                    combine(
                        localDb.medicineReminderDao().observeAllReminders(),
                        prefManager.familyAlertsEnabled
                    ) { allMedsRaw, globalAlerts ->
                        val allMeds = allMedsRaw.distinctBy { it.id }
                        val memberMeds = allMeds.filter { it.profileId == memberId }.map { it.toMedicine() }
                        
                        val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
                        val anchorTime = java.time.ZonedDateTime.now(forcedZone)
                        
                        val report = AdherenceCalculator.calculateReport(
                            medicines = memberMeds,
                            anchorTime = anchorTime
                        )
                        
                        val timeline = buildProactiveTimeline(memberMeds, emptyMap(), anchorTime)

                        // Phase 3: identify low / out-of-stock medicines for self view
                        val lowStock = memberMeds.filter { it.pillsLeft <= it.refillAt }

                        MemberDetailUiState.Success(
                            name = "You",
                            relation = "Self",
                            type = MemberType.PRIMARY,
                            adherence = report.adherencePercentage ?: 0,
                            adherenceLabel = "",
                            todayDoses = report.todayStats.total,
                            missedWk = report.dueSoFarStats.total - report.dueSoFarStats.taken,
                            lastMissedDose = null,
                            isLive = true,
                            lastSyncedText = "Just now",
                            todayTimeline = timeline,
                            weeklyAdherence = mapWeeklyStats(report),
                            connectionId = connectionId,
                            isGlobalAlertsEnabled = globalAlerts,
                            lowStockMedicines = lowStock,
                            hasStockAlert = lowStock.isNotEmpty()
                        )
                    }.collect { _uiState.value = it }
                } else {
                    // REMOTE CASE - Mirroring Home Logic via Firestore
                    val sevenDaysAgo = getDaysAgoUTC(6)

                    combine(
                        connectionRepo.listenToMemberMedicines(memberId).catch { emit(emptyList()) },
                        connectionRepo.listenToWeeklyLogs(memberId, sevenDaysAgo).catch { emit(emptyList()) },
                        connectionRepo.listenToMemberProfile(memberId).catch { emit(null) },
                        connectionRepo.observeAcceptedConnections(currentUid).catch { emit(emptyList()) },
                        prefManager.familyAlertsEnabled
                    ) { medicinesRaw, logs, profile, connections, globalAlerts ->
                        val medicines = medicinesRaw.distinctBy { it.id }
                        val connection = connections.find { it.senderId == memberId || it.receiverId == memberId }
                        val lastActiveAt = profile?.get("lastActiveAt") as? com.google.firebase.Timestamp
                        
                        val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
                        val anchorTime = java.time.ZonedDateTime.now(forcedZone)

                        val logsMap = logs.associate { log ->
                            val medId = log["medicineId"] as? String ?: ""
                            val date = log["date"] as? String ?: ""
                            val time = log["time"] as? String ?: ""
                            "${medId}_${date}_$time" to (log["status"] as? String ?: "PENDING")
                        }

                        val report = AdherenceCalculator.calculateReport(
                            medicines = medicines,
                            externalLogs = logsMap,
                            anchorTime = anchorTime
                        )

                        val rawTimeline = buildProactiveTimeline(medicines, logsMap, anchorTime)
                        val timeline = rawTimeline.map { 
                            it.copy(status = if (it.status == "MISSED") "Missed ${formatDoseTimeAgo(it.time, anchorTime)}" else it.status)
                        }
                        
                        val lastMissedItem = timeline.filter { it.status.startsWith("Missed") }.lastOrNull()

                        // Phase 3: identify low / out-of-stock medicines for caregiver view
                        val lowStock = medicines.filter { it.pillsLeft <= it.refillAt }
                        
                        MemberDetailUiState.Success(
                            name = memberName,
                            relation = connection?.relation ?: "Family Member",
                            type = MemberType.CONNECTED,
                            adherence = report.adherencePercentage ?: 0,
                            adherenceLabel = "", 
                            todayDoses = report.todayStats.total,
                            missedWk = report.dueSoFarStats.total - report.dueSoFarStats.taken,
                            lastMissedDose = lastMissedItem,
                            isLive = isMemberLive(lastActiveAt),
                            lastActiveAt = lastActiveAt,
                            lastSyncedText = formatTimeAgo(lastActiveAt),
                            todayTimeline = timeline,
                            weeklyAdherence = mapWeeklyStats(report),
                            connectionId = connectionId,
                            phone = profile?.get("phone") as? String ?: "",
                            isGlobalAlertsEnabled = globalAlerts,
                            lowStockMedicines = lowStock,
                            hasStockAlert = lowStock.isNotEmpty()
                        )
                    }.catch { e ->
                        val msg = when (e) {
                            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                                when (e.code) {
                                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                        "Access denied. You do not have permission to view this family member's data."
                                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                        "Network connection is unavailable. Please check your settings."
                                    else -> "Database synchronization failed. Please try again later."
                                }
                            }
                            else -> "Failed to sync remote family data. Please check your connection."
                        }
                        _uiState.value = MemberDetailUiState.Error(msg)
                    }.collect { _uiState.value = it }
                }
            }
        }
    }

    fun refreshRealtimeLabels() {
        val current = _uiState.value
        if (current is MemberDetailUiState.Success && current.type == MemberType.CONNECTED) {
            val lastActive = current.lastActiveAt ?: return
            val newIsLive = isMemberLive(lastActive)
            val newTimeText = formatTimeAgo(lastActive)
            
            if (newIsLive != current.isLive || newTimeText != current.lastSyncedText) {
                _uiState.value = current.copy(
                    isLive = newIsLive,
                    lastSyncedText = newTimeText
                )
            }
        }
    }

    private fun isMemberLive(lastActive: com.google.firebase.Timestamp?): Boolean {
        if (lastActive == null) return false
        val now = System.currentTimeMillis()
        val last = lastActive.toDate().time
        return (now - last) < 120_000 // 2 Minutes
    }

    private fun formatTimeAgo(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return "Never synced"
        val time = timestamp.toDate().time
        val now = System.currentTimeMillis()
        val diff = now - time
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> {
                val mins = diff / 60_000
                if (mins == 1L) "1 minute ago" else "$mins minutes ago"
            }
            diff < 86400_000 -> {
                val hrs = diff / 3600_000
                if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
            }
            else -> {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                val dateStr = sdf.format(timestamp.toDate())
                val cal = Calendar.getInstance()
                val lastCal = Calendar.getInstance().apply { timeInMillis = time }
                
                if (cal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)) {
                    "Today, $dateStr"
                } else {
                    val daySdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    daySdf.format(timestamp.toDate())
                }
            }
        }
    }

    private fun formatDoseTimeAgo(doseTime: String, anchorTime: java.time.ZonedDateTime): String {
        return try {
            val scheduledTime = java.time.LocalTime.parse(doseTime)
            val scheduledDateTime = anchorTime.toLocalDate().atTime(scheduledTime).atZone(anchorTime.zone)
            val diff = java.time.Duration.between(scheduledDateTime, anchorTime)
            val minutes = diff.toMinutes()
            
            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> if (minutes == 1L) "1 min ago" else "$minutes mins ago"
                minutes < 1440 -> {
                    val hrs = minutes / 60
                    if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
                }
                else -> doseTime // Fallback to absolute time for old doses
            }
        } catch (e: Exception) {
            "Missed"
        }
    }

    private fun buildProactiveTimeline(
        medicines: List<Medicine>,
        logs: Map<String, String>,
        anchorTime: java.time.ZonedDateTime
    ): List<TimelineItem> {
        val todayStr = anchorTime.toLocalDate().toString()

        return medicines.flatMap { med ->
            med.times.mapNotNull { time ->
                if (ScheduleUtils.isDoseValid(med, todayStr, time)) {
                    val statusKey = med.constructStatusKey(todayStr, time)
                    val logStatus = logs["${med.id}_$statusKey"] ?: med.statusMap[statusKey]
                    
                    val finalStatus = when {
                        logStatus != null -> logStatus
                        else -> {
                            val t = java.time.LocalTime.parse(time)
                            if (t.isAfter(anchorTime.toLocalTime())) "LATER"
                            else "PENDING"
                        }
                    }

                    TimelineItem(
                        medicineName = med.name,
                        time = time,
                        status = finalStatus,
                        medId = med.id,
                        caregiverAlertEnabled = med.caregiverAlertEnabled,
                        pillsLeft = med.pillsLeft,
                        doseQuantity = med.doseQuantity
                    )
                } else null
            }
        }.sortedBy { it.time }
    }

    private fun mapWeeklyStats(report: AdherenceReport): List<DailyAdherence> {
        val labels = listOf("M", "T", "W", "T", "F", "S", "S")
        return report.dailyResults.map { day ->
            val dayOfWeek = (day.date.dayOfWeek.value - 1) % 7
            DailyAdherence(
                label = labels[dayOfWeek],
                dateKey = day.date.toString(),
                percentage = day.percentage.toFloat() / 100f,
                taken = day.taken,
                total = day.total,
                color = when (day.status) {
                    AdherenceDayStatus.TAKEN -> PrimaryGreen
                    AdherenceDayStatus.EMPTY -> Color.LightGray.copy(alpha = 0.3f)
                    AdherenceDayStatus.MISSED -> Color(0xFFB73B3B) // RED
                    AdherenceDayStatus.BLUE -> Color(0xFF3B82F6)   // BLUE
                    else -> AmberWarning                           // AMBER
                }
            )
        }
    }

    fun revokeConnection(targetUserId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                connectionRepo.removeConnection(currentUserId, targetUserId)
            } catch (e: Exception) {
                android.util.Log.e("MemberDetailViewModel", "Failed to revoke connection: ${e.message}", e)
            } finally {
                onDone()
            }
        }
    }

    private fun getDaysAgoUTC(days: Int): com.google.firebase.Timestamp {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -days)
        return com.google.firebase.Timestamp(cal.time)
    }

    fun updateLabel(newLabel: String) {
        viewModelScope.launch {
            try {
                connectionRepo.updateConnectionLabel(memberId, newLabel)
            } catch (e: Exception) {
                // Error handled by repository
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: String,
    memberName: String,
    connectionId: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val viewModel: MemberDetailViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MemberDetailViewModel(
                    application = context,
                    memberId = memberId,
                    memberName = memberName,
                    connectionId = connectionId
                ) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(memberId) {
        viewModel.refreshRealtimeLabels()
        val delayToNextMinute = 60_000 - (System.currentTimeMillis() % 60_000)
        kotlinx.coroutines.delay(delayToNextMinute)
        while (isActive) {
            viewModel.refreshRealtimeLabels()
            kotlinx.coroutines.delay(60_000)
        }
    }

    var showEditLabel by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState is MemberDetailUiState.Success) {
                val state = uiState as MemberDetailUiState.Success
                if (state.type == MemberType.CONNECTED) {
                    DisconnectButtonSection(
                        isProcessing = isProcessing,
                        onDisconnect = {
                            if (!isProcessing) {
                                isProcessing = true
                                viewModel.revokeConnection(memberId) {
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Family member disconnected successfully",
                                            duration = SnackbarDuration.Short
                                        )
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (result == SnackbarResult.Dismissed || result == SnackbarResult.ActionPerformed) {
                                            onBack()
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))
        ) {
            when (val state = uiState) {
                is MemberDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = PrimaryGreen)
                is MemberDetailUiState.Error -> Text(state.message, Modifier.align(Alignment.Center), color = Color.Red)
                is MemberDetailUiState.Success -> {
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    var highlightedMedId by remember { mutableStateOf<String?>(null) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        HeaderWithStats(
                            state = state, 
                            onBack = onBack,
                            onEditRelation = { showEditLabel = true }
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = paddingValues.calculateBottomPadding() + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.isStartingToday) {
                                item { StartingTodayBanner() }
                            }
                            if (!state.isGlobalAlertsEnabled) {
                                item { GlobalAlertsWarning() }
                            }
                            // Phase 3: Stock alert banner — shown when any medicine is low or empty
                            if (state.hasStockAlert) {
                                item {
                                    StockAlertBanner(
                                        medicines = state.lowStockMedicines,
                                        memberName = state.name
                                    )
                                }
                            }
                            state.lastMissedDose?.let { missed ->
                                item { 
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn() + androidx.compose.animation.slideInVertically()
                                    ) {
                                        val missedTodayCount = state.todayTimeline.count { it.status == "MISSED" }
                                        MissedAlertBox(
                                            missed = missed,
                                            name = state.name,
                                            relation = state.relation,
                                            phone = state.phone,
                                            missedCount = missedTodayCount,
                                            onCheckSchedule = {
                                                coroutineScope.launch {
                                                    highlightedMedId = missed.medId
                                                    // Compute target index dynamically based on visible banners
                                                    val targetIndex = (if (state.isStartingToday) 1 else 0) + 
                                                                      (if (!state.isGlobalAlertsEnabled) 1 else 0) + 
                                                                      1
                                                    
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    listState.animateScrollToItem(targetIndex)
                                                    kotlinx.coroutines.delay(2500)
                                                    highlightedMedId = null
                                                }
                                            },
                                            onShowSnackbar = { message ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            }
                                        ) 
                                    }
                                }
                            }
                            item { ScheduleHeader() }
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                                ) {
                                    Column {
                                        state.todayTimeline.forEachIndexed { index, item ->
                                            MedicineScheduleItem(
                                                item = item,
                                                showDivider = index < state.todayTimeline.size - 1,
                                                isHighlighted = item.medId == highlightedMedId
                                            )
                                        }
                                    }
                                }
                            }
                            item { AdherenceChartSection(state.weeklyAdherence) }
                        }
                    }
                }
            }
        }
    }

    if (showEditLabel && uiState is MemberDetailUiState.Success) {
        val state = uiState as MemberDetailUiState.Success
        EditLabelBottomSheet(
            currentLabel = state.relation,
            onDismiss = { showEditLabel = false },
            onConfirm = { newLabel ->
                viewModel.updateLabel(newLabel)
                showEditLabel = false
                scope.launch {
                    snackbarHostState.showSnackbar("Relationship updated")
                }
            }
        )
    }
}

@Composable
fun HeaderWithStats(
    state: MemberDetailUiState.Success, 
    onBack: () -> Unit,
    onEditRelation: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(PrimaryGreen).statusBarsPadding().padding(bottom = 32.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val syncedText by rememberUpdatedState(state.lastSyncedText)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = state.type != MemberType.PRIMARY) { onEditRelation() }
                    ) {
                        Text(
                            text = "${state.relation} • Last synced $syncedText",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                        if (state.type != MemberType.PRIMARY) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit label",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = state.isLive) { LiveBadge() }
            }
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(title = "Adherence", value = state.adherenceLabel.ifEmpty { "${state.adherence}%" }, modifier = Modifier.weight(1.3f))
                StatCard(title = "Today's doses", value = state.todayDoses.toString(), modifier = Modifier.weight(1f))
                StatCard(title = "Missed today", value = state.missedWk.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(85.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun LiveBadge() {
    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Live", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MissedAlertBox(
    missed: TimelineItem, 
    name: String, 
    relation: String, 
    phone: String, 
    missedCount: Int = 1,
    onCheckSchedule: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEE2E2))
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Color(0xFFFEE2E2)
                ) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$name ($relation)",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color(0xFF991B1B),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (missedCount > 1) "Missed $missedCount doses today" else "Missed ${missed.time} dose",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF7F1D1D)
                    )
                    Text(
                        text = "Needs attention",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626).copy(alpha = 0.8f)
                    )
                }
                Text(missed.status, fontSize = 12.sp, color = Color(0xFFDC2626).copy(alpha = 0.8f))
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "${missed.medicineName} was not taken. Please check if they are okay.",
                fontSize = 14.sp,
                color = Color(0xFF991B1B).copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
            
            Spacer(Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCheckSchedule,
                    modifier = Modifier.weight(1.1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check Schedule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                OutlinedButton(
                    onClick = { 
                        if (phone.isNotEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:$phone")
                            }
                            context.startActivity(intent)
                        } else {
                            onShowSnackbar("Phone number not available for this family member.")
                        }
                    },
                    modifier = Modifier.weight(0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp), tint = PrimaryGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Call Now", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ScheduleHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("Today's schedule", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
fun MedicineScheduleItem(item: TimelineItem, showDivider: Boolean, isHighlighted: Boolean = false) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFFE8F5E9) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
        label = "highlight_fade"
    )

    // Real-time stock flags (mirrors Phase 2 HomeScreen logic)
    val isOutOfStock = item.pillsLeft < item.doseQuantity
    val isLastPill = item.pillsLeft == item.doseQuantity
    
    Column(modifier = Modifier.fillMaxWidth().background(backgroundColor)) {
        val pillColors = when {
            item.status == "TAKEN" -> Pair(Color(0xFFD1FAE5), Color(0xFF065F46))
            isOutOfStock && item.status != "TAKEN" -> Pair(Color(0xFFFEE2E2), Color(0xFF991B1B))
            isLastPill && item.status != "TAKEN" -> Pair(Color(0xFFFFF7ED), Color(0xFF9A3412))
            item.status == "PENDING" -> Pair(Color(0xFFFFEDD5), Color(0xFF9A3412))
            else -> Pair(Color(0xFFF3F4F6), Color(0xFF374151))
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.medicineName,
                            fontWeight = FontWeight.Bold,
                            color = if (isOutOfStock && item.status != "TAKEN") Color(0xFF991B1B) else TextPrimary
                        )
                        if (!item.caregiverAlertEnabled) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = "Alerts Off",
                                modifier = Modifier.size(14.dp),
                                tint = TextSecondary.copy(alpha = 0.5f)
                            )
                        }
                        // Stock badge: only shown for non-taken doses
                        if (item.status != "TAKEN" && (isOutOfStock || isLastPill)) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOutOfStock) Color(0xFFFEE2E2) else Color(0xFFFFF7ED)
                            ) {
                                Text(
                                    text = if (isOutOfStock) "Out of stock" else "Last pill",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutOfStock) Color(0xFFDC2626) else Color(0xFFC2410C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = item.time,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    // Stock sub-label for out-of-stock non-taken doses
                    if (isOutOfStock && item.status != "TAKEN") {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (item.pillsLeft == 0) "No pills left" else "${item.pillsLeft} pill${if (item.pillsLeft != 1) "s" else ""} left \u2014 not enough",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626).copy(alpha = 0.8f)
                        )
                    }
                }
                Surface(color = pillColors.first, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = item.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = pillColors.second,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = DividerGray)
        }
    }
}

@Composable
fun AdherenceChartSection(data: List<DailyAdherence>) {
    val todayStr = java.time.LocalDate.now().toString()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Text("7-day adherence", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { dayData ->
                val isToday = dayData.dateKey == todayStr
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).height(120.dp) // Fixed height for the column
                ) {
                    // Bar Container (Takes remaining space)
                    Box(
                        modifier = Modifier
                            .weight(1f) // Take available space above label
                            .width(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(dayData.percentage.coerceIn(0.1f, 1f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(dayData.color)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // The Day Label
                    Text(
                        text = dayData.label,
                        fontSize = 12.sp,
                        color = if (isToday) PrimaryGreen else TextSecondary,
                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier.height(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isToday) {
                            Box(Modifier.size(4.dp).clip(CircleShape).background(PrimaryGreen))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisconnectButtonSection(isProcessing: Boolean, onDisconnect: () -> Unit) {
    Button(
        onClick = onDisconnect,
        enabled = !isProcessing,
        modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Disconnect Family Member", color = Color.Red, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StartingTodayBanner() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFE8F5E9)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = PrimaryGreen)
            Spacer(Modifier.width(12.dp))
            Text("Tracking starts tomorrow.", color = PrimaryGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GlobalAlertsWarning() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF7ED),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsPaused, null, tint = Color(0xFFC2410C), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Family alerts are silenced",
                    color = Color(0xFFC2410C),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "You won't receive push notifications for any family members. Tap to manage settings.",
                    color = Color(0xFFC2410C).copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFC2410C).copy(alpha = 0.3f))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLabelBottomSheet(
    currentLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val presets = listOf("Mom", "Dad", "Spouse", "Child", "Sibling", "Caregiver")
    
    // Determine initial state: is the current label a preset?
    val isInitialPreset = currentLabel in presets
    var selectedPreset by remember { mutableStateOf(if (isInitialPreset) currentLabel else "Custom...") }
    var customText by remember { mutableStateOf(if (!isInitialPreset) currentLabel else "") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "Edit relationship label",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (presets + "Custom...").forEach { relation ->
                    FilterChip(
                        selected = selectedPreset == relation,
                        onClick = { selectedPreset = relation },
                        label = { Text(relation) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryGreen.copy(alpha = 0.1f),
                            selectedLabelColor = PrimaryGreen
                        )
                    )
                }
            }
            
            if (selectedPreset == "Custom...") {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { if (it.length <= 30) customText = it },
                    label = { Text("Custom Relationship") },
                    placeholder = { Text("e.g. Grandma, Uncle, Nana") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        focusedLabelColor = PrimaryGreen
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val finalValue = if (selectedPreset == "Custom...") customText.trim() else selectedPreset
            val isChanged = finalValue != currentLabel
            val isValid = finalValue.isNotBlank()
            
            Button(
                onClick = { onConfirm(finalValue) },
                enabled = isChanged && isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stock Alert Banner — caregiver view of member's low/out-of-stock medicines
// ---------------------------------------------------------------------------
@Composable
fun StockAlertBanner(
    medicines: List<com.pralayakaveri.medisave.model.Medicine>,
    memberName: String
) {
    val outOfStock = medicines.filter { it.pillsLeft < it.doseQuantity }
    val lowStock   = medicines.filter { it.pillsLeft >= it.doseQuantity && it.pillsLeft <= it.refillAt }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF7ED),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFEDD5)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFC2410C),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Stock Alert",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF92400E)
                    )
                    val desc = when {
                        outOfStock.isNotEmpty() && lowStock.isNotEmpty() ->
                            "${outOfStock.size} out of stock · ${lowStock.size} running low"
                        outOfStock.isNotEmpty() ->
                            "${outOfStock.size} medicine${if (outOfStock.size != 1) "s" else ""} out of stock"
                        else ->
                            "${lowStock.size} medicine${if (lowStock.size != 1) "s" else ""} running low"
                    }
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = Color(0xFFC2410C).copy(alpha = 0.85f)
                    )
                }
            }

            if (outOfStock.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "OUT OF STOCK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF991B1B),
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                outOfStock.forEach { med ->
                    StockMedicineRow(
                        name = med.name,
                        pillsLeft = med.pillsLeft,
                        doseQuantity = med.doseQuantity,
                        isOutOfStock = true
                    )
                }
            }

            if (lowStock.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "RUNNING LOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFC2410C),
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                lowStock.forEach { med ->
                    StockMedicineRow(
                        name = med.name,
                        pillsLeft = med.pillsLeft,
                        doseQuantity = med.doseQuantity,
                        isOutOfStock = false
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Please remind $memberName to refill their medication.",
                fontSize = 12.sp,
                color = Color(0xFF92400E).copy(alpha = 0.75f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StockMedicineRow(
    name: String,
    pillsLeft: Int,
    doseQuantity: Int,
    isOutOfStock: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isOutOfStock) Color(0xFFDC2626) else Color(0xFFC2410C))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF78350F),
                maxLines = 1
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isOutOfStock) Color(0xFFFEE2E2) else Color(0xFFFFF7ED)
        ) {
            Text(
                text = if (pillsLeft == 0) "0 pills" else "$pillsLeft pill${if (pillsLeft != 1) "s" else ""} left",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOutOfStock) Color(0xFF991B1B) else Color(0xFF9A3412),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
