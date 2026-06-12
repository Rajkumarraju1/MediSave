package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceDetailsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onViewAllActivity: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    
    var selectedPeriod by remember { mutableStateOf(com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK) }
    val activeStats = when (selectedPeriod) {
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> uiState.thisWeekStats
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> uiState.lastWeekStats
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> uiState.last30DaysStats
    }

    // Dynamic color tokens mapping strictly to existing MediSave tokens
    val backgroundColor = if (isDark) Color(0xFF0B0F0C) else Color(0xFFF7F9FA)
    val cardBgColor = if (isDark) Color(0xFF121815) else Color(0xFFFFFFFF)
    val outlineColor = if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.7f) else Color(0xFF8E8E93)
    val accentColor = if (isDark) Color(0xFF22C55E) else Color(0xFF1D9E75)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Performance Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 0. Premium Metrics Summary Banner Card
            SummaryBannerCard(
                weeklyAdherence = uiState.thisWeekStats.adherencePercentage,
                totalTaken = uiState.thisWeekStats.takenCount,
                overdueCount = uiState.thisWeekStats.overdueCount,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. ADHERENCE SCORE CARD
            AdherenceScoreCard(
                weeklyAdherence = activeStats.adherencePercentage,
                selectedPeriod = selectedPeriod,
                isDark = isDark,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. STREAKS ROW
            StreakRow(
                globalStreak = uiState.globalStreak,
                longestStreak = uiState.longestStreak,
                isDark = isDark,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. RECENT ACTIVITY TIMELINE
            RecentActivityTimeline(
                recentActivities = uiState.recentActivities,
                onViewAllActivity = onViewAllActivity,
                isDark = isDark,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. WEEKLY TREND CHART (Reused directly, removing outer card wrappers)
            WeeklyTrendChart(
                dailyResults = activeStats.dailyResults,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. MONTHLY SUMMARY REPORT CARD
            MonthlySummaryCard(
                monthlyAdherence = if (uiState.monthlyTotal > 0) ((uiState.monthlyTaken.toFloat() / uiState.monthlyTotal) * 100).toInt() else 0,
                monthlyTaken = uiState.monthlyTaken,
                monthlyOverdue = uiState.monthlyOverdueCount,
                monthlyBestStreak = uiState.monthlyBestStreak,
                hasData = uiState.monthlyTotal > 0,
                isDark = isDark,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. PERSONAL INSIGHTS & CLINICAL RECOMMENDATIONS
            PersonalInsightsCard(
                insights = uiState.insights,
                isDark = isDark,
                cardBgColor = cardBgColor,
                outlineColor = outlineColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )
        }
    }
}

@Composable
fun SummaryBannerCard(
    weeklyAdherence: Int,
    totalTaken: Int,
    overdueCount: Int,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Text(
                text = "This Week",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📈", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$weeklyAdherence%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Adherence",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(outlineColor)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalTaken",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Taken",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(outlineColor)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$overdueCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Overdue",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AdherenceScoreCard(
    weeklyAdherence: Int,
    selectedPeriod: com.pralayakaveri.medisave.viewmodel.TrendPeriod,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                CircularProgressIndicator(
                    progress = { weeklyAdherence / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                    color = accentColor,
                    trackColor = if (isDark) Color(0xFF2C3630) else Color(0x201D9E75)
                )
                Text(
                    text = "$weeklyAdherence%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                val title = when (selectedPeriod) {
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> "Weekly Success Rate"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> "Weekly Success Rate"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> "30-Day Success Rate"
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val complianceText = when (selectedPeriod) {
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> when {
                        weeklyAdherence >= 80 -> "Excellent consistency over the last 30 days."
                        weeklyAdherence >= 60 -> "You're doing well. Keep the momentum going."
                        weeklyAdherence >= 40 -> "A few doses were missed in the last 30 days. Stay consistent."
                        else -> "Focus on your doses to rebuild your routine."
                    }
                    else -> when {
                        weeklyAdherence >= 80 -> "Excellent consistency this week."
                        weeklyAdherence >= 60 -> "You're doing well. Keep the momentum going."
                        weeklyAdherence >= 40 -> "A few doses were missed this week. Stay consistent."
                        else -> "Focus on today's doses to rebuild your routine."
                    }
                }
                Text(
                    text = complianceText,
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
        }
    }
}

@Composable
fun StreakRow(
    globalStreak: Int,
    longestStreak: Int,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StreakCard(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            value = "$globalStreak Days",
            line1 = "Current",
            line2 = "Streak",
            icon = Icons.Default.Whatshot,
            iconColor = Color(0xFFFF5722),
            iconBg = if (isDark) Color(0xFF2C221D) else Color(0xFFFFF6ED),
            cardBgColor = cardBgColor,
            outlineColor = outlineColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        StreakCard(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            value = "$longestStreak Days",
            line1 = "Best",
            line2 = "Streak",
            icon = Icons.Default.Star,
            iconColor = accentColor,
            iconBg = if (isDark) Color(0xFF1B221E) else Color(0xFFE2F4EE),
            cardBgColor = cardBgColor,
            outlineColor = outlineColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )
    }
}

@Composable
fun StreakCard(
    modifier: Modifier,
    value: String,
    line1: String,
    line2: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line1,
                fontSize = 11.sp,
                color = textSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = line2,
                fontSize = 11.sp,
                color = textSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecentActivityTimeline(
    recentActivities: List<com.pralayakaveri.medisave.viewmodel.ActivityLog>,
    onViewAllActivity: () -> Unit,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Recent Activity Log",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (recentActivities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent activity logged.",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val showCount = minOf(recentActivities.size, 5)
                Column {
                    for (index in 0 until showCount) {
                        val activity = recentActivities[index]
                        val (statusText, statusColor, statusIcon) = when (activity.status) {
                            "Taken" -> Triple("Taken", accentColor, Icons.Default.CheckCircle)
                            "Overdue" -> Triple("Overdue", Color(0xFFF19D38), Icons.Default.Warning)
                            else -> Triple("Missed", Color(0xFFEF5350), Icons.Default.Cancel)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (index < showCount - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(68.dp)
                                            .background(outlineColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activity.dateLabel} • ${activity.time}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Medicine Chip (Standard Styled)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) Color(0xFF1B221E) else Color(0xFFE2F4EE))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = activity.medicineName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White else Color(0xFF1D9E75)
                                        )
                                    }

                                    // Status Badge
                                    val statusSymbol = when (activity.status) {
                                        "Taken" -> "✓"
                                        "Overdue" -> "⚠"
                                        else -> "✕"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$statusSymbol $statusText",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onViewAllActivity,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "View All Activity →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(
    monthlyAdherence: Int,
    monthlyTaken: Int,
    monthlyOverdue: Int,
    monthlyBestStreak: Int,
    hasData: Boolean,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Monthly Review (Last 30 Days)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No scheduled doses in the last 30 days.",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$monthlyAdherence%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor
                        )
                        Text(
                            text = "Monthly Adherence",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(60.dp)
                            .background(outlineColor)
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Taken: $monthlyTaken doses",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                        Text(
                            text = "Overdue: $monthlyOverdue doses",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (monthlyOverdue > 0) Color(0xFFEF5350) else textPrimary
                        )
                        Text(
                            text = "Best Streak: $monthlyBestStreak Days",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalInsightsCard(
    insights: List<String>,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Personal Insights & Tips",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (insights.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Not enough adherence data to show personalized recommendations yet.",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    insights.forEach { insight ->
                        val isRecommendation = insight.startsWith("Recommendation:")

                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isRecommendation) Icons.Default.Info else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isRecommendation) Color(0xFFF19D38) else accentColor,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = insight,
                                fontSize = 13.sp,
                                fontWeight = if (isRecommendation) FontWeight.Bold else FontWeight.Normal,
                                color = if (isRecommendation) (if (isDark) Color(0xFFFFF6ED) else Color(0xFF915900)) else textPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
