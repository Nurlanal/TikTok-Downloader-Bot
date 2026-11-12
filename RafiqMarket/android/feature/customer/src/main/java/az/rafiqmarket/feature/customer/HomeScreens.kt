package az.rafiqmarket.feature.customer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.Urls
import az.rafiqmarket.core.network.dto.BannerDto
import az.rafiqmarket.core.network.dto.CategoryDto
import az.rafiqmarket.core.network.dto.ProductDto
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.money
import az.rafiqmarket.core.util.parseColor
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onCategory: (Long, String) -> Unit,
    onNotifications: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val home by vm.home.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val qty by vm.cartQuantities.collectAsStateWithLifecycle()

    when {
        home == null && error != null -> ErrorBox(error!!, { vm.load() })
        home == null -> LoadingBox()
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.load() }) {
            val h = home!!
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fullWidth {
                    Column(
                        Modifier.background(
                            Brush.verticalGradient(listOf(RafiqColors.Green800, RafiqColors.Green600))
                        ).statusBarsPadding().padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RafiqLogo(44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Rafiq Market", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.DeliveryDining, null, tint = RafiqColors.Lime, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${h.settings.zoneName} daxilində çatdırılma", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = onNotifications) { Icon(Icons.Outlined.Notifications, "Bildirişlər", tint = Color.White) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            onClick = onSearch, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text("Məhsul axtar…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        DeliveryInfoRow(h.settings)
                    }
                }
                if (!h.settings.ordersEnabled || !h.settings.isOpenNow) fullWidth {
                    InfoBanner(
                        if (!h.settings.ordersEnabled) "Market hazırda sifariş qəbul etmir."
                        else "Market bağlıdır. Sifariş saatları: ${h.settings.openTime}–${h.settings.closeTime}",
                        Icons.Outlined.Schedule, RafiqColors.Orange, Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (h.banners.isNotEmpty()) fullWidth { BannerPager(h.banners) { b -> b.categoryId?.let { id -> onCategory(id, h.categories.firstOrNull { it.id == id }?.name ?: b.title) } } }
                if (h.categories.isNotEmpty()) {
                    fullWidth { SectionHeader("Kateqoriyalar") }
                    fullWidth { CategoryRow(h.categories, onCategory) }
                }
                productSection("🔥 Endirimli məhsullar", h.discounted, qty, vm::add, vm::minus)
                productSection("✨ Yeni məhsullar", h.newProducts, qty, vm::add, vm::minus)
                productSection("⭐ Ən çox satılanlar", h.bestSellers, qty, vm::add, vm::minus)
                if (h.categories.isEmpty() && h.newProducts.isEmpty() && h.discounted.isEmpty() && h.bestSellers.isEmpty()) fullWidth {
                    EmptyState("🛒", "Market hazırlanır", "Məhsullar tezliklə əlavə olunacaq", Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.productSection(
    title: String, list: List<ProductDto>, qty: Map<Long, Int>, onAdd: (ProductDto) -> Unit, onMinus: (ProductDto) -> Unit,
) {
    if (list.isEmpty()) return
    fullWidth { SectionHeader(title) }
    fullWidth {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { p ->
                ProductCard(p, qty[p.id] ?: 0, { onAdd(p) }, { onMinus(p) }, Modifier.width(168.dp))
            }
        }
    }
}

@Composable
private fun DeliveryInfoRow(s: az.rafiqmarket.core.network.dto.PublicSettingsDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("🚚 Çatdırılma ${money(s.deliveryFee)}")
        Pill("🧾 Min. ${money(s.minOrderAmount)}")
        s.freeDeliveryThreshold?.let { Pill("🎁 ${money(it)}+ pulsuz") }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BannerPager(banners: List<BannerDto>, onClick: (BannerDto) -> Unit) {
    val pager = rememberPagerState { banners.size }
    LaunchedEffect(banners.size) {
        while (banners.size > 1) { delay(4500); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column(Modifier.padding(top = 4.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { i ->
            val b = banners[i]
            val c = Color(parseColor(b.color))
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f))))
                    .clickable { onClick(b) },
            ) {
                Urls.resolve(b.imageUrl)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
                Column(Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxWidth(0.7f)) {
                    Text(b.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                    if (b.subtitle.isNotBlank()) Text(b.subtitle, color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (banners.size > 1) Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(banners.size) { i ->
                Box(Modifier.size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun CategoryRow(categories: List<CategoryDto>, onCategory: (Long, String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(categories, key = { it.id }) { c ->
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable { onCategory(c.id, c.name) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon3D(c.icon, Color(parseColor(c.color)), 60.dp)
                Spacer(Modifier.height(6.dp))
                Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

// ---------------- Axtarış və kateqoriya məhsulları ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(onBack: (() -> Unit)?, vm: ProductsViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val qty by vm.cartQuantities.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (vm.categoryId != null) vm.title else "Axtarış") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
        )
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it }, singleLine = true,
            placeholder = { Text(if (vm.categoryId != null) "Bu kateqoriyada axtar" else "Məhsul adı və ya barkod") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Təmizlə") } },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        when {
            error != null -> ErrorBox(error!!, { vm.retry() })
            products == null -> LoadingBox()
            products!!.isEmpty() && vm.categoryId == null && q.isBlank() -> EmptyState("🔎", "Nə axtarırsınız?", "Məhsul adını yazın")
            products!!.isEmpty() -> EmptyState("🫙", "Heç nə tapılmadı")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(products!!, key = { it.id }) { p -> ProductCard(p, qty[p.id] ?: 0, { vm.add(p) }, { vm.minus(p) }) }
            }
        }
    }
}

// ---------------- Səbət ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(onCheckout: () -> Unit, onShop: () -> Unit, vm: CartViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Səbət") }, actions = {
                if (s.items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Səbəti boşalt") }
            })
        },
        bottomBar = {
            if (s.items.isNotEmpty()) Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    val q = s.quote
                    if (q != null) {
                        KeyValue("Məhsullar", money(q.subtotal))
                        KeyValue("Çatdırılma haqqı", if (q.deliveryFee.toBigDecimal().signum() == 0) "Pulsuz" else money(q.deliveryFee))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KeyValue("Yekun", money(q.total), bold = true)
                        q.freeDeliveryThreshold?.let { thr ->
                            val left = thr.toBigDecimal() - q.subtotal.toBigDecimal()
                            if (left.signum() > 0) Text("Pulsuz çatdırılma üçün daha ${money(left)} alış-veriş edin",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!q.meetsMinimum) Text("Minimum sifariş məbləği ${money(q.minOrderAmount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else if (s.quoteError != null) {
                        Text(s.quoteError!!, color = MaterialTheme.colorScheme.error)
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    val ok = q != null && q.meetsMinimum && q.lines.all { it.ok } && !s.loading
                    Button(onClick = onCheckout, enabled = ok, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Sifarişi rəsmiləşdir" + (q?.let { " • ${money(it.total)}" } ?: ""))
                    }
                }
            }
        },
    ) { pad ->
        if (s.items.isEmpty()) {
            EmptyState("🛒", "Səbətiniz boşdur", "Məhsulları əlavə edib sifariş verin", Modifier.padding(pad).fillMaxSize()) {
                Button(onClick = onShop) { Text("Alış-verişə başla") }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.items, key = { it.productId }) { it ->
                    val line = s.quote?.lines?.firstOrNull { l -> l.productId == it.productId }
                    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductImage(it.imageUrl, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${money(it.price)} / ${it.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(it.price.toBigDecimal() * it.quantity.toBigDecimal()), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if (line != null && !line.ok) Text(
                                    if (line.availableQuantity <= 0) "Stokda qalmayıb" else "Stokda yalnız ${line.availableQuantity} ${it.unit}",
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                            QuantityStepper(it.quantity, { vm.minus(it.productId) }, { vm.plus(it.productId) }, plusEnabled = it.quantity < it.maxStock, compact = true)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Səbəti boşalt", "Bütün məhsullar səbətdən silinsin?", "Sil", { vm.clear() }) { confirmClear = false }
}
