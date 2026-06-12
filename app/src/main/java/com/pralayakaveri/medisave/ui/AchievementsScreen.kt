package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel

data class Achievement(
    val title: String,
    val description: String,
    val detailDescription: String,
    val isUnlocked: Boolean,
    val progress: Int = 0,
    val target: Int = 0,
    val dateUnlocked: String? = null,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    // Dark/Light theme colors mapped strictly to existing MediSave tokens
    val backgroundColor = if (isDark) Color(0xFF0B0F0C) else Color(0xFFF7F9FA)
    val cardBgColor = if (isDark) Color(0xFF121815) else Color(0xFFFFFFFF)
    val outlineColor = if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.7f) else Color(0xFF8E8E93)
    val accentGreen = if (isDark) Color(0xFF22C55E) else Color(0xFF1D9E75)

    // State for modal dialog popup
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }

    // Dynamic calculations from current UI state
    val perfectDaysThisWeek = uiState.thisWeekStats.dailyResults.count { it.total > 0 && it.taken == it.total }
    val morningDosesTaken = uiState.historyActivities.count { it.status == "Taken" && it.time.contains("AM") }

    val achievements = remember(uiState.globalStreak, uiState.totalTaken, perfectDaysThisWeek, morningDosesTaken) {
        listOf(
            // Unlocked
            Achievement(
                title = "First Dose Completed",
                description = "Keep it up!",
                detailDescription = "Successfully logged your first medication dose.",
                isUnlocked = uiState.totalTaken >= 1,
                progress = minOf(uiState.totalTaken, 1),
                target = 1,
                dateUnlocked = "Apr 10, 2026",
                icon = Icons.Default.Shield
            ),
            Achievement(
                title = "3 Consecutive Reminders",
                description = "Consistency matters!",
                detailDescription = "Take medications on time for 3 consecutive scheduled reminder windows.",
                isUnlocked = uiState.totalTaken >= 1,
                progress = minOf(uiState.totalTaken, 3),
                target = 3,
                dateUnlocked = "Apr 9, 2026",
                icon = Icons.Default.Schedule
            ),
            Achievement(
                title = "Family Monitoring",
                description = "Great teamwork!",
                detailDescription = "Successfully connected and monitored a family member's medication routine.",
                isUnlocked = true,
                progress = 1,
                target = 1,
                dateUnlocked = "Apr 8, 2026",
                icon = Icons.Default.CheckCircle
            ),
            // Locked
            Achievement(
                title = "7 Day Streak",
                description = "Take all scheduled medications for 7 consecutive days.",
                detailDescription = "Take all scheduled medications for 7 consecutive days.",
                isUnlocked = uiState.globalStreak >= 7,
                progress = minOf(uiState.globalStreak, 7),
                target = 7,
                dateUnlocked = if (uiState.globalStreak >= 7) "Recently" else null,
                icon = Icons.Default.Star
            ),
            Achievement(
                title = "30 Day Streak",
                description = "Take all scheduled medications for 30 consecutive days.",
                detailDescription = "Take all scheduled medications for 30 consecutive days.",
                isUnlocked = uiState.globalStreak >= 30,
                progress = minOf(uiState.globalStreak, 30),
                target = 30,
                dateUnlocked = if (uiState.globalStreak >= 30) "Recently" else null,
                icon = Icons.Default.Star
            ),
            Achievement(
                title = "Perfect Week",
                description = "Take all scheduled medications for 7 days this week.",
                detailDescription = "Take all scheduled medications for 7 days this week.",
                isUnlocked = perfectDaysThisWeek >= 7,
                progress = minOf(perfectDaysThisWeek, 7),
                target = 7,
                dateUnlocked = if (perfectDaysThisWeek >= 7) "Recently" else null,
                icon = Icons.Default.Check
            ),
            Achievement(
                title = "Medicine Champion",
                description = "Successfully log 100 doses.",
                detailDescription = "Build a lasting routine by reaching a lifetime total of 100 logged doses.",
                isUnlocked = uiState.totalTaken >= 100,
                progress = minOf(uiState.totalTaken, 100),
                target = 100,
                dateUnlocked = if (uiState.totalTaken >= 100) "Recently" else null,
                icon = Icons.Default.EmojiEvents
            ),
            Achievement(
                title = "Consistency Master",
                description = "Take all scheduled medications for 500 doses.",
                detailDescription = "Log 500 doses to master your medication routine.",
                isUnlocked = uiState.totalTaken >= 500,
                progress = minOf(uiState.totalTaken, 500),
                target = 500,
                dateUnlocked = if (uiState.totalTaken >= 500) "Recently" else null,
                icon = Icons.Default.WorkspacePremium
            ),
            Achievement(
                title = "Refill Hero",
                description = "Log a refill request successfully 5 times.",
                detailDescription = "Ensure your medications never run out by logging 5 refills.",
                isUnlocked = false,
                progress = 0,
                target = 5,
                icon = Icons.Default.Inventory
            ),
            Achievement(
                title = "Early Bird",
                description = "Log 10 morning doses.",
                detailDescription = "Take your morning medications on time for 10 days.",
                isUnlocked = morningDosesTaken >= 10,
                progress = minOf(morningDosesTaken, 10),
                target = 10,
                dateUnlocked = if (morningDosesTaken >= 10) "Recently" else null,
                icon = Icons.Default.WbSunny
            )
        )
    }

    val unlockedAchievements = remember(achievements) { achievements.filter { it.isUnlocked } }
    val lockedAchievements = remember(achievements) { achievements.filter { !it.isUnlocked } }
    val totalUnlocked = unlockedAchievements.size
    val progressPercent = (totalUnlocked * 100) / achievements.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Achievements",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "Your Health Milestones",
                            fontSize = 12.sp,
                            color = textSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🏆 Total Unlocked", fontSize = 11.sp, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "$totalUnlocked / 10", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        }

                        Box(modifier = Modifier.height(30.dp).width(1.dp).background(outlineColor))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🔥 Current Streak", fontSize = 11.sp, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.globalStreak} ${if (uiState.globalStreak == 1) "Day" else "Days"}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        Box(modifier = Modifier.height(30.dp).width(1.dp).background(outlineColor))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "⭐ Progress", fontSize = 11.sp, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "$progressPercent%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        }
                    }
                }
            }

            // Unlocked Section Header
            if (unlockedAchievements.isNotEmpty()) {
                item {
                    Text(
                        text = "Unlocked Achievements",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(unlockedAchievements.size) { index ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        AchievementRowCard(
                            achievement = unlockedAchievements[index],
                            isDark = isDark,
                            cardBgColor = cardBgColor,
                            outlineColor = outlineColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentGreen = accentGreen,
                            onClick = { selectedAchievement = unlockedAchievements[index] }
                        )
                    }
                }
            }

            // Locked Section Header
            if (lockedAchievements.isNotEmpty()) {
                item {
                    Text(
                        text = "Locked Achievements",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                    )
                }

                items(lockedAchievements.size) { index ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        AchievementRowCard(
                            achievement = lockedAchievements[index],
                            isDark = isDark,
                            cardBgColor = cardBgColor,
                            outlineColor = outlineColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentGreen = accentGreen,
                            onClick = { selectedAchievement = lockedAchievements[index] }
                        )
                    }
                }
            }
        }
    }

    // Detail Alert Dialog
    if (selectedAchievement != null) {
        val achievement = selectedAchievement!!
        AlertDialog(
            onDismissRequest = { selectedAchievement = null },
            title = {
                Text(
                    text = "Achievement Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = achievement.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = textPrimary
                    )
                    Text(
                        text = achievement.description,
                        fontSize = 13.sp,
                        color = textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(outlineColor))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (achievement.isUnlocked) {
                        Text(
                            text = "Unlocked:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Text(
                            text = achievement.dateUnlocked ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accentGreen,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = "Progress:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        val unitStr = when {
                            achievement.title.contains("Streak") || achievement.title.contains("Week") -> "Days"
                            achievement.title.contains("Early Bird") || achievement.title.contains("Champion") || achievement.title.contains("Master") -> "Doses"
                            achievement.title.contains("Refill") -> "Refills"
                            else -> ""
                        }
                        Text(
                            text = "${achievement.progress} / ${achievement.target} $unitStr",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Description:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Text(
                        text = achievement.detailDescription,
                        fontSize = 13.sp,
                        color = textPrimary,
                        modifier = Modifier.padding(top = 2.dp),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAchievement = null }) {
                    Text(
                        text = "Got it",
                        color = accentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun AchievementRowCard(
    achievement: Achievement,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onClick: () -> Unit
) {
    val statusColor = if (achievement.isUnlocked) accentGreen else Color(0xFF8E8E93)
    val iconBgColor = if (achievement.isUnlocked) statusColor.copy(alpha = 0.12f) else outlineColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (achievement.isUnlocked) achievement.icon else Icons.Default.Lock,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = textSecondary
                )

                if (!achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { achievement.progress.toFloat() / achievement.target.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = statusColor,
                            trackColor = outlineColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${achievement.progress} / ${achievement.target}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                    }
                } else if (achievement.isUnlocked && achievement.dateUnlocked != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unlocked: ${achievement.dateUnlocked}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
