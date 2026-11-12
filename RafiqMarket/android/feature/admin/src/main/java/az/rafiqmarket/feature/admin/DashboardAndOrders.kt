package az.rafiqmarket.feature.admin

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.dto.OrderDto
import az.rafiqmarket.core.network.dto.OrderPatchBody
import az.rafiqmarket.core.network.dto.PeriodStatsDto
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.shared.dial
import az.rafiqmarket.core.ui.shared.navigateTo
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.*

// ---------------- Panel / statistika ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOrders: () -> Unit, onNotifications: () -> Unit, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf("today") }
    Scaffold(topBar = {
        TopAppBar(title = { BrandTitle() }, actions = { IconButton(onClick = onNotifications) { Icon(Icons.Outlined.Notifications, "Bildirişlər") } })
    }) { pad ->
        val data = s.data
        when {
            data == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad).fillMaxSize())
            data == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> PullToRefreshBox(s.loading, onRefresh = { vm.load() }, modifier = Modifier.padding(pad)) {
                val (st, counts) = data
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (counts.new > 0) {
                        ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onOrders), colors = CardDefaults.elevatedCardColors(containerColor = RafiqColors.Orange.copy(alpha = 0.15f))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon3D("🛎️", RafiqColors.Orange, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("${counts.new} yeni sifariş qəbul gözləyir", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Hazırlanır", "${counts.accepted + counts.preparing}", Icons.Outlined.Kitchen, RafiqColors.Green600, Modifier.weight(1f))
                        StatCard("Yoldadır", "${counts.delivering}", Icons.Outlined.DeliveryDining, androidx.compose.ui.graphics.Color(0xFF7B1FA2), Modifier.weight(1f))
                    }
                    Text("Satış statistikası", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("today" to "Günlük", "week" to "Həftəlik", "month" to "Aylıq").forEachIndexed { i, (k, l) ->
                            SegmentedButton(period == k, { period = k }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                        }
                    }
                    val p: PeriodStatsDto = when (period) { "week" -> st.week; "month" -> st.month; else -> st.today }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Satış", money(p.revenue), Icons.Outlined.Payments, RafiqColors.Green700, Modifier.weight(1f))
                        StatCard("Sifariş", "${p.orders}", Icons.Outlined.ReceiptLong, androidx.compose.ui.graphics.Color(0xFF1E88E5), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Tamamlanmış", "${p.completed}", Icons.Outlined.TaskAlt, RafiqColors.Green500, Modifier.weight(1f))
                        StatCard("Ləğv edilmiş", "${p.cancelled}", Icons.Outlined.Cancel, RafiqColors.Red, Modifier.weight(1f))
                    }
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Son 7 gün", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            RevenueBars(st.last7Days.map { it.day to (it.revenue.toFloatOrNull() ?: 0f) })
                        }
                    }
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ümumi gəlir", style = MaterialTheme.typography.titleMedium)
                            Text(money(st.allTime.revenue), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            KeyValue("Tamamlanmış sifarişlər", "${st.allTime.completed}")
                            KeyValue("Ləğv edilmiş sifarişlər", "${st.allTime.cancelled}")
                        }
                    }
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ödəniş hesabatı", style = MaterialTheme.typography.titleMedium)
                            KeyValue("💵 Nağd (${st.payments.cashCount})", money(st.payments.cash))
                            KeyValue("💳 Onlayn (${st.payments.onlineCount})", money(st.payments.online))
                            KeyValue("Uğursuz ödənişlər", "${st.payments.failedPayments}")
                        }
                    }
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ən çox satılan məhsullar", style = MaterialTheme.typography.titleMedium)
                            if (st.topProducts.isEmpty()) Text("Hələ satış yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            st.topProducts.forEachIndexed { i, t -> KeyValue("${i + 1}. ${t.name} (${t.quantity})", money(t.revenue)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevenueBars(data: List<Pair<String, Float>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = data.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.55f
            data.forEachIndexed { i, (_, v) ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(12f))
                val h = size.height * (v / max)
                drawRoundRect(color, Offset(x, size.height - h), Size(bw, h), CornerRadius(12f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (d, _) -> Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

// ---------------- Sifarişlər ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrdersScreen(onOpen: (Long) -> Unit, vm: AdminOrdersViewModel = hiltViewModel()) {
    val group by vm.group.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val countOf = mapOf("new" to counts.new, "accepted" to counts.accepted, "preparing" to counts.preparing,
        "delivering" to counts.delivering, "delivered" to counts.delivered, "cancelled" to counts.cancelled)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Sifarişlər") })
        ScrollableTabRow(selectedTabIndex = vm.groups.indexOfFirst { it.first == group }, edgePadding = 12.dp) {
            vm.groups.forEach { (k, l) ->
                Tab(group == k, { vm.group.value = k }, text = { Text("$l (${countOf[k] ?: 0})") })
            }
        }
        when {
            orders == null && error != null -> ErrorBox(error!!, { vm.load() })
            orders == null -> LoadingBox()
            orders!!.isEmpty() -> EmptyState("📭", "Bu bölmədə sifariş yoxdur")
            else -> PullToRefreshBox(false, { vm.load() }) {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(orders!!, key = { it.id }) { o -> AdminOrderCard(o) { onOpen(o.id) } }
                }
            }
        }
    }
}

