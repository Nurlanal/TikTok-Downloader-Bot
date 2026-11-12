package az.catdir.app.ui.customer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.catdir.app.AppViewModel
import az.catdir.app.model.Money
import az.catdir.app.model.Product
import az.catdir.app.model.STORE_CATEGORIES
import az.catdir.app.model.Store
import az.catdir.app.ui.common.AppSnackbarHost
import az.catdir.app.ui.common.EmptyState
import az.catdir.app.ui.common.GradientCard
import az.catdir.app.ui.common.HSpace
import az.catdir.app.ui.common.Icon3D
import az.catdir.app.ui.common.OrderCard
import az.catdir.app.ui.common.QtyStepper
import az.catdir.app.ui.common.VSpace
import az.catdir.app.ui.common.categoryIcon
import az.catdir.app.ui.theme.Gradients

// ---------------------------------------------------------------- Mağazalar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresScreen(vm: AppViewModel, onOpen: (Store) -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(stores) { STORE_CATEGORIES.filter { c -> stores.any { it.category == c } } }
    val visible = stores.filter {
        (category == null || it.category == category) &&
            (query.isBlank() || it.name.contains(query.trim(), true) || it.description.contains(query.trim(), true))
    }

    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = {
        TopAppBar(title = {
            Column {
                Text("Salam, ${user?.name?.substringBefore(' ') ?: ""}!")
                Text("Bu gün nə sifariş edirik?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Mağaza axtar…") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Təmizlə") } },
                    singleLine = true, shape = RoundedCornerShape(18.dp),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    FilterChip(category == null, { category = null }, { Text("Hamısı") }, shape = RoundedCornerShape(14.dp), colors = colors)
                    categories.forEach { c ->
                        FilterChip(
                            category == c, { category = if (category == c) null else c }, { Text(c) },
                            leadingIcon = { Icon(categoryIcon(c), null) }, shape = RoundedCornerShape(14.dp), colors = colors,
                        )
                    }
                }
            }
            if (visible.isEmpty()) item {
                EmptyState(Icons.Filled.Storefront, if (stores.isEmpty()) "Hələ mağaza yoxdur" else "Heç nə tapılmadı",
                    if (stores.isEmpty()) "Admin mağaza əlavə edəndə burada görünəcək" else "Başqa söz və ya kateqoriya seçin")
            }
            items(visible, key = { it.id }) { s -> StoreCard(s, onClick = { onOpen(s) }) }
        }
    }
}

