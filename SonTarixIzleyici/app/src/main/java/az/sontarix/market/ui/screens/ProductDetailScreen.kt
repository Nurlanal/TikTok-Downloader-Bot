package az.sontarix.market.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.data.Product
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.EmptyState
import az.sontarix.market.ui.components.ImageViewerDialog
import az.sontarix.market.ui.components.ProductCard
import az.sontarix.market.ui.components.ProductImage
import az.sontarix.market.ui.components.SectionTitle
import az.sontarix.market.ui.components.StatusChip
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus

@Composable
fun ProductDetailScreen(
    vm: MainViewModel,
    productId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddBatch: (Long) -> Unit,
    onOpenProduct: (Long) -> Unit
) {
    val product by remember(productId) { vm.repo.observeProduct(productId) }.collectAsState(initial = null)
    val settings by vm.settings.collectAsStateWithLifecycle()
    var loadedOnce by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewImage by remember { mutableStateOf(false) }

    val p = product
    if (p == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            if (loadedOnce) EmptyState(Icons.Outlined.SearchOff, "Məhsul tapılmadı", "Bu məhsul silinib və ya mövcud deyil.")
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(600); loadedOnce = true }
        return
    }
    val siblings by remember(p.id, p.barcode, p.name) { vm.repo.observeSiblings(p) }.collectAsState(initial = emptyList())
    val days = p.daysLeft(DateUtils.today())
    val status = ExpiryStatus.of(days, settings.warningDays)
    val color = StatusColors.of(status)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            ProductImage(p.imageFile, Modifier.fillMaxSize().clickable(enabled = p.imageFile != null) { viewImage = true }, iconSize = 72.dp)
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
            Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth()) {
                FilledIconButton(onClick = onBack, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = { onEdit(p.id) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f))) {
                    Icon(Icons.Filled.Edit, "Redaktə et", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { confirmDelete = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f))) {
                    Icon(Icons.Filled.Delete, "Sil", tint = Color.White)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(p.name, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status)
                Spacer(Modifier.width(10.dp))
                Text(DateUtils.remainingText(days), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow(Icons.Filled.CalendarMonth, "Son istifadə tarixi", DateUtils.format(p.expiryDate), color)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Miqdar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${p.quantity} ədəd", style = MaterialTheme.typography.titleMedium)
                        }
                        FilledIconButton(onClick = { vm.updateQuantity(p, p.quantity - 1) }, enabled = p.quantity > 0) { Icon(Icons.Filled.Remove, "Azalt") }
                        FilledIconButton(onClick = { vm.updateQuantity(p, p.quantity + 1) }) { Icon(Icons.Filled.Add, "Artır") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.QrCode2, "Barkod", p.barcode.ifBlank { "—" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.Category, "Kateqoriya", p.category.ifBlank { "—" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.Layers, "Partiya nömrəsi", p.batchNumber.ifBlank { "—" })
                    if (p.note.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(Icons.AutoMirrored.Filled.Notes, "Qeyd", p.note)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.History, "Əlavə edilib / yenilənib",
                        "${DateUtils.formatMillis(p.createdAt)}  •  ${DateUtils.formatMillis(p.updatedAt)}")
                }
            }

            NotificationPlan(p, settings.thresholds, settings.notifyExpired, settings.notificationsEnabled)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onEdit(p.id) }, modifier = Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Redaktə et")
                }
                FilledTonalButton(onClick = { onAddBatch(p.id) }, modifier = Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Filled.Layers, null); Spacer(Modifier.width(6.dp)); Text("Yeni partiya")
                }
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                Text("Məhsulu sil", color = MaterialTheme.colorScheme.error)
            }

            if (siblings.isNotEmpty()) {
                SectionTitle("Bu məhsulun digər partiyaları (${siblings.size})")
                siblings.forEach { s ->
                    ProductCard(s, settings.warningDays, onClick = { onOpenProduct(s.id) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Məhsulu sil",
            message = "\"${p.name}\" və ona aid bütün bildirişlər silinəcək. Davam edilsin?",
            confirmText = "Sil", destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(p); onBack() },
            onDismiss = { confirmDelete = false }
        )
    }
    if (viewImage && p.imageFile != null) ImageViewerDialog(p.imageFile) { viewImage = false }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Bu məhsul üçün planlaşdırılmış bildiriş tarixləri. */
@Composable
private fun NotificationPlan(p: Product, thresholds: List<Int>, notifyExpired: Boolean, enabled: Boolean) {
    val today = DateUtils.today()
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Bildiriş planı", style = MaterialTheme.typography.titleMedium)
            }
            if (!enabled) {
                Text("Bildirişlər parametrlərdə söndürülüb.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            val entries = thresholds.sortedDescending().map { t ->
                (if (t == 0) "Bitdiyi gün" else "$t gün əvvəl") to p.expiryDate.minusDays(t.toLong())
            } + (if (notifyExpired) listOf("Tarix keçdikdə" to p.expiryDate.plusDays(1)) else emptyList())
            entries.forEach { (label, date) ->
                val past = date.isBefore(today)
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (past) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(10.dp))
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(DateUtils.format(date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
