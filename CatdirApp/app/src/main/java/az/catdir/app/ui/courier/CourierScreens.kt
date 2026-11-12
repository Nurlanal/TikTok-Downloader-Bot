package az.catdir.app.ui.courier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.catdir.app.AppViewModel
import az.catdir.app.model.Money
import az.catdir.app.model.OrderStatus
import az.catdir.app.ui.common.AppSnackbarHost
import az.catdir.app.ui.common.EmptyState
import az.catdir.app.ui.common.GradientCard
import az.catdir.app.ui.common.OrderCard
import az.catdir.app.ui.common.VSpace
import az.catdir.app.ui.theme.Gradients
import java.util.Calendar

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierHomeScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val availableFlow = remember { vm.availableOrders() }
    val mineFlow = remember { vm.courierOrders() }
    val available by availableFlow.collectAsState(initial = emptyList())
    val mine by mineFlow.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val online = user?.online == true
    val active = mine.filter { it.status.isActive }
    val todayDone = mine.filter { it.status == OrderStatus.DELIVERED && (it.timeline[OrderStatus.DELIVERED.name] ?: 0) >= startOfToday() }

    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = { TopAppBar(title = { Text("Kuryer paneli") }) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GradientCard(colors = if (online) Gradients.Teal else Gradients.Slate) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (online) "Xəttdəsiniz" else "Xəttdən kənar", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text(user?.name ?: "", color = Color.White.copy(alpha = 0.85f))
                            }
                            Switch(
                                checked = online, onCheckedChange = { vm.setOnline(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color.White.copy(alpha = 0.4f), checkedThumbColor = Color.White),
                            )
                        }
                        VSpace(12.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("Bu gün çatdırıldı", "${todayDone.size}", Modifier.weight(1f))
                            Stat("Toplanan nağd", Money.format(todayDone.sumOf { it.totalQ }), Modifier.weight(1.2f))
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Mövcud (${if (online) available.size else 0})") }
                    SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Mənim (${active.size})") }
                }
            }
            if (tab == 0) {
                if (!online) item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Icons.Filled.NightsStay, "Xəttdən kənardasınız", "Sifarişləri görmək üçün xəttə çıxın")
                        Button(onClick = { vm.setOnline(true) }, shape = RoundedCornerShape(16.dp)) { Text("Xəttə çıx") }
                    }
                } else {
                    if (available.isEmpty()) item { EmptyState(Icons.Filled.DeliveryDining, "Hazırda sifariş yoxdur", "Yeni sifariş gələndə burada görünəcək") }
                    items(available, key = { it.id }) { o ->
                        OrderCard(
                            o, onClick = { onOpen(o.id) },
                            subtitle = "📍 ${o.storeName} → ${o.address}",
                            trailing = { Button(onClick = { vm.takeOrder(o.id) { ok -> if (ok) tab = 1 } }, shape = RoundedCornerShape(14.dp)) { Text("Götür") } },
                        )
                    }
                }
            } else {
                if (active.isEmpty()) item { EmptyState(Icons.Filled.DeliveryDining, "Aktiv sifarişiniz yoxdur", "\"Mövcud\" bölməsindən sifariş götürün") }
                items(active, key = { it.id }) { o ->
                    OrderCard(o, onClick = { onOpen(o.id) }, subtitle = "${o.status.label}\n📍 ${o.address}")
                }
                val past = mine.filter { it.status.isFinal }.take(20)
                if (past.isNotEmpty()) item { Text("Son çatdırılmalar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(past, key = { "p" + it.id }) { o -> OrderCard(o, onClick = { onOpen(o.id) }) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
