package com.iporadar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iporadar.app.data.local.DarkMode

private val Green = Color(0xFF16A34A)
private val GreenBright = Color(0xFF4ADE80)
private val Red = Color(0xFFDC2626)
private val RedBright = Color(0xFFF87171)
private val Amber = Color(0xFFF59E0B)
private val Blue = Color(0xFF2563EB)
private val BlueBright = Color(0xFF60A5FA)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F172A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Blue,
    onSecondary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF5B6572),
    outline = Color(0xFFD5DBE3),
    outlineVariant = Color(0xFFE7EBF0),
    error = Red,
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE2E8F0),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFE2E8F0),
    secondary = BlueBright,
    onSecondary = Color(0xFF0B1220),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE7EBF0),
    surface = Color(0xFF131C2E),
    onSurface = Color(0xFFE7EBF0),
    surfaceVariant = Color(0xFF1B2639),
    onSurfaceVariant = Color(0xFF97A3B4),
    outline = Color(0xFF2C3A4F),
    outlineVariant = Color(0xFF1F2A3D),
    error = RedBright,
    onError = Color(0xFF0B1220)
)

/** Semantic colors that Material3 has no slot for (gain / loss / neutral signals). */
data class MarketColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val positiveContainer: Color,
    val negativeContainer: Color,
    val warningContainer: Color
)

val LocalMarketColors = staticCompositionLocalOf {
    MarketColors(Green, Red, Amber, Color(0x1416A34A), Color(0x14DC2626), Color(0x14F59E0B))
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun IpoRadarTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val market = if (dark) {
        MarketColors(
            positive = GreenBright,
            negative = RedBright,
            warning = Color(0xFFFBBF24),
            positiveContainer = Color(0x264ADE80),
            negativeContainer = Color(0x26F87171),
            warningContainer = Color(0x26FBBF24)
        )
    } else {
        MarketColors(
            positive = Green,
            negative = Red,
            warning = Amber,
            positiveContainer = Color(0x1F16A34A),
            negativeContainer = Color(0x1FDC2626),
            warningContainer = Color(0x1FF59E0B)
        )
    }

    CompositionLocalProvider(LocalMarketColors provides market) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content
        )
    }
}
