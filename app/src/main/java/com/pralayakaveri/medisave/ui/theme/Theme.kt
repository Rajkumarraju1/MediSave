package com.pralayakaveri.medisave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    background = LightGrayBg,
    surface = CardWhite,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outlineVariant = DividerGray
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22C55E), // Vibrant green accent
    background = Color(0xFF0B0F0C), // Near black neutral
    surface = Color(0xFF121815),
    onPrimary = Color.Black,
    onBackground = Color(0xFFE6F4EA), // High contrast readable text
    onSurface = Color(0xFFE6F4EA),
    secondaryContainer = Color(0xFF1E2923),
    onSecondaryContainer = Color(0xFF22C55E),
    outline = Color(0xFF2C3630),
    outlineVariant = Color(0xFF1F2622)
)

@Composable
fun MediSaveTheme(
    themePreference: String = "Light",
    content: @Composable () -> Unit
) {
    val darkTheme = themePreference == "Dark"
    val colorScheme = if (darkTheme) DarkColors else LightColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
