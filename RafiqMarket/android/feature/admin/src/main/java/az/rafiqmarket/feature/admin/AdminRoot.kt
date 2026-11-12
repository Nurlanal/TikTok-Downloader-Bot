package az.rafiqmarket.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.rafiqmarket.core.ui.components.IconTile3D
import az.rafiqmarket.core.ui.shared.NotificationsScreen
import az.rafiqmarket.core.ui.shared.ProfileScreen

/** Admin idarəetmə paneli – öz aşağı naviqasiya menyusu ilə */
@Composable
fun AdminRoot(openOrderId: Long? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Triple("dashboard", "Panel", Icons.Outlined.Insights),
        Triple("orders", "Sifarişlər", Icons.Outlined.ReceiptLong),
        Triple("products", "Məhsullar", Icons.Outlined.Inventory2),
        Triple("couriers", "Kuryerlər", Icons.Outlined.DeliveryDining),
        Triple("more", "Daha çox", Icons.Outlined.GridView),
    )
    Scaffold(bottomBar = {
        if (tabs.any { it.first == route }) NavigationBar {
            tabs.forEach { (r, label, icon) ->
                NavigationBarItem(
                    selected = route == r,
                    onClick = { nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") { DashboardScreen(onOrders = { nav.navigate("orders") }, onNotifications = { nav.navigate("notifications") }) }
            composable("orders") { AdminOrdersScreen(onOpen = { nav.navigate("order/$it") }) }
            composable("order/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.LongType })) {
                AdminOrderScreen(onBack = { nav.popBackStack() })
            }
            composable("products") { AdminProductsScreen(onEdit = { nav.navigate("product/${it ?: 0}") }, onCategories = { nav.navigate("categories") }) }
            composable("product/{productId}", arguments = listOf(navArgument("productId") { type = NavType.LongType })) {
                ProductEditScreen(onDone = { nav.popBackStack() })
            }
            composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable("banners") { BannersScreen(onBack = { nav.popBackStack() }) }
            composable("couriers") { CouriersScreen(onLiveMap = { nav.navigate("live") }) }
            composable("live") { LiveCouriersScreen(onBack = { nav.popBackStack() }) }
            composable("customers") { CustomersScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("user/$it") }) }
            composable("user/{userId}", arguments = listOf(navArgument("userId") { type = NavType.LongType })) {
                UserDetailScreen(onBack = { nav.popBackStack() }, onOrder = { nav.navigate("order/$it") })
            }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onZone = { nav.navigate("zone") }) }
            composable("zone") { ZoneEditorScreen(onBack = { nav.popBackStack() }) }
            composable("more") { MoreScreen(onNavigate = { nav.navigate(it) }) }
            composable("profile") { ProfileScreen(onNotifications = { nav.navigate("notifications") }) }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }, onOpenOrder = { nav.navigate("order/$it") }) }
        }
    }
    LaunchedEffect(openOrderId) { if (openOrderId != null) nav.navigate("order/$openOrderId") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScreen(onNavigate: (String) -> Unit) {
    data class Item(val route: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color)
    val items = listOf(
        Item("customers", "Müştərilər", "Siyahı, sifariş tarixçəsi, hesab statusu", Icons.Outlined.People, Color(0xFF1E88E5)),
        Item("categories", "Kateqoriyalar", "Kateqoriya yarat və redaktə et", Icons.Outlined.Category, Color(0xFF8E24AA)),
        Item("banners", "Reklam bannerləri", "Ana səhifə bannerləri", Icons.Outlined.ViewCarousel, Color(0xFFFB8C00)),
        Item("settings", "Çatdırılma parametrləri", "Haqq, minimum məbləğ, saatlar, sifariş qəbulu", Icons.Outlined.Tune, Color(0xFF43A047)),
        Item("zone", "Çatdırılma zonası", "Xıdırlı kəndi sərhədini xəritədə çək", Icons.Outlined.Map, Color(0xFF00897B)),
        Item("notifications", "Bildirişlər", "Yeni sifarişlər, ödənişlər, stok", Icons.Outlined.Notifications, Color(0xFFE53935)),
        Item("profile", "Profil", "Hesab, görünüş, çıxış", Icons.Outlined.Person, Color(0xFF546E7A)),
    )
    Scaffold(topBar = { TopAppBar(title = { Text("İdarəetmə") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { it ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { onNavigate(it.route) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile3D(it.icon, it.color, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.title, style = MaterialTheme.typography.titleMedium)
                            Text(it.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
}
