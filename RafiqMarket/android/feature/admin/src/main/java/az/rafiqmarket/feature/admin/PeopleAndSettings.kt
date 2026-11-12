package az.rafiqmarket.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.shared.dial
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

// ---------------- Kuryerlər ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouriersScreen(onLiveMap: () -> Unit, vm: CouriersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    MessageEffect(s.message, snack, vm::clearMessage)
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kuryerlər") }, actions = { TextButton(onLiveMap) { Icon(Icons.Outlined.Map, null); Spacer(Modifier.width(4.dp)); Text("Canlı xəritə") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kuryer əlavə et") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val list = s.data
        when {
            list == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> EmptyState("🛵", "Kuryer yoxdur", "Kuryerin telefon nömrəsini əlavə edin. Kuryer eyni tətbiqə həmin nömrə ilə daxil olacaq.", Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Bu gün çatdırılıb", "${list.sumOf { it.deliveredToday }}", Icons.Outlined.TaskAlt, RafiqColors.Green600, Modifier.weight(1f))
                        StatCard("Aktiv çatdırılma", "${list.sumOf { it.activeOrders }}", Icons.Outlined.DeliveryDining, Color(0xFF7B1FA2), Modifier.weight(1f))
                    }
                }
                items(list, key = { it.id }) { c ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon3D("🛵", if (c.isActive) RafiqColors.Green600 else Color.Gray, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name.ifBlank { "Adsız" }, fontWeight = FontWeight.SemiBold)
                                    Text(prettyPhone(c.phone), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { dial(context, c.phone) })
                                }
                                Switch(c.isActive, { vm.setActive(c.id, it) })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Bu gün: ${c.deliveredToday} • Ümumi: ${c.deliveredTotal} • Aktiv: ${c.activeOrders}", style = MaterialTheme.typography.bodySmall)
                            c.location?.let { Text("📍 Son mövqe: ${ago(it.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = RafiqColors.Green600) }
                            if (!c.isActive) Text("Deaktiv – daxil ola bilmir", style = MaterialTheme.typography.labelSmall, color = RafiqColors.Red)
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var phone by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Kuryer əlavə et") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text("Ad Soyad") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Mobil nömrə") }, prefix = { Text("+994 ") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Text("Kuryer Rafiq Market tətbiqini quraşdırıb bu nömrə ilə SMS kodla daxil olacaq və avtomatik kuryer interfeysi açılacaq.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({ vm.add(phone, name); adding = false }, enabled = phone.length == 9 && name.trim().length >= 2) { Text("Əlavə et") } },
            dismissButton = { TextButton({ adding = false }) { Text("İmtina") } },
        )
    }
}

/** Aktiv çatdırılmaların canlı xəritəsi */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCouriersScreen(onBack: () -> Unit, vm: CouriersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Aktiv çatdırılmalar") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) }) { pad ->
        val located = s.data.orEmpty().filter { it.location != null }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!MapsSupport.keyConfigured) { MapsKeyMissing(Modifier.fillMaxWidth().weight(1f)); return@Column }
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(40.4093, 49.8671), 11f) }
            LaunchedEffect(located.size) {
                if (located.isNotEmpty()) {
                    val b = LatLngBounds.builder(); located.forEach { b.include(LatLng(it.location!!.lat, it.location!!.lng)) }
                    runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 150)) }
                }
            }
            GoogleMap(Modifier.fillMaxWidth().weight(1f), cameraPositionState = camera) {
                located.forEach { c ->
                    val pos = LatLng(c.location!!.lat, c.location!!.lng)
                    val ms = rememberMarkerState(key = "c${c.id}", position = pos)
                    LaunchedEffect(pos) { ms.position = pos }
                    Marker(state = ms, title = c.name, snippet = "Yeniləndi: ${ago(c.location!!.updatedAt)}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                }
            }
            Text(if (located.isEmpty()) "Hazırda canlı izlənən çatdırılma yoxdur" else "${located.size} kuryer xəritədə",
                modifier = Modifier.padding(16.dp))
        }
    }
}

