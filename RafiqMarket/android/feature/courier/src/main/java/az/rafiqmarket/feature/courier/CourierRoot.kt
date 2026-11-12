package az.rafiqmarket.feature.courier

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.rafiqmarket.core.network.dto.OrderDto
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.shared.NotificationsScreen
import az.rafiqmarket.core.ui.shared.ProfileScreen
import az.rafiqmarket.core.ui.shared.dial
import az.rafiqmarket.core.ui.shared.navigateTo
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/** Kuryer interfeysi – öz aşağı naviqasiya menyusu ilə */
@Composable
fun CourierRoot(openOrderId: Long? = null) {
    val nav = rememberNavController()
    val vm: CourierHomeViewModel = hiltViewModel()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val needsTracking by vm.needsTracking.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Triple("new", "Yeni", Icons.Outlined.NotificationImportant),
        Triple("active", "Aktiv", Icons.Outlined.DeliveryDining),
        Triple("delivered", "Çatdırılmış", Icons.Outlined.TaskAlt),
        Triple("profile", "Profil", Icons.Outlined.Person),
    )

    GpsController(needsTracking)
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }

    Scaffold(bottomBar = {
        if (tabs.any { it.first == route }) NavigationBar {
            tabs.forEach { (r, label, icon) ->
                NavigationBarItem(
                    selected = route == r,
                    onClick = { nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = {
                        val n = orders[r]?.size ?: 0
                        BadgedBox(badge = { if (r == "new" && n > 0) Badge { Text("$n") } }) { Icon(icon, label) }
                    },
                    label = { Text(label) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = "active", modifier = Modifier.padding(pad)) {
            listOf("new", "active", "delivered").forEach { tab ->
                composable(tab) { CourierOrdersTab(tab, vm, onOpen = { nav.navigate("order/$it") }, onNotifications = { nav.navigate("notifications") }) }
            }
            composable("profile") { ProfileScreen(onNotifications = { nav.navigate("notifications") }) }
            composable("order/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.LongType })) {
                CourierOrderScreen(onBack = { nav.popBackStack(); vm.refresh() })
            }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }, onOpenOrder = { nav.navigate("order/$it") }) }
        }
    }
    LaunchedEffect(openOrderId) { if (openOrderId != null) nav.navigate("order/$openOrderId") }
}

/**
 * GPS paylaşımını idarə edir: aktiv çatdırılma varsa – icazə + foreground service; yoxdursa – dayandırır.
 * İcazədən əvvəl məxfilik açıqlaması göstərilir (Google Play tələbi).
 */
@Composable
private fun GpsController(needsTracking: Boolean) {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) { denied = false; LocationTrackingService.start(context) } else denied = true
    }
    LaunchedEffect(needsTracking) {
        if (needsTracking) {
            if (LocationTrackingService.hasPermission(context)) LocationTrackingService.start(context) else showDisclosure = true
        } else LocationTrackingService.stop(context)
    }
    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false; denied = true },
        icon = { Icon(Icons.Outlined.MyLocation, null) },
        title = { Text("Mövqe paylaşımı") },
        text = {
            Text("Rafiq Market çatdırılma zamanı GPS mövqeyinizi toplayır və yalnız həmin sifarişin müştərisinə və market adminə göstərir. " +
                "Paylaşım yalnız sifarişi götürdükdən sonra başlayır və sifariş təhvil verildikdə avtomatik dayanır. " +
                "Tətbiq arxa planda olarkən bildiriş panelində daimi bildiriş görünəcək.")
        },
        confirmButton = {
            TextButton(onClick = {
                showDisclosure = false
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                launcher.launch(perms.toTypedArray())
            }) { Text("Davam et") }
        },
        dismissButton = { TextButton(onClick = { showDisclosure = false; denied = true }) { Text("İmtina") } },
    )
    if (denied && needsTracking) {
        AlertDialog(
            onDismissRequest = { denied = false },
            title = { Text("GPS icazəsi lazımdır") },
            text = { Text("Müştəri sizi xəritədə izləyə bilməyəcək. İcazəni Parametrlər > Tətbiqlər > Rafiq Market > İcazələr bölməsindən verə bilərsiniz.") },
            confirmButton = { TextButton(onClick = { denied = false; showDisclosure = true }) { Text("Yenidən cəhd et") } },
            dismissButton = { TextButton(onClick = { denied = false }) { Text("Bağla") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourierOrdersTab(tab: String, vm: CourierHomeViewModel, onOpen: (Long) -> Unit, onNotifications: () -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val gps by LocationTrackingService.running.collectAsStateWithLifecycle()
    val list = orders[tab]
    val title = when (tab) { "new" -> "Yeni təyin olunmuş"; "active" -> "Aktiv sifarişlər"; else -> "Çatdırılmış" }

    Scaffold(topBar = {
        TopAppBar(title = { BrandTitle() }, actions = { IconButton(onClick = onNotifications) { Icon(Icons.Outlined.Notifications, "Bildirişlər") } })
    }) { pad ->
        PullToRefreshBox(loading, onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Bu gün çatdırılıb", "${stats?.deliveredToday ?: "–"}", Icons.Outlined.TaskAlt, RafiqColors.Green600, Modifier.weight(1f))
                        StatCard("Bu gün nağd", stats?.cashCollectedToday?.let { money(it) } ?: "–", Icons.Outlined.Payments, RafiqColors.Orange, Modifier.weight(1f))
                    }
                }
                if (gps) item { InfoBanner("GPS paylaşımı aktivdir – müştəri mövqeyinizi görür", Icons.Outlined.MyLocation, RafiqColors.Green600) }
                item { Text(title, style = MaterialTheme.typography.titleMedium) }
                if (error != null && list == null) item { ErrorBox(error!!, { vm.refresh() }, Modifier.fillMaxWidth()) }
                if (list != null && list.isEmpty()) item {
                    EmptyState(if (tab == "delivered") "📦" else "🛵", if (tab == "new") "Yeni sifariş yoxdur" else "Sifariş yoxdur", modifier = Modifier.fillMaxWidth())
                }
                items(list.orEmpty(), key = { it.id }) { o -> CourierOrderCard(o) { onOpen(o.id) } }
            }
        }
    }
}

@Composable
private fun CourierOrderCard(o: OrderDto, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.orderNumber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(o.status, if (o.status == OrderStatus.COURIER_ASSIGNED && o.courierAcceptedAt == null) "Təsdiq gözləyir" else null)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${o.address.street}, ev ${o.address.house}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(o.contactName.ifBlank { o.customer?.name ?: "" }, modifier = Modifier.weight(1f))
                Text(money(o.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(paymentLabel(o.paymentMethod) + if (o.paymentMethod == "ONLINE") " (ödənilib)" else " – pulu alın",
                style = MaterialTheme.typography.labelMedium, color = if (o.paymentMethod == "CASH") RafiqColors.Orange else RafiqColors.Green600)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierOrderScreen(onBack: () -> Unit, vm: CourierOrderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    MessageEffect(s.message, snack, vm::clearMessage)

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.order?.orderNumber ?: "Sifariş") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            val o = s.order ?: return@Scaffold
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (o.status == OrderStatus.COURIER_ASSIGNED && o.courierAcceptedAt == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { confirm = "Bu sifarişdən imtina edirsiniz?" to { vm.reject(); Unit } }, enabled = !s.busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("İmtina") }
                            Button(onClick = vm::accept, enabled = !s.busy, modifier = Modifier.weight(2f).height(52.dp)) { Text("Sifarişi qəbul et") }
                        }
                    }
                    val cashNeeded = o.paymentMethod == "CASH" && o.cashConfirmedAt == null
                    if (cashNeeded && (o.status == OrderStatus.ON_THE_WAY || o.status == OrderStatus.ARRIVED)) {
                        Button(
                            onClick = { confirm = "${money(o.total)} nağd ödənişi aldığınızı təsdiqləyirsiniz?" to { vm.confirmCash(); Unit } },
                            enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RafiqColors.Orange),
                        ) { Icon(Icons.Outlined.Payments, null); Spacer(Modifier.width(8.dp)); Text("Nağd ${money(o.total)} alındı") }
                    }
                    nextCourierStep(o)?.let { (next, label) ->
                        val blocked = next == OrderStatus.DELIVERED && cashNeeded
                        Button(onClick = {
                            if (next == OrderStatus.DELIVERED) confirm = "Sifarişi təhvil verdiyinizi təsdiqləyirsiniz?" to { vm.advance(next); Unit } else vm.advance(next)
                        }, enabled = !s.busy && !blocked, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (blocked) "Əvvəlcə nağd ödənişi təsdiqləyin" else label)
                        }
                    }
                }
            }
        },
    ) { pad ->
        val o = s.order
        when {
            o == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad).fillMaxSize())
            o == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusChip(o.status)
                // Xəritə və marşrut
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    val dest = LatLng(o.address.lat, o.address.lng)
                    if (MapsSupport.keyConfigured) {
                        val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(dest, 16f) }
                        GoogleMap(
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                            cameraPositionState = cam,
                            properties = MapProperties(isMyLocationEnabled = LocationTrackingService.hasPermission(context)),
                        ) { Marker(state = rememberMarkerState(position = dest), title = o.contactName, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) }
                    } else MapsKeyMissing(Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { navigateTo(context, o.address.lat, o.address.lng) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Directions, null); Spacer(Modifier.width(6.dp)); Text("Marşrut")
                        }
                        FilledTonalButton(onClick = { dial(context, o.contactPhone) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(6.dp)); Text("Zəng et")
                        }
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Müştəri", style = MaterialTheme.typography.titleMedium)
                        KeyValue("Ad", o.contactName.ifBlank { o.customer?.name ?: "" })
                        KeyValue("Telefon", prettyPhone(o.contactPhone))
                        KeyValue("Ünvan", "${o.address.street}, ev ${o.address.house}")
                        if (o.address.note.isNotBlank()) KeyValue("Əlavə", o.address.note)
                        if (o.customerNote.isNotBlank()) KeyValue("Qeyd", o.customerNote)
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Məhsullar", style = MaterialTheme.typography.titleMedium)
                        o.items.forEach { i -> KeyValue("${i.quantity} × ${i.name}", money(i.lineTotal)) }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KeyValue("Yekun məbləğ", money(o.total), bold = true)
                        KeyValue("Ödəniş üsulu", paymentLabel(o.paymentMethod))
                        KeyValue("Ödəniş statusu", paymentStatusLabel(o.paymentStatus))
                    }
                }
            }
        }
    }
    confirm?.let { (text, action) -> ConfirmDialog("Təsdiq", text, "Bəli", action) { confirm = null } }
}
