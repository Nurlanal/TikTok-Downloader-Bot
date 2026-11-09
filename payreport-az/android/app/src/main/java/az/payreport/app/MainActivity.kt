package az.payreport.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.payreport.app.ui.admin.AdminScreen
import az.payreport.app.ui.dashboard.DashboardScreen
import az.payreport.app.ui.monthly.MonthlyReportScreen
import az.payreport.app.ui.phone.PhoneReportScreen
import az.payreport.app.ui.settings.SettingsScreen
import az.payreport.app.ui.theme.PayReportTheme

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("dashboard", "Panel", Icons.Outlined.Dashboard),
    Dest("phone", "Nömrələr", Icons.Outlined.PhoneInTalk),
    Dest("monthly", "Hesabat", Icons.Outlined.Assessment),
    Dest("admin", "Admin", Icons.Outlined.AdminPanelSettings),
    Dest("settings", "Ayarlar", Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PayReportApp).container
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            container.settings.current.notifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PayReportTheme {
                AppRoot(container)
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route?.substringBefore("?")
    val pending by remember { container.db.payments().countPending() }.collectAsState(initial = 0)

    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = !route.contains("?")
        }
    }

    Scaffold(
        // Yuxarı (status paneli) boşluğunu hər ekran özü idarə edir; burada yalnız alt panel nəzərə alınır
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                DESTS.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = { go(d.route) },
                        icon = {
                            if (d.route == "admin" && pending > 0) {
                                BadgedBox(badge = { Badge { Text(if (pending > 99) "99+" else pending.toString()) } }) { Icon(d.icon, d.label) }
                            } else Icon(d.icon, d.label)
                        },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad)) {
            NavHost(nav, startDestination = "dashboard") {
                composable("dashboard") {
                    DashboardScreen(
                        onOpenSettings = { go("settings") },
                        onOpenPending = { go("admin?tab=1") },
                        onOpenReports = { go("monthly") },
                    )
                }
                composable("phone") { PhoneReportScreen() }
                composable("monthly") { MonthlyReportScreen() }
                composable(
                    "admin?tab={tab}",
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    AdminScreen(initialTab = entry.arguments?.getInt("tab") ?: 0, onOpenSettings = { go("settings") })
                }
                composable("settings") { SettingsScreen(onBack = null) }
            }
        }
    }
}
