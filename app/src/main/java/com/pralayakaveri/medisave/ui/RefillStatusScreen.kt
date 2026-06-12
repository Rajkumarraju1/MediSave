package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel

@Composable
fun RefillStatusScreen(
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

    val medicines = uiState.medicines

    // Categorise medicines
    val outOfStock = medicines.filter { it.pillsLeft == 0 }
    val criticalLow = medicines.filter { it.pillsLeft > 0 && it.pillsLeft < it.doseQuantity }
    val refillSoon = medicines.filter { it.pillsLeft >= it.doseQuantity && it.pillsLeft <= it.refillAt }
    val healthy = medicines.filter { it.pillsLeft > it.refillAt }

    val overallStatus = when {
        outOfStock.isNotEmpty() || criticalLow.isNotEmpty() -> "Refill Required"
        refillSoon.isNotEmpty() -> "Refill Soon"
        else -> "Stock Healthy"
    }
    val (statusColor, statusBg) = when (overallStatus) {
        "Refill Required" -> Pair(Color(0xFFEF5350), if (isDark) Color(0xFF2D1E1E) else Color(0xFFFDEDED))
        "Refill Soon" -> Pair(Color(0xFFF19D38), if (isDark) Color(0xFF2C221D) else Color(0xFFFFF3E0))
        else -> Pair(accentGreen, if (isDark) Color(0xFF1B221E) else Color(0xFFE8F5E9))
    }

    // Quantity picker dialog state
    var showRefillDialog by remember { mutableStateOf(false) }
    var targetMedicine by remember { mutableStateOf<Medicine?>(null) }
    var refillQuantity by remember { mutableStateOf(30) }

    if (showRefillDialog && targetMedicine != null) {
        AlertDialog(
            onDismissRequest = { showRefillDialog = false },
            containerColor = surfaceColor,
            title = {
                Text(
                    text = "Refill Stock",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Medicine: ${targetMedicine!!.name}",
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Quantity (tablets):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Decrease
                        FilledIconButton(
                            onClick = { if (refillQuantity > 5) refillQuantity -= 5 },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isDark) Color(0xFF1E2920) else Color(0xFFE8F5E9)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease",
                                tint = accentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "$refillQuantity",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        // Increase
                        FilledIconButton(
                            onClick = { if (refillQuantity < 500) refillQuantity += 5 },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isDark) Color(0xFF1E2920) else Color(0xFFE8F5E9)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase",
                                tint = accentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.refillMedicine(targetMedicine!!.id, refillQuantity)
                        showRefillDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                ) {
                    Text("Add Stock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefillDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────────
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
                text = "Refill Status",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
        }

        // ── Overall Status Card ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(statusBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (overallStatus) {
                            "Refill Required" -> Icons.Default.Warning
                            "Refill Soon" -> Icons.Default.Schedule
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = overallStatus,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = uiState.refillExplanation,
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
            }
        }

        // ── Low Stock Warnings ──────────────────────────────────────────────
        val warnMeds = outOfStock + criticalLow + refillSoon
        if (warnMeds.isNotEmpty()) {
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
                        text = "⚠ Attention Required",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFFF19D38)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    warnMeds.forEach { med ->
                        val (warnColor, warnText) = when {
                            med.pillsLeft == 0 -> Pair(Color(0xFFEF5350), "Out of stock")
                            med.pillsLeft < med.doseQuantity -> Pair(Color(0xFFEF5350), "Not enough for 1 dose (${med.pillsLeft} left)")
                            else -> Pair(Color(0xFFF19D38), "Low — refill soon (${med.pillsLeft} left)")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(warnColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = med.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Text(text = warnText, fontSize = 12.sp, color = warnColor)
                            }
                        }
                    }
                }
            }
        }

        // ── Medication Inventory ─────────────────────────────────────────────
        Text(
            text = "Medication Inventory",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        if (medicines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No medications found",
                    fontSize = 14.sp,
                    color = textSecondary
                )
            }
        } else {
            medicines.forEach { med ->
                val dosesPerDay = med.times.size
                val dailyUsage = med.doseQuantity * dosesPerDay
                val daysRemaining = if (dailyUsage > 0) med.pillsLeft / dailyUsage else Int.MAX_VALUE

                val (stockColor, stockLabel) = when {
                    med.pillsLeft == 0 -> Pair(Color(0xFFEF5350), "Out of Stock")
                    med.pillsLeft < med.doseQuantity -> Pair(Color(0xFFEF5350), "Critical")
                    med.pillsLeft <= med.refillAt -> Pair(Color(0xFFF19D38), "Low Stock")
                    else -> Pair(accentGreen, "Healthy")
                }

                // Progress for stock bar (relative to a "target" of 30-day supply)
                val targetSupply = (dailyUsage * 30).coerceAtLeast(1)
                val stockProgress = (med.pillsLeft.toFloat() / targetSupply).coerceIn(0f, 1f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Medicine icon circle
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        try {
                                            Color(android.graphics.Color.parseColor(med.colorHex))
                                                .copy(alpha = 0.18f)
                                        } catch (e: Exception) {
                                            accentGreen.copy(alpha = 0.18f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    tint = try {
                                        Color(android.graphics.Color.parseColor(med.colorHex))
                                    } catch (e: Exception) { accentGreen },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = med.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${med.doseQuantity} tablet${if (med.doseQuantity != 1) "s" else ""} × ${dosesPerDay}x/day" +
                                        if (dailyUsage > 0) " = $dailyUsage/day" else "",
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                            // Stock badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (stockLabel) {
                                    "Out of Stock" -> Color(0xFF2D1E1E)
                                    "Critical" -> Color(0xFF2D1E1E)
                                    "Low Stock" -> Color(0xFF2C221D)
                                    else -> if (isDark) Color(0xFF1B221E) else Color(0xFFE8F5E9)
                                }
                            ) {
                                Text(
                                    text = stockLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = stockColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stock progress bar
                        LinearProgressIndicator(
                            progress = stockProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = stockColor,
                            trackColor = borderColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${med.pillsLeft} tablets remaining",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                                Text(
                                    text = if (daysRemaining == Int.MAX_VALUE || dailyUsage == 0)
                                        "Daily usage not set"
                                    else
                                        "~$daysRemaining day${if (daysRemaining != 1) "s" else ""} left",
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                            // Refill button
                            FilledTonalButton(
                                onClick = {
                                    targetMedicine = med
                                    refillQuantity = 30
                                    showRefillDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isDark) Color(0xFF1E2920) else Color(0xFFE8F5E9),
                                    contentColor = accentGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refill", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
