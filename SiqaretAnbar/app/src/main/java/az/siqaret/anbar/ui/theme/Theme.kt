package az.siqaret.anbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF5B6475),
    outline = Color(0xFFCBD2DE),
    outlineVariant = Color(0xFFE3E7EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F3F8),
    surfaceContainerHighest = Color(0xFFEAEDF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B8CFF),
    onPrimary = Color(0xFF14123B),
    primaryContainer = Color(0xFF2E2A7A),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C3B57),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF4A3005),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF4C1515),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFE8EAF2),
    surface = Color(0xFF0B0D14),
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFF9AA3B5),
    outline = Color(0xFF353B4F),
    outlineVariant = Color(0xFF262B3A),
    surfaceContainerLowest = Color(0xFF0F121B),
    surfaceContainerLow = Color(0xFF151823),
    surfaceContainer = Color(0xFF151823),
    surfaceContainerHigh = Color(0xFF1B1F2C),
    surfaceContainerHighest = Color(0xFF222735),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SiqaretTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 3D ikonlar üçün qradiyentlər. */
object Gradients {
    val Violet = listOf(Color(0xFFA78BFA), Color(0xFF6D28D9))
    val Blue = listOf(Color(0xFF7DD3FC), Color(0xFF2563EB))
    val Teal = listOf(Color(0xFF6EE7B7), Color(0xFF059669))
    val Amber = listOf(Color(0xFFFCD34D), Color(0xFFD97706))
    val Rose = listOf(Color(0xFFFDA4AF), Color(0xFFE11D48))
    val Indigo = listOf(Color(0xFFA5B4FC), Color(0xFF4338CA))
    val Red = listOf(Color(0xFFFCA5A5), Color(0xFFDC2626))
    val Green = listOf(Color(0xFF86EFAC), Color(0xFF16A34A))
    val Slate = listOf(Color(0xFFCBD5E1), Color(0xFF475569))
    val Hero = listOf(Color(0xFF6366F1), Color(0xFF7C3AED), Color(0xFF9333EA))

    private val products = listOf(Amber, Violet, Blue, Teal, Rose, Indigo)
    fun forName(name: String): List<Color> = products[Math.floorMod(name.lowercase().hashCode(), products.size)]
}

val PositiveColor = Color(0xFF16A34A)
val NegativeColor = Color(0xFFDC2626)

