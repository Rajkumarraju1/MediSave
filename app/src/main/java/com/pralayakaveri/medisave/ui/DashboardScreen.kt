package com.pralayakaveri.medisave.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel
import com.pralayakaveri.medisave.viewmodel.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
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
            Text(
                text = "Adherence Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BrandingGreen,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // Motivation Card
            MotivationCard(message = uiState.motivationalMessage)
            
            Spacer(modifier = Modifier.height(24.dp))

            // Progress Section
            DailyProgressCircle(progress = uiState.todayProgress)

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Weekly Performance",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
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
                            modifier = Modifier.weight(1f),
                            label = "Success Rate",
                            value = "${uiState.weeklyAdherence}%",
                            icon = Icons.Default.TrendingUp,
                            color = BrandingGreen
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem(
                            modifier = Modifier.weight(1f),
                            label = "Total Taken",
                            value = uiState.totalTaken.toString(),
                            icon = Icons.Default.CheckCircle,
                            color = Color(0xFF4CAF50)
                        )
                        StatItem(
                            modifier = Modifier.weight(1f),
                            label = "Total Missed",
                            value = uiState.totalMissed.toString(),
                            icon = Icons.Default.Warning,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TakenGreenBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                color = PrimaryGreen,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun DailyProgressCircle(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000)
    )

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

@Composable
fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    animate: Boolean = false
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
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
