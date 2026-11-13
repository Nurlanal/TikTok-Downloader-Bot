package az.sontarix.market.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.sontarix.market.DeepLink
import az.sontarix.market.appContainer
import az.sontarix.market.ui.screens.CategoriesScreen
import az.sontarix.market.ui.screens.DashboardScreen
import az.sontarix.market.ui.screens.ImportPreviewScreen
import az.sontarix.market.ui.screens.NotificationsScreen
import az.sontarix.market.ui.screens.ProductDetailScreen
import az.sontarix.market.ui.screens.ProductEditScreen
import az.sontarix.market.ui.screens.ProductsScreen
import az.sontarix.market.ui.screens.ScannerScreen
import az.sontarix.market.ui.screens.SettingsScreen
import az.sontarix.market.ui.screens.TransferScreen
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val DASHBOARD = "dashboard"
    const val PRODUCTS = "products?filter={filter}"
    fun products(filter: String = "") = "products?filter=$filter"
    const val EDIT = "edit?id={id}&copyFrom={copyFrom}&barcode={barcode}"
    fun add(copyFrom: Long = 0, barcode: String = "") = "edit?id=0&copyFrom=$copyFrom&barcode=${android.net.Uri.encode(barcode)}"
    fun edit(id: Long) = "edit?id=$id&copyFrom=0&barcode="
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
    const val SCANNER = "scanner"
    const val NOTIFICATIONS = "notifications"
    const val TRANSFER = "transfer"
    const val IMPORT_PREVIEW = "import_preview"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
}

