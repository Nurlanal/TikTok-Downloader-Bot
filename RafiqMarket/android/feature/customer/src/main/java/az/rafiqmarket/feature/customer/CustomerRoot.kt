package az.rafiqmarket.feature.customer

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.rafiqmarket.core.data.CartRepository
import az.rafiqmarket.core.ui.shared.NotificationsScreen
import az.rafiqmarket.core.ui.shared.ProfileScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Ana səhifə", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("search", "Axtarış", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("cart", "Səbət", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab("orders", "Sifarişlər", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Tab("profile", "Profil", Icons.Outlined.Person, Icons.Filled.Person),
)

@HiltViewModel
class CartBadgeViewModel @Inject constructor(cart: CartRepository) : ViewModel() {
    val count = cart.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}

/** Müştəri interfeysi – öz aşağı naviqasiya menyusu ilə */
@Composable
fun CustomerRoot(openOrderId: Long? = null) {
    val nav = rememberNavController()
    val badge: CartBadgeViewModel = hiltViewModel()
    val count by badge.count.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    val selected = route == t.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateTab(t.route) },
                        icon = {
                            BadgedBox(badge = { if (t.route == "cart" && count > 0) Badge { Text("$count") } }) {
                                Icon(if (selected) t.selectedIcon else t.icon, t.label)
                            }
                        },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onSearch = { nav.navigateTab("search") },
                    onCategory = { id, name -> nav.navigate("category/$id?title=${android.net.Uri.encode(name)}") },
                    onNotifications = { nav.navigate("notifications") },
                )
            }
            composable("search") { ProductsScreen(onBack = null) }
            composable(
                "category/{categoryId}?title={title}",
                arguments = listOf(navArgument("categoryId") { type = NavType.LongType }, navArgument("title") { type = NavType.StringType; defaultValue = "" }),
            ) { ProductsScreen(onBack = { nav.popBackStack() }) }
            composable("cart") { CartScreen(onCheckout = { nav.navigate("checkout") }, onShop = { nav.navigateTab("home") }) }
            composable("checkout") {
                CheckoutScreen(onBack = { nav.popBackStack() }, onPlaced = { id ->
                    nav.navigate("order/$id") { popUpTo("cart") { inclusive = false } }
                })
            }
            composable("orders") { OrdersScreen(onOpen = { nav.navigate("order/$it") }) }
            composable("order/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.LongType })) {
                OrderDetailScreen(onBack = { nav.popBackStack() })
            }
            composable("profile") { ProfileScreen(onNotifications = { nav.navigate("notifications") }) }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }, onOpenOrder = { nav.navigate("order/$it") }) }
        }
    }
    androidx.compose.runtime.LaunchedEffect(openOrderId) { if (openOrderId != null) nav.navigate("order/$openOrderId") }
}

internal fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
