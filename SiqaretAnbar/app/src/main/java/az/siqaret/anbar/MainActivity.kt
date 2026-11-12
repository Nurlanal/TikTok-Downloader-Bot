package az.siqaret.anbar

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.siqaret.anbar.ui.components.LocalSnackbarHost
import az.siqaret.anbar.ui.screens.FirmDetailScreen
import az.siqaret.anbar.ui.screens.FirmsScreen
import az.siqaret.anbar.ui.screens.HistoryScreen
import az.siqaret.anbar.ui.screens.InvoiceDetailScreen
import az.siqaret.anbar.ui.screens.InvoiceEditScreen
import az.siqaret.anbar.ui.screens.HomeScreen
import az.siqaret.anbar.ui.screens.ProductEditScreen
import az.siqaret.anbar.ui.screens.ReportScreen
import az.siqaret.anbar.ui.theme.SiqaretTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SiqaretApp
        setContent {
            val vm: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(app))
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            SiqaretTheme(darkTheme = dark) { AppRoot(vm) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Ana səhifə", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("firms", "Firmalar", Icons.Outlined.Business, Icons.Filled.Business),
    Tab("history", "Tarixçə", Icons.Outlined.History, Icons.Filled.History),
    Tab("report", "Hesabat", Icons.Outlined.Assessment, Icons.Filled.Assessment),
)

@Composable
fun AppRoot(vm: InventoryViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = m.actionLabel,
                withDismissAction = m.actionLabel == null,
                duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                m.undoDelete?.let { vm.restoreProduct(it) }
                if (m.shareUri != null) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = m.shareMime
                        putExtra(Intent.EXTRA_STREAM, m.shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Hesabatı paylaş")) }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    vm,
                    onAdd = {
                        val f = vm.firmFilter.value?.takeIf { it > 0 }
                        nav.navigate(if (f != null) "edit?firmId=$f" else "edit")
                    },
                    onEdit = { id -> nav.navigate("edit?id=$id") },
                )
            }
            composable("firms") { FirmsScreen(vm, onOpen = { id -> nav.navigate("firm/$id") }) }
            composable(
                "firm/{firmId}",
                arguments = listOf(navArgument("firmId") { type = NavType.LongType }),
            ) { back ->
                FirmDetailScreen(
                    vm,
                    firmId = back.arguments?.getLong("firmId") ?: InventoryViewModel.NO_FIRM_FILTER,
                    onBack = { nav.popBackStack() },
                    onAddProduct = { f -> nav.navigate(if (f != null) "edit?firmId=$f" else "edit") },
                    onEditProduct = { id -> nav.navigate("edit?id=$id") },
                    onAddInvoice = { f -> nav.navigate("invoice/new/$f") },
                    onOpenInvoice = { id -> nav.navigate("invoice/$id") },
                )
            }
            composable(
                "invoice/new/{firmId}",
                arguments = listOf(navArgument("firmId") { type = NavType.LongType }),
            ) { back ->
                InvoiceEditScreen(
                    vm,
                    firmId = back.arguments?.getLong("firmId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable(
                "invoice/{invoiceId}",
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType }),
            ) { back ->
                InvoiceDetailScreen(vm, back.arguments?.getLong("invoiceId") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("history") { HistoryScreen(vm) }
            composable("report") { ReportScreen(vm) }
            composable(
                "edit?id={id}&firmId={firmId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("firmId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { back ->
                val id = back.arguments?.getLong("id")?.takeIf { it > 0 }
                val firmId = back.arguments?.getLong("firmId")?.takeIf { it > 0 }
                ProductEditScreen(vm, id, initialFirmId = firmId, onBack = { nav.popBackStack() })
            }
        }
        }
    }
}
