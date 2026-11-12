package az.catdir.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.catdir.app.model.Money
import az.catdir.app.model.Order
import az.catdir.app.model.OrderStatus
import az.catdir.app.ui.theme.Gradients
import az.catdir.app.ui.theme.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun HSpace(w: Dp) = Spacer(Modifier.width(w))
@Composable fun VSpace(h: Dp) = Spacer(Modifier.height(h))

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppSnackbarHost() {
    SnackbarHost(LocalSnackbar.current) { Snackbar(it, shape = RoundedCornerShape(16.dp)) }
}

/** Qradiyent + parıltı + kölgə ilə "3D" ikon. */
@Composable
fun Icon3D(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier
            .size(size)
            .shadow(size * 0.18f, shape, ambientColor = colors.last(), spotColor = colors.last())
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.38f), 0.5f to Color.White.copy(alpha = 0.05f), 1f to Color.Transparent))
                drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f)))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.Black.copy(alpha = 0.22f), modifier = Modifier.size(size * 0.54f).offset(size * 0.02f, size * 0.05f))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.54f))
    }
}

@Composable
fun GradientCard(colors: List<Color> = Gradients.Hero, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.last(), spotColor = colors.last())
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .drawWithContent {
                drawContent()
                drawCircle(Color.White.copy(alpha = 0.09f), size.height * 0.9f, Offset(size.width * 0.95f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.06f), size.height * 0.6f, Offset(size.width * 0.75f, size.height * 1.1f))
            }
    ) { content() }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon3D(icon, Gradients.Slate, size = 72.dp)
        VSpace(16.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        VSpace(4.dp)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val c = statusColor(status)
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            HSpace(6.dp)
            Text(status.short, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

fun categoryIcon(c: String): ImageVector = when (c) {
    "Market" -> Icons.Filled.LocalGroceryStore
    "Restoran" -> Icons.Filled.Restaurant
    "Aptek" -> Icons.Filled.LocalPharmacy
    "Gül" -> Icons.Filled.LocalFlorist
    "Elektronika" -> Icons.Filled.Devices
    "Geyim" -> Icons.Filled.Checkroom
    else -> Icons.Filled.Category
}

/** Kompakt sifariş kartı (bütün rollar üçün). */
@Composable
fun OrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(order.storeName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "#${order.shortId} · ${timeAgo(order.createdAt)} · ${order.itemCount} məhsul",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(order.status)
            }
            VSpace(8.dp)
            Text(
                subtitle ?: order.items.joinToString(", ") { "${it.qty}× ${it.name}" },
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            VSpace(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(order.totalQ), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
    }
}

@Composable
fun QtyStepper(qty: Int, onMinus: () -> Unit, onPlus: () -> Unit, plusEnabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Filled.Remove, "Azalt")
        }
        Text("$qty", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        FilledIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Filled.Add, "Artır")
        }
    }
}

private val AZ: Locale = Locale.forLanguageTag("az-AZ")
fun clock(ms: Long): String = SimpleDateFormat("HH:mm", AZ).format(Date(ms))
fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", AZ).format(Date(ms))

fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - ms) / 60_000
    return when {
        min < 1 -> "indicə"
        min < 60 -> "$min dəq əvvəl"
        min < 24 * 60 -> "${min / 60} saat əvvəl"
        else -> dateTime(ms)
    }
}

fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
}
