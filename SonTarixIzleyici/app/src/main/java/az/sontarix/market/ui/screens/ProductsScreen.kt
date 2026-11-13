package az.sontarix.market.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.data.Product
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.EmptyState
import az.sontarix.market.ui.components.ImageViewerDialog
import az.sontarix.market.ui.components.ProductCard
import az.sontarix.market.util.DateUtils

enum class StatusFilter(val key: String, val label: String) {
    ALL("all", "Hamısı"),
    URGENT("urgent", "Təcili"),
    NORMAL("normal", "Normal"),
    APPROACHING("approaching", "Yaxınlaşan"),
    WEEK("week", "7 gün və az"),
    TODAY("today", "Bu gün bitən"),
    EXPIRED("expired", "Müddəti keçmiş");

    fun matches(days: Long, warningDays: Int): Boolean = when (this) {
        ALL -> true
        URGENT -> days <= 7
        NORMAL -> days > warningDays
        APPROACHING -> days in 1..warningDays.toLong()
        WEEK -> days in 0..7
        TODAY -> days == 0L
        EXPIRED -> days < 0
    }

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: ALL }
}

enum class SortOrder(val label: String) {
    EXPIRY_ASC("Son tarix (yaxın → uzaq)"),
    EXPIRY_DESC("Son tarix (uzaq → yaxın)"),
    NAME("Ad (A → Z)"),
    QUANTITY("Miqdar (çox → az)"),
    NEWEST("Son əlavə edilənlər")
}

@Composable
fun ProductsScreen(
    vm: MainViewModel,
    initialFilter: String,
    scannedBarcode: String?,
    onScannedConsumed: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    onExportSelected: (List<Long>) -> Unit
) {
    val products by vm.products.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable(initialFilter) { mutableStateOf(StatusFilter.of(initialFilter)) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.EXPIRY_ASC) }
    var sortMenu by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf<List<Product>?>(null) }
    var viewImage by remember { mutableStateOf<String?>(null) }
    val selectionMode = selection.isNotEmpty()

    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) { query = scannedBarcode; onScannedConsumed() }
    }
    BackHandler(enabled = selectionMode) { selection = emptySet() }

    val today = DateUtils.today()
    val filtered = remember(products, query, status, category, sort, settings.warningDays) {
        val q = query.trim()
        products.asSequence()
            .filter { status.matches(it.daysLeft(today), settings.warningDays) }
            .filter { category == null || it.category == category }
            .filter {
                q.isEmpty() || it.name.contains(q, true) || it.barcode.contains(q) ||
                    it.batchNumber.contains(q, true) || it.note.contains(q, true) || it.category.contains(q, true)
            }
            .let { seq ->
                when (sort) {
                    SortOrder.EXPIRY_ASC -> seq.sortedBy { it.expiryEpochDay }
                    SortOrder.EXPIRY_DESC -> seq.sortedByDescending { it.expiryEpochDay }
                    SortOrder.NAME -> seq.sortedBy { it.name.lowercase() }
                    SortOrder.QUANTITY -> seq.sortedByDescending { it.quantity }
                    SortOrder.NEWEST -> seq.sortedByDescending { it.createdAt }
                }
            }.toList()
    }
    val usedCategories = remember(categories, products) {
        (categories.map { it.name } + products.map { it.category }).filter { it.isNotBlank() }.distinct().sorted()
    }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                AnimatedContent(selectionMode, label = "topbar") { sel ->
                    if (sel) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Ləğv et") }
                            Text("${selection.size} seçilib", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { selection = filtered.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Hamısını seç") }
                            IconButton(onClick = { onExportSelected(selection.toList()); selection = emptySet() }) { Icon(Icons.Filled.IosShare, "Seçilənləri ixrac et") }
                            IconButton(onClick = { confirmDelete = products.filter { it.id in selection } }) {
                                Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Məhsullar", style = MaterialTheme.typography.titleLarge)
                                Text("${filtered.size} / ${products.size} partiya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOrder.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { sort = s; sortMenu = false }
                                    )
                                }
                            }
                            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Əlavə et") }
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ad, barkod, partiya ilə axtar...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Təmizlə") }
                            IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, "Barkod skan et") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatusFilter.entries.toList()) { f ->
                        FilterChip(selected = status == f, onClick = { status = f }, label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                    }
                }
                if (usedCategories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Bütün kateqoriyalar") }) }
                        items(usedCategories) { c ->
                            FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            if (products.isEmpty()) {
                EmptyState(Icons.Filled.Add, "Məhsul yoxdur", "Yeni məhsul əlavə etmək üçün aşağıdakı düyməyə toxunun.") {
                    Button(onClick = onAdd) { Text("Məhsul əlavə et") }
                }
            } else {
                EmptyState(Icons.Outlined.SearchOff, "Nəticə tapılmadı", "Axtarış və ya filtr şərtlərini dəyişin.")
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { p ->
                    ProductCard(
                        product = p,
                        warningDays = settings.warningDays,
                        modifier = Modifier.animateItem(),
                        selectionMode = selectionMode,
                        selected = p.id in selection,
                        onClick = {
                            if (selectionMode) selection = if (p.id in selection) selection - p.id else selection + p.id
                            else onOpenProduct(p.id)
                        },
                        onLongClick = { selection = selection + p.id },
                        onImageClick = if (selectionMode) null else ({ viewImage = p.imageFile })
                    )
                }
                item { Spacer(Modifier.width(1.dp).height(24.dp)) }
            }
        }
    }

    confirmDelete?.let { list ->
        ConfirmDialog(
            title = "Məhsulları sil",
            message = "${list.size} məhsul və onlara aid bildirişlər silinəcək. Bu əməliyyat geri qaytarıla bilməz.",
            confirmText = "Sil", destructive = true,
            onConfirm = { vm.deleteMany(list); selection = emptySet(); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
    viewImage?.let { ImageViewerDialog(it) { viewImage = null } }
}
