package az.rafiqmarket.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.OrderStatus

/** Rafiq Market loqosu: alış-veriş çantası + "R" */
@Composable
fun RafiqLogo(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(12.dp, RoundedCornerShape(size * 0.3f), spotColor = RafiqColors.Green700)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(RafiqColors.Green500, RafiqColors.Green800))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.62f)) {
            val w = this.size.width; val h = this.size.height
            val stroke = w * 0.07f
            // sap
            drawArc(Color.White, 180f, 180f, false, Offset(w * 0.3f, h * 0.02f), Size(w * 0.4f, h * 0.4f), style = Stroke(stroke))
            // çanta
            drawRoundRect(Color.White, Offset(w * 0.08f, h * 0.22f), Size(w * 0.84f, h * 0.76f), CornerRadius(w * 0.14f))
        }
        Text("R", color = RafiqColors.Green700, fontWeight = FontWeight.Black, fontSize = (size.value * 0.34f).sp,
            modifier = Modifier.padding(top = size * 0.12f))
    }
}

@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RafiqLogo(36.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Rafiq Market", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "3D" görünüşlü ikon plitəsi: qradiyent, işıq parıltısı və kölgə */
@Composable
fun Icon3D(emoji: String, color: Color, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(size * 0.32f), spotColor = color, ambientColor = color)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), color), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.value * 0.6f, size.value * 0.4f), radius = size.value * 1.2f)
            )
        )
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun IconTile3D(icon: ImageVector, color: Color, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).shadow(6.dp, RoundedCornerShape(size * 0.3f), spotColor = color)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.7f), color))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Yenidən yoxla") }
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier.fillMaxSize(), action: @Composable (() -> Unit)? = null) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon3D(emoji, MaterialTheme.colorScheme.primary, 88.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun QuantityStepper(quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit, plusEnabled: Boolean = true, compact: Boolean = false) {
    val h = if (compact) 34.dp else 40.dp
    Row(
        Modifier.height(h).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(h)) {
            Icon(if (quantity <= 1) Icons.Outlined.Delete else Icons.Default.Remove, "Azalt", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        Text("$quantity", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 18.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(h)) {
            Icon(Icons.Default.Add, "Artır", tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (plusEnabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

fun statusColor(status: String): Color = when (status) {
    OrderStatus.DELIVERED -> Color(0xFF2E7D32)
    OrderStatus.CANCELLED -> Color(0xFFD32F2F)
    OrderStatus.PENDING_PAYMENT -> Color(0xFF8D6E63)
    OrderStatus.PLACED -> Color(0xFFFF8F00)
    OrderStatus.ACCEPTED, OrderStatus.PREPARING -> Color(0xFF1E88E5)
    else -> Color(0xFF7B1FA2)
}

@Composable
fun StatusChip(status: String, label: String? = null) {
    val c = statusColor(status)
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(label ?: OrderStatus.label(status), color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 8 mərhələli sifariş zaman xətti */
@Composable
fun OrderTimeline(status: String, times: Map<String, String>) {
    if (status == OrderStatus.CANCELLED) {
        StatusChip(status, "Sifariş ləğv edildi"); return
    }
    val currentIdx = OrderStatus.STEPS.indexOf(status)
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column {
        OrderStatus.STEPS.forEachIndexed { i, step ->
            val done = i <= currentIdx
            val current = i == currentIdx && step != OrderStatus.DELIVERED
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(22.dp).scale(if (current) pulse.value else 1f).clip(CircleShape)
                            .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                    if (i < OrderStatus.STEPS.lastIndex) {
                        Box(Modifier.width(2.dp).height(22.dp).background(if (i < currentIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f).padding(top = 1.dp)) {
                    Text("${i + 1}. ${OrderStatus.label(step)}", modifier = Modifier.weight(1f),
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    times[step]?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun InfoBanner(text: String, icon: ImageVector = Icons.Outlined.Info, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, subtitle: String? = null) {
    ElevatedCard(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp)) {
            IconTile3D(icon, color, 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** Snackbar mesajlarını göstərmək üçün sadə köməkçi */
@Composable
fun MessageEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) { host.showSnackbar(message); onShown() }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Bəli", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

@Composable
fun FadeIn(visible: Boolean = true, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}
