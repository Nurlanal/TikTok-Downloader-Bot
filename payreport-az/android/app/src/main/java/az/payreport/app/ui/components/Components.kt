package az.payreport.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentSource
import az.payreport.app.data.db.PaymentStatus
import az.payreport.app.ui.theme.AppTheme
import az.payreport.app.ui.theme.MoneyStyle

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, border = border, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, colors = colors, border = border, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.height(12.dp))
        Text(value, style = MoneyStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val extra = AppTheme.extra
    val (text, fg, bg) = when (status) {
        PaymentStatus.CONFIRMED -> Triple("Təsdiqlənib", extra.positive, extra.positiveContainer)
        PaymentStatus.PENDING -> Triple("Gözləyir", extra.warning, extra.warningContainer)
        PaymentStatus.CANCELLED -> Triple("Ləğv edilib", extra.danger, extra.dangerContainer)
        else -> Triple(status, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

fun sourceIcon(source: String): ImageVector = when (source) {
    PaymentSource.PDF -> Icons.Outlined.PictureAsPdf
    PaymentSource.IMAGE -> Icons.Outlined.Image
    PaymentSource.MANUAL -> Icons.Outlined.Edit
    PaymentSource.TEXT -> Icons.Outlined.Email
    else -> Icons.AutoMirrored.Outlined.ReceiptLong
}

fun sourceName(source: String): String = when (source) {
    PaymentSource.PDF -> "PDF"
    PaymentSource.IMAGE -> "Şəkil (OCR)"
    PaymentSource.MANUAL -> "Əl ilə"
    PaymentSource.TEXT -> "Email mətni"
    else -> source
}

@Composable
fun PaymentRow(p: PaymentEntity, modifier: Modifier = Modifier, showPhone: Boolean = true, onClick: (() -> Unit)? = null) {
    val extra = AppTheme.extra
    val cancelled = p.status == PaymentStatus.CANCELLED
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(
                when (p.status) {
                    PaymentStatus.CONFIRMED -> extra.positiveContainer
                    PaymentStatus.PENDING -> extra.warningContainer
                    else -> extra.dangerContainer
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                sourceIcon(p.source), null, modifier = Modifier.size(20.dp),
                tint = when (p.status) {
                    PaymentStatus.CONFIRMED -> extra.positive
                    PaymentStatus.PENDING -> extra.warning
                    else -> extra.danger
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (showPhone) PhoneUtils.format(p.phone) else Fmt.dateTime(p.paidAt),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showPhone) Fmt.dateTime(p.paidAt) else (p.txnId?.let { "ID: $it" } ?: sourceName(p.source)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (p.status == PaymentStatus.CONFIRMED) "+" else "") + Fmt.azn(p.amountMinor),
                style = MoneyStyle.copy(fontSize = 15.sp),
                color = when {
                    cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                    p.status == PaymentStatus.CONFIRMED -> extra.positive
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            if (p.status != PaymentStatus.CONFIRMED) {
                Spacer(Modifier.height(2.dp))
                StatusChip(p.status)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f))
    }
}

@Composable
fun ThinDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/**
 * Sütunlu qrafik (Compose Canvas). Sütuna toxunduqda dəyəri göstərilir.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    barColor: Color = AppTheme.extra.positive,
    highlightLast: Boolean = true,
    labelEvery: Int = 1,
    valueFormatter: (Long) -> String = { Fmt.azn(it) },
) {
    var selected by remember(values) { mutableIntStateOf(if (highlightLast) values.lastIndex else -1) }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dim = barColor.copy(alpha = 0.35f)
    Column(modifier) {
        Text(
            if (selected in values.indices) "${labels[selected]} · ${valueFormatter(values[selected])}" else " ",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(values) {
                detectTapGestures { offset ->
                    if (values.isNotEmpty()) {
                        val slot = size.width / values.size
                        selected = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                    }
                }
            }
        ) {
            if (values.isEmpty()) return@Canvas
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            for (i in 0..3) {
                val y = size.height * i / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = dash)
            }
            val slot = size.width / values.size
            val bw = (slot * 0.62f).coerceAtMost(48f)
            values.forEachIndexed { i, v ->
                val h = if (v == 0L) 4f else (v.toFloat() / max) * (size.height - 6f)
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(
                    color = if (i == selected) barColor else dim,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % labelEvery == 0 || i == labels.lastIndex) l else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}
