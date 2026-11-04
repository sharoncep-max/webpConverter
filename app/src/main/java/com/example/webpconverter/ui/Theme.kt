package com.example.webpconverter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0891B2), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE), onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFFF59E0B), onTertiary = Color(0xFF3B2300),
    background = Color(0xFFF4F6FB), onBackground = Color(0xFF111827),
    surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF1F7), onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF1F3F9), surfaceContainerHigh = Color(0xFFEBEEF5),
    surfaceContainerHighest = Color(0xFFE4E8F0),
    outline = Color(0xFFC3CAD8), outlineVariant = Color(0xFFE1E5EE),
    error = Color(0xFFDC2626), onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC), onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF67E8F9), onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF155E75), onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF3B2300),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF131A2B), onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1E2638), onSurfaceVariant = Color(0xFFA9B2C4),
    surfaceContainerLowest = Color(0xFF0E1424), surfaceContainerLow = Color(0xFF151C2E),
    surfaceContainer = Color(0xFF182032), surfaceContainerHigh = Color(0xFF1E2639),
    surfaceContainerHighest = Color(0xFF252E42),
    outline = Color(0xFF46506A), outlineVariant = Color(0xFF2A3348),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WebpConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}

data class StatusColors(val container: Color, val content: Color)

internal val isDarkScheme: Boolean
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

internal val successColors: StatusColors
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme) StatusColors(Color(0xFF14532D), Color(0xFFDCFCE7))
    else StatusColors(Color(0xFFDCFCE7), Color(0xFF14532D))

internal val headerBrush: Brush
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme) Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF164E63)))
    else Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF0891B2)))
