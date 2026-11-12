package az.siqaret.anbar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.data.InvoiceItem
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.title
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.data.totalBoxes
import az.siqaret.anbar.ui.components.AnimatedValueText
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.GradientCard
import az.siqaret.anbar.ui.components.HSpace
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.VSpace
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryCalc
import az.siqaret.anbar.util.InvoiceSummary
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Qaimə sətri (forma vəziyyəti)
// ---------------------------------------------------------------------------------------------

/**
 * Bir sətir. Paçka və blok qiyməti bir-birindən avtomatik hesablanır, amma yalnız
 * istifadəçi o biri sahəni özü yazmayıbsa (əl ilə yazılmış qiymət heç vaxt üstündən yazılmır).
 */
class InvoiceLineState(
    val key: Long,
    name: String = "",
    box: String = "",
    block: String = "",
    blocks: String = "",
    boxManual: Boolean = false,
    blockManual: Boolean = false,
) {
    var name by mutableStateOf(name)
    var box by mutableStateOf(box)
    var block by mutableStateOf(block)
    var blocks by mutableStateOf(blocks)
    var boxManual by mutableStateOf(boxManual)
    var blockManual by mutableStateOf(blockManual)

    val boxPrice: Double? get() = Fmt.parseDecimal(box)
    val blockPrice: Double? get() = Fmt.parseDecimal(block)
    val blockCount: Int? get() = blocks.toIntOrNull()
    val amount: Double get() = InventoryCalc.totalAmount(blockCount ?: 0, blockPrice ?: 0.0)
    val isBlank: Boolean get() = name.isBlank() && box.isBlank() && block.isBlank() && (blockCount ?: 0) == 0

    fun onBox(v: String) {
        box = Fmt.sanitizeDecimal(v, maxDecimals = 3)
        boxManual = box.isNotEmpty()
        if (!blockManual) block = boxPrice?.let { Fmt.inputNumber(InventoryCalc.blockPriceFromBox(it)) } ?: ""
    }

    fun onBlock(v: String) {
        block = Fmt.sanitizeDecimal(v)
        blockManual = block.isNotEmpty()
        if (!boxManual) box = blockPrice?.let { Fmt.inputNumber(InventoryCalc.boxPriceFromBlock(it)) } ?: ""
    }

    fun pick(p: Product) {
        name = p.name
        box = Fmt.inputNumber(p.boxPrice)
        block = Fmt.inputNumber(p.blockPrice)
        boxManual = true
        blockManual = true
    }

    fun toInput() = InventoryViewModel.InvoiceLineInput(name, boxPrice, blockPrice, blockCount)
}

private val LinesSaver: Saver<SnapshotStateList<InvoiceLineState>, Any> = Saver(
    save = { list ->
        ArrayList(list.map { arrayListOf<Any>(it.key, it.name, it.box, it.block, it.blocks, it.boxManual, it.blockManual) })
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        (saved as List<List<Any>>).map {
            InvoiceLineState(
                it[0] as Long, it[1] as String, it[2] as String, it[3] as String, it[4] as String,
                it[5] as Boolean, it[6] as Boolean,
            )
        }.toMutableStateList()
    },
)

