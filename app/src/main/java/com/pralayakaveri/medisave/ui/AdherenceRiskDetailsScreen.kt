package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel
import com.pralayakaveri.medisave.viewmodel.TrendPeriod

@Composable
fun AdherenceRiskDetailsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) Color(0xFF2C3630) else MaterialTheme.colorScheme.outlineVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentGreen = MaterialTheme.colorScheme.primary

    // Risk colours
    val (riskColor, riskBg, riskIcon) = when (uiState.adherenceStatus) {
        "Action Required" -> Triple(Color(0xFFEF5350), if (isDark) Color(0xFF2D1E1E) else Color(0xFFFDEDED), Icons.Default.Warning)
        "Needs Attention" -> Triple(Color(0xFFF19D38), if (isDark) Color(0xFF2C221D) else Color(0xFFFFF3E0), Icons.Default.Warning)
        else -> Triple(accentGreen, if (isDark) Color(0xFF1B221E) else Color(0xFFE8F5E9), Icons.Default.Shield)
    }

    // Build dynamic recommendations
    val recommendations = buildList {
        if (uiState.recentMissedCount > 5) {
            add("⚠ You've missed ${uiState.recentMissedCount} doses recently. Try setting a louder alarm.")
        }
        if (uiState.eveningMissPercent > 0) {
            add("🌙 Evening doses are missed ${uiState.eveningMissPercent}% more often — consider moving the reminder 30 min earlier.")
        }
        if (uiState.globalStreak < 3) {
            add("🔥 Your current streak is only ${uiState.globalStreak} day(s). Try to take medications at the same time each day.")
        }
        val lowStock = uiState.medicines.filter { it.pillsLeft > 0 && it.pillsLeft <= it.refillAt }
        if (lowStock.isNotEmpty()) {
            add("💊 ${lowStock.first().name} is running low. Refill soon to avoid missed doses.")
        }
        if (uiState.weeklyAdherence < 70) {
            add("📅 Your adherence is below 70%. Consider using pill organizers to make doses more visible.")
        }
        if (isEmpty()) {
            add("✅ You're doing well! Keep taking your medications on time.")
        }
    }

    // Recent missed logs
    val recentMissed = uiState.historyActivities.filter { it.status != "Taken" }.take(5)

    // Period state for the embedded trend chart
    var selectedPeriod by remember { mutableStateOf(TrendPeriod.THIS_WEEK) }
    val activeStats = when (selectedPeriod) {
        TrendPeriod.THIS_WEEK -> uiState.thisWeekStats
        TrendPeriod.LAST_WEEK -> uiState.lastWeekStats
        TrendPeriod.LAST_30_DAYS -> uiState.last30DaysStats
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Adherence Risk",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
        }

        // ── Status Summary Card ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(riskBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = riskIcon,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = uiState.adherenceStatus,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor
                        )
                        Text(
                            text = uiState.adherenceExplanation,
                            fontSize = 13.sp,
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = borderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(modifier = Modifier.fillMaxWidth()) {
                    RiskStatChip(
                        label = "Adherence Rate",
                        value = "${uiState.weeklyAdherence}%",
                        accent = accentGreen,
                        modifier = Modifier.weight(1f)
                    )
                    RiskStatChip(
                        label = "Missed (7 days)",
                        value = "${uiState.recentMissedCount}",
                        accent = if (uiState.recentMissedCount > 3) Color(0xFFEF5350) else textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    RiskStatChip(
                        label = "Streak",
                        value = "${uiState.globalStreak}d",
                        accent = accentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Weekly Trend Chart ───────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Adherence Trend",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyTrendChart(
                    dailyResults = activeStats.dailyResults,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it }
                )
            }
        }

        // ── Recent Missed / Overdue Doses ────────────────────────────────────
        if (recentMissed.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Recent Missed Doses",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    recentMissed.forEachIndexed { index, log ->
                        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.status == "Overdue") Color(0xFF2C221D) else Color(0xFF2D1E1E)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.status == "Overdue") Icons.Default.Schedule else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (log.status == "Overdue") Color(0xFFF19D38) else Color(0xFFEF5350),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.medicineName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                                Text(
                                    text = "${log.dateLabel} • ${log.time}",
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (log.status == "Overdue") Color(0xFF2C221D) else Color(0xFF2D1E1E)
                            ) {
                                Text(
                                    text = log.status,
                                    fontSize = 11.sp,
                                    color = if (log.status == "Overdue") Color(0xFFF19D38) else Color(0xFFEF5350),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Personalized Recommendations ────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Recommendations",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                recommendations.forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Spacer(modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentGreen)
                            .padding(top = 7.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = rec,
                            fontSize = 13.sp,
                            color = textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RiskStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
