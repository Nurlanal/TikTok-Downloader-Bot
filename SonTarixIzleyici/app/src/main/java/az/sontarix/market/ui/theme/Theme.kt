package az.sontarix.market.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.sontarix.market.data.ThemeMode
import az.sontarix.market.util.ExpiryStatus

object StatusColors {
    val Green = Color(0xFF16A34A)
    val Yellow = Color(0xFFEAB308)
    val Orange = Color(0xFFF97316)
    val DeepOrange = Color(0xFFEA580C)
    val Red = Color(0xFFDC2626)
    val Indigo = Color(0xFF4F46E5)
    val Sky = Color(0xFF0EA5E9)
    val Violet = Color(0xFF8B5CF6)
    val Teal = Color(0xFF14B8A6)
    val Pink = Color(0xFFEC4899)

    fun of(status: ExpiryStatus): Color = when (status) {
        ExpiryStatus.NORMAL -> Green
        ExpiryStatus.APPROACHING -> Yellow
        ExpiryStatus.CRITICAL -> Orange
        ExpiryStatus.TODAY -> DeepOrange
        ExpiryStatus.EXPIRED -> Red
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFFF97316),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF431407),
    tertiary = Color(0xFF14B8A6),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE9EDF5),
    surfaceContainerLow = Color(0xFFF8FAFC),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFFB923C),
    onSecondary = Color(0xFF431407),
    secondaryContainer = Color(0xFF7C2D12),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFF2DD4BF),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF161B26),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1F2633),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF161B26),
    surfaceContainerHigh = Color(0xFF1F2633),
    surfaceContainerHighest = Color(0xFF283041),
    surfaceContainerLow = Color(0xFF121722),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF253041),
    error = Color(0xFFF87171)
)

val LocalIsDark = staticCompositionLocalOf { false }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SonTarixTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Suppress("unused")
val BigNumber = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
