package az.rafiqmarket.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.rafiqmarket.core.session.ThemeMode

object RafiqColors {
    val Green900 = Color(0xFF0B3D1E)
    val Green800 = Color(0xFF1B5E20)
    val Green700 = Color(0xFF2E7D32)
    val Green600 = Color(0xFF388E3C)
    val Green500 = Color(0xFF43A047)
    val Green300 = Color(0xFF81C784)
    val Green100 = Color(0xFFDCEFDF)
    val Green50 = Color(0xFFF1F8F2)
    val Lime = Color(0xFFB9F27C)
    val Orange = Color(0xFFFF8F00)
    val Red = Color(0xFFD32F2F)
}

private val Light = lightColorScheme(
    primary = RafiqColors.Green700,
    onPrimary = Color.White,
    primaryContainer = RafiqColors.Green100,
    onPrimaryContainer = RafiqColors.Green900,
    secondary = RafiqColors.Green500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4D7),
    onSecondaryContainer = Color(0xFF1F3A0E),
    tertiary = RafiqColors.Orange,
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF111A13),
    surface = Color.White,
    onSurface = Color(0xFF111A13),
    surfaceVariant = Color(0xFFEFF4EF),
    onSurfaceVariant = Color(0xFF4F5B52),
    surfaceContainer = Color(0xFFF3F7F3),
    surfaceContainerHigh = Color(0xFFECF2EC),
    outline = Color(0xFFC7D3C9),
    outlineVariant = Color(0xFFE1E8E2),
    error = RafiqColors.Red,
)

private val Dark = darkColorScheme(
    primary = RafiqColors.Green300,
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1E4D27),
    onPrimaryContainer = Color(0xFFCDEFD1),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF0B3D1E),
    secondaryContainer = Color(0xFF29412A),
    onSecondaryContainer = Color(0xFFD9F2D5),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0E1410),
    onBackground = Color(0xFFE1E8E2),
    surface = Color(0xFF151D17),
    onSurface = Color(0xFFE1E8E2),
    surfaceVariant = Color(0xFF223026),
    onSurfaceVariant = Color(0xFFB7C5BA),
    surfaceContainer = Color(0xFF19221B),
    surfaceContainerHigh = Color(0xFF1F2A21),
    outline = Color(0xFF3E4D41),
    outlineVariant = Color(0xFF2A362C),
    error = Color(0xFFEF9A9A),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val PriceStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RafiqTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
