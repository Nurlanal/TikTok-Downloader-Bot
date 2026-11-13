package az.sontarix.market.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.transfer.DuplicateAction
import az.sontarix.market.transfer.ImportItem
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.EmptyState
import az.sontarix.market.ui.components.Icon3D
import az.sontarix.market.ui.components.ProductImage
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import coil.compose.AsyncImage

@Composable
fun ImportPreviewScreen(vm: MainViewModel, onFinished: () -> Unit, onBack: () -> Unit) {
    val session by vm.importSession.collectAsStateWithLifecycle()
    val restoreMode by vm.restoreMode.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = session

    BackHandler { onBack() }

    if (s == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onFinished) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            EmptyState(Icons.Filled.Inventory2, "İdxal sessiyası yoxdur", "Faylı yenidən seçin.")
        }
        return
    }

    // null = seçilməyib (idxal edilməyəcək)
    val actions = remember(s.id) {
        mutableStateMapOf<Int, DuplicateAction>().apply {
            s.items.forEach { put(it.index, if (it.duplicate != null) DuplicateAction.KEEP_EXISTING else DuplicateAction.ADD_SEPARATE) }
        }
    }
    var confirm by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var showWarnings by remember { mutableStateOf(false) }
    var bulkMenu by remember { mutableStateOf(false) }
    val duplicates = s.items.count { it.duplicate != null }
    val toAdd = s.items.count { val a = actions[it.index]; a == DuplicateAction.ADD_SEPARATE || (it.duplicate == null && a != null && a != DuplicateAction.SKIP) }
    val toUpdate = s.items.count { it.duplicate != null && actions[it.index] == DuplicateAction.UPDATE }
    val skipped = s.items.size - toAdd - toUpdate

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Column(Modifier.weight(1f)) {
                    Text(if (restoreMode) "Ehtiyat nüsxədən bərpa" else "İdxal önizləməsi", style = MaterialTheme.typography.titleLarge)
                    Text("${s.sourceName} • ${s.format} • ${s.items.size} məhsul", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("✅ Fayl yoxlanıldı və təhlükəsizdir", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Məhsul: ${s.items.size} • Şəkilli: ${s.items.count { it.stagedImage != null }} • Təkrar: $duplicates",
                            color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        if (s.warnings.isNotEmpty()) {
                            TextButton(onClick = { showWarnings = !showWarnings }) {
                                Icon(Icons.Filled.WarningAmber, null, tint = StatusColors.Orange)
                                Spacer(Modifier.width(6.dp))
                                Text("${s.warnings.size} xəbərdarlıq ${if (showWarnings) "▲" else "▼"}")
                            }
                            if (showWarnings) s.warnings.take(50).forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            if (restoreMode) {
                item {
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon3D(Icons.Filled.Restore, StatusColors.Red, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Tam bərpa", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Hazırkı ${products.size} məhsul silinəcək və ehtiyat nüsxədəki ${s.items.size} məhsul ilə əvəz olunacaq. " +
                                "Əməliyyat yarımçıq qalarsa, mövcud məlumatlar dəyişməz qalır.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { confirmRestore = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Bütün məlumatları bərpa et") }
                            TextButton(onClick = { vm.restoreMode.value = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Əvəzinə seçərək idxal et (birləşdir)")
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { s.items.forEach { actions[it.index] = if (it.duplicate != null) DuplicateAction.KEEP_EXISTING else DuplicateAction.ADD_SEPARATE } }) { Text("Hamısını seç") }
                        TextButton(onClick = { s.items.forEach { actions[it.index] = DuplicateAction.SKIP } }) { Text("Heç birini") }
                        Spacer(Modifier.weight(1f))
                        if (duplicates > 0) {
                            Box {
                                OutlinedButton(onClick = { bulkMenu = true }) { Text("Təkrarlar üçün") }
                                DropdownMenu(bulkMenu, onDismissRequest = { bulkMenu = false }) {
                                    DuplicateAction.entries.forEach { a ->
                                        DropdownMenuItem(text = { Text(a.label) }, onClick = {
                                            s.items.filter { it.duplicate != null }.forEach { actions[it.index] = a }
                                            bulkMenu = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                items(s.items, key = { it.index }) { item ->
                    ImportRow(item, actions[item.index] ?: DuplicateAction.SKIP, settings.warningDays) { actions[item.index] = it }
                }
            }
        }
        if (!restoreMode) {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Text("Yeni: $toAdd • Yenilənəcək: $toUpdate • Ötürüləcək: $skipped", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { confirm = true }, enabled = !busy && (toAdd + toUpdate) > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("İdxalı təsdiqlə (${toAdd + toUpdate})")
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "İdxalı təsdiqlə",
            message = "$toAdd yeni məhsul əlavə ediləcək, $toUpdate məhsul yenilənəcək. Bildirişlər avtomatik planlaşdırılacaq.",
            confirmText = "İdxal et",
            onConfirm = {
                confirm = false
                val selection = s.items.mapNotNull { item ->
                    val a = actions[item.index] ?: DuplicateAction.SKIP
                    if (a == DuplicateAction.SKIP || (item.duplicate != null && a == DuplicateAction.KEEP_EXISTING)) null else item.index to a
                }.toMap()
                vm.commitImport(selection) { onFinished() }
            },
            onDismiss = { confirm = false }
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Bərpanı təsdiqlə", message = "Bütün cari məhsullar ehtiyat nüsxə ilə əvəz olunacaq. Davam edilsin?",
            confirmText = "Bərpa et", destructive = true,
            onConfirm = { confirmRestore = false; vm.restore { onFinished() } },
            onDismiss = { confirmRestore = false }
        )
    }
}

@Composable
private fun ImportRow(item: ImportItem, action: DuplicateAction, warningDays: Int, onAction: (DuplicateAction) -> Unit) {
    val selected = action != DuplicateAction.SKIP
    val days = DateUtils.daysBetween(DateUtils.today(), item.expiry)
    val status = ExpiryStatus.of(days, warningDays)
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (selected) 3.dp else 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                onAction(if (selected) DuplicateAction.SKIP else if (item.duplicate != null) DuplicateAction.KEEP_EXISTING else DuplicateAction.ADD_SEPARATE)
            }) {
                Icon(if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (item.stagedImage != null) AsyncImage(model = item.stagedImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Son tarix: ${DateUtils.format(item.expiry)} • Say: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                    if (item.barcode.isNotBlank()) Text("Barkod: ${item.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.batchNumber.isNotBlank()) Text("Partiya: ${item.batchNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.label + " • " + DateUtils.remainingText(days), style = MaterialTheme.typography.labelSmall, color = StatusColors.of(status), fontWeight = FontWeight.Bold)
                }
            }
            if (item.otherBatches > 0 && item.duplicate == null) {
                Text("ℹ️ Bu barkodla ${item.otherBatches} fərqli partiya artıq mövcuddur — yeni partiya kimi əlavə olunacaq.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            item.duplicate?.let { d ->
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StatusColors.Orange.copy(alpha = 0.12f)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, null, tint = StatusColors.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bu məhsul artıq bazada var", fontWeight = FontWeight.Bold, color = StatusColors.Orange)
                    }
                    Text("Səbəb: " + item.duplicateReasons.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        ProductImage(d.imageFile, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), iconSize = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Mövcud: ${d.name} • ${DateUtils.format(d.expiryDate)} • Say: ${d.quantity}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Seçim: ${action.label}") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DuplicateAction.entries.forEach { a ->
                                DropdownMenuItem(text = { Text(a.label, fontWeight = if (a == action) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { onAction(a); menu = false })
                            }
                        }
                    }
                }
            }
        }
    }
}
