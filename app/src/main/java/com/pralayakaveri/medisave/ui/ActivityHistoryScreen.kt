package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel
import com.pralayakaveri.medisave.viewmodel.ActivityLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    viewModel: DashboardViewModel,
    initialFilter: String = "all",
    onBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    // Dynamic color tokens mapping strictly to existing MediSave tokens
    val backgroundColor = if (isDark) Color(0xFF0B0F0C) else Color(0xFFF7F9FA)
    val cardBgColor = if (isDark) Color(0xFF121815) else Color(0xFFFFFFFF)
    val outlineColor = if (isDark) Color(0xFF2C3630) else Color(0xFFE0E0E0)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.7f) else Color(0xFF8E8E93)
    val accentColor = if (isDark) Color(0xFF22C55E) else Color(0xFF1D9E75)

    // Map initial filter query string to lowercase tabs
    var selectedFilter by remember { 
        mutableStateOf(
            when (initialFilter.lowercase()) {
                "taken" -> "Taken"
                "overdue" -> "Overdue"
                "missed" -> "Missed"
                else -> "All"
            }
        )
    }

    // Filter activities based on selection
    val filteredActivities = remember(uiState.historyActivities, selectedFilter) {
        if (selectedFilter == "All") {
            uiState.historyActivities
        } else {
            uiState.historyActivities.filter { 
                it.status.equals(selectedFilter, ignoreCase = true)
            }
        }
    }

    // Group filtered activities by dateLabel (chronologically ordered Map)
    val groupedActivities = remember(filteredActivities) {
        filteredActivities.groupBy { it.dateLabel }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Activity History",
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
        ) {
            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "Taken", "Overdue", "Missed")
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val tabBgColor = if (isSelected) accentColor else (if (isDark) Color(0xFF1A221E) else Color(0xFFEAF5F1))
                    val tabTextColor = if (isSelected) Color.White else (if (isDark) Color(0xFFE6F4EA).copy(alpha = 0.8f) else Color(0xFF1D9E75))

                    var boxModifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(tabBgColor)

                    if (!isSelected) {
                        boxModifier = boxModifier.border(1.dp, outlineColor, RoundedCornerShape(50))
                    }

                    boxModifier = boxModifier
                        .clickable { selectedFilter = filter }
                        .padding(vertical = 10.dp)

                    Box(
                        modifier = boxModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = tabTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (groupedActivities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No records found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Any medication compliance events logged in the last 30 days will appear here.",
                            fontSize = 13.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groupedActivities.forEach { (dateLabel, activitiesForDate) ->
                        item {
                            Text(
                                text = dateLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dateLabel == "Today") Color(0xFF22C55E) else textPrimary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        itemsIndexed(activitiesForDate) { index, activity ->
                            val (statusText, statusColor, statusIcon) = when (activity.status) {
                                "Taken" -> Triple("Taken", accentColor, Icons.Default.CheckCircle)
                                "Overdue" -> Triple("Overdue", Color(0xFFF19D38), Icons.Default.Warning)
                                else -> Triple("Missed", Color(0xFFEF5350), Icons.Default.Cancel)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onNavigateToDetails(activity.id) },
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
                                            .background(statusColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activity.medicineName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Scheduled at ${activity.time}",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                        if (activity.status == "Taken" && activity.actualCompletionTime != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Taken at ${activity.actualCompletionTime}",
                                                fontSize = 12.sp,
                                                color = textSecondary
                                            )
                                        } else if (activity.status == "Overdue" && activity.overdueDuration != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Overdue by ${activity.overdueDuration}",
                                                fontSize = 12.sp,
                                                color = textSecondary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Status Badge
                                    val statusSymbol = when (activity.status) {
                                        "Taken" -> "✓"
                                        "Overdue" -> "⚠"
                                        else -> "✕"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "$statusSymbol $statusText",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
