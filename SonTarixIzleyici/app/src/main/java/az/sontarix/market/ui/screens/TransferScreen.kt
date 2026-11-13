package az.sontarix.market.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.data.HistoryEntry
import az.sontarix.market.data.OperationStatus
import az.sontarix.market.data.OperationType
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.Icon3D
import az.sontarix.market.ui.components.ProductImage
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.transfer.ExportFormat
import az.sontarix.market.transfer.ExportScope
import az.sontarix.market.util.DateUtils
import java.io.File

private enum class ScopeKind(val label: String) {
    ALL("Bütün məhsulları ixrac et"),
    SELECTED("Seçilmiş məhsulları ixrac et"),
    CATEGORY("Kateqoriyaya görə ixrac et"),
    RANGE("Tarix aralığına görə ixrac et")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(vm: MainViewModel, onImportReady: () -> Unit) {
    val context = LocalContext.current
    val products by vm.products.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pendingSelection by vm.pendingExportSelection.collectAsStateWithLifecycle()

    var scopeKind by remember { mutableStateOf(ScopeKind.ALL) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var category by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(DateUtils.today()) }
    var to by remember { mutableStateOf(DateUtils.today().plusMonths(1)) }
    var format by remember { mutableStateOf(ExportFormat.ZIP) }
    var showPicker by remember { mutableStateOf(false) }
    var datePickFor by remember { mutableStateOf<Int?>(null) }
    var fileToSave by remember { mutableStateOf<File?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSelection) {
        pendingSelection?.let { ids ->
            scopeKind = ScopeKind.SELECTED; selectedIds = ids.toSet(); vm.pendingExportSelection.value = null
        }
    }

    val usedCategories = remember(categories, products) {
        (categories.map { it.name } + products.map { it.category }).filter { it.isNotBlank() }.distinct().sorted()
    }
    val count = remember(products, scopeKind, selectedIds, category, from, to) {
        when (scopeKind) {
            ScopeKind.ALL -> products.size
            ScopeKind.SELECTED -> products.count { it.id in selectedIds }
            ScopeKind.CATEGORY -> products.count { it.category == category }
            ScopeKind.RANGE -> products.count { it.expiryEpochDay in from.toEpochDay()..to.toEpochDay() }
        }
    }
    fun scope(): Pair<ExportScope, String> = when (scopeKind) {
        ScopeKind.ALL -> ExportScope.All to "Bütün məhsullar"
        ScopeKind.SELECTED -> ExportScope.Selected(selectedIds.toList()) to "Seçilmiş məhsullar"
        ScopeKind.CATEGORY -> ExportScope.ByCategory(category) to "Kateqoriya: $category"
        ScopeKind.RANGE -> ExportScope.ByDateRange(from, to) to "Tarix: ${DateUtils.format(from)} – ${DateUtils.format(to)}"
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = fileToSave
        if (uri != null && f != null) vm.saveCopy(f, uri)
        fileToSave = null
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport(uri, restore = false, onReady = onImportReady)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport(uri, restore = true, onReady = onImportReady)
    }
    val importMimes = arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream", "text/plain", "*/*")

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("İxrac / İdxal", style = MaterialTheme.typography.titleLarge)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ───── İXRAC ─────
            item {
                SectionCard(Icons.Filled.CloudUpload, StatusColors.Indigo, "Məhsulları ixrac et",
                    "Məhsulları şəkilləri ilə birlikdə başqa market sahibinə göndərin") {
                    ScopeKind.entries.forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .selectable(selected = scopeKind == k, role = Role.RadioButton, onClick = { scopeKind = k })
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = scopeKind == k, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(k.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    when (scopeKind) {
                        ScopeKind.SELECTED -> OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(if (selectedIds.isEmpty()) "Məhsulları seç" else "${selectedIds.size} məhsul seçilib — dəyiş")
                        }
                        ScopeKind.CATEGORY -> {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 6.dp)) {
                                OutlinedTextField(
                                    value = category.ifBlank { "Kateqoriya seçin" }, onValueChange = {}, readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    usedCategories.forEach { c ->
                                        DropdownMenuItem(text = { Text("$c (${products.count { it.category == c }})") }, onClick = { category = c; expanded = false })
                                    }
                                }
                            }
                        }
                        ScopeKind.RANGE -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { datePickFor = 0 }, modifier = Modifier.weight(1f)) { Text("Başlanğıc: ${DateUtils.format(from)}") }
                            OutlinedButton(onClick = { datePickFor = 1 }, modifier = Modifier.weight(1f)) { Text("Son: ${DateUtils.format(to)}") }
                        }
                        ScopeKind.ALL -> {}
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val formats = ExportFormat.entries
                        formats.forEachIndexed { i, f ->
                            SegmentedButton(
                                selected = format == f, onClick = { format = f },
                                shape = SegmentedButtonDefaults.itemShape(i, formats.size),
                                label = { Text(when (f) { ExportFormat.ZIP -> "ZIP"; ExportFormat.JSON -> "JSON"; ExportFormat.EXCEL -> "Excel"; ExportFormat.PDF -> "PDF" }) }
                            )
                        }
                    }
                    Text(
                        when (format) {
                            ExportFormat.ZIP -> "Şəkillər və bütün məlumatlar bir faylda (tövsiyə olunur)"
                            ExportFormat.JSON -> "Məhsul məlumatları (şəkillər faylın içində)"
                            ExportFormat.EXCEL -> "Excel cədvəli — məhsul siyahısı"
                            ExportFormat.PDF -> "Rəngli PDF hesabatı — çap üçün"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("$count məhsul ixrac ediləcək", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    val canExport = count > 0 && !busy && (scopeKind != ScopeKind.CATEGORY || category.isNotBlank())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { val (s, l) = scope(); vm.export(s, format, l) { f -> context.startActivity(vm.shareIntent(f, format)) } },
                            enabled = canExport, modifier = Modifier.weight(1f).height(54.dp)
                        ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Paylaş") }
                        FilledTonalButton(
                            onClick = { val (s, l) = scope(); vm.export(s, format, l) { f -> fileToSave = f; saveLauncher.launch(f.name) } },
                            enabled = canExport, modifier = Modifier.weight(1f).height(54.dp)
                        ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Yadda saxla") }
                    }
                    Text("Paylaş: WhatsApp, Telegram, Bluetooth, Quick Share və digər proqramlar",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // ───── İDXAL ─────
            item {
                SectionCard(Icons.Filled.CloudDownload, StatusColors.Teal, "Məhsulları idxal et",
                    "Başqa istifadəçidən gələn ZIP və ya JSON faylını açın") {
                    Button(onClick = { importLauncher.launch(importMimes) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Fayl seç (ZIP / JSON)")
                    }
                    Text("Fayl yoxlanılacaq, məhsullar önizləmədə göstəriləcək və yalnız sizin təsdiqinizlə əlavə ediləcək.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // ───── EHTİYAT ─────
            item {
                SectionCard(Icons.Filled.Backup, StatusColors.Violet, "Ehtiyat nüsxə və bərpa",
                    "Bütün məlumatların (şəkillər daxil) lokal ehtiyat nüsxəsi") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { backupLauncher.launch("sontarix_ehtiyat_${DateUtils.fileStamp()}.zip") }, enabled = !busy,
                            modifier = Modifier.weight(1f).height(54.dp)) { Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(6.dp)); Text("Nüsxə yarat") }
                        OutlinedButton(onClick = { restoreLauncher.launch(importMimes) }, enabled = !busy,
                            modifier = Modifier.weight(1f).height(54.dp)) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Bərpa et") }
                    }
                }
            }
            // ───── TARİXÇƏ ─────
            item {
                val lastExport = history.firstOrNull { it.type == OperationType.EXPORT && it.status == OperationStatus.SUCCESS }
                val lastImport = history.firstOrNull { it.type == OperationType.IMPORT && (it.status == OperationStatus.SUCCESS || it.status == OperationStatus.PARTIAL) }
                SectionCard(Icons.Filled.History, StatusColors.Orange, "İxrac və idxal tarixçəsi", null,
                    trailing = { if (history.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Təmizlə") } }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("Son ixrac", lastExport?.let { DateUtils.formatMillis(it.timestamp) } ?: "—", lastExport?.let { "${it.itemCount} məhsul" } ?: "", Modifier.weight(1f))
                        SummaryTile("Son idxal", lastImport?.let { DateUtils.formatMillis(it.timestamp) } ?: "—", lastImport?.let { "${it.itemCount} məhsul" } ?: "", Modifier.weight(1f))
                    }
                    if (history.isEmpty()) {
                        Text("Hələ əməliyyat olmayıb.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            items(history.take(100), key = { it.id }) { HistoryRow(it) }
        }
    }

    if (showPicker) {
        ProductPickerDialog(vm, selectedIds, onDismiss = { showPicker = false }, onConfirm = { selectedIds = it; showPicker = false })
    }
    datePickFor?.let { which ->
        val state = rememberDatePickerState(initialSelectedDateMillis = DateUtils.toUtcMillis(if (which == 0) from else to))
        DatePickerDialog(
            onDismissRequest = { datePickFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = DateUtils.fromUtcMillis(it)
                        if (which == 0) { from = d; if (to.isBefore(d)) to = d } else { to = d; if (from.isAfter(d)) from = d }
                    }
                    datePickFor = null
                }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { datePickFor = null }) { Text("Ləğv et") } }
        ) { DatePicker(state) }
    }
    if (confirmClear) {
        ConfirmDialog("Tarixçəni təmizlə", "İxrac/idxal tarixçəsi silinəcək. Məhsullara təsir etmir.", confirmText = "Təmizlə", destructive = true,
            onConfirm = { confirmClear = false; vm.clearHistory() }, onDismiss = { confirmClear = false })
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, subtitle: String?,
    trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(icon, color, 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HistoryRow(h: HistoryEntry) {
    val color = when (h.status) {
        OperationStatus.SUCCESS -> StatusColors.Green
        OperationStatus.PARTIAL -> StatusColors.Yellow
        OperationStatus.FAILED -> StatusColors.Red
        OperationStatus.CANCELLED -> StatusColors.Orange
    }
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${h.type.label} • ${h.format} • ${h.itemCount} məhsul", style = MaterialTheme.typography.titleSmall)
                if (h.detail.isNotBlank()) Text(h.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (h.fileName.isNotBlank()) Text(h.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(DateUtils.formatMillis(h.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(h.status.label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProductPickerDialog(vm: MainViewModel, initial: Set<Long>, onDismiss: () -> Unit, onConfirm: (Set<Long>) -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    var sel by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    val list = products.filter { q.isBlank() || it.name.contains(q, true) || it.barcode.contains(q) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Məhsulları seçin (${sel.size})") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Axtar...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { sel = sel + list.map { it.id } }) { Text("Hamısını seç") }
                    TextButton(onClick = { sel = emptySet() }) { Text("Təmizlə") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { p ->
                        val checked = p.id in sel
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = if (checked) sel - p.id else sel + p.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(8.dp))
                            ProductImage(p.imageFile, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), iconSize = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(DateUtils.format(p.expiryDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(sel) }) { Text("Təsdiqlə") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ləğv et") } }
    )
}
