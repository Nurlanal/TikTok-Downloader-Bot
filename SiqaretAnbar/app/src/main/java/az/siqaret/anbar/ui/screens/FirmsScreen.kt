package az.siqaret.anbar.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.data.Firm
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.ui.components.AnimatedValueText
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.EmptyState
import az.siqaret.anbar.ui.components.GradientCard
import az.siqaret.anbar.ui.components.HSpace
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.InfoPill
import az.siqaret.anbar.ui.components.ThemeMenuButton
import az.siqaret.anbar.ui.components.VSpace
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.util.FirmSummary
import az.siqaret.anbar.util.Fmt

private val NoFirmGradient = Gradients.Slate

/** Firmalar siyahısı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmsScreen(vm: InventoryViewModel, onOpen: (Long) -> Unit) {
    val summaries by vm.firmSummaries.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = TabScreenInsets,
        snackbarHost = { AppSnackbarHost() },
        topBar = { TopAppBar(title = { Text("Firmalar") }, actions = { ThemeMenuButton(theme, vm::setTheme) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Firma əlavə et") },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summaries.none { it.firm != null }) {
                item {
                    EmptyState(
                        Icons.Filled.Business, "Hələ firma yoxdur",
                        "Siqaret aldığınız firmaları əlavə edin. Sonra hər firmadan alınan siqaretləri o firmanın içinə əlavə edə bilərsiniz.",
                    )
                }
            }
            items(summaries, key = { it.firm?.id ?: -1L }) { s ->
                FirmCard(s, onClick = { onOpen(s.firm?.id ?: InventoryViewModel.NO_FIRM_FILTER) }, modifier = Modifier.animateItem())
            }
        }
    }

    if (creating) {
        FirmDialog(vm, firm = null, onDismiss = { creating = false }, onSaved = { id -> creating = false; onOpen(id) })
    }
}

@Composable
private fun FirmCard(s: FirmSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(
                    if (s.firm == null) Icons.Filled.Inventory else Icons.Filled.Business,
                    if (s.firm == null) NoFirmGradient else Gradients.forName(s.name), size = 50.dp,
                )
                HSpace(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        s.firm == null -> "Firması seçilməmiş siqaretlər"
                        s.firm.phone.isNotBlank() -> s.firm.phone
                        else -> "${s.stats.productCount} növ siqaret"
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Siqaret", Fmt.count(s.stats.productCount), Modifier.weight(0.8f))
                InfoPill("Blok", Fmt.count(s.stats.totalBlocks), Modifier.weight(0.8f))
                InfoPill("Paçka", Fmt.count(s.stats.totalBoxes), Modifier.weight(0.9f))
                InfoPill("Dəyər", Fmt.money(s.stats.totalValue), Modifier.weight(1.5f), highlight = true)
            }
        }
    }
}

/** Bir firmanın səhifəsi: həmin firmadan alınan siqaretlər. firmId = -1 -> firmasız siqaretlər. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmDetailScreen(
    vm: InventoryViewModel,
    firmId: Long,
    onBack: () -> Unit,
    onAddProduct: (firmId: Long?) -> Unit,
    onEditProduct: (Long) -> Unit,
    onAddInvoice: (firmId: Long) -> Unit = {},
    onOpenInvoice: (invoiceId: Long) -> Unit = {},
) {
    val summaries by vm.firmSummaries.collectAsStateWithLifecycle()
    val allInvoices by vm.invoiceSummaries.collectAsStateWithLifecycle()
    val invoices = remember(allInvoices, firmId) { allInvoices.filter { it.invoice.firmId == firmId } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val all by vm.products.collectAsStateWithLifecycle()
    val isNoFirm = firmId == InventoryViewModel.NO_FIRM_FILTER
    val summary = summaries.firstOrNull { if (isNoFirm) it.firm == null else it.firm?.id == firmId }
    val firm = summary?.firm
    val context = LocalContext.current

    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(summary?.name ?: FirmSummary.NO_FIRM, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (firm != null) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Firmanı redaktə et") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Firmanı sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (firm != null) onAddInvoice(firm.id) else onAddProduct(null) },
                icon = { Icon(if (firm != null) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Add, null) },
                text = { Text(if (firm != null) "Qaimə əlavə et" else "Siqaret əlavə et") },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        val products = summary?.products.orEmpty().sortedBy { it.name.lowercase() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GradientCard(colors = if (firm == null) listOf(Color(0xFF64748B), Color(0xFF334155)) else Gradients.Hero) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Bu firmadakı stok dəyəri", color = Color.White.copy(alpha = 0.85f))
                                AnimatedValueText(
                                    Fmt.money(summary?.stats?.totalValue ?: 0.0),
                                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                )
                                Text(
                                    "${Fmt.count(summary?.stats?.productCount ?: 0)} növ · " +
                                        "${Fmt.count(summary?.stats?.totalBlocks ?: 0)} blok · " +
                                        "${Fmt.count(summary?.stats?.totalBoxes ?: 0)} paçka",
                                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon3D(Icons.Filled.Business, Gradients.Amber, size = 56.dp)
                        }
                        if (firm != null && (firm.phone.isNotBlank() || firm.note.isNotBlank())) {
                            VSpace(12.dp)
                            if (firm.phone.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${firm.phone}")))
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(Icons.Filled.Phone, null, Modifier.size(18.dp)); HSpace(6.dp); Text(firm.phone)
                                }
                            }
                            if (firm.note.isNotBlank()) {
                                VSpace(6.dp)
                                Text(firm.note, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (firm != null) {
                item(key = "tabs") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = tab == 0, onClick = { tab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            label = { Text("Siqaretlər (${products.size})") },
                        )
                        SegmentedButton(
                            selected = tab == 1, onClick = { tab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            label = { Text("Qaimələr (${invoices.size})") },
                        )
                    }
                }
            }
            if (firm != null && tab == 1) {
                if (invoices.isEmpty()) {
                    item(key = "noInvoices") {
                        EmptyState(
                            Icons.AutoMirrored.Filled.ReceiptLong, "Qaimə yoxdur",
                            "\"Qaimə əlavə et\" düyməsinə basın və firmadan gələn siqaretləri alt-alta yazın. " +
                                "Yadda saxlayanda hamısı stoka əlavə olunacaq.",
                        )
                    }
                } else {
                    item(key = "invTotal") {
                        Text(
                            "Cəmi alış: ${Fmt.money(az.siqaret.anbar.util.InventoryCalc.sum(invoices.map { it.totalAmount }))}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(invoices, key = { "inv${it.invoice.id}" }) { inv ->
                    InvoiceCard(inv, onClick = { onOpenInvoice(inv.invoice.id) }, modifier = Modifier.animateItem())
                }
                return@LazyColumn
            }
            if (firm != null) {
                item(key = "addSingle") {
                    OutlinedButton(
                        onClick = { onAddProduct(firm.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Icon(Icons.Filled.Add, null); HSpace(6.dp); Text("Siqaret əlavə et") }
                }
            }
            if (products.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.SmokingRooms, "Bu firmada siqaret yoxdur",
                        "\"Qaimə əlavə et\" ilə bir neçə siqareti birdən, \"Siqaret əlavə et\" ilə isə tək-tək əlavə edin.",
                    )
                }
            }
            items(products, key = { it.id }) { p ->
                ProductCard(
                    product = p,
                    firmName = null,
                    onClick = { onEditProduct(p.id) },
                    onPlus = { vm.adjustStock(p, 1) },
                    onMinus = { vm.adjustStock(p, -1) },
                    onAdjust = { adjusting = p },
                    onEdit = { onEditProduct(p.id) },
                    onDelete = { deleting = p },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (editing && firm != null) {
        FirmDialog(vm, firm, onDismiss = { editing = false }, onSaved = { editing = false })
    }
    if (confirmDelete && firm != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon3D(Icons.Filled.Delete, Gradients.Red, size = 52.dp) },
            title = { Text("Firma silinsin?") },
            text = {
                Text(
                    "\"${firm.name}\" və onun ${invoices.size} qaiməsi silinəcək. Bu firmanın ${summary.stats.productCount} " +
                        "siqareti və stoku silinməyəcək, \"${FirmSummary.NO_FIRM}\" bölməsinə keçəcək."
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteFirm(firm); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et") } },
        )
    }
    deleting?.let { p ->
        ProductDeleteDialog(p, onDismiss = { deleting = null }, onConfirm = { vm.deleteProduct(p); deleting = null })
    }
    adjusting?.let { p ->
        val current = all.firstOrNull { it.id == p.id } ?: p
        StockDialog(current, onDismiss = { adjusting = null }, onApply = { d -> vm.adjustStock(current, d); adjusting = null })
    }
}

/** Firma əlavə etmə / redaktə pəncərəsi. */
@Composable
fun FirmDialog(vm: InventoryViewModel, firm: Firm?, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf(firm?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(firm?.phone ?: "") }
    var note by rememberSaveable { mutableStateOf(firm?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon3D(Icons.Filled.Business, Gradients.Indigo, size = 52.dp) },
        title = { Text(if (firm == null) "Yeni firma" else "Firmanı redaktə et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60); error = null },
                    label = { Text("Firmanın adı") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Business, null) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(20) },
                    label = { Text("Telefon (istəyə görə)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Phone, null) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text("Qeyd (istəyə görə)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3,
                )
                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        HSpace(6.dp)
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    vm.saveFirm(firm?.id, name, phone, note) { err, id ->
                        saving = false
                        if (err != null) error = err else if (id != null) onSaved(id)
                    }
                },
            ) { Text("Yadda saxla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ləğv et") } },
    )
}

