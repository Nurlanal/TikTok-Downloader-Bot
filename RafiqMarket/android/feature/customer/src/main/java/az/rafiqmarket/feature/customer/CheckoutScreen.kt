package az.rafiqmarket.feature.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.dto.LatLngDto
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.money
import az.rafiqmarket.core.util.polygonCenter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun openPayment(context: Context, url: String) {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(onBack: () -> Unit, onPlaced: (Long) -> Unit, vm: CheckoutViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.result.collect { r ->
            when (r) {
                is CheckoutResult.Placed -> onPlaced(r.orderId)
                is CheckoutResult.Pay -> { onPlaced(r.orderId); openPayment(context, r.url) }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sifarişin rəsmiləşdirilməsi") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    if (s.error != null) {
                        Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = vm::submit, enabled = !s.submitting, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        if (s.submitting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(if (s.paymentMethod == "ONLINE") "Ödənişə keç" + (s.quote?.let { " • ${money(it.total)}" } ?: "")
                                  else "Sifarişi təsdiqlə" + (s.quote?.let { " • ${money(it.total)}" } ?: ""))
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner("Çatdırılma yalnız ${s.zoneName} daxilində həyata keçirilir.", Icons.Outlined.LocalShipping)
            Text("Xəritədə ünvanı seçin", style = MaterialTheme.typography.titleMedium)
            AddressMapPicker(s.zone, s.lat, s.lng, s.insideZone, onPick = vm::setLocation)
            when (s.insideZone) {
                true -> InfoBanner("Ünvan çatdırılma zonasındadır ✅", Icons.Outlined.CheckCircle, RafiqColors.Green600)
                false -> InfoBanner(CheckoutViewModel.ZONE_MESSAGE, Icons.Outlined.Block, RafiqColors.Red)
                null -> Unit
            }
            if (s.zoneLoaded && s.zone.size < 3) InfoBanner("Çatdırılma zonası hələ market tərəfindən təyin edilməyib. Sifariş hazırda qəbul edilmir.", Icons.Outlined.Warning, RafiqColors.Orange)

            Text("Ünvan məlumatları", style = MaterialTheme.typography.titleMedium)
            Field(s.street, { v -> vm.update { it.copy(street = v.take(160)) } }, "Küçə və ya məhəllə", Icons.Outlined.Signpost)
            Field(s.house, { v -> vm.update { it.copy(house = v.take(40)) } }, "Ev nömrəsi", Icons.Outlined.Home)
            Field(s.addressNote, { v -> vm.update { it.copy(addressNote = v.take(300)) } }, "Əlavə ünvan məlumatı (bina, qapı, nişanə)", Icons.Outlined.Notes, single = false)
            Field(s.contactName, { v -> vm.update { it.copy(contactName = v.take(120)) } }, "Ad", Icons.Outlined.Person)
            OutlinedTextField(
                s.contactPhone, { v -> vm.update { it.copy(contactPhone = v.filter { c -> c.isDigit() }.take(9)) } },
                label = { Text("Telefon nömrəsi") }, prefix = { Text("+994 ") }, leadingIcon = { Icon(Icons.Outlined.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Field(s.note, { v -> vm.update { it.copy(note = v.take(300)) } }, "Sifarişə qeyd (istəyə bağlı)", Icons.Outlined.EditNote, single = false)

            Text("Ödəniş üsulu", style = MaterialTheme.typography.titleMedium)
            PaymentOption("CASH", "Nağd ödəniş", "Kuryerə təhvil zamanı ödəyin", "💵", s.paymentMethod) { vm.update { it.copy(paymentMethod = "CASH") } }
            PaymentOption("ONLINE", "Onlayn bank kartı", "Epoint təhlükəsiz ödəniş səhifəsi", "💳", s.paymentMethod) { vm.update { it.copy(paymentMethod = "ONLINE") } }

            s.quote?.let { q ->
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        KeyValue("Məhsullar", money(q.subtotal))
                        KeyValue("Çatdırılma haqqı", if (q.deliveryFee.toBigDecimal().signum() == 0) "Pulsuz" else money(q.deliveryFee))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KeyValue("Yekun məbləğ", money(q.total), bold = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, single: Boolean = true) {
    OutlinedTextField(value, onChange, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, singleLine = single,
        minLines = if (single) 1 else 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PaymentOption(value: String, title: String, subtitle: String, emoji: String, selected: String, onSelect: () -> Unit) {
    val isSel = value == selected
    OutlinedCard(
        Modifier.fillMaxWidth().selectable(isSel, onClick = onSelect), shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D(emoji, MaterialTheme.colorScheme.primary, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(isSel, onClick = onSelect)
        }
    }
}

/** Xəritədə ünvan seçimi: xəritəni sürüşdürün, mərkəzdəki sancaq ünvanı göstərir */
@SuppressLint("MissingPermission")
@Composable
fun AddressMapPicker(zone: List<LatLngDto>, lat: Double?, lng: Double?, inside: Boolean?, onPick: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!MapsSupport.keyConfigured) { MapsKeyMissing(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp))); return }

    val start = when {
        lat != null && lng != null -> LatLng(lat, lng)
        zone.isNotEmpty() -> polygonCenter(zone)!!.let { LatLng(it.lat, it.lng) }
        else -> LatLng(40.4093, 49.8671) // yalnız ilkin kamera mövqeyi
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, if (zone.isNotEmpty()) 15f else 11f) }
    LaunchedEffect(zone) {
        if (lat == null && zone.isNotEmpty()) polygonCenter(zone)?.let { camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 15f)) }
    }
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving) camera.position.target.let { onPick(it.latitude, it.longitude) }
    }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    suspend fun goToMe() {
        val loc = runCatching {
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        }.getOrNull() ?: return
        camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f))
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasPermission = r.values.any { it }
        if (hasPermission) scope.launch { goToMe() }
    }

    Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp))) {
        GoogleMap(
            modifier = Modifier.matchParentSize(), cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        ) {
            if (zone.size >= 3) Polygon(
                points = zone.map { LatLng(it.lat, it.lng) },
                fillColor = RafiqColors.Green500.copy(alpha = 0.15f), strokeColor = RafiqColors.Green700, strokeWidth = 4f,
            )
        }
        Icon(
            Icons.Filled.LocationOn, "Seçilmiş ünvan",
            tint = if (inside == false) RafiqColors.Red else RafiqColors.Green700,
            modifier = Modifier.align(Alignment.Center).size(44.dp).offset(y = (-20).dp),
        )
        SmallFloatingActionButton(
            onClick = {
                if (hasPermission) scope.launch { goToMe() }
                else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { Icon(Icons.Outlined.MyLocation, "Mənim yerim") }
        Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
            Text("Sancağı evinizin üzərinə gətirin", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}