@Composable
fun StoreCard(s: Store, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D(categoryIcon(s.category), if (s.isOpen) Gradients.forCategory(s.category) else Gradients.Slate, size = 56.dp)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.description.ifBlank { s.category }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                VSpace(6.dp)
                if (s.isOpen) {
                    Text(
                        "${s.etaMinutes} dəq · Çatdırılma ${Money.format(s.deliveryFeeQ)}" +
                            if (s.minOrderQ > 0) " · Min. ${Money.format(s.minOrderQ)}" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text("Hazırda bağlıdır", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Mağaza səhifəsi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(vm: AppViewModel, storeId: String, onBack: () -> Unit, onCart: () -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    val store = stores.firstOrNull { it.id == storeId }
    val productsFlow = remember(storeId) { vm.products(storeId) }
    val products by productsFlow.collectAsState(initial = emptyList())
    val cart by vm.cart.collectAsStateWithLifecycle()
    var conflict by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text(store?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
        bottomBar = { if (cart.store?.id == storeId && !cart.isEmpty) CartBar(cart.count, cart.subtotalQ, onCart) },
    ) { pad ->
        if (store == null) return@Scaffold
        val groups = products.groupBy { it.category.ifBlank { "Digər" } }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GradientCard(colors = if (store.isOpen) Gradients.forCategory(store.category) else Gradients.Slate) {
                    Column(Modifier.padding(20.dp)) {
                        Text(store.category, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Text(store.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        if (store.description.isNotBlank()) Text(store.description, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                        VSpace(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(Icons.Filled.Schedule, "${store.etaMinutes} dəq")
                            Pill(Icons.Filled.ShoppingBag, "Çatdırılma ${Money.format(store.deliveryFeeQ)}")
                        }
                        if (!store.isOpen) { VSpace(8.dp); Text("Mağaza hazırda bağlıdır, sifariş qəbul olunmur", color = Color.White, fontWeight = FontWeight.Bold) }
                        else if (store.minOrderQ > 0) { VSpace(8.dp); Text("Minimum sifariş: ${Money.format(store.minOrderQ)}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (products.isEmpty()) item { EmptyState(Icons.Filled.ShoppingBag, "Məhsul yoxdur", "Bu mağazada hələ məhsul əlavə olunmayıb") }
            groups.forEach { (cat, list) ->
                item(key = "h_$cat") { Text(cat, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { it.id }) { p ->
                    ProductRow(
                        p, qty = if (cart.store?.id == storeId) cart.qtyOf(p.id) else 0, enabled = store.isOpen && p.available,
                        onAdd = { if (!vm.addToCart(store, p)) conflict = p },
                        onMinus = { vm.changeQty(p.id, -1) },
                    )
                }
            }
        }
    }

    conflict?.let { p ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("Səbət boşaldılsın?") },
            text = { Text("Səbətinizdə \"${cart.store?.name}\" mağazasının malları var. Bir sifarişdə yalnız bir mağazadan mal ola bilər.") },
            confirmButton = { Button(onClick = { store?.let { vm.replaceCartWith(it, p) }; conflict = null }) { Text("Boşalt və əlavə et") } },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("Ləğv et") } },
        )
    }
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.height(16.dp)); HSpace(4.dp)
            Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProductRow(p: Product, qty: Int, enabled: Boolean, onAdd: () -> Unit, onMinus: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                VSpace(4.dp)
                Text(Money.format(p.priceQ), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (!p.available) Text("Hazırda yoxdur", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (qty > 0) QtyStepper(qty, onMinus, onAdd, plusEnabled = enabled)
            else FilledIconButton(onClick = onAdd, enabled = enabled, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Add, "Səbətə əlavə et") }
        }
    }
}

@Composable
fun CartBar(count: Int, subtotalQ: Long, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Button(
            onClick = onClick,
            modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Filled.ShoppingCart, null); HSpace(8.dp)
            Text("Səbətə bax · $count məhsul", modifier = Modifier.weight(1f))
            Text(Money.format(subtotalQ), fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------------------------------------------------------------- Səbət və sifariş

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(vm: AppViewModel, onOrdered: (String) -> Unit, onBrowse: () -> Unit) {
    val cart by vm.cart.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf(user?.address ?: "") }
    var phone by rememberSaveable { mutableStateOf(user?.phone ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val store = cart.store

    Scaffold(
        snackbarHost = { AppSnackbarHost() },
        topBar = { TopAppBar(title = { Text("Səbət") }) },
        bottomBar = {
            if (!cart.isEmpty && store != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
                Button(
                    onClick = { busy = true; vm.placeOrder(address, phone, note) { id -> busy = false; if (id != null) onOrdered(id) } },
                    enabled = !busy && store.isOpen,
                    modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Sifariş ver", modifier = Modifier.weight(1f))
                    Text(Money.format(cart.totalQ), fontWeight = FontWeight.ExtraBold)
                }
            }
        },
    ) { pad ->
        if (cart.isEmpty || store == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Filled.ShoppingCart, "Səbət boşdur", "Mağazaya keçin və məhsul əlavə edin")
                Button(onClick = onBrowse, shape = RoundedCornerShape(16.dp)) { Text("Mağazalara bax") }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(categoryIcon(store.category), Gradients.forCategory(store.category), size = 40.dp); HSpace(10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(store.name, style = MaterialTheme.typography.titleMedium)
                        Text("${store.etaMinutes} dəqiqəyə çatdırılma", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.clearCart() }) { Text("Təmizlə") }
                }
            }
            items(cart.lines, key = { it.productId }) { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(l.totalQ), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    QtyStepper(l.qty, { vm.changeQty(l.productId, -1) }, { vm.changeQty(l.productId, 1) })
                }
            }
            item {
                HorizontalDivider()
                VSpace(6.dp)
                Sum("Məhsullar", cart.subtotalQ); Sum("Çatdırılma", store.deliveryFeeQ); Sum("Cəmi", cart.totalQ, true)
                if (cart.subtotalQ < store.minOrderQ) {
                    VSpace(6.dp)
                    Text("Minimum sifariş ${Money.format(store.minOrderQ)}. Daha ${Money.format(store.minOrderQ - cart.subtotalQ)} əlavə edin.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("Çatdırılma", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            item {
                OutlinedTextField(address, { address = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Ünvan (küçə, ev, mənzil, mərtəbə)") },
                    leadingIcon = { Icon(Icons.Filled.Home, null) }, shape = RoundedCornerShape(16.dp), minLines = 2)
            }
            item {
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(20) }, Modifier.fillMaxWidth(),
                    label = { Text("Telefon") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(16.dp))
            }
            item {
                OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Kuryerə qeyd (istəyə görə)") },
                    leadingIcon = { Icon(Icons.Filled.EditNote, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            }
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Payments, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); HSpace(10.dp)
                        Text("Ödəniş: nağd, çatdırılanda kuryerə", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sum(label: String, q: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(Money.format(q), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

// ---------------------------------------------------------------- Sifarişlərim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyOrdersScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val flow = remember { vm.myOrders() }
    val orders by flow.collectAsState(initial = emptyList())
    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = { TopAppBar(title = { Text("Sifarişlərim") }) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (orders.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Hələ sifariş yoxdur", "İlk sifarişinizi verin, burada izləyəcəksiniz") }
            val active = orders.filter { it.status.isActive }
            val past = orders.filter { it.status.isFinal }
            if (active.isNotEmpty()) item { Text("Aktiv", style = MaterialTheme.typography.titleMedium) }
            items(active, key = { it.id }) { o -> OrderCard(o, onClick = { onOpen(o.id) }) }
            if (past.isNotEmpty()) item { Text("Keçmiş", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(past, key = { it.id }) { o -> OrderCard(o, onClick = { onOpen(o.id) }) }
        }
    }
}