private data class Tab(val route: String, val baseRoute: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "dashboard", "Ana səhifə", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.products(), "products", "Məhsullar", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Tab(Routes.NOTIFICATIONS, "notifications", "Bildirişlər", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Tab(Routes.TRANSFER, "transfer", "İxrac/İdxal", Icons.Outlined.SwapVert, Icons.Filled.SwapVert),
    Tab(Routes.SETTINGS, "settings", "Parametrlər", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel, deepLink: MutableStateFlow<DeepLink?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val link by deepLink.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Android 13+ bildiriş icazəsi (ilk açılışda bir dəfə soruşulur)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Bildiriş icazəsi verilmədi. Parametrlərdən aktiv edə bilərsiniz.")
    }
    LaunchedEffect(Unit) {
        val s = context.appContainer.settings
        if (Build.VERSION.SDK_INT >= 33 && !s.permissionAsked) {
            s.permissionAsked = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(link) {
        when (val l = link) {
            is DeepLink.OpenProduct -> nav.navigate(Routes.detail(l.id)) { launchSingleTop = true }
            is DeepLink.OpenScreen -> when (l.route) {
                "notifications" -> nav.navigateTab(Routes.NOTIFICATIONS)
                "dashboard" -> nav.navigateTab(Routes.DASHBOARD)
            }
            is DeepLink.ImportFile -> {
                nav.navigateTab(Routes.TRANSFER)
                vm.prepareImport(l.uri, restore = false) { nav.navigate(Routes.IMPORT_PREVIEW) }
            }
            null -> {}
        }
        if (link != null) deepLink.value = null
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: ""
    val showBar = tabs.any { currentRoute.startsWith(it.baseRoute) } || currentRoute.startsWith("edit")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(visible = showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                BottomBar(currentRoute = currentRoute, onTab = { nav.navigateTab(it) }, onAdd = {
                    if (!currentRoute.startsWith("edit")) nav.navigate(Routes.add()) { launchSingleTop = true }
                })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = Routes.DASHBOARD,
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 12 } },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 12 } }
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(vm,
                        onOpenProducts = { filter -> nav.navigateTab(Routes.products(filter)) },
                        onOpenProduct = { nav.navigate(Routes.detail(it)) },
                        onAdd = { nav.navigate(Routes.add()) },
                        onScan = { nav.navigate(Routes.SCANNER) })
                }
                composable(
                    Routes.PRODUCTS,
                    arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "" })
                ) { entry ->
                    val scanned = entry.savedStateHandle.getStateFlow<String?>("barcode", null).collectAsStateWithLifecycle().value
                    ProductsScreen(vm,
                        initialFilter = entry.arguments?.getString("filter").orEmpty(),
                        scannedBarcode = scanned,
                        onScannedConsumed = { entry.savedStateHandle["barcode"] = null },
                        onOpenProduct = { nav.navigate(Routes.detail(it)) },
                        onEdit = { nav.navigate(Routes.edit(it)) },
                        onScan = { nav.navigate(Routes.SCANNER) },
                        onAdd = { nav.navigate(Routes.add()) },
                        onExportSelected = { ids ->
                            nav.navigateTab(Routes.TRANSFER)
                            vm.pendingExportSelection.value = ids
                        })
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("copyFrom") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("barcode") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    val scanned = entry.savedStateHandle.getStateFlow<String?>("barcode", null).collectAsStateWithLifecycle().value
                    ProductEditScreen(
                        vm = vm,
                        productId = entry.arguments?.getLong("id") ?: 0L,
                        copyFromId = entry.arguments?.getLong("copyFrom") ?: 0L,
                        initialBarcode = entry.arguments?.getString("barcode").orEmpty(),
                        scannedBarcode = scanned,
                        onScannedConsumed = { entry.savedStateHandle["barcode"] = null },
                        onScan = { nav.navigate(Routes.SCANNER) },
                        onDone = { savedId ->
                            nav.popBackStack()
                            if (savedId > 0 && nav.currentDestination?.route?.startsWith("detail") != true) {
                                nav.navigate(Routes.detail(savedId))
                            }
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ProductDetailScreen(
                        vm = vm,
                        productId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { if (!nav.popBackStack()) nav.navigateTab(Routes.DASHBOARD) },
                        onEdit = { nav.navigate(Routes.edit(it)) },
                        onAddBatch = { nav.navigate(Routes.add(copyFrom = it)) },
                        onOpenProduct = { nav.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.SCANNER) {
                    ScannerScreen(
                        onResult = { code ->
                            nav.previousBackStackEntry?.savedStateHandle?.set("barcode", code)
                            nav.popBackStack()
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(vm, onOpenProduct = { nav.navigate(Routes.detail(it)) }, onSettings = { nav.navigateTab(Routes.SETTINGS) })
                }
                composable(Routes.TRANSFER) {
                    TransferScreen(vm, onImportReady = { nav.navigate(Routes.IMPORT_PREVIEW) })
                }
                composable(Routes.IMPORT_PREVIEW) {
                    ImportPreviewScreen(vm, onFinished = {
                        nav.popBackStack(Routes.TRANSFER, inclusive = false)
                    }, onBack = {
                        vm.cancelImport()
                        nav.popBackStack()
                    })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(vm, onCategories = { nav.navigate(Routes.CATEGORIES) })
                }
                composable(Routes.CATEGORIES) {
                    CategoriesScreen(vm, onBack = { nav.popBackStack() })
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    val filteredProducts = route.startsWith("products?filter=") && route != Routes.products()
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !filteredProducts
    }
}

@Composable
private fun BottomBar(currentRoute: String, onTab: (String) -> Unit, onAdd: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.take(2).forEach { BarItem(it, currentRoute.startsWith(it.baseRoute), Modifier.weight(1f)) { onTab(it.route) } }
            AddButton(Modifier.weight(1f), selected = currentRoute.startsWith("edit"), onAdd)
            tabs.drop(2).forEach { BarItem(it, currentRoute.startsWith(it.baseRoute), Modifier.weight(1f)) { onTab(it.route) } }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "tab")
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 3.dp)
        ) {
            Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = color, modifier = Modifier.size(24.dp).scale(scale))
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, fontSize = 10.5.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddButton(modifier: Modifier, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .offset(y = (-10).dp)
                .size(58.dp)
                .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF3730A3))))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, "Məhsul əlavə et", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(
            "Əlavə et", fontSize = 10.5.sp, modifier = Modifier.offset(y = (-6).dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
