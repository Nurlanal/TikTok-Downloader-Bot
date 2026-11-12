package az.rafiqmarket.feature.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.dto.OrderDto
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOpen: (Long) -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }
    Scaffold(topBar = { TopAppBar(title = { Text("Sifarişlərim") }) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                orders == null && error != null -> ErrorBox(error!!, { vm.load() })
                orders == null -> LoadingBox()
                orders!!.isEmpty() -> EmptyState("🧾", "Hələ sifarişiniz yoxdur")
                else -> PullToRefreshBox(false, onRefresh = { vm.load() }) {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(orders!!, key = { it.id }) { o -> OrderRow(o) { onOpen(o.id) } }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderRow(o: OrderDto, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.orderNumber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(o.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(formatDateTime(o.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                Text("${o.itemCount ?: o.items.size} məhsul • ${paymentLabel(o.paymentMethod)}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(o.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(onBack: () -> Unit, vm: OrderDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf(false) }
    MessageEffect(s.message, snack, vm::clearMessage)
    LifecycleResumeEffect(Unit) { vm.onResume(); onPauseOrDispose { } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.order?.orderNumber ?: "Sifariş") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val o = s.order
        when {
            o == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad).fillMaxSize())
            o == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (o.status == OrderStatus.PENDING_PAYMENT) {
                    InfoBanner("Onlayn ödəniş gözlənilir. Ödəniş bank tərəfindən təsdiqləndikdən sonra sifariş marketə göndəriləcək.", Icons.Outlined.CreditCard, RafiqColors.Orange)
                    Button(onClick = { vm.pay { url -> openPayment(context, url) } }, modifier = Modifier.fillMaxWidth()) { Text("Ödənişi tamamla") }
                }
                if (o.trackingActive && o.courier != null) LiveTrackingCard(s, o)

                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sifariş statusu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusChip(o.status)
                        }
                        Spacer(Modifier.height(12.dp))
                        val times = o.history.groupBy { it.status }.mapValues { formatTime(it.value.first().at) }
                        OrderTimeline(o.status, times)
                        o.cancelReason?.let { Spacer(Modifier.height(8.dp)); Text("Səbəb: $it", color = MaterialTheme.colorScheme.error) }
                    }
                }

                if (o.courier != null && !o.trackingActive && !OrderStatus.isFinal(o.status)) {
                    CourierCard(o.courier!!.name, o.courier!!.phone) { dial(context, o.courier!!.phone) }
                }

                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Məhsullar", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        o.items.forEach { i ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("${i.quantity} × ${i.name}", modifier = Modifier.weight(1f))
                                Text(money(i.lineTotal))
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        KeyValue("Məhsullar", money(o.subtotal))
                        KeyValue("Çatdırılma", money(o.deliveryFee))
                        KeyValue("Yekun", money(o.total), bold = true)
                        KeyValue("Ödəniş", "${paymentLabel(o.paymentMethod)} • ${paymentStatusLabel(o.paymentStatus)}")
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Çatdırılma ünvanı", style = MaterialTheme.typography.titleMedium)
                        Text("${o.address.street}, ev ${o.address.house}")
                        if (o.address.note.isNotBlank()) Text(o.address.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(prettyPhone(o.contactPhone), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (o.status == OrderStatus.PLACED || o.status == OrderStatus.PENDING_PAYMENT) {
                    OutlinedButton(onClick = { confirmCancel = true }, enabled = !s.busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RafiqColors.Red)) { Text("Sifarişi ləğv et") }
                }
            }
        }
    }
    if (confirmCancel) ConfirmDialog("Sifarişi ləğv et", "Sifarişi ləğv etmək istədiyinizə əminsiniz?", "Ləğv et", vm::cancel) { confirmCancel = false }
}

@Composable
private fun CourierCard(name: String, phone: String, onCall: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D("🛵", MaterialTheme.colorScheme.primary, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Kuryer" }, fontWeight = FontWeight.SemiBold)
                Text(prettyPhone(phone), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onCall) { Icon(Icons.Outlined.Call, "Zəng et") }
        }
    }
}

/** Kuryerin canlı mövqeyi – yalnız bu sifarişə təyin edilmiş kuryer */
@Composable
private fun LiveTrackingCard(s: TrackUi, o: OrderDto) {
    val context = LocalContext.current
    val dest = LatLng(o.address.lat, o.address.lng)
    val courier = if (s.courierLat != null && s.courierLng != null) LatLng(s.courierLat, s.courierLng) else null
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val age = remember(s.locationAt, tick) { secondsSince(s.locationAt) }

    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column {
            if (MapsSupport.keyConfigured) {
                val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(courier ?: dest, 15f) }
                LaunchedEffect(courier) {
                    if (courier != null) {
                        val bounds = LatLngBounds.builder().include(courier).include(dest).build()
                        runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
                    }
                }
                GoogleMap(
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    cameraPositionState = camera, uiSettings = MapUiSettings(zoomControlsEnabled = false),
                ) {
                    Marker(state = rememberMarkerState(key = "dest", position = dest), title = "Çatdırılma ünvanı")
                    if (courier != null) {
                        val ms = rememberMarkerState(key = "courier", position = courier)
                        LaunchedEffect(courier) { ms.position = courier }
                        Marker(state = ms, title = "Kuryer", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        Polyline(listOf(courier, dest), color = RafiqColors.Green600, width = 8f)
                    }
                }
            } else MapsKeyMissing(Modifier.fillMaxWidth().height(160.dp))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D("🛵", MaterialTheme.colorScheme.primary, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.courier?.name?.ifBlank { "Kuryer" } ?: "Kuryer", fontWeight = FontWeight.SemiBold)
                        Text(prettyPhone(o.courier?.phone ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { o.courier?.phone?.let { dial(context, it) } }) { Icon(Icons.Outlined.Call, "Zəng et") }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    s.locationAt == null -> InfoBanner("Kuryerin mövqeyi gözlənilir…", Icons.Outlined.GpsNotFixed)
                    age > 60 -> InfoBanner("GPS bağlantısı kəsilib. Son məlum mövqe: ${formatTime(s.locationAt)} (${ago(s.locationAt)})", Icons.Outlined.GpsOff, RafiqColors.Orange)
                    else -> Text("● Canlı • yeniləndi ${ago(s.locationAt)}", color = RafiqColors.Green600, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
