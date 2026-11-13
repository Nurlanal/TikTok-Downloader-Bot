package az.sontarix.market.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.EmptyState
import az.sontarix.market.ui.components.Icon3D
import az.sontarix.market.ui.components.ProductCard
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils

@Composable
fun NotificationsScreen(vm: MainViewModel, onOpenProduct: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val products by vm.products.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sent by vm.recentNotifications.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var canNotify by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    // Parametrlərdən qayıtdıqda icazə vəziyyətini yenilə
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) canNotify = NotificationHelper.canNotify(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = NotificationHelper.canNotify(context)
        // İcazə əvvəlcədən həmişəlik rədd edilibsə, sistem parametrlərini aç
        if (!granted) openNotificationSettings(context)
    }

    val today = DateUtils.today()
    val upcoming = remember(products, settings) {
        products.filter { it.daysLeft(today) <= settings.warningDays }.sortedBy { it.expiryEpochDay }
    }
    val namesById = remember(products) { products.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bildirişlər", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.checkNow() }) { Icon(Icons.Filled.Refresh, "İndi yoxla") }
                    if (tab == 1 && sent.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Tarixçəni təmizlə") }
                }
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Yaxınlaşan (${upcoming.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Göndərilmiş (${sent.size})") })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                StatusCard(
                    enabled = settings.notificationsEnabled, canNotify = canNotify,
                    time = "%02d:%02d".format(settings.notifyHour, settings.notifyMinute),
                    days = settings.thresholds,
                    onGrant = {
                        val needsRuntime = Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (needsRuntime) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openNotificationSettings(context)
                    },
                    onSettings = onSettings
                )
            }
            if (tab == 0) {
                if (upcoming.isEmpty()) item {
                    EmptyState(Icons.Outlined.EventAvailable, "Yaxınlaşan tarix yoxdur", "Növbəti ${settings.warningDays} gündə bitən məhsul yoxdur.")
                }
                items(upcoming, key = { it.id }) { p ->
                    ProductCard(p, settings.warningDays, onClick = { onOpenProduct(p.id) })
                }
            } else {
                if (sent.isEmpty()) item {
                    EmptyState(Icons.Outlined.NotificationsNone, "Bildiriş göndərilməyib", "Göndərilən xəbərdarlıqlar burada görünəcək.")
                }
                items(sent, key = { it.id }) { log ->
                    val p = namesById[log.productId]
                    val color = when {
                        log.threshold < 0 -> StatusColors.Red
                        log.threshold <= 7 -> StatusColors.Orange
                        else -> StatusColors.Yellow
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = p != null) { onOpenProduct(log.productId) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Icon3D(Icons.Filled.NotificationsActive, color, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(log.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                                Spacer(Modifier.height(4.dp))
                                Text(log.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(DateUtils.formatMillis(log.sentAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog("Tarixçəni təmizlə", "Göndərilmiş bildirişlərin siyahısı silinəcək. Eyni bildirişlər təkrar göndərilməyəcək.",
            confirmText = "Təmizlə", destructive = true,
            onConfirm = { confirmClear = false; vm.clearNotificationHistory() }, onDismiss = { confirmClear = false })
    }
}

@Composable
private fun StatusCard(enabled: Boolean, canNotify: Boolean, time: String, days: List<Int>, onGrant: () -> Unit, onSettings: () -> Unit) {
    val ok = enabled && canNotify
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3D(if (ok) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    if (ok) StatusColors.Indigo else StatusColors.Red, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when { !enabled -> "Bildirişlər söndürülüb"; !canNotify -> "Bildiriş icazəsi yoxdur"; else -> "Bildirişlər aktivdir" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, Modifier.padding(end = 4.dp))
                        Text("Hər gün $time • ${days.joinToString(", ")} gün əvvəl", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!canNotify) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Bildiriş icazəsi ver") }
            } else if (!enabled) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Parametrlərdə aktiv et") }
            }
        }
    }
}

fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
