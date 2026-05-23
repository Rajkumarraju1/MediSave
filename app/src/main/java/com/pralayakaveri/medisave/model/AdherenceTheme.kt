package com.pralayakaveri.medisave.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class AdherenceSeverity {
    SUCCESS, WARNING, DANGER, NEUTRAL
}

data class AdherenceThemeConfig(
    val severity: AdherenceSeverity,
    val cardBackground: Color,
    val progressBarColor: Color = Color.White,
    val footerMessage: String,
    val footerIcon: ImageVector,
    val labelText: String,
    val showStreak: Boolean = false
)

object AdherenceThemeMapper {
    fun mapReportToTheme(
        report: AdherenceReport,
        currentStreak: Int
    ): AdherenceThemeConfig {
        val percentage = report.adherencePercentage ?: 0
        val daysWithData = report.daysWithData
        
        // 1. Determine Severity based on thresholds
        val severity = when {
            daysWithData <= 1 -> AdherenceSeverity.NEUTRAL
            percentage >= 85 -> AdherenceSeverity.SUCCESS
            percentage >= 60 -> AdherenceSeverity.WARNING
            else -> AdherenceSeverity.DANGER
        }

        // 2. Map Visuals and Content
        val missedCount = report.dueSoFarStats.total - report.dueSoFarStats.taken

        return when (severity) {
            AdherenceSeverity.SUCCESS -> AdherenceThemeConfig(
                severity = severity,
                cardBackground = Color(0xFF1D9E75), // PrimaryGreen
                footerMessage = if (currentStreak >= 3) {
                    "🔥 $currentStreak-day streak — keep it going!"
                } else {
                    "Perfect week so far! Keep it up."
                },
                footerIcon = Icons.Default.Whatshot,
                labelText = "Excellent"
            )
            AdherenceSeverity.WARNING -> AdherenceThemeConfig(
                severity = severity,
                cardBackground = Color(0xFFB07F1A), // Amber
                footerMessage = if (missedCount > 0) {
                    "$missedCount missed dose${if (missedCount > 1) "s" else ""} — you can still recover!"
                } else {
                    "Good start! Keep taking your doses on time."
                },
                footerIcon = Icons.Default.Info,
                labelText = "Good"
            )
            AdherenceSeverity.DANGER -> AdherenceThemeConfig(
                severity = severity,
                cardBackground = Color(0xFFB73B3B), // Red
                footerMessage = if (missedCount > 0) {
                    "$missedCount dose${if (missedCount > 1) "s" else ""} missed — please take your medicine"
                } else {
                    "Multiple doses missed. Stay on track with your meds."
                },
                footerIcon = Icons.Default.Warning,
                labelText = "Needs Attention"
            )
            AdherenceSeverity.NEUTRAL -> AdherenceThemeConfig(
                severity = severity,
                cardBackground = Color(0xFF1E6F9F), // Blue
                footerMessage = "Fresh week — start strong today!",
                footerIcon = Icons.Default.KeyboardArrowUp,
                labelText = "Getting Started"
            )
        }
    }
}
