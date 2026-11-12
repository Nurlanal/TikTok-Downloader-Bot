package az.siqaret.anbar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.data.Firm
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.ui.components.AnimatedValueText
import az.siqaret.anbar.ui.components.GradientCard
import az.siqaret.anbar.ui.components.HSpace
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.VSpace
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryCalc
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    vm: InventoryViewModel,
    productId: Long?,
    initialFirmId: Long? = null,
    onBack: () -> Unit,
) {
    val isEdit = productId != null
    var loaded by rememberSaveable { mutableStateOf(!isEdit) }
    var original by remember { mutableStateOf<Product?>(null) }

    var name by rememberSaveable { mutableStateOf("") }
    var boxText by rememberSaveable { mutableStateOf("") }
    var blockText by rememberSaveable { mutableStateOf("") }
    var blocksText by rememberSaveable { mutableStateOf("") }
    var linked by rememberSaveable { mutableStateOf(true) }
    // İstifadəçinin son yazdığı qiymət sahəsi ("block" / "box"). Avtomatik rejim yalnız ondan hesablayır.
    var priceSource by rememberSaveable { mutableStateOf("block") }
    var firmId by rememberSaveable { mutableStateOf(initialFirmId) }
    var creatingFirm by remember { mutableStateOf(false) }
    val firms by vm.firms.collectAsStateWithLifecycle()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        if (productId != null) {
            val p = vm.getProduct(productId)
            original = p
            if (p == null) { onBack(); return@LaunchedEffect }
            if (!loaded) {
                name = p.name
                boxText = Fmt.inputNumber(p.boxPrice)
                blockText = Fmt.inputNumber(p.blockPrice)
                blocksText = p.blockCount.toString()
                firmId = p.firmId
                linked = abs(InventoryCalc.blockPriceFromBox(p.boxPrice) - p.blockPrice) < 0.005
                loaded = true
            }
        }
    }

    // --- Real vaxt hesablamalar ---
    val boxPrice = Fmt.parseDecimal(boxText)
    val blockPrice = Fmt.parseDecimal(blockText)
    val blocks = blocksText.toIntOrNull()
    val totalBoxes = InventoryCalc.totalBoxes(blocks ?: 0)
    val totalAmount = InventoryCalc.totalAmount(blocks ?: 0, blockPrice ?: 0.0)

    fun onBoxChange(v: String) {
        boxText = Fmt.sanitizeDecimal(v, maxDecimals = 3)
        priceSource = "box"
        error = null
        if (linked) {
            blockText = Fmt.parseDecimal(boxText)?.let { Fmt.inputNumber(InventoryCalc.blockPriceFromBox(it)) } ?: ""
        }
    }

    fun onBlockChange(v: String) {
        blockText = Fmt.sanitizeDecimal(v)
        priceSource = "block"
        error = null
        if (linked) {
            boxText = Fmt.parseDecimal(blockText)?.let { Fmt.inputNumber(InventoryCalc.boxPriceFromBlock(it)) } ?: ""
        }
    }

    fun save() {
        if (saving) return
        saving = true
        vm.saveProduct(productId, name, boxPrice, blockPrice, blocks, firmId?.takeIf { id -> firms.any { it.id == id } }) { err ->
            saving = false
            if (err == null) onBack() else error = err
        }
    }

    val focus = LocalFocusManager.current

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Məhsulu redaktə et" else "Yeni məhsul") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Canlı nəticə kartı
            GradientCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(Icons.Filled.Calculate, Gradients.Amber, size = 44.dp)
                        HSpace(12.dp)
                        Column {
                            Text("Ümumi məbləğ", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                            AnimatedValueText(
                                Fmt.money(totalAmount),
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                            )
                        }
                    }
                    VSpace(14.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveTile("Blok sayı", Fmt.count(blocks ?: 0), Modifier.weight(1f))
                        LiveTile("Ümumi paçka", Fmt.count(totalBoxes), Modifier.weight(1f))
                    }
                    VSpace(12.dp)
                    Text(
                        "${Fmt.count(blocks ?: 0)} blok × ${Fmt.money(blockPrice ?: 0.0)} = ${Fmt.money(totalAmount)}",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${Fmt.count(blocks ?: 0)} blok × ${InventoryCalc.BOXES_PER_BLOCK} = ${Fmt.count(totalBoxes)} paçka",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            FieldCard {
                FirmPicker(
                    firms = firms,
                    selectedId = firmId,
                    onSelect = { firmId = it; error = null },
                    onCreate = { creatingFirm = true },
                )
            }

            FieldCard {
                BigField(
                    value = name,
                    onChange = { name = it.take(60); error = null },
                    label = "Siqaretin adı",
                    placeholder = "Məs: Marlboro Red",
                    icon = { Icon3D(Icons.Filled.SmokingRooms, Gradients.forName(name.ifBlank { "x" }), size = 34.dp) },
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                VSpace(12.dp)
                BigField(
                    value = boxText,
                    onChange = ::onBoxChange,
                    label = "Bir paçkanın qiyməti (₼)",
                    placeholder = "0.00",
                    icon = { Icon3D(Icons.Filled.Inventory2, Gradients.Teal, size = 34.dp) },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                VSpace(12.dp)
                BigField(
                    value = blockText,
                    onChange = ::onBlockChange,
                    label = "Bir blokun qiyməti (₼)",
                    placeholder = "0.00",
                    icon = { Icon3D(Icons.Filled.ViewInAr, Gradients.Blue, size = 34.dp) },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                VSpace(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avtomatik qiymət", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Blok qiyməti = paçka qiyməti × ${InventoryCalc.BOXES_PER_BLOCK}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = linked, onCheckedChange = {
                        linked = it
                        if (it) {
                            // Son yazılan sahədən hesabla; blok qiyməti varsa və son yazılan o idisə, ona toxunma
                            when {
                                priceSource == "box" && boxPrice != null -> onBoxChange(boxText)
                                blockPrice != null -> onBlockChange(blockText)
                                boxPrice != null -> onBoxChange(boxText)
                            }
                        }
                    })
                }
            }

            FieldCard {
                Text("Blok sayı", style = MaterialTheme.typography.titleSmall)
                VSpace(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(Icons.Filled.Remove, "Azalt", enabled = (blocks ?: 0) > 0) {
                        blocksText = ((blocks ?: 0) - 1).coerceAtLeast(0).toString(); error = null
                    }
                    HSpace(10.dp)
                    OutlinedTextField(
                        value = blocksText,
                        onValueChange = { blocksText = Fmt.sanitizeInt(it); error = null },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("0") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); save() }),
                    )
                    HSpace(10.dp)
                    StepButton(Icons.Filled.Add, "Artır") {
                        blocksText = ((blocks ?: 0) + 1).coerceAtMost(9_999_999).toString(); error = null
                    }
                }
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 25, 50).forEach { n ->
                        Surface(
                            onClick = { blocksText = ((blocks ?: 0) + n).coerceAtMost(9_999_999).toString(); error = null },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "+$n", modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        HSpace(10.dp)
                        Text(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Button(
                onClick = { focus.clearFocus(); save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.Check, null)
                HSpace(8.dp)
                Text(if (isEdit) "Dəyişiklikləri yadda saxla" else "Məhsulu əlavə et", style = MaterialTheme.typography.titleMedium)
            }
            VSpace(24.dp)
        }
    }

    if (creatingFirm) {
        FirmDialog(vm, firm = null, onDismiss = { creatingFirm = false }, onSaved = { id ->
            firmId = id
            creatingFirm = false
        })
    }

    if (confirmDelete) {
        val p = original
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon3D(Icons.Filled.Delete, Gradients.Red, size = 52.dp) },
            title = { Text("Məhsul silinsin?") },
            text = { Text("\"${p?.name ?: name}\" siyahıdan silinəcək.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        if (p != null) vm.deleteProduct(p)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et") } },
        )
    }
}

