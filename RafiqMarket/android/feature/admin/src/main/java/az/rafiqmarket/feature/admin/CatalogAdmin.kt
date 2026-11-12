package az.rafiqmarket.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.dto.BannerBody
import az.rafiqmarket.core.network.dto.BannerDto
import az.rafiqmarket.core.network.dto.CategoryBody
import az.rafiqmarket.core.network.dto.CategoryDto
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.money
import az.rafiqmarket.core.util.parseColor
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// ---------------- Məhsullar siyahısı ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductsScreen(onEdit: (Long?) -> Unit, onCategories: () -> Unit, vm: AdminProductsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val low by vm.lowStock.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    MessageEffect(s.message, snack, vm::clearMessage)
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Məhsullar") }, actions = { TextButton(onClick = onCategories) { Text("Kateqoriyalar") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { onEdit(null) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Məhsul əlavə et") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { vm.query.value = it }, placeholder = { Text("Ad və ya barkod") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Row(Modifier.padding(horizontal = 12.dp)) {
                FilterChip(low, { vm.lowStock.value = !low }, label = { Text("Stoku azalanlar") }, leadingIcon = { Icon(Icons.Outlined.Warning, null, Modifier.size(16.dp)) })
            }
            val list = s.data
            when {
                list == null && s.error != null -> ErrorBox(s.error!!, { vm.load() })
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState("📦", "Məhsul yoxdur", "Yeni məhsul əlavə edin")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { p ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { onEdit(p.id) }, shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProductImage(p.imageUrl, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text("${money(p.price)} / ${p.unit}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    StockLabel(p.stock, p.lowStockThreshold)
                                    p.barcode?.let { Text("Barkod: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Switch(p.isActive, { vm.setActive(p, it) })
                                    Text(if (p.isActive) "Aktiv" else "Passiv", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Məhsul əlavə et / redaktə et ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(onDone: () -> Unit, vm: ProductEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var stockDialog by remember { mutableStateOf(false) }
    MessageEffect(msg, snack) { vm.message.value = null }
    LaunchedEffect(Unit) { vm.done.collect { onDone() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::uploadImage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Məhsulu redaktə et" else "Yeni məhsul") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { if (vm.isEdit) IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Sil", tint = RafiqColors.Red) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(vm::save, enabled = !saving && !uploading, modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Yadda saxla")
                }
            }
        },
    ) { pad ->
        if (loading) { LoadingBox(Modifier.padding(pad).fillMaxSize()); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                ProductImage(f.imageUrl, Modifier.matchParentSize())
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (uploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (f.imageUrl == null) "Şəkil yüklə" else "Şəkli dəyiş", color = Color.White)
                    }
                }
            }
            OutlinedTextField(f.name, { v -> vm.update { it.copy(name = v.take(160)) } }, label = { Text("Məhsulun adı *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CategoryDropdown(cats, f.categoryId) { id -> vm.update { it.copy(categoryId = id) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f.price, { v -> vm.update { it.copy(price = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)) } },
                    label = { Text("Qiymət ₼ *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(f.oldPrice, { v -> vm.update { it.copy(oldPrice = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)) } },
                    label = { Text("Köhnə qiymət") }, supportingText = { Text("Endirim üçün") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            UnitPicker(f.unit) { u -> vm.update { it.copy(unit = u) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(f.stock, { v -> vm.update { it.copy(stock = v.filter { c -> c.isDigit() }.take(7)) } }, enabled = !vm.isEdit,
                    label = { Text("Stok") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(f.lowStockThreshold, { v -> vm.update { it.copy(lowStockThreshold = v.filter { c -> c.isDigit() }.take(5)) } },
                    label = { Text("Az stok həddi") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            if (vm.isEdit) FilledTonalButton({ stockDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Inventory, null); Spacer(Modifier.width(6.dp)); Text("Stoku idarə et (artır / azalt)") }
            OutlinedTextField(
                f.barcode, { v -> vm.update { it.copy(barcode = v.take(64)) } }, label = { Text("Barkod") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton({
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { b -> b.rawValue?.let { v -> vm.update { it.copy(barcode = v) } } }
                            .addOnFailureListener { vm.message.value = "Skaner açılmadı: ${it.message}" }
                    }) { Icon(Icons.Outlined.QrCodeScanner, "Barkodu skan et") }
                },
            )
            OutlinedTextField(f.description, { v -> vm.update { it.copy(description = v.take(2000)) } }, label = { Text("Təsvir") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            ListItem(headlineContent = { Text("Aktiv (müştərilərə görünür)") }, trailingContent = { Switch(f.isActive, { v -> vm.update { it.copy(isActive = v) } }) })
            ListItem(headlineContent = { Text("\"Yeni məhsul\" bölməsində göstər") }, trailingContent = { Switch(f.isNew, { v -> vm.update { it.copy(isNew = v) } }) })
        }
    }
    if (confirmDelete) ConfirmDialog("Məhsulu sil", "\"${f.name}\" silinsin? Keçmiş sifarişlərdə adı qalacaq.", "Sil", vm::delete) { confirmDelete = false }
    if (stockDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stockDialog = false },
            title = { Text("Stok: ${f.stock} ${f.unit}") },
            text = { OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Miqdar") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton({ amount.toIntOrNull()?.let { vm.adjustStock(it) }; stockDialog = false }) { Text("+ Əlavə et") } },
            dismissButton = { TextButton({ amount.toIntOrNull()?.let { vm.adjustStock(-it) }; stockDialog = false }) { Text("− Azalt") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(cats: List<CategoryDto>, selected: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            cats.firstOrNull { it.id == selected }?.let { "${it.icon} ${it.name}" } ?: "Kateqoriyasız", {}, readOnly = true,
            label = { Text("Kateqoriya") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Kateqoriyasız") }, { onSelect(null); open = false })
            cats.forEach { c -> DropdownMenuItem({ Text("${c.icon} ${c.name}") }, { onSelect(c.id); open = false }) }
        }
    }
}

@Composable
private fun UnitPicker(unit: String, onChange: (String) -> Unit) {
    val units = listOf("ədəd", "kq", "q", "litr", "ml", "paket", "qutu", "dəstə")
    Column {
        Text("Ölçü vahidi", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { u -> FilterChip(unit == u, { onChange(u) }, label = { Text(u) }) }
        }
    }
}

// ---------------- Kateqoriyalar ----------------
private val palette = listOf("#2E7D32", "#1E88E5", "#F9A825", "#E53935", "#8E24AA", "#6D4C41", "#FB8C00", "#00ACC1", "#43A047", "#D81B60")
private val emojis = listOf("🥛", "🍞", "🍎", "🥕", "🍗", "🥩", "🐟", "🧀", "🥚", "🧃", "🥤", "☕", "🍫", "🍬", "🍚", "🫘", "🧂", "🫒", "🧼", "🧻", "👶", "🐾", "🍦", "🛒")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<CategoryDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryDto?>(null) }
    MessageEffect(s.message, snack, vm::clearMessage)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kateqoriyalar") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kateqoriya yarat") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val list = s.data
        when {
            list == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> EmptyState("🗂️", "Kateqoriya yoxdur", modifier = Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { c ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { editing = c }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon3D(c.icon, Color(parseColor(c.color)), 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Medium)
                                Text("${c.productCount ?: 0} məhsul • ${if (c.isActive) "aktiv" else "passiv"}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ deleting = c }) { Icon(Icons.Outlined.Delete, "Sil") }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) CategoryDialog(editing, onDismiss = { creating = false; editing = null }) { body ->
        vm.save(editing?.id, body); creating = false; editing = null
    }
    deleting?.let { c -> ConfirmDialog("Kateqoriyanı sil", "\"${c.name}\" silinsin? Məhsullar kateqoriyasız qalacaq.", "Sil", { vm.delete(c.id) }) { deleting = null } }
}

@Composable
private fun CategoryDialog(c: CategoryDto?, onDismiss: () -> Unit, onSave: (CategoryBody) -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }
    var icon by remember { mutableStateOf(c?.icon ?: "🛒") }
    var color by remember { mutableStateOf(c?.color ?: palette.first()) }
    var sort by remember { mutableStateOf((c?.sortOrder ?: 0).toString()) }
    var active by remember { mutableStateOf(c?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c == null) "Yeni kateqoriya" else "Kateqoriyanı redaktə et") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(icon, Color(parseColor(color)), 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Ad") }, singleLine = true)
                }
                Text("İkon", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    emojis.forEach { e -> Text(e, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clip(CircleShape)
                        .background(if (e == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { icon = e }.padding(6.dp)) }
                }
                Text("Rəng", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { p ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(parseColor(p)))
                            .border(if (p == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { color = p })
                    }
                }
                OutlinedTextField(sort, { sort = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Sıra") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktiv", Modifier.weight(1f)); Switch(active, { active = it }) }
            }
        },
        confirmButton = { TextButton({ onSave(CategoryBody(name.trim(), icon, color, sort.toIntOrNull() ?: 0, active)) }, enabled = name.isNotBlank()) { Text("Yadda saxla") } },
        dismissButton = { TextButton(onDismiss) { Text("İmtina") } },
    )
}

// ---------------- Bannerlər ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BannersScreen(onBack: () -> Unit, vm: BannersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<BannerDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    MessageEffect(s.message, snack, vm::clearMessage)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Reklam bannerləri") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.uploadedUrl.value = null; creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Banner əlavə et") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val list = s.data
        when {
            list == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> EmptyState("🖼️", "Banner yoxdur", modifier = Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { b ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { vm.uploadedUrl.value = b.imageUrl; editing = b }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(parseColor(b.color)))) {
                                b.imageUrl?.let { ProductImage(it, Modifier.matchParentSize()) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, fontWeight = FontWeight.Medium)
                                Text(if (b.isActive) "Aktiv" else "Passiv", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ vm.delete(b.id) }) { Icon(Icons.Outlined.Delete, "Sil") }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        val b = editing
        val uploaded by vm.uploadedUrl.collectAsStateWithLifecycle()
        var title by remember { mutableStateOf(b?.title ?: "") }
        var subtitle by remember { mutableStateOf(b?.subtitle ?: "") }
        var color by remember { mutableStateOf(b?.color ?: "#1B5E20") }
        var categoryId by remember { mutableStateOf(b?.categoryId) }
        var active by remember { mutableStateOf(b?.isActive ?: true) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::upload) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (b == null) "Yeni banner" else "Banneri redaktə et") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it.take(120) }, label = { Text("Başlıq") })
                    OutlinedTextField(subtitle, { subtitle = it.take(200) }, label = { Text("Alt mətn") })
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (uploaded == null) "Şəkil yüklə (istəyə bağlı)" else "Şəkil yükləndi ✓")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("#1B5E20") + palette).distinct().forEach { p ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(parseColor(p)))
                                .border(if (p == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { color = p })
                        }
                    }
                    Text("Kliklədikdə açılacaq kateqoriya", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(categoryId == null, { categoryId = null }, label = { Text("Heç biri") })
                        cats.forEach { c -> FilterChip(categoryId == c.id, { categoryId = c.id }, label = { Text("${c.icon} ${c.name}") }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktiv", Modifier.weight(1f)); Switch(active, { active = it }) }
                }
            },
            confirmButton = {
                TextButton({
                    vm.save(b?.id, BannerBody(title.trim(), subtitle.trim(), uploaded, color, categoryId, b?.sortOrder ?: 0, active))
                    creating = false; editing = null
                }, enabled = title.isNotBlank()) { Text("Yadda saxla") }
            },
            dismissButton = { TextButton({ creating = false; editing = null }) { Text("İmtina") } },
        )
    }
}