// ---------------- Müştərilər ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, vm: CustomersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Müştərilər") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { vm.query.value = it }, placeholder = { Text("Ad və ya nömrə") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            val list = s.data
            when {
                list == null && s.error != null -> ErrorBox(s.error!!, { vm.load() })
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState("👥", "Müştəri tapılmadı")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { c ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name.ifBlank { "Adsız" }, fontWeight = FontWeight.SemiBold)
                                    Text(prettyPhone(c.phone), style = MaterialTheme.typography.bodySmall)
                                    Text("${c.orderCount} sifariş • ${money(c.totalSpent)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!c.isActive) AssistChip({}, label = { Text("Bloklanıb") })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(onBack: () -> Unit, onOrder: (Long) -> Unit, vm: UserDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var roleConfirm by remember { mutableStateOf<String?>(null) }
    MessageEffect(s.message, snack, vm::clearMessage)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Müştəri məlumatları") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val d = s.data
        when {
            d == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad))
            d == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(d.user.name.ifBlank { "Adsız" }, style = MaterialTheme.typography.titleLarge)
                            KeyValue("Telefon", prettyPhone(d.user.phone))
                            KeyValue("Rol", az.rafiqmarket.core.ui.shared.roleLabel(d.user.roleEnum))
                            KeyValue("Qeydiyyat", formatDateTime(d.user.createdAt))
                            KeyValue("Hesab statusu", if (d.user.isActive) "Aktiv" else "Bloklanıb")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton({ dial(context, d.user.phone) }, Modifier.weight(1f)) { Text("Zəng et") }
                                OutlinedButton({ vm.setActive(!d.user.isActive) }, Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (d.user.isActive) RafiqColors.Red else RafiqColors.Green600)) {
                                    Text(if (d.user.isActive) "Blokla" else "Aktivləşdir")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Rol təyini (yalnız admin)", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("CUSTOMER" to "Müştəri", "COURIER" to "Kuryer", "ADMIN" to "Admin").forEach { (r, l) ->
                                    FilterChip(d.user.role == r, { if (d.user.role != r) roleConfirm = r }, label = { Text(l) })
                                }
                            }
                        }
                    }
                }
                item { Text("Sifariş tarixçəsi (${d.orders.size})", style = MaterialTheme.typography.titleMedium) }
                items(d.orders, key = { it.id }) { o -> AdminOrderCard(o) { onOrder(o.id) } }
            }
        }
    }
    roleConfirm?.let { r ->
        ConfirmDialog("Rolu dəyiş", "İstifadəçinin rolu \"${mapOf("CUSTOMER" to "Müştəri", "COURIER" to "Kuryer", "ADMIN" to "Admin")[r]}\" olsun? Admin rolu tam idarəetmə səlahiyyəti verir.",
            "Dəyiş", { vm.setRole(r) }) { roleConfirm = null }
    }
}

