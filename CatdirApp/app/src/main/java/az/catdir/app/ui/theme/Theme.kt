package az.catdir.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.catdir.app.model.OrderStatus

private val Light = lightColorScheme(
    primary = Color(0xFFEA580C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5), onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF0F766E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1), onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFF4F46E5), tertiaryContainer = Color(0xFFE0E7FF), onTertiaryContainer = Color(0xFF312E81),
    error = Color(0xFFDC2626), errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF7F7F9), onBackground = Color(0xFF111827),
    surface = Color(0xFFF7F7F9), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEEFF3), onSurfaceVariant = Color(0xFF5B6270),
    outline = Color(0xFFCDD1DA), outlineVariant = Color(0xFFE4E6EC),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F3F6), surfaceContainerHighest = Color(0xFFEAECF1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFB923C), onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF5A2410), onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFF2DD4BF), onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A), onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFA5B4FC), tertiaryContainer = Color(0xFF312E81), onTertiaryContainer = Color(0xFFE0E7FF),
    error = Color(0xFFF87171), errorContainer = Color(0xFF4C1515), onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0E0F13), onBackground = Color(0xFFECEDF1),
    surface = Color(0xFF0E0F13), onSurface = Color(0xFFECEDF1),
    surfaceVariant = Color(0xFF1F2129), onSurfaceVariant = Color(0xFF9CA1AE),
    outline = Color(0xFF3A3D48), outlineVariant = Color(0xFF282A33),
    surfaceContainerLowest = Color(0xFF121318), surfaceContainerLow = Color(0xFF17181E), surfaceContainer = Color(0xFF17181E),
    surfaceContainerHigh = Color(0xFF1E2027), surfaceContainerHighest = Color(0xFF252731),
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

@Composable
fun CatdirTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}

object Gradients {
    val Orange = listOf(Color(0xFFFDBA74), Color(0xFFEA580C))
    val Teal = listOf(Color(0xFF5EEAD4), Color(0xFF0F766E))
    val Indigo = listOf(Color(0xFFA5B4FC), Color(0xFF4338CA))
    val Rose = listOf(Color(0xFFFDA4AF), Color(0xFFE11D48))
    val Amber = listOf(Color(0xFFFDE68A), Color(0xFFD97706))
    val Green = listOf(Color(0xFF86EFAC), Color(0xFF16A34A))
    val Sky = listOf(Color(0xFF7DD3FC), Color(0xFF0284C7))
    val Violet = listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED))
    val Slate = listOf(Color(0xFFCBD5E1), Color(0xFF475569))
    val Hero = listOf(Color(0xFFF97316), Color(0xFFEA580C), Color(0xFFC2410C))

    fun forCategory(c: String) = when (c) {
        "Market" -> Green; "Restoran" -> Orange; "Aptek" -> Sky; "Gül" -> Rose
        "Elektronika" -> Indigo; "Geyim" -> Violet; else -> Amber
    }
}

fun statusColor(s: OrderStatus): Color = when (s) {
    OrderStatus.NEW -> Color(0xFF2563EB)
    OrderStatus.ACCEPTED -> Color(0xFFD97706)
    OrderStatus.READY -> Color(0xFF7C3AED)
    OrderStatus.PICKED_UP -> Color(0xFFEA580C)
    OrderStatus.DELIVERED -> Color(0xFF16A34A)
    OrderStatus.CANCELLED -> Color(0xFF6B7280)
}
