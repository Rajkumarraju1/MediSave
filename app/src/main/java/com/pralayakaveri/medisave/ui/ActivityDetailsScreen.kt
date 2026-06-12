package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cancel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsScreen(
    viewModel: DashboardViewModel,
    activityId: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    val backgroundColor = if (isDark) Color(0xFF0B0F0C) else Color(0xFFF7F9FA)
    val cardBgColor = if (isDark) Color(0xFF121815) else Color(0xFFFFFFFF)
    val outlineColor = if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.7f) else Color(0xFF8E8E93)
    val accentColor = if (isDark) Color(0xFF22C55E) else Color(0xFF1D9E75)

    val activity = uiState.historyActivities.firstOrNull { it.id == activityId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Activity Details",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (activity == null) {
                Text(
                    text = "Activity not found.",
                    color = textSecondary,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                val (statusColor, statusIcon) = when (activity.status) {
                    "Taken" -> Pair(accentColor, Icons.Default.CheckCircle)
                    "Overdue" -> Pair(Color(0xFFF19D38), Icons.Default.Warning)
                    else -> Pair(Color(0xFFEF5350), Icons.Default.Cancel)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Medicine Icon & Name Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = activity.medicineName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Status Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activity.status,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = outlineColor)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Detail Rows
                        DetailRow(label = "Date", value = activity.dateLabel, isDark = isDark, textPrimary = textPrimary, textSecondary = textSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        DetailRow(label = "Scheduled Time", value = activity.time, isDark = isDark, textPrimary = textPrimary, textSecondary = textSecondary)
                        
                        if (activity.actualCompletionTime != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailRow(label = "Completion Time", value = activity.actualCompletionTime, isDark = isDark, textPrimary = textPrimary, textSecondary = textSecondary)
                        }

                        if (activity.delayFromScheduled != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailRow(
                                label = "Delay from Scheduled",
                                value = activity.delayFromScheduled,
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                valueColor = if (activity.delayFromScheduled.contains("late")) Color(0xFFEF5350) else accentColor
                            )
                        } else if (activity.status == "Overdue" && activity.overdueDuration != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailRow(
                                label = "Overdue Duration",
                                value = activity.overdueDuration,
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                valueColor = Color(0xFFF19D38)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    valueColor: Color = textPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = textSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
