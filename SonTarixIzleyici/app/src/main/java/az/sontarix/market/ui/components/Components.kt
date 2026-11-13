package az.sontarix.market.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import az.sontarix.market.data.Product
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import az.sontarix.market.util.ImageStorage
import coil.compose.AsyncImage

/** Qradiyent, işıq parıltısı və kölgə ilə "3D" ikon nişanı. */
@Composable
fun Icon3D(icon: ImageVector, color: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val light = lerp(color, Color.White, 0.35f)
    val dark = lerp(color, Color.Black, 0.30f)
    Box(
        modifier
            .size(size)
            .shadow(elevation = size / 6, shape = RoundedCornerShape(size / 3.2f), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(listOf(light, color, dark), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center
    ) {
        // Üst parıltı — həcm effekti
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .height(size * 0.42f)
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.0f))))
        )
        Icon(
            icon, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(size * 0.52f).graphicsLayer { shadowElevation = 6f }
        )
    }
}

/** Basıldıqda yumşaq şəkildə kiçilən modifikator. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f), label = "press")
    return this.scale(s)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatCard(
    title: String, value: String, icon: ImageVector, color: Color,
    modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .pressScale(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = onClick != null, onClick = { onClick?.invoke() }),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(color.copy(alpha = 0.10f), Color.Transparent)))) {
            Column(Modifier.padding(16.dp)) {
                Icon3D(icon, color, 44.dp)
                Spacer(Modifier.height(12.dp))
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
fun StatusChip(status: ExpiryStatus, modifier: Modifier = Modifier) {
    val c = StatusColors.of(status)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProductImage(imageFile: String?, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
    val context = LocalContext.current
    val file = remember(imageFile) { ImageStorage.fileFor(context, imageFile) }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) {
            AsyncImage(model = file, contentDescription = "Məhsulun şəkli", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCard(
    product: Product,
    warningDays: Int,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null
) {
    val days = product.daysLeft(DateUtils.today())
    val status = ExpiryStatus.of(days, warningDays)
    val color = StatusColors.of(status)
    val interaction = remember { MutableInteractionSource() }
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "sel")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .border(2.dp, border, MaterialTheme.shapes.large)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 10.dp).size(26.dp)
                    )
                }
                ProductImage(
                    product.imageFile,
                    Modifier.size(76.dp).clip(MaterialTheme.shapes.medium)
                        .then(if (onImageClick != null && product.imageFile != null) Modifier.combinedClickable(onClick = onImageClick) else Modifier)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Son tarix: ${DateUtils.format(product.expiryDate)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Say: ${product.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (product.barcode.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Outlined.QrCode2, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" ${product.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (product.batchNumber.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Layers, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" Partiya: ${product.batchNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(status)
                        Text(
                            DateUtils.remainingText(days), style = MaterialTheme.typography.labelMedium,
                            color = color, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon3D(icon, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmText: String = "Bəli", dismissText: String = "Ləğv et",
    destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } }
    )
}

/** Tam ekran şəkil baxışı (iki barmaqla böyütmə dəstəyi ilə). */
@Composable
fun ImageViewerDialog(imageFile: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember(imageFile) { ImageStorage.fileFor(context, imageFile) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }) {
                if (file != null) {
                    AsyncImage(
                        model = file, contentDescription = "Şəkil", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        }
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Filled.Close, "Bağla", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
