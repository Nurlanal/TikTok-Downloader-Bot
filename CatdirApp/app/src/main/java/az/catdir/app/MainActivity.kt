package az.catdir.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.catdir.app.model.Role
import az.catdir.app.ui.admin.AdminOrdersScreen
import az.catdir.app.ui.admin.AdminStoresScreen
import az.catdir.app.ui.admin.ProductEditScreen
import az.catdir.app.ui.admin.StoreEditScreen
import az.catdir.app.ui.admin.UsersScreen
import az.catdir.app.ui.common.AuthScreen
import az.catdir.app.ui.common.LocalSnackbar
import az.catdir.app.ui.common.OrderDetailScreen
import az.catdir.app.ui.common.ProfileScreen
import az.catdir.app.ui.courier.CourierHomeScreen
import az.catdir.app.ui.customer.CartScreen
import az.catdir.app.ui.customer.MyOrdersScreen
import az.catdir.app.ui.customer.StoreScreen
import az.catdir.app.ui.customer.StoresScreen
import az.catdir.app.ui.theme.CatdirTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val backend = (application as CatdirApp).backend
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.factory(backend))
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                    SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            CatdirTheme(dark) { Root(vm) }
        }
    }
}

@Composable
fun Root(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val user by vm.user.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it, withDismissAction = true) }
    }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        val u = user
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            u == null -> AuthScreen(vm)
            // rol dəyişəndə (məs. admin kuryer təyin edəndə) naviqasiya sıfırlansın
            else -> androidx.compose.runtime.key(u.uid, u.role) {
                when (u.role) {
                    Role.CUSTOMER -> CustomerShell(vm)
                    Role.COURIER -> CourierShell(vm)
                    Role.ADMIN -> AdminShell(vm)
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0)

@Composable
private fun Shell(tabs: List<Tab>, nav: NavHostController, start: String, graph: NavGraphBuilder.() -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            if (route != t.route) nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = {
                            if (t.badge > 0) BadgedBox(badge = { Badge { Text("${t.badge}") } }) { Icon(t.icon, null) }
                            else Icon(t.icon, null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad -> NavHost(nav, start, Modifier.padding(pad), builder = graph) }
}

private fun NavGraphBuilder.orderRoute(vm: AppViewModel, nav: NavHostController) {
    composable("order/{id}", listOf(navArgument("id") { type = NavType.StringType })) { b ->
        OrderDetailScreen(vm, b.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
    }
}

@Composable
private fun CustomerShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val cart by vm.cart.collectAsStateWithLifecycle()
    Shell(
        listOf(
            Tab("stores", "Mağazalar", Icons.Filled.Storefront),
            Tab("cart", "Səbət", Icons.Filled.ShoppingCart, cart.count),
            Tab("orders", "Sifarişlər", Icons.AutoMirrored.Filled.ReceiptLong),
            Tab("profile", "Profil", Icons.Filled.Person),
        ),
        nav, "stores",
    ) {
        composable("stores") { StoresScreen(vm, onOpen = { nav.navigate("store/${it.id}") }) }
        composable("store/{id}", listOf(navArgument("id") { type = NavType.StringType })) { b ->
            StoreScreen(vm, b.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }, onCart = { nav.navigate("cart") })
        }
        composable("cart") {
            CartScreen(
                vm,
                onOrdered = { id ->
                    nav.navigate("orders") { popUpTo(nav.graph.findStartDestination().id) }
                    nav.navigate("order/$id")
                },
                onBrowse = { nav.navigate("stores") { popUpTo(nav.graph.findStartDestination().id) } },
            )
        }
        composable("orders") { MyOrdersScreen(vm, onOpen = { nav.navigate("order/$it") }) }
        composable("profile") { ProfileScreen(vm) }
        orderRoute(vm, nav)
    }
}

@Composable
private fun CourierShell(vm: AppViewModel) {
    val nav = rememberNavController()
    Shell(
        listOf(Tab("home", "Sifarişlər", Icons.Filled.DeliveryDining), Tab("profile", "Profil", Icons.Filled.Person)),
        nav, "home",
    ) {
        composable("home") { CourierHomeScreen(vm, onOpen = { nav.navigate("order/$it") }) }
        composable("profile") { ProfileScreen(vm) }
        orderRoute(vm, nav)
    }
}

@Composable
private fun AdminShell(vm: AppViewModel) {
    val nav = rememberNavController()
    Shell(
        listOf(
            Tab("orders", "Sifarişlər", Icons.AutoMirrored.Filled.ReceiptLong),
            Tab("stores", "Mağazalar", Icons.Filled.Storefront),
            Tab("users", "İstifadəçilər", Icons.Filled.Group),
            Tab("profile", "Profil", Icons.Filled.Person),
        ),
        nav, "orders",
    ) {
        composable("orders") { AdminOrdersScreen(vm, onOpen = { nav.navigate("order/$it") }) }
        composable("stores") { AdminStoresScreen(vm, onEdit = { id -> nav.navigate(if (id == null) "storeEdit" else "storeEdit?id=$id") }) }
        composable("storeEdit?id={id}", listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { b ->
            StoreEditScreen(
                vm, b.arguments?.getString("id"), onBack = { nav.popBackStack() },
                onEditProduct = { s, p -> nav.navigate(if (p == null) "product/$s" else "product/$s?pid=$p") },
            )
        }
        composable(
            "product/{storeId}?pid={pid}",
            listOf(navArgument("storeId") { type = NavType.StringType }, navArgument("pid") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { b ->
            ProductEditScreen(vm, b.arguments?.getString("storeId") ?: "", b.arguments?.getString("pid"), onBack = { nav.popBackStack() })
        }
        composable("users") { UsersScreen(vm) }
        composable("profile") { ProfileScreen(vm) }
        orderRoute(vm, nav)
    }
}