@Composable
private fun LiveTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            AnimatedValueText(value, MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

@Composable
private fun FieldCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BigField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: @Composable () -> Unit,
    keyboard: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Box(Modifier.padding(start = 8.dp, end = 4.dp)) { icon() } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = keyboard,
        textStyle = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Icon(icon, description, modifier = Modifier.size(28.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirmPicker(
    firms: List<Firm>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = firms.firstOrNull { it.id == selectedId }
    Text("Firma (haradan alınıb)", style = MaterialTheme.typography.titleSmall)
    VSpace(10.dp)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Firma seçilməyib",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            leadingIcon = {
                Box(Modifier.padding(start = 8.dp, end = 4.dp)) {
                    Icon3D(Icons.Filled.Business, if (selected != null) Gradients.forName(selected.name) else Gradients.Slate, size = 34.dp)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.titleMedium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Firma seçilməyib") },
                onClick = { onSelect(null); open = false },
                trailingIcon = { if (selected == null) Icon(Icons.Filled.Check, null) },
            )
            firms.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.name) },
                    leadingIcon = { Icon(Icons.Filled.Business, null) },
                    trailingIcon = { if (f.id == selectedId) Icon(Icons.Filled.Check, null) },
                    onClick = { onSelect(f.id); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Yeni firma əlavə et", color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { open = false; onCreate() },
            )
        }
    }
}