@Composable
fun AdminOrderCard(o: OrderDto, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.orderNumber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(o.status)
            }
            Text("${o.contactName.ifBlank { o.customer?.name ?: "" }} • ${prettyPhone(o.contactPhone)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${o.address.street}, ev ${o.address.house}", style = MaterialTheme.typography.bodySmall)
            Row {
                Text("${formatDateTime(o.createdAt)} • ${paymentLabel(o.paymentMethod)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(money(o.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            o.courier?.let { Text("🛵 ${it.name}", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrderScreen(onBack: () -> Unit, vm: AdminOrderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val couriers by vm.couriers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var assignOpen by remember { mutableStateOf(false) }
    var cancelOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    MessageEffect(s.message, snack, vm::clearMessage)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(s.data?.orderNumber ?: "Sifariş") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { if (s.data != null && !OrderStatus.isFinal(s.data!!.status)) IconButton(onClick = { editOpen = true }) { Icon(Icons.Outlined.Edit, "Redaktə") } })
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val o = s.data
        when {
            o == null && s.error != null -> ErrorBox(s.error!!, { vm.load() }, Modifier.padding(pad).fillMaxSize())
            o == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Əməliyyatlar
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusChip(o.status)
                        }
                        when (o.status) {
                            OrderStatus.PLACED -> Button({ vm.status(OrderStatus.ACCEPTED) }, Modifier.fillMaxWidth()) { Text("Sifarişi qəbul et") }
                            OrderStatus.ACCEPTED -> Button({ vm.status(OrderStatus.PREPARING) }, Modifier.fillMaxWidth()) { Text("Hazırlanmağa başla") }
                            else -> Unit
                        }
                        if (o.status in listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.COURIER_ASSIGNED)) {
                            FilledTonalButton({ vm.loadCouriers(); assignOpen = true }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.DeliveryDining, null); Spacer(Modifier.width(6.dp))
                                Text(if (o.courier == null) "Kuryer təyin et" else "Kuryeri dəyiş")
                            }
                        }
                        // Kuryerin telefonu işləmədikdə admin statusu əl ilə irəlilədə bilər
                        val manual = when (o.status) {
                            OrderStatus.COURIER_ASSIGNED -> OrderStatus.PICKED_UP
                            OrderStatus.PICKED_UP -> OrderStatus.ON_THE_WAY
                            OrderStatus.ON_THE_WAY -> OrderStatus.ARRIVED
                            OrderStatus.ARRIVED -> OrderStatus.DELIVERED
                            else -> null
                        }
                        if (o.paymentMethod == "CASH" && o.cashConfirmedAt == null && o.status in listOf(OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED)) {
                            OutlinedButton(vm::confirmCash, Modifier.fillMaxWidth()) { Text("Nağd ödənişi təsdiqlə") }
                        }
                        if (manual != null) OutlinedButton({ vm.status(manual) }, Modifier.fillMaxWidth()) { Text("Əl ilə: ${OrderStatus.label(manual)}") }
                        if (!OrderStatus.isFinal(o.status)) OutlinedButton({ cancelOpen = true }, Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RafiqColors.Red)) { Text("Sifarişi ləğv et") }
                        if (o.paymentStatus == "REFUND_REQUIRED") InfoBanner("Onlayn ödənilmiş sifariş ləğv edilib – məbləği ödəniş provayderinin kabinetindən geri qaytarın.", Icons.Outlined.Warning, RafiqColors.Orange)
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Müştəri və ünvan", style = MaterialTheme.typography.titleMedium)
                        KeyValue("Ad", o.contactName.ifBlank { o.customer?.name ?: "" })
                        KeyValue("Telefon", prettyPhone(o.contactPhone))
                        KeyValue("Ünvan", "${o.address.street}, ev ${o.address.house}")
                        if (o.address.note.isNotBlank()) KeyValue("Əlavə", o.address.note)
                        if (o.customerNote.isNotBlank()) KeyValue("Qeyd", o.customerNote)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            FilledTonalButton({ dial(context, o.contactPhone) }, Modifier.weight(1f)) { Text("Zəng et") }
                            FilledTonalButton({ navigateTo(context, o.address.lat, o.address.lng) }, Modifier.weight(1f)) { Text("Xəritədə") }
                        }
                    }
                }
                o.courier?.let { c ->
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Kuryer", style = MaterialTheme.typography.titleMedium)
                            KeyValue("Ad", c.name)
                            KeyValue("Telefon", prettyPhone(c.phone))
                            KeyValue("Təyinatı qəbul edib", if (o.courierAcceptedAt != null) "Bəli (${formatTime(o.courierAcceptedAt)})" else "Gözlənilir")
                        }
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Məhsullar", style = MaterialTheme.typography.titleMedium)
                        o.items.forEach { i -> KeyValue("${i.quantity} × ${i.name}", money(i.lineTotal)) }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KeyValue("Məhsullar", money(o.subtotal))
                        KeyValue("Çatdırılma", money(o.deliveryFee))
                        KeyValue("Yekun", money(o.total), bold = true)
                        KeyValue("Ödəniş", "${paymentLabel(o.paymentMethod)} • ${paymentStatusLabel(o.paymentStatus)}")
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tarixçə", style = MaterialTheme.typography.titleMedium)
                        o.history.forEach { h -> KeyValue(formatDateTime(h.at), h.note ?: h.label) }
                        o.cancelReason?.let { KeyValue("Ləğv səbəbi", it) }
                    }
                }
            }
        }
    }
    if (assignOpen) AlertDialog(
        onDismissRequest = { assignOpen = false },
        title = { Text("Kuryer seçin") },
        text = {
            if (couriers.isEmpty()) Text("Aktiv kuryer yoxdur. Kuryerlər bölməsindən əlavə edin.")
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                couriers.forEach { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text("${prettyPhone(c.phone)} • aktiv: ${c.activeOrders} • bu gün: ${c.deliveredToday}") },
                        modifier = Modifier.clickable { vm.assign(c.id); assignOpen = false },
                    )
                }
            }
        },
        confirmButton = { TextButton({ assignOpen = false }) { Text("Bağla") } },
    )
    if (cancelOpen) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelOpen = false },
            title = { Text("Sifarişi ləğv et") },
            text = { OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("Səbəb") }) },
            confirmButton = { TextButton({ vm.status(OrderStatus.CANCELLED, reason.ifBlank { null }); cancelOpen = false }) { Text("Ləğv et") } },
            dismissButton = { TextButton({ cancelOpen = false }) { Text("İmtina") } },
        )
    }
    if (editOpen && s.data != null) {
        val o = s.data!!
        var note by remember { mutableStateOf(o.address.note) }
        var phone by remember { mutableStateOf(o.contactPhone.removePrefix("+994")) }
        var cnote by remember { mutableStateOf(o.customerNote) }
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("Sifariş məlumatları") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Telefon") }, prefix = { Text("+994 ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(note, { note = it.take(300) }, label = { Text("Ünvan qeydi") })
                    OutlinedTextField(cnote, { cnote = it.take(300) }, label = { Text("Sifariş qeydi") })
                }
            },
            confirmButton = { TextButton({ vm.patch(OrderPatchBody(addressNote = note, contactPhone = "+994$phone", customerNote = cnote)); editOpen = false }) { Text("Yadda saxla") } },
            dismissButton = { TextButton({ editOpen = false }) { Text("İmtina") } },
        )
    }
}
