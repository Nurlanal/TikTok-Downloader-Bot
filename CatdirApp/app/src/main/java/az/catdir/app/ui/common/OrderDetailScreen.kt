package az.catdir.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.catdir.app.AppViewModel
import az.catdir.app.model.Money
import az.catdir.app.model.Order
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Role
import az.catdir.app.model.UserProfile
import az.catdir.app.ui.theme.Gradients
import az.catdir.app.ui.theme.statusColor

private val CANCEL_REASONS = listOf("Fikrimi dəyişdim", "Məhsul bitib", "Ünvana çatdırılmır", "Müştəri cavab vermir", "Digər")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OrderDetailScreen(vm: AppViewModel, orderId: String, onBack: () -> Unit) {
    val flow = remember(orderId) { vm.order(orderId) }
    val order by flow.collectAsState(initial = null)
    val user by vm.user.collectAsStateWithLifecycle()
    var cancelling by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(order?.let { "Sifariş #${it.shortId}" } ?: "Sifariş") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
        bottomBar = {
            val o = order
            val u = user
            if (o != null && u != null) ActionBar(vm, u, o, busy, onBusy = { busy = it }, onCancel = { cancelling = true })
        },
    ) { pad ->
        val o = order
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GradientCard(colors = if (o.status == OrderStatus.CANCELLED) Gradients.Slate else Gradients.Hero) {
                    Column(Modifier.padding(20.dp)) {
                        Text(o.status.label, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        if (o.status == OrderStatus.CANCELLED && o.cancelReason.isNotBlank())
                            Text("Səbəb: ${o.cancelReason}", color = Color.White.copy(alpha = 0.9f))
                        VSpace(4.dp)
                        Text("${o.storeName} · ${dateTime(o.createdAt)}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        VSpace(10.dp)
                        Text(Money.format(o.totalQ), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text("Ödəniş: nağd, kuryerə", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (o.status != OrderStatus.CANCELLED) item { Timeline(o) }
            item {
                InfoCard {
                    InfoRow(Icons.Filled.Storefront, "Mağaza", o.storeName, o.storeAddress, o.storePhone.takeIf { user?.role != Role.CUSTOMER })
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    InfoRow(Icons.Filled.Home, "Çatdırılma ünvanı", o.address, o.note.takeIf { it.isNotBlank() }?.let { "Qeyd: $it" }, null)
                    if (user?.role != Role.CUSTOMER) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow(Icons.Filled.Person, "Müştəri", o.customerName, o.customerPhone, o.customerPhone)
                    }
                    if (o.courierId != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow(Icons.Filled.DeliveryDining, "Kuryer", o.courierName, o.courierPhone, o.courierPhone)
                    }
                }
            }
            item { Text("Məhsullar", style = MaterialTheme.typography.titleMedium) }
            items(o.items, key = { it.productId }) { it ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${it.qty}×", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(it.priceQ), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(it.totalQ), fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                HorizontalDivider()
                VSpace(8.dp)
                SumRow("Məhsullar", o.subtotalQ)
                SumRow("Çatdırılma", o.deliveryFeeQ)
                SumRow("Cəmi", o.totalQ, bold = true)
                VSpace(80.dp)
            }
        }
    }

    if (cancelling) {
        val o = order
        var reason by remember { mutableStateOf(if (user?.role == Role.CUSTOMER) CANCEL_REASONS[0] else CANCEL_REASONS[1]) }
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text("Sifariş ləğv edilsin?") },
            text = {
                Column {
                    Text("Səbəb seçin:")
                    VSpace(8.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CANCEL_REASONS.forEach { r -> FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) }) }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { cancelling = false; if (o != null) vm.setStatus(o.id, OrderStatus.CANCELLED, reason) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Ləğv et") }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Geri") } },
        )
    }
}

/** Rola görə növbəti addım düyməsi. */
@Composable
private fun ActionBar(vm: AppViewModel, u: UserProfile, o: Order, busy: Boolean, onBusy: (Boolean) -> Unit, onCancel: () -> Unit) {
    val primary: Pair<String, () -> Unit>? = when {
        OrderRules.canTake(u, o) -> "Sifarişi götür" to { onBusy(true); vm.takeOrder(o.id) { onBusy(false) } }
        OrderRules.canTransition(u, o, OrderStatus.ACCEPTED) -> "Qəbul et" to { onBusy(true); vm.setStatus(o.id, OrderStatus.ACCEPTED) { onBusy(false) } }
        OrderRules.canTransition(u, o, OrderStatus.READY) -> "Hazırdır" to { onBusy(true); vm.setStatus(o.id, OrderStatus.READY) { onBusy(false) } }
        OrderRules.canTransition(u, o, OrderStatus.PICKED_UP) -> "Mağazadan götürdüm" to { onBusy(true); vm.setStatus(o.id, OrderStatus.PICKED_UP) { onBusy(false) } }
        OrderRules.canTransition(u, o, OrderStatus.DELIVERED) -> "Çatdırdım (${Money.format(o.totalQ)} aldım)" to { onBusy(true); vm.setStatus(o.id, OrderStatus.DELIVERED) { onBusy(false) } }
        else -> null
    }
    val canCancel = OrderRules.canTransition(u, o, OrderStatus.CANCELLED)
    if (primary == null && !canCancel) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canCancel) {
                OutlinedButton(
                    onClick = onCancel, enabled = !busy, modifier = Modifier.height(56.dp).then(if (primary == null) Modifier.weight(1f) else Modifier),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Ləğv et") }
            }
            if (primary != null) {
                Button(onClick = primary.second, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Filled.Check, null); HSpace(6.dp); Text(primary.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Timeline(o: Order) {
    InfoCard {
        val current = OrderStatus.FLOW.indexOf(o.status)
        OrderStatus.FLOW.forEachIndexed { i, s ->
            val done = i <= current
            val c = if (done) statusColor(s) else MaterialTheme.colorScheme.outline
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.copy(alpha = if (done) 1f else 0.3f)), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                HSpace(12.dp)
                Text(
                    s.label, modifier = Modifier.weight(1f),
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                o.timeline[s.name]?.let { Text(clock(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, sub: String?, phone: String?) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon3D(icon, Gradients.Orange, size = 36.dp)
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!phone.isNullOrBlank()) IconButton(onClick = { dial(context, phone) }) { Icon(Icons.Filled.Call, "Zəng et", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun SumRow(label: String, q: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(Money.format(q), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, color = if (bold) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}
