package az.payreport.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Navy = Color(0xFF0B1F3A)
    val NavyMid = Color(0xFF13315C)
    val NavyDeep = Color(0xFF071426)
    val Green = Color(0xFF16A34A)
    val GreenLight = Color(0xFF22C55E)
    val GreenSoft = Color(0xFFDCFCE7)
    val LightGray = Color(0xFFF3F5F9)
    val Outline = Color(0xFFE2E8F0)
    val Slate = Color(0xFF64748B)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0xFFFEF3C7)
    val Red = Color(0xFFDC2626)
    val RedSoft = Color(0xFFFEE2E2)
}

@Immutable
data class ExtraColors(
    val positive: Color,
    val positiveContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val muted: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Brand.Green, Brand.GreenSoft, Brand.Amber, Brand.AmberSoft, Brand.Red, Brand.RedSoft, Brand.Navy, Brand.NavyMid, Brand.Slate)
}

private val LightColors = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F5),
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Green,
    onSecondary = Color.White,
    secondaryContainer = Brand.GreenSoft,
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Brand.Amber,
    background = Brand.LightGray,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Brand.Slate,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFEEF2F7),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Brand.Outline,
    error = Brand.Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8E6),
    onPrimary = Brand.NavyDeep,
    primaryContainer = Brand.NavyMid,
    onPrimaryContainer = Color(0xFFDCE6F5),
    secondary = Brand.GreenLight,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Brand.GreenSoft,
    tertiary = Brand.Amber,
    background = Brand.NavyDeep,
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F2341),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1B3358),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF0F2341),
    surfaceContainerLow = Color(0xFF0C1D36),
    surfaceContainerHigh = Color(0xFF1B3358),
    outline = Color(0xFF334E78),
    outlineVariant = Color(0xFF1E3A63),
    error = Color(0xFFF87171),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MoneyStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)

@Composable
fun PayReportTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extra = if (darkTheme) {
        ExtraColors(Brand.GreenLight, Color(0xFF14532D), Brand.Amber, Color(0xFF422006), Color(0xFFF87171), Color(0xFF450A0A),
            Brand.NavyMid, Color(0xFF1E4A85), Color(0xFF94A3B8))
    } else {
        ExtraColors(Brand.Green, Brand.GreenSoft, Color(0xFFB45309), Brand.AmberSoft, Brand.Red, Brand.RedSoft,
            Brand.Navy, Brand.NavyMid, Brand.Slate)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp),
            ),
            content = content,
        )
    }
}

object AppTheme {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}