// ---------------------------------------------------------------------------------------------
// Yeni qaimə
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditScreen(vm: InventoryViewModel, firmId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val firms by vm.firms.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val firm = firms.firstOrNull { it.id == firmId }
    val firmProducts = remember(products, firmId) { products.filter { it.firmId == firmId }.sortedBy { it.name.lowercase() } }

    var number by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var nextKey by rememberSaveable { mutableLongStateOf(1L) }
    val lines = rememberSaveable(saver = LinesSaver) { mutableListOf(InvoiceLineState(0L)).toMutableStateList() }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val filled = lines.filterNot { it.isBlank }
    val totalBlocks = filled.sumOf { (it.blockCount ?: 0).toLong() }
    val totalAmount = InventoryCalc.sum(filled.map { it.amount })

    fun addLine() {
        lines.add(InvoiceLineState(nextKey))
        nextKey++
        scope.launch { scroll.animateScrollTo(scroll.maxValue + 2000) }
    }

    fun save() {
        if (saving) return
        saving = true
        vm.saveInvoice(firmId, number, date, note, lines.map { it.toInput() }) { err, id ->
            saving = false
            if (err != null) error = err else if (id != null) onSaved(id)
        }
    }

    val back = { if (filled.isNotEmpty()) confirmLeave = true else onBack() }
    androidx.activity.compose.BackHandler(enabled = filled.isNotEmpty()) { confirmLeave = true }

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Yeni qaimə")
                        Text(
                            firm?.name ?: "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${filled.size} sətir · ${Fmt.count(totalBlocks)} blok · ${Fmt.count(totalBlocks * InventoryCalc.BOXES_PER_BLOCK)} paçka",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Cəmi: ", style = MaterialTheme.typography.titleMedium)
                                AnimatedValueText(
                                    Fmt.money(totalAmount),
                                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Button(
                            onClick = ::save,
                            enabled = !saving,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Filled.Check, null); HSpace(6.dp); Text("Qaiməni yadda saxla")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Başlıq: nömrə, tarix, qeyd
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(Icons.AutoMirrored.Filled.ReceiptLong, Gradients.Indigo, size = 40.dp)
                        HSpace(12.dp)
                        Text("Qaimə məlumatları", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = number, onValueChange = { number = it.take(30) },
                            label = { Text("Qaimə №") }, singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        Box(Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = Fmt.date(date), onValueChange = {}, readOnly = true,
                                label = { Text("Tarix") }, singleLine = true,
                                trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                            )
                            // Bütün sahəyə toxunanda təqvim açılsın
                            Surface(
                                onClick = { pickDate = true }, color = Color.Transparent,
                                modifier = Modifier.matchParentSize(),
                            ) {}
                        }
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(200) },
                        label = { Text("Qeyd (istəyə görə)") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text("Siqaretlər", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            lines.forEachIndexed { index, line ->
                androidx.compose.runtime.key(line.key) {
                    InvoiceLineCard(
                        index = index,
                        line = line,
                        suggestions = firmProducts,
                        canRemove = lines.size > 1,
                        onRemove = { lines.remove(line) },
                        onChange = { error = null },
                    )
                }
            }

            OutlinedButton(
                onClick = ::addLine,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Add, null); HSpace(6.dp); Text("Yeni sətir əlavə et")
            }

            AnimatedVisibility(visible = error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        HSpace(10.dp)
                        Text(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            VSpace(24.dp)
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { date = it }; pickDate = false }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Ləğv et") } },
        ) { DatePicker(state = state) }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Qaimə yadda saxlanılmayıb") },
            text = { Text("Yazdığınız sətirlər itəcək. Çıxmaq istəyirsiniz?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Çıx") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Qal") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceLineCard(
    index: Int,
    line: InvoiceLineState,
    suggestions: List<Product>,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onChange: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val matches = remember(line.name, suggestions) {
        val q = line.name.trim()
        suggestions.filter {
            (q.isEmpty() || it.name.contains(q, ignoreCase = true)) && !it.name.equals(q, ignoreCase = true)
        }.take(8)
    }
    val existing = suggestions.firstOrNull { it.name.equals(line.name.trim(), ignoreCase = true) }
    val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "${index + 1}", color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                HSpace(10.dp)
                Text(
                    when {
                        line.name.isBlank() -> "Sətir ${index + 1}"
                        existing != null -> "Stokda var: ${Fmt.count(existing.blockCount)} blok → +${Fmt.count(line.blockCount ?: 0)}"
                        else -> "Yeni siqaret"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (existing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Sətri sil") }
                }
            }

            ExposedDropdownMenuBox(expanded = menu && matches.isNotEmpty(), onExpandedChange = { menu = it }) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { line.name = it.take(60); menu = true; onChange() },
                    label = { Text("Siqaretin adı") },
                    leadingIcon = {
                        Box(Modifier.padding(start = 8.dp, end = 4.dp)) {
                            Icon3D(Icons.Filled.SmokingRooms, Gradients.forName(line.name.ifBlank { "x" }), size = 30.dp)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded = menu && matches.isNotEmpty(), onDismissRequest = { menu = false }) {
                    matches.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(p.name)
                                    Text(
                                        "Blok: ${Fmt.money(p.blockPrice)} · stokda ${Fmt.count(p.blockCount)} blok",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { line.pick(p); menu = false; onChange() },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = line.box, onValueChange = { line.onBox(it); onChange() },
                    label = { Text("Paçka qiyməti") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = line.block, onValueChange = { line.onBlock(it); onChange() },
                    label = { Text("Blok qiyməti") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { line.blocks = ((line.blockCount ?: 0) - 1).coerceAtLeast(0).toString(); onChange() },
                    modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.Remove, "Blok azalt") }
                HSpace(8.dp)
                OutlinedTextField(
                    value = line.blocks, onValueChange = { line.blocks = Fmt.sanitizeInt(it); onChange() },
                    label = { Text("Blok sayı") }, singleLine = true, modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                HSpace(8.dp)
                FilledTonalIconButton(
                    onClick = { line.blocks = ((line.blockCount ?: 0) + 1).coerceAtMost(9_999_999).toString(); onChange() },
                    modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.Add, "Blok artır") }
            }

            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Fmt.count(line.blockCount ?: 0)} blok × ${Fmt.money(line.blockPrice ?: 0.0)} · " +
                            "${Fmt.count(InventoryCalc.totalBoxes(line.blockCount ?: 0))} paçka",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedValueText(
                        Fmt.money(line.amount),
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Qaimə siyahısı kartı və baxış ekranı
// ---------------------------------------------------------------------------------------------

@Composable
fun InvoiceCard(s: InvoiceSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D(Icons.AutoMirrored.Filled.ReceiptLong, Gradients.Indigo, size = 44.dp)
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.date(s.invoice.date)} · ${s.items.size} siqaret · ${Fmt.count(s.totalBlocks)} blok",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            HSpace(8.dp)
            Text(Fmt.money(s.totalAmount), fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(vm: InventoryViewModel, invoiceId: Long, onBack: () -> Unit) {
    val summaries by vm.invoiceSummaries.collectAsStateWithLifecycle()
    val summary = summaries.firstOrNull { it.invoice.id == invoiceId }
    var loadedOnce by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(summary) {
        if (summary != null) loadedOnce = true
        else if (loadedOnce && !deleting) onBack()
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(summary?.title ?: "Qaimə", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (summary != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, "Qaiməni sil", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { pad ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GradientCard {
                    Column(Modifier.padding(20.dp)) {
                        Text(summary.firmName, color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Fmt.money(summary.totalAmount), color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        )
                        Text(
                            "${Fmt.longDate(summary.invoice.date)}",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                        VSpace(12.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Siqaret" to Fmt.count(summary.items.size),
                                "Blok" to Fmt.count(summary.totalBlocks),
                                "Paçka" to Fmt.count(summary.totalBoxes),
                            ).forEach { (l, v) ->
                                Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(l, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                        Text(v, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        if (summary.invoice.note.isNotBlank()) {
                            VSpace(10.dp)
                            Text(summary.invoice.note, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            itemsIndexed(summary.items, key = { _, it -> it.id }) { i, item -> InvoiceItemRow(i, item) }
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("CƏMİ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Fmt.money(summary.totalAmount), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (confirmDelete && summary != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon3D(Icons.Filled.Delete, Gradients.Red, size = 52.dp) },
            title = { Text("Qaimə silinsin?") },
            text = { Text("${summary.title} silinəcək və onunla gələn ${Fmt.count(summary.totalBlocks)} blok stokdan çıxılacaq.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        deleting = true
                        vm.deleteInvoice(invoiceId) { ok -> deleting = false; if (ok) onBack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et") } },
        )
    }
}

@Composable
private fun InvoiceItemRow(i: Int, item: InvoiceItem) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "${i + 1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge,
                )
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(item.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.count(item.blockCount)} blok × ${Fmt.money(item.blockPrice)} · ${Fmt.count(item.totalBoxes)} paçka × ${Fmt.price(item.boxPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HSpace(8.dp)
            Text(Fmt.money(item.totalAmount), fontWeight = FontWeight.Bold)
        }
    }
}

