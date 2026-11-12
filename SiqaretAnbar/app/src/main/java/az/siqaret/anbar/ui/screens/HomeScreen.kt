package az.siqaret.anbar.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.SortOption
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.data.totalBoxes
import az.siqaret.anbar.ui.components.AnimatedValueText
import az.siqaret.anbar.ui.components.EmptyState
import az.siqaret.anbar.ui.components.GradientCard
import az.siqaret.anbar.ui.components.HSpace
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.InfoPill
import az.siqaret.anbar.ui.components.StatCard
import az.siqaret.anbar.ui.components.ThemeMenuButton
import az.siqaret.anbar.ui.components.VSpace
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.ui.theme.NegativeColor
import az.siqaret.anbar.ui.theme.PositiveColor
import az.siqaret.anbar.util.FirmSummary
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryCalc

val TabScreenInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: InventoryViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val products by vm.visibleProducts.collectAsStateWithLifecycle()
    val all by vm.products.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val firms by vm.firms.collectAsStateWithLifecycle()
    val firmFilter by vm.firmFilter.collectAsStateWithLifecycle()
    val firmNames = remember(firms) { firms.associate { it.id to it.name } }

    var deleting by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = TabScreenInsets,
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Siqaret Anbarı", style = MaterialTheme.typography.titleLarge)
                        Text(
                            Fmt.longDate(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { ThemeMenuButton(theme, vm::setTheme) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Məhsul əlavə et") },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                GradientCard {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ümumi stok dəyəri", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                            VSpace(6.dp)
                            AnimatedValueText(
                                Fmt.money(stats.totalValue),
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                            )
                            VSpace(4.dp)
                            Text(
                                "${Fmt.count(stats.totalBlocks)} blok · ${Fmt.count(stats.totalBoxes)} paçka",
                                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon3D(Icons.Filled.AccountBalanceWallet, Gradients.Amber, size = 60.dp)
                    }
                }
            }
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Filled.Category, Gradients.Violet, "Məhsul sayı", Fmt.count(stats.productCount), Modifier.weight(1f))
                    StatCard(Icons.Filled.ViewInAr, Gradients.Blue, "Ümumi blok", Fmt.count(stats.totalBlocks), Modifier.weight(1f))
                    StatCard(Icons.Filled.Inventory2, Gradients.Teal, "Ümumi paçka", Fmt.count(stats.totalBoxes), Modifier.weight(1f))
                }
            }
            item(key = "search") {
                SearchBarRow(query, { vm.query.value = it }, sort, { vm.sort.value = it })
            }
            if (firms.isNotEmpty()) {
                item(key = "firmFilter") {
                    FirmFilterRow(
                        firms = firms,
                        selected = firmFilter,
                        showNoFirm = all.any { it.firmId == null || it.firmId !in firmNames },
                        onSelect = { vm.firmFilter.value = it },
                    )
                }
            }
            if (products.isEmpty()) {
                item(key = "empty") {
                    if (all.isEmpty()) {
                        EmptyState(
                            Icons.Filled.SmokingRooms, "Hələ məhsul yoxdur",
                            "\"Məhsul əlavə et\" düyməsi ilə ilk siqareti əlavə edin. Ad, qiymət və blok sayını yazın — qalanını proqram hesablayacaq.",
                        )
                    } else if (query.isNotBlank()) {
                        EmptyState(Icons.Filled.SearchOff, "Heç nə tapılmadı", "\"$query\" adına uyğun məhsul yoxdur")
                    } else {
                        EmptyState(Icons.Filled.Business, "Bu firmada siqaret yoxdur", "Başqa firma seçin və ya yeni siqaret əlavə edin")
                    }
                }
            } else {
                item(key = "count") {
                    Text(
                        "${products.size} məhsul · ${sort.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(products, key = { it.id }) { p ->
                ProductCard(
                    product = p,
                    firmName = p.firmId?.let { firmNames[it] },
                    onClick = { onEdit(p.id) },
                    onPlus = { vm.adjustStock(p, 1) },
                    onMinus = { vm.adjustStock(p, -1) },
                    onAdjust = { adjusting = p },
                    onEdit = { onEdit(p.id) },
                    onDelete = { deleting = p },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    deleting?.let { p ->
        ProductDeleteDialog(p, onDismiss = { deleting = null }, onConfirm = { vm.deleteProduct(p); deleting = null })
    }
    adjusting?.let { p ->
        // ən son vəziyyəti göstər
        val current = all.firstOrNull { it.id == p.id } ?: p
        StockDialog(current, onDismiss = { adjusting = null }, onApply = { d -> vm.adjustStock(current, d); adjusting = null })
    }
}

@Composable
private fun SearchBarRow(
    query: String, onQuery: (String) -> Unit,
    sort: SortOption, onSort: (SortOption) -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ada görə axtar…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, "Təmizlə") }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        HSpace(8.dp)
        Box {
            FilledTonalIconButton(
                onClick = { sortOpen = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                Text(
                    "Sıralama", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SortOption.entries.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label) },
                        leadingIcon = { Icon(Icons.Filled.SwapVert, null) },
                        trailingIcon = { if (o == sort) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onSort(o); sortOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    firmName: String?,
    onClick: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(Icons.Filled.SmokingRooms, Gradients.forName(product.name), size = 50.dp)
                HSpace(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        product.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (firmName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Business, null, modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            HSpace(4.dp)
                            Text(
                                firmName, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        "Blok: ${Fmt.money(product.blockPrice)}  ·  Paçka: ${Fmt.price(product.boxPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menyu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Redaktə et") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Stok miqdarını dəyiş") }, leadingIcon = { Icon(Icons.Filled.SwapVert, null) },
                            onClick = { menu = false; onAdjust() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Blok sayı", Fmt.count(product.blockCount), Modifier.weight(0.8f))
                InfoPill("Paçka sayı", Fmt.count(product.totalBoxes), Modifier.weight(0.9f))
                InfoPill("Ümumi məbləğ", Fmt.money(product.totalAmount), Modifier.weight(1.5f), highlight = true)
            }
            VSpace(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onMinus,
                    enabled = product.blockCount > 0,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = NegativeColor.copy(alpha = 0.12f), contentColor = NegativeColor,
                    ),
                ) { Icon(Icons.Filled.Remove, "1 blok azalt") }
                Column(
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Stok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedValueText("${Fmt.count(product.blockCount)} blok", MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(
                    onClick = onPlus,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = PositiveColor.copy(alpha = 0.14f), contentColor = PositiveColor,
                    ),
                ) { Icon(Icons.Filled.Add, "1 blok artır") }
            }
        }
    }
}

@Composable
fun ProductDeleteDialog(p: Product, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon3D(Icons.Filled.Delete, Gradients.Red, size = 52.dp) },
        title = { Text("Məhsul silinsin?") },
        text = {
            Text("\"${p.name}\" (${Fmt.count(p.blockCount)} blok, ${Fmt.money(p.totalAmount)}) siyahıdan silinəcək. Tarixçədə qeyd qalacaq.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Sil") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ləğv et") } },
    )
}

@Composable
fun StockDialog(p: Product, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("1") }
    val amount = text.toIntOrNull() ?: 0
    val afterIn = p.blockCount.toLong() + amount
    val afterOut = p.blockCount.toLong() - amount
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon3D(Icons.Filled.SwapVert, Gradients.Blue, size = 52.dp) },
        title = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "Hazırda: ${Fmt.count(p.blockCount)} blok (${Fmt.count(p.totalBoxes)} paçka)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VSpace(12.dp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = Fmt.sanitizeInt(it) },
                    label = { Text("Blok sayı") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 50).forEach { n ->
                        AssistChip(onClick = { text = n.toString() }, label = { Text("$n") })
                    }
                }
                VSpace(8.dp)
                Text(
                    "Artırsanız: ${Fmt.count(afterIn)} blok · ${Fmt.money(InventoryCalc.totalAmount(afterIn.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), p.blockPrice))}",
                    style = MaterialTheme.typography.bodySmall, color = PositiveColor,
                )
                Text(
                    if (afterOut >= 0) "Azaltsanız: ${Fmt.count(afterOut)} blok · ${Fmt.money(InventoryCalc.totalAmount(afterOut.toInt(), p.blockPrice))}"
                    else "Azaltmaq üçün stok kifayət etmir",
                    style = MaterialTheme.typography.bodySmall, color = NegativeColor,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onApply(-amount) },
                    enabled = amount > 0 && afterOut >= 0,
                    modifier = Modifier.height(48.dp),
                ) { Icon(Icons.Filled.Remove, null); HSpace(4.dp); Text("Azalt") }
                Button(
                    onClick = { onApply(amount) },
                    enabled = amount > 0,
                    modifier = Modifier.height(48.dp),
                ) { Icon(Icons.Filled.Add, null); HSpace(4.dp); Text("Artır") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}

@Composable
private fun FirmFilterRow(
    firms: List<az.siqaret.anbar.data.Firm>,
    selected: Long?,
    showNoFirm: Boolean,
    onSelect: (Long?) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null, onClick = { onSelect(null) },
            label = { Text("Bütün firmalar") }, shape = RoundedCornerShape(14.dp), colors = colors,
        )
        firms.forEach { f ->
            FilterChip(
                selected = selected == f.id, onClick = { onSelect(f.id) },
                label = { Text(f.name, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Business, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(14.dp), colors = colors,
            )
        }
        if (showNoFirm) {
            FilterChip(
                selected = selected == InventoryViewModel.NO_FIRM_FILTER,
                onClick = { onSelect(InventoryViewModel.NO_FIRM_FILTER) },
                label = { Text(FirmSummary.NO_FIRM) }, shape = RoundedCornerShape(14.dp), colors = colors,
            )
        }
    }
}
