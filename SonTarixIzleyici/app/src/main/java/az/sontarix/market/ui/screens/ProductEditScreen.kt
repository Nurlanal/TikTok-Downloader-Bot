package az.sontarix.market.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.data.Product
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.Icon3D
import az.sontarix.market.ui.components.ProductImage
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import az.sontarix.market.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    vm: MainViewModel,
    productId: Long,
    copyFromId: Long,
    initialBarcode: String,
    scannedBarcode: String?,
    onScannedConsumed: () -> Unit,
    onScan: () -> Unit,
    onDone: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isEdit = productId > 0

    var loadedOriginal by remember { mutableStateOf<Product?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf(initialBarcode) }
    var category by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("1") }
    var expiryEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var imageFile by rememberSaveable { mutableStateOf<String?>(null) }
    /** Bu ekranda yaradılmış, hələ yadda saxlanılmamış şəkil faylları (ləğv olunarsa silinir). */
    val tempImages = remember { mutableStateListOf<String>() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showImageSource by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var barcodeMatches by remember { mutableStateOf<List<Product>>(emptyList()) }
    var confirmPast by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun setDate(d: LocalDate) { expiryEpoch = d.toEpochDay(); dateText = DateUtils.format(d); dateError = null }

    fun fillFrom(p: Product, asNewBatch: Boolean) {
        name = p.name; barcode = p.barcode; category = p.category; note = p.note
        if (!asNewBatch) {
            quantityText = p.quantity.toString(); batch = p.batchNumber; setDate(p.expiryDate); imageFile = p.imageFile
        } else {
            // Yeni partiya: şəkil kopyalanır ki, hər partiyanın öz faylı olsun
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    ImageStorage.fileFor(context, p.imageFile)?.let { ImageStorage.importFile(context, it) }
                }
                if (copied != null) { tempImages += copied; imageFile = copied }
            }
        }
    }

    LaunchedEffect(productId, copyFromId) {
        if (initialized) {
            if (isEdit) loadedOriginal = vm.repo.getProduct(productId)
            return@LaunchedEffect
        }
        when {
            isEdit -> vm.repo.getProduct(productId)?.let { loadedOriginal = it; fillFrom(it, asNewBatch = false) }
            copyFromId > 0 -> vm.repo.getProduct(copyFromId)?.let { fillFrom(it, asNewBatch = true) }
        }
        initialized = true
    }

    suspend fun lookupBarcode(code: String) {
        if (isEdit || code.isBlank()) { barcodeMatches = emptyList(); return }
        barcodeMatches = vm.repo.findByBarcode(code)
    }

    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            barcode = scannedBarcode
            onScannedConsumed()
            lookupBarcode(scannedBarcode)
        }
    }

    fun onNewImage(uri: Uri) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { ImageStorage.saveFromUri(context, uri) }
            if (saved != null) {
                imageFile?.let { old -> if (old in tempImages) { ImageStorage.delete(context, old); tempImages.remove(old) } }
                tempImages += saved
                imageFile = saved
            } else vm.toast("Şəkil yüklənmədi")
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingCameraUri?.let(Uri::parse)
        if (ok && u != null) onNewImage(u)
        pendingCameraUri = null
    }
    fun launchCamera() {
        val u = ImageStorage.newCameraUri(context)
        pendingCameraUri = u.toString()
        takePicture.launch(u)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else vm.toast("Kamera icazəsi verilmədi")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onNewImage(uri)
    }

    fun cleanupAndExit() {
        tempImages.filter { it != loadedOriginal?.imageFile }.forEach { ImageStorage.delete(context, it) }
        onBack()
    }

    val dirty = name.isNotBlank() || tempImages.isNotEmpty()
    BackHandler(enabled = !isEdit && dirty) { confirmDiscard = true }

    fun doSave() {
        val qty = quantityText.trim().toIntOrNull()
        val epoch = expiryEpoch
        saving = true
        scope.launch {
            val now = System.currentTimeMillis()
            val base = loadedOriginal
            val product = (base ?: Product(uid = UUID.randomUUID().toString(), name = "", expiryEpochDay = 0)).copy(
                name = name.trim(), barcode = barcode.trim(), category = category.trim(),
                quantity = (qty ?: 0).coerceAtLeast(0), expiryEpochDay = epoch!!, note = note.trim(),
                batchNumber = batch.trim(), imageFile = imageFile, updatedAt = now
            )
            val id = vm.repo.save(product)
            // İstifadə olunmayan müvəqqəti şəkilləri təmizlə
            tempImages.filter { it != imageFile }.forEach { ImageStorage.delete(context, it) }
            tempImages.clear()
            vm.toast(if (isEdit) "Məhsul yeniləndi" else "Məhsul əlavə edildi")
            saving = false
            onDone(if (isEdit) 0L else id)
        }
    }

    fun validateAndSave() {
        nameError = name.isBlank()
        if (dateText.isNotBlank() && (expiryEpoch == null || DateUtils.format(LocalDate.ofEpochDay(expiryEpoch!!)) != dateText.trim())) {
            DateUtils.parse(dateText)?.let { setDate(it) } ?: run { dateError = "Tarixi gg.aa.iiii formatında daxil edin" }
        }
        if (expiryEpoch == null && dateError == null) dateError = "Son istifadə tarixini seçin"
        val qty = quantityText.trim().toIntOrNull()
        if (qty == null || qty < 0) { vm.toast("Məhsulun sayını düzgün daxil edin"); return }
        if (nameError || dateError != null) { vm.toast("Zəruri sahələri doldurun"); return }
        if (LocalDate.ofEpochDay(expiryEpoch!!).isBefore(DateUtils.today()) && !isEdit) { confirmPast = true; return }
        doSave()
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!isEdit && dirty) confirmDiscard = true else cleanupAndExit() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text(
                    when { isEdit -> "Məhsulu redaktə et"; copyFromId > 0 -> "Yeni partiya əlavə et"; else -> "Məhsul əlavə et" },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Şəkil ──
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Box(Modifier.fillMaxWidth().height(210.dp).clickable { showImageSource = true }, contentAlignment = Alignment.Center) {
                    if (imageFile != null) {
                        ProductImage(imageFile, Modifier.fillMaxSize())
                        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(onClick = { showImageSource = true }) { Icon(Icons.Filled.AddAPhoto, "Şəkli dəyiş") }
                            FilledIconButton(onClick = {
                                imageFile?.let { if (it in tempImages) { ImageStorage.delete(context, it); tempImages.remove(it) } }
                                imageFile = null
                            }) { Icon(Icons.Filled.Delete, "Şəkli sil") }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon3D(Icons.Filled.AddAPhoto, MaterialTheme.colorScheme.primary, 64.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Məhsulun şəklini əlavə et", style = MaterialTheme.typography.titleMedium)
                            Text("Kamera və ya qalereya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
                OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Qalereya") }
            }

            // ── Ad ──
            OutlinedTextField(
                value = name, onValueChange = { name = it; nameError = false },
                label = { Text("Məhsulun adı *") }, leadingIcon = { Icon(Icons.Filled.ShoppingBag, null) },
                isError = nameError, supportingText = { if (nameError) Text("Məhsulun adını daxil edin") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )

            // ── Barkod ──
            OutlinedTextField(
                value = barcode,
                onValueChange = { v -> barcode = v.filter { !it.isWhitespace() }.take(64); scope.launch { lookupBarcode(barcode) } },
                label = { Text("Barkod") }, leadingIcon = { Icon(Icons.Filled.QrCode2, null) },
                trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, "Kamera ilə skan et", tint = MaterialTheme.colorScheme.primary) } },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            if (barcodeMatches.isNotEmpty() && !isEdit) {
                val first = barcodeMatches.first()
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Bu barkodla ${barcodeMatches.size} partiya artıq mövcuddur", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${first.name} • tarixlər: " + barcodeMatches.take(3).joinToString { DateUtils.format(it.expiryDate) },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(onClick = { fillFrom(first, asNewBatch = true); barcodeMatches = emptyList() }) {
                            Text("Məlumatları doldur (yeni partiya kimi)")
                        }
                    }
                }
            }

            // ── Kateqoriya ──
            var catExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                OutlinedTextField(
                    value = category, onValueChange = { category = it.take(80); catExpanded = true },
                    label = { Text("Kateqoriya") }, leadingIcon = { Icon(Icons.Filled.Category, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                    singleLine = true, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                val options = categories.map { it.name }.filter { category.isBlank() || it.contains(category, true) }
                if (options.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        options.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { category = opt; catExpanded = false })
                        }
                    }
                }
            }

            // ── Say ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = quantityText, onValueChange = { v -> quantityText = v.filter { it.isDigit() }.take(8) },
                    label = { Text("Məhsulun sayı") }, leadingIcon = { Icon(Icons.Filled.Inventory, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { quantityText = ((quantityText.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Remove, "Azalt")
                }
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = { quantityText = ((quantityText.toIntOrNull() ?: 0) + 1).toString() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Add, "Artır")
                }
            }

            // ── Son istifadə tarixi ──
            OutlinedTextField(
                value = dateText,
                onValueChange = { v ->
                    dateText = v.filter { it.isDigit() || it == '.' }.take(10)
                    DateUtils.parse(dateText)?.let { expiryEpoch = it.toEpochDay(); dateError = null }
                },
                label = { Text("Son istifadə tarixi * (gg.aa.iiii)") },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Təqvim") } },
                isError = dateError != null, supportingText = { dateError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = DateUtils.today()
                val quick = listOf("+3 gün" to today.plusDays(3), "+1 həftə" to today.plusWeeks(1), "+1 ay" to today.plusMonths(1),
                    "+3 ay" to today.plusMonths(3), "+6 ay" to today.plusMonths(6), "+1 il" to today.plusYears(1))
                items(quick) { (label, d) -> AssistChip(onClick = { setDate(d) }, label = { Text(label) }) }
            }
            expiryEpoch?.let { e ->
                val days = DateUtils.daysBetween(DateUtils.today(), LocalDate.ofEpochDay(e))
                val st = ExpiryStatus.of(days, settings.warningDays)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(StatusColors.of(st)))
                    Spacer(Modifier.width(8.dp))
                    Text("${st.label} • ${DateUtils.remainingText(days)}", color = StatusColors.of(st), fontWeight = FontWeight.Bold)
                }
            }

            // ── Partiya ──
            OutlinedTextField(
                value = batch, onValueChange = { batch = it.take(64) },
                label = { Text("Partiya (Batch) nömrəsi") }, leadingIcon = { Icon(Icons.Filled.Layers, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )

            // ── Qeyd ──
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(2000) },
                label = { Text("Məhsul haqqında qeyd") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )

            Button(
                onClick = { validateAndSave() }, enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(58.dp), shape = MaterialTheme.shapes.large
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text(if (isEdit) "Dəyişiklikləri yadda saxla" else "Yadda saxla") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.toUtcMillis(expiryEpoch?.let { LocalDate.ofEpochDay(it) } ?: DateUtils.today())
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { setDate(DateUtils.fromUtcMillis(it)) }
                    showDatePicker = false
                }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Ləğv et") } }
        ) { DatePicker(state = state, title = { Text("Son istifadə tarixi", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }

    if (showImageSource) {
        AlertDialog(
            onDismissRequest = { showImageSource = false },
            title = { Text("Şəkil mənbəyi") },
            text = {
                Column {
                    TextButton(onClick = {
                        showImageSource = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    }) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Kamera ilə çək") }
                    TextButton(onClick = {
                        showImageSource = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Qalereyadan seç") }
                }
            },
            confirmButton = { TextButton(onClick = { showImageSource = false }) { Text("Bağla") } }
        )
    }

    if (confirmPast) {
        ConfirmDialog(
            title = "Tarix keçib",
            message = "Seçdiyiniz son istifadə tarixi artıq keçib. Məhsul \"müddəti bitmiş\" kimi qeyd olunacaq. Davam edilsin?",
            confirmText = "Yadda saxla",
            onConfirm = { confirmPast = false; doSave() }, onDismiss = { confirmPast = false }
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Dəyişikliklər itəcək", message = "Daxil etdiyiniz məlumatlar yadda saxlanılmayacaq. Çıxmaq istəyirsiniz?",
            confirmText = "Çıx", destructive = true,
            onConfirm = { confirmDiscard = false; cleanupAndExit() }, onDismiss = { confirmDiscard = false }
        )
    }
}
