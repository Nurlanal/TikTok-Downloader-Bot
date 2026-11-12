package az.catdir.app.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.catdir.app.AppViewModel
import az.catdir.app.model.Money
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.STORE_CATEGORIES
import az.catdir.app.model.Store
import az.catdir.app.ui.common.AppSnackbarHost
import az.catdir.app.ui.common.EmptyState
import az.catdir.app.ui.common.GradientCard
import az.catdir.app.ui.common.HSpace
import az.catdir.app.ui.common.Icon3D
import az.catdir.app.ui.common.OrderCard
import az.catdir.app.ui.common.VSpace
import az.catdir.app.ui.common.categoryIcon
import az.catdir.app.ui.customer.StoreCard
import az.catdir.app.ui.theme.Gradients
import java.util.Calendar

private enum class OrderTab(val label: String, val statuses: Set<OrderStatus>) {
    NEW("Yeni", setOf(OrderStatus.NEW)),
    PREP("Hazırlanır", setOf(OrderStatus.ACCEPTED)),
    READY("Hazırdır", setOf(OrderStatus.READY)),
    ROAD("Yolda", setOf(OrderStatus.PICKED_UP)),
    DONE("Bitmiş", setOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED)),
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdminOrdersScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val flow = remember { vm.allOrders() }
    val orders by flow.collectAsState(initial = emptyList())
    val user by vm.user.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(OrderTab.NEW) }
    val today = remember {
        Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    val todays = orders.filter { it.createdAt >= today }
    val delivered = todays.filter { it.status == OrderStatus.DELIVERED }

    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = { TopAppBar(title = { Text("Sifarişlər") }) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GradientCard(colors = Gradients.Indigo + Color(0xFF312E81)) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AStat("Bu gün sifariş", "${todays.size}", Modifier.weight(1f))
                        AStat("Çatdırıldı", "${delivered.size}", Modifier.weight(1f))
                        AStat("Dövriyyə", Money.format(delivered.sumOf { it.totalQ }), Modifier.weight(1.3f))
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderTab.entries.forEach { t ->
                        val n = orders.count { it.status in t.statuses }
                        FilterChip(
                            tab == t, { tab = t }, { Text("${t.label} ($n)") }, shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
            }
            val list = orders.filter { it.status in tab.statuses }
            if (list.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Bu bölmədə sifariş yoxdur", "Yeni sifarişlər real vaxtda burada görünür") }
            items(list, key = { it.id }) { o ->
                val u = user
                OrderCard(
                    o, onClick = { onOpen(o.id) },
                    subtitle = "${o.customerName} · ${o.address}\n" + o.items.joinToString(", ") { "${it.qty}× ${it.name}" } +
                        (if (o.courierId != null) "\nKuryer: ${o.courierName}" else if (o.status == OrderStatus.READY) "\nKuryer gözlənilir" else ""),
                    trailing = {
                        if (u != null) when {
                            OrderRules.canTransition(u, o, OrderStatus.ACCEPTED) ->
                                Button(onClick = { vm.setStatus(o.id, OrderStatus.ACCEPTED) }, shape = RoundedCornerShape(14.dp)) { Text("Qəbul et") }
                            OrderRules.canTransition(u, o, OrderStatus.READY) ->
                                Button(onClick = { vm.setStatus(o.id, OrderStatus.READY) }, shape = RoundedCornerShape(14.dp)) { Text("Hazırdır") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AStat(label: String, value: String, modifier: Modifier) {
    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Mağazalar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdminStoresScreen(vm: AppViewModel, onEdit: (String?) -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = { TopAppBar(title = { Text("Mağazalar") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit(null) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Mağaza əlavə et") },
                shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stores.isEmpty()) item { EmptyState(Icons.Filled.Storefront, "Mağaza yoxdur", "İlk mağazanı əlavə edin, sonra ona məhsullar yazın") }
            items(stores, key = { it.id }) { s -> StoreCard(s, onClick = { onEdit(s.id) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StoreEditScreen(vm: AppViewModel, storeId: String?, onBack: () -> Unit, onEditProduct: (storeId: String, productId: String?) -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    val existing = stores.firstOrNull { it.id == storeId }
    var loaded by rememberSaveable { mutableStateOf(storeId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(STORE_CATEGORIES.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("1.50") }
    var minOrder by rememberSaveable { mutableStateOf("0") }
    var eta by rememberSaveable { mutableStateOf("30") }
    var open by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            name = existing.name; category = existing.category; description = existing.description; address = existing.address
            phone = existing.phone; fee = Money.input(existing.deliveryFeeQ); minOrder = Money.input(existing.minOrderQ)
            eta = existing.etaMinutes.toString(); open = existing.isOpen; loaded = true
        }
    }
    val productsFlow = remember(storeId) { storeId?.let { vm.products(it) } }
    val products by (productsFlow ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(if (storeId == null) "Yeni mağaza" else "Mağaza") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { if (storeId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Mağazanı sil", tint = MaterialTheme.colorScheme.error) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextIn(name, { name = it.take(50) }, "Mağazanın adı", KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Text("Kateqoriya", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STORE_CATEGORIES.forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }, leadingIcon = { Icon(categoryIcon(c), null) }) }
            }
            TextIn(description, { description = it.take(120) }, "Qısa təsvir")
            TextIn(address, { address = it.take(120) }, "Mağazanın ünvanı (kuryer üçün)")
            TextIn(phone, { phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(20) }, "Telefon", KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextIn(fee, { fee = Money.sanitize(it) }, "Çatdırılma ₼", KeyboardOptions(keyboardType = KeyboardType.Decimal), Modifier.weight(1f))
                TextIn(minOrder, { minOrder = Money.sanitize(it) }, "Min. sifariş ₼", KeyboardOptions(keyboardType = KeyboardType.Decimal), Modifier.weight(1f))
                TextIn(eta, { eta = it.filter(Char::isDigit).take(3) }, "Dəqiqə", KeyboardOptions(keyboardType = KeyboardType.Number), Modifier.weight(0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "Açıqdır — sifariş qəbul edir" else "Bağlıdır", modifier = Modifier.weight(1f))
                Switch(open, { open = it })
            }
            Button(
                onClick = {
                    busy = true
                    vm.saveStore(
                        Store(
                            id = storeId ?: "", name = name, category = category, description = description, address = address, phone = phone,
                            deliveryFeeQ = Money.parse(fee) ?: 0, minOrderQ = Money.parse(minOrder) ?: 0, etaMinutes = eta.toIntOrNull() ?: 0, isOpen = open,
                        )
                    ) { ok -> busy = false; if (ok && storeId == null) onBack() }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Mağazanı yadda saxla") }

            if (storeId != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Məhsullar (${products.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onEditProduct(storeId, null) }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Add, null); HSpace(4.dp); Text("Məhsul") }
                }
                if (products.isEmpty()) Text("Hələ məhsul yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant)
                products.forEach { p ->
                    Card(onClick = { onEditProduct(storeId, p.id) }, shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.category.ifBlank { "—" }}${if (!p.available) " · yoxdur" else ""}", style = MaterialTheme.typography.bodySmall,
                                    color = if (p.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                            }
                            Text(Money.format(p.priceQ), color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.Edit, null, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            VSpace(24.dp)
        }
    }
    if (confirmDelete && storeId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Mağaza silinsin?") },
            text = { Text("\"$name\" və bütün məhsulları silinəcək. Köhnə sifarişlər qalır.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; vm.deleteStore(storeId) { if (it) onBack() } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProductEditScreen(vm: AppViewModel, storeId: String, productId: String?, onBack: () -> Unit) {
    val flow = remember(storeId) { vm.products(storeId) }
    val products by flow.collectAsState(initial = emptyList())
    val existing = products.firstOrNull { it.id == productId }
    var loaded by rememberSaveable { mutableStateOf(productId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var available by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            name = existing.name; category = existing.category; description = existing.description
            price = Money.input(existing.priceQ); available = existing.available; loaded = true
        }
    }
    val categories = products.map { it.category }.filter { it.isNotBlank() }.distinct()

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Yeni məhsul" else "Məhsul") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (existing != null) IconButton(onClick = { vm.deleteProduct(existing); onBack() }) {
                        Icon(Icons.Filled.Delete, "Məhsulu sil", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextIn(name, { name = it.take(60) }, "Məhsulun adı", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
            TextIn(price, { price = Money.sanitize(it) }, "Qiymət ₼", KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
            TextIn(category, { category = it.take(30) }, "Bölmə (məs. İçkilər)")
            if (categories.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) }
            }
            TextIn(description, { description = it.take(150) }, "Təsvir (istəyə görə)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (available) "Satışdadır" else "Hazırda yoxdur", modifier = Modifier.weight(1f))
                Switch(available, { available = it })
            }
            Button(
                onClick = {
                    busy = true
                    vm.saveProduct(Product(productId ?: "", storeId, name, description, category, Money.parse(price) ?: 0, available)) { ok ->
                        busy = false; if (ok) onBack()
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Məhsulu yadda saxla") }
        }
    }
}

@Composable
private fun TextIn(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardOptions = KeyboardOptions.Default, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = true, keyboardOptions = keyboard, shape = RoundedCornerShape(16.dp))
}

// ---------------------------------------------------------------- İstifadəçilər

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun UsersScreen(vm: AppViewModel) {
    val flow = remember { vm.users() }
    val users by flow.collectAsState(initial = emptyList())
    val me by vm.user.collectAsStateWithLifecycle()
    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = { TopAppBar(title = { Text("İstifadəçilər") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Kuryer təyin etmək üçün istifadəçi əvvəlcə proqramda qeydiyyatdan keçməlidir, sonra burada rolunu dəyişin.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(users, key = { it.uid }) { u ->
                var menu by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(
                            when (u.role) { Role.CUSTOMER -> Icons.Filled.Person; Role.COURIER -> Icons.Filled.DeliveryDining; Role.ADMIN -> Icons.Filled.AdminPanelSettings },
                            when (u.role) { Role.CUSTOMER -> Gradients.Orange; Role.COURIER -> Gradients.Teal; Role.ADMIN -> Gradients.Indigo }, size = 40.dp,
                        )
                        HSpace(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(u.name + if (u.uid == me?.uid) " (siz)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${u.phone} · ${u.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (u.role == Role.COURIER) Text(if (u.online) "● Xəttdə" else "○ Xəttdən kənar", style = MaterialTheme.typography.labelSmall,
                                color = if (u.online) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            TextButton(onClick = { menu = true }) { Text(u.role.label) }
                            DropdownMenu(menu, { menu = false }) {
                                Role.entries.forEach { r ->
                                    DropdownMenuItem(text = { Text(r.label) }, onClick = { menu = false; if (r != u.role) vm.setRole(u.uid, r) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