// ---------------- Çatdırılma parametrləri ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onZone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    MessageEffect(msg, snack) { vm.message.value = null }
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Çatdırılma parametrləri") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(vm::save, enabled = f != null && !saving, modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp)) { Text("Yadda saxla") }
            }
        },
    ) { pad ->
        val s = f
        when {
            s == null && error != null -> ErrorBox(error!!, { vm.load() }, Modifier.padding(pad))
            s == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onZone), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile3D(Icons.Outlined.Map, Color(0xFF00897B), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Çatdırılma sərhədi", style = MaterialTheme.typography.titleMedium)
                            Text(if (s.zonePoints >= 3) "${s.zonePoints} nöqtəli zona təyin edilib" else "Təyin edilməyib – sifariş qəbul edilmir!",
                                color = if (s.zonePoints >= 3) RafiqColors.Green600 else RafiqColors.Red, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Outlined.Edit, null)
                    }
                }
                ListItem(
                    headlineContent = { Text("Sifariş qəbulu") },
                    supportingContent = { Text(if (s.ordersEnabled) "Aktiv – müştərilər sifariş verə bilər" else "Passiv – yeni sifariş qəbul edilmir") },
                    trailingContent = { Switch(s.ordersEnabled, { v -> vm.update { it.copy(ordersEnabled = v) } }) },
                )
                OutlinedTextField(s.zoneName, { v -> vm.update { it.copy(zoneName = v.take(80)) } }, label = { Text("Zonanın adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Money(s.deliveryFee, "Çatdırılma haqqı ₼") { v -> vm.update { it.copy(deliveryFee = v) } }
                Money(s.minOrderAmount, "Minimum sifariş məbləği ₼") { v -> vm.update { it.copy(minOrderAmount = v) } }
                ListItem(headlineContent = { Text("Pulsuz çatdırılma limiti") }, trailingContent = { Switch(s.freeEnabled, { v -> vm.update { it.copy(freeEnabled = v) } }) })
                if (s.freeEnabled) Money(s.freeThreshold, "Bu məbləğdən yuxarı pulsuz ₼") { v -> vm.update { it.copy(freeThreshold = v) } }
                Text("Çatdırılma saatları (Bakı vaxtı)", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.openTime, { v -> vm.update { it.copy(openTime = v.take(5)) } }, label = { Text("Açılış (SS:DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(s.closeTime, { v -> vm.update { it.copy(closeTime = v.take(5)) } }, label = { Text("Bağlanış (SS:DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Açılış və bağlanış eyni olarsa market 24 saat açıq sayılır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Money(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

// ---------------- Çatdırılma zonası redaktoru ----------------
/**
 * Admin Xıdırlı kəndinin çatdırılma sərhədini xəritədə nöqtə-nöqtə çəkir.
 * Sərhəd əvvəlcədən doldurulmur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditorScreen(onBack: () -> Unit, vm: ZoneEditorViewModel = hiltViewModel()) {
    val points by vm.points.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    MessageEffect(msg, snack) { vm.message.value = null }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Çatdırılma zonası") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    IconButton(vm::undo, enabled = points.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Geri al") }
                    IconButton({ confirmClear = true }, enabled = points.isNotEmpty()) { Icon(Icons.Outlined.DeleteSweep, "Təmizlə") }
                })
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Text("Nöqtələr: ${points.size} (ən azı 3)", style = MaterialTheme.typography.bodySmall)
                    Button(vm::save, enabled = points.size >= 3 && !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Zonanı yadda saxla") }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!loaded) { LoadingBox(); return@Box }
            if (!MapsSupport.keyConfigured) { MapsKeyMissing(Modifier.fillMaxSize()); return@Box }
            val start = polygonCenter(points)?.let { LatLng(it.lat, it.lng) } ?: LatLng(40.4093, 49.8671)
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, if (points.isEmpty()) 10f else 15f) }
            GoogleMap(
                Modifier.fillMaxSize(), cameraPositionState = camera,
                properties = MapProperties(mapType = MapType.HYBRID),
                onMapClick = { vm.add(it.latitude, it.longitude) },
            ) {
                val ll = points.map { LatLng(it.lat, it.lng) }
                if (ll.size >= 3) Polygon(ll, fillColor = RafiqColors.Green500.copy(alpha = 0.25f), strokeColor = RafiqColors.Lime, strokeWidth = 5f)
                else if (ll.size == 2) Polyline(ll, color = RafiqColors.Lime, width = 6f)
                ll.forEachIndexed { i, p ->
                    val ms = rememberMarkerState(key = "p$i", position = p)
                    LaunchedEffect(p) { if (!ms.isDragging) ms.position = p }
                    LaunchedEffect(ms.isDragging) { if (!ms.isDragging && ms.position != p) vm.move(i, ms.position.latitude, ms.position.longitude) }
                    Marker(state = ms, draggable = true, title = "Nöqtə ${i + 1}")
                }
            }
            Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                Text("Kəndin sərhədi boyunca xəritəyə toxunaraq nöqtələr qoyun. Nöqtəni sürüşdürmək üçün basıb saxlayın.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmClear) ConfirmDialog("Zonanı təmizlə", "Bütün nöqtələr silinsin? (Yadda saxlamadan əvvəl serverdəki zona dəyişmir.)", "Təmizlə", vm::clear) { confirmClear = false }
}
