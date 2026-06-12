package com.pralayakaveri.medisave.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel
import com.pralayakaveri.medisave.viewmodel.DashboardUiState
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onViewDetails: () -> Unit = {},
    onNavigateToHistory: (String) -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToRiskDetails: () -> Unit = {},
    onNavigateToRefillStatus: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK) }

    val activeStats = when (selectedPeriod) {
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> uiState.thisWeekStats
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> uiState.lastWeekStats
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> uiState.last30DaysStats
    }
    
    if (uiState.isEmpty) {
        EmptyDashboardState()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insights",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else BrandingGreen
                )
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = if (isDark) Color.White.copy(alpha = 0.85f) else BrandingGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = {
                        Text(
                            text = "Understanding Your Metrics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isDark) Color.White else BrandingGreen
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoItem(
                                title = "Adherence Rate (Success Rate)",
                                description = "The percentage of scheduled doses you logged as 'Taken' during the selected period. Future doses are excluded.",
                                isDark = isDark
                            )
                            InfoItem(
                                title = "Current Streak",
                                description = "The number of consecutive days you have taken all scheduled medications, scanning backwards from today.",
                                isDark = isDark
                            )
                            InfoItem(
                                title = "Best Streak",
                                description = "Your longest consecutive streak of perfect adherence days achieved.",
                                isDark = isDark
                            )
                            InfoItem(
                                title = "Overdue Doses",
                                description = "Scheduled doses that were not logged as 'Taken' by their scheduled times (marked as Missed or Pending).",
                                isDark = isDark
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text("Got it", color = if (isDark) Color(0xFF22C55E) else BrandingGreen)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = if (isDark) Color(0xFF121815) else Color.White,
                    tonalElevation = 6.dp
                )
            }

            // 1. Motivation Card
            MotivationCard(message = uiState.motivationalMessage)
            
            Spacer(modifier = Modifier.height(24.dp))

            // 2. Adherence Score Card
            DailyProgressCircle(progress = uiState.todayProgress, taken = uiState.todayTaken, total = uiState.todayTotal)

            Spacer(modifier = Modifier.height(24.dp))

            if (isDark) {
                // 3. Performance Section Header (Dynamic Label based on period)
                val performanceHeader = when (selectedPeriod) {
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> "Weekly Performance"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> "Weekly Performance"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> "30-Day Performance"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = performanceHeader,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onViewDetails() }
                    ) {
                        Text(
                            text = "View details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 2x2 Performance Grid with dark premium borders
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF2C3630))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                DarkStatItem(
                                    label = "Global Streak",
                                    value = "${uiState.globalStreak} Days",
                                    icon = Icons.Default.Whatshot,
                                    color = if (uiState.globalStreak >= 5) Color(0xFFFF5722) else Color(0xFFFF9800),
                                    iconBgColor = Color(0xFF2C221D)
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .padding(start = 8.dp)
                                .clickable { onViewDetails() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF2C3630))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                DarkStatItem(
                                    label = "Success Rate",
                                    value = "${activeStats.adherencePercentage}%",
                                    icon = Icons.Default.TrendingUp,
                                    color = MaterialTheme.colorScheme.primary,
                                    iconBgColor = Color(0xFF1B221E)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .padding(end = 8.dp)
                                .clickable { onNavigateToHistory("taken") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF2C3630))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                DarkStatItem(
                                    label = "Total Taken",
                                    value = activeStats.takenCount.toString(),
                                    icon = Icons.Default.CheckCircle,
                                    color = MaterialTheme.colorScheme.primary,
                                    iconBgColor = Color(0xFF1B221E)
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .padding(start = 8.dp)
                                .clickable { onNavigateToHistory("overdue") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF2C3630))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                DarkStatItem(
                                    label = "Overdue Doses",
                                    value = activeStats.overdueCount.toString(),
                                    icon = Icons.Default.Warning,
                                    color = Color(0xFFF44336),
                                    iconBgColor = Color(0xFF2D1E1E)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Weekly Trend Chart
                WeeklyTrendChart(
                    dailyResults = activeStats.dailyResults,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Achievements Section
                AchievementsSection(
                    longestStreak = uiState.longestStreak,
                    onViewAllAchievements = onNavigateToAchievements
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Risk Analysis Section
                RiskAnalysisSection(
                    adherenceStatus = uiState.adherenceStatus,
                    adherenceExplanation = uiState.adherenceExplanation,
                    refillStatus = uiState.refillStatus,
                    refillExplanation = uiState.refillExplanation,
                    onNavigateToRiskDetails = onNavigateToRiskDetails,
                    onNavigateToRefillStatus = onNavigateToRefillStatus
                )

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // Original stats card for Light Theme (pixel-identical layout)
                val performanceHeader = when (selectedPeriod) {
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> "Weekly Performance"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> "Weekly Performance"
                    com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> "30-Day Performance"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = performanceHeader,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onViewDetails() }
                            ) {
                                Text(
                                    text = "View details",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = BrandingGreen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = BrandingGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatItem(
                                modifier = Modifier.weight(1f),
                                label = "Global Streak",
                                value = "${uiState.globalStreak} Days",
                                icon = Icons.Default.Whatshot,
                                color = if (uiState.globalStreak >= 5) Color(0xFFFF5722) else Color(0xFFFF9800),
                                animate = uiState.globalStreak >= 5
                            )
                            StatItem(
                                modifier = Modifier.weight(1f).clickable { onViewDetails() },
                                label = "Success Rate",
                                value = "${activeStats.adherencePercentage}%",
                                icon = Icons.Default.TrendingUp,
                                color = BrandingGreen
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatItem(
                                modifier = Modifier.weight(1f).clickable { onNavigateToHistory("taken") },
                                label = "Total Taken",
                                value = activeStats.takenCount.toString(),
                                icon = Icons.Default.CheckCircle,
                                color = Color(0xFF4CAF50)
                            )
                            StatItem(
                                modifier = Modifier.weight(1f).clickable { onNavigateToHistory("overdue") },
                                label = "Total Missed",
                                value = activeStats.overdueCount.toString(),
                                icon = Icons.Default.Warning,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // 4. Weekly Trend Chart (Visible in both themes)
                WeeklyTrendChart(
                    dailyResults = activeStats.dailyResults,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Achievements Section (Visible in both themes)
                AchievementsSection(
                    longestStreak = uiState.longestStreak,
                    onViewAllAchievements = onNavigateToAchievements
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Risk Analysis Section (Visible in both themes)
                RiskAnalysisSection(
                    adherenceStatus = uiState.adherenceStatus,
                    adherenceExplanation = uiState.adherenceExplanation,
                    refillStatus = uiState.refillStatus,
                    refillExplanation = uiState.refillExplanation,
                    onNavigateToRiskDetails = onNavigateToRiskDetails,
                    onNavigateToRefillStatus = onNavigateToRefillStatus
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Last Updated
            if (uiState.lastUpdated.isNotEmpty()) {
                Text(
                    text = "Last updated: Today ${uiState.lastUpdated}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyDashboardState() {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "📊",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Start tracking your medicines to see insights",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MotivationCard(message: String) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    
    if (isDark) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Color(0xFF2C3630))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D3D2D),
                                Color(0xFF072017)
                            )
                        )
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F4E3A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Great progress!",
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Keep going, every dose counts! 💚",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else {
        // Original Light Theme motivation card (pixel-identical)
        val cardColor = TakenGreenBg
        val tintColor = PrimaryGreen
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = message,
                    color = tintColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun DailyProgressCircle(progress: Float, taken: Int, total: Int) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000)
    )

    if (isDark) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF2C3630))
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Progress circle + "On Track" status badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(130.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 10.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = "Today's Goal",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "● On Track",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                // Right Column: taken stats count + dose indicators + Daily Goal text
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$taken",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " / $total",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = "Doses taken today",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Row of dots representing today's doses
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val clampedTotal = total.coerceAtMost(6)
                        for (i in 0 until clampedTotal) {
                            val isTaken = i < taken
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isTaken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonChecked,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Daily Goal",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "$total Doses",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.size(200.dp),
                strokeWidth = 12.dp,
                color = BrandingGreen,
                trackColor = BrandingGreen.copy(alpha = 0.1f)
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Today's Goal",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun WeeklyTrendChart(
    dailyResults: List<com.pralayakaveri.medisave.model.DayResult>,
    selectedPeriod: com.pralayakaveri.medisave.viewmodel.TrendPeriod,
    onPeriodSelected: (com.pralayakaveri.medisave.viewmodel.TrendPeriod) -> Unit
) {
    val TextPrimary = MaterialTheme.colorScheme.onBackground
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    
    val chartTitle = when (selectedPeriod) {
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> "Weekly Trend"
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> "Weekly Trend"
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> "30-Day Trend"
    }

    val periodLabel = when (selectedPeriod) {
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK -> "This Week"
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK -> "Last Week"
        com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS -> "Last 30 Days"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chartTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { expanded = true }
                    ) {
                        Text(
                            text = periodLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("This Week") },
                            onClick = {
                                onPeriodSelected(com.pralayakaveri.medisave.viewmodel.TrendPeriod.THIS_WEEK)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last Week") },
                            onClick = {
                                onPeriodSelected(com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_WEEK)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 30 Days") },
                            onClick = {
                                onPeriodSelected(com.pralayakaveri.medisave.viewmodel.TrendPeriod.LAST_30_DAYS)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Dashed baseline overlay inside the graph
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(bottom = 30.dp)
                ) {
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        pathEffect = pathEffect,
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (dailyResults.size > 7) Modifier.horizontalScroll(scrollState) else Modifier),
                    horizontalArrangement = if (dailyResults.size <= 7) Arrangement.SpaceBetween else Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 0 until dailyResults.size) {
                        val result = dailyResults[i]
                        val total = result.total
                        val taken = result.taken
                        
                        val barColor = when {
                            total == 0 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            taken == total -> MaterialTheme.colorScheme.primary
                            taken == 0 -> Color(0xFFEF5350)
                            else -> Color(0xFFF19D38)
                        }
                        
                        val dayOfWeekLetter = when (result.date.dayOfWeek) {
                            java.time.DayOfWeek.MONDAY -> "M"
                            java.time.DayOfWeek.TUESDAY -> "T"
                            java.time.DayOfWeek.WEDNESDAY -> "W"
                            java.time.DayOfWeek.THURSDAY -> "T"
                            java.time.DayOfWeek.FRIDAY -> "F"
                            java.time.DayOfWeek.SATURDAY -> "S"
                            java.time.DayOfWeek.SUNDAY -> "S"
                            else -> "M"
                        }
                        
                        val labelText = if (dailyResults.size > 7) {
                            result.date.dayOfMonth.toString()
                        } else {
                            dayOfWeekLetter
                        }

                        val colModifier = if (dailyResults.size <= 7) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.width(28.dp)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = colModifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (total > 0) {
                                    val fillHeightPct = taken.toFloat() / total
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(fillHeightPct.coerceIn(0.15f, 1.0f))
                                            .clip(CircleShape)
                                            .background(barColor)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = labelText,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(label = "Taken", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(label = "Partial", color = Color(0xFFF19D38))
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(label = "Missed", color = Color(0xFFEF5350))
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(label = "No Data", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AchievementsSection(
    longestStreak: Int,
    onViewAllAchievements: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isDark) Color.White else com.pralayakaveri.medisave.ui.theme.TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onViewAllAchievements() }
            ) {
                Text(
                    text = "View all",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AchievementCard(
                title = "First Dose Completed",
                description = "Keep it up!",
                date = "Apr 10, 2026",
                icon = Icons.Default.Shield,
                iconColor = MaterialTheme.colorScheme.primary
            )
            AchievementCard(
                title = "3 Consecutive Reminders",
                description = "Consistency matters!",
                date = "Apr 9, 2026",
                icon = Icons.Default.Schedule,
                iconColor = MaterialTheme.colorScheme.primary
            )
            AchievementCard(
                title = "Family Monitoring",
                description = "Great teamwork!",
                date = "Apr 8, 2026",
                icon = Icons.Default.CheckCircle,
                iconColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AchievementCard(
    title: String,
    description: String,
    date: String,
    icon: ImageVector,
    iconColor: Color
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    Card(
        modifier = Modifier
            .width(220.dp)
            .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1B221E) else Color(0xFFE6F7ED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isDark) Color.White else com.pralayakaveri.medisave.ui.theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = date,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RiskAnalysisSection(
    adherenceStatus: String,
    adherenceExplanation: String,
    refillStatus: String,
    refillExplanation: String,
    onNavigateToRiskDetails: () -> Unit = {},
    onNavigateToRefillStatus: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    
    // Icon and background colors for Adherence
    val (adherenceIcon, adherenceColor, adherenceBg) = when (adherenceStatus) {
        "Action Required" -> Triple(Icons.Default.Warning, Color(0xFFEF5350), if (isDark) Color(0xFF2D1E1E) else Color(0xFFFEE2E2))
        "Needs Attention" -> Triple(Icons.Default.Warning, Color(0xFFF19D38), if (isDark) Color(0xFF2C221D) else Color(0xFFFFF3E0))
        else -> Triple(Icons.Default.Shield, if (isDark) MaterialTheme.colorScheme.primary else BrandingGreen, if (isDark) Color(0xFF1B221E) else Color(0xFFE6F7ED))
    }

    // Icon and background colors for Refill
    val (refillIcon, refillColor, refillBg) = when (refillStatus) {
        "Refill Required" -> Triple(Icons.Default.Warning, Color(0xFFEF5350), if (isDark) Color(0xFF2D1E1E) else Color(0xFFFEE2E2))
        "Refill Soon" -> Triple(Icons.Default.Schedule, Color(0xFFF19D38), if (isDark) Color(0xFF2C221D) else Color(0xFFFFF3E0))
        else -> Triple(Icons.Default.CheckCircle, if (isDark) MaterialTheme.colorScheme.primary else BrandingGreen, if (isDark) Color(0xFF1B221E) else Color(0xFFE6F7ED))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Risk Analysis",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (isDark) Color.White else com.pralayakaveri.medisave.ui.theme.TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // Adherence Status Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .height(95.dp)
                    .clickable { onNavigateToRiskDetails() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(adherenceBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = adherenceIcon,
                            contentDescription = null,
                            tint = adherenceColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Adherence Status",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = adherenceStatus,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = adherenceColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = adherenceExplanation,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Refill Status Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .height(95.dp)
                    .clickable { onNavigateToRefillStatus() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(refillBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = refillIcon,
                            contentDescription = null,
                            tint = refillColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Refill Status",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = refillStatus,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = refillColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = refillExplanation,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DarkStatItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    iconBgColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    animate: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary

    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StreakScale"
    )

    Column(
        modifier = modifier.graphicsLayer {
            if (animate) {
                scaleX = scale
                scaleY = scale
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InfoItem(title: String, description: String, isDark: Boolean) {
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val BrandingGreen = Color(0xFF1D9E75)
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (isDark) Color(0xFF22C55E) else BrandingGreen
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 12.sp,
            color = if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.7f) else TextSecondary,
            lineHeight = 16.sp
        )
    }
}
