package com.techport.tvremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.techport.tvremote.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0A2350),
    primaryContainer = Color(0xFF233A63),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFB7C6E4),
    onSecondary = Color(0xFF213048),
    secondaryContainer = Color(0xFF2D3A50),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF7FD8C4),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF101317),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF101317),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2B3038),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = Color(0xFF171B21),
    surfaceContainer = Color(0xFF1C2027),
    surfaceContainerHigh = Color(0xFF242932),
    surfaceContainerHighest = Color(0xFF2F343D),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF3A3F47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF006B5C),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE0E2E7),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
)

private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0B),
    surfaceContainer = Color(0xFF111214),
    surfaceContainerHigh = Color(0xFF18191C),
    surfaceContainerHighest = Color(0xFF202226),
)

@Composable
fun TvRemoteTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val colors = if (themeMode == ThemeMode.AMOLED) base.amoled() else base

    MaterialTheme(colorScheme = colors, typography = TvRemoteTypography, content = content)
}
