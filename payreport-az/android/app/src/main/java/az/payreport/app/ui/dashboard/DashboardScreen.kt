package az.payreport.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.AppContainer
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.format.Periods
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.SyncRunEntity
import az.payreport.app.data.settings.SettingsState
import az.payreport.app.domain.DayTotal
import az.payreport.app.domain.Reports
import az.payreport.app.ui.common.appViewModel
import az.payreport.app.ui.common.ticker
import az.payreport.app.ui.components.AppCard
import az.payreport.app.ui.components.BarChart
import az.payreport.app.ui.components.EmptyState
import az.payreport.app.ui.components.PaymentRow
import az.payreport.app.ui.components.SectionTitle
import az.payreport.app.ui.components.StatCard
import az.payreport.app.ui.components.ThinDivider
import az.payreport.app.ui.payment.PaymentDetailSheet
import az.payreport.app.ui.theme.AppTheme
import az.payreport.app.ui.theme.MoneyStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val todayMinor: Long = 0,
    val yesterdayMinor: Long = 0,
    val weekMinor: Long = 0,
    val monthMinor: Long = 0,
    val totalMinor: Long = 0,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val chart: List<DayTotal> = emptyList(),
    val recent: List<PaymentEntity> = emptyList(),
    val lastRun: SyncRunEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val c: AppContainer) : ViewModel() {
    private val dao = c.db.payments()

    val state: StateFlow<DashboardState> = ticker { Periods.now() }.flatMapLatest { p ->
        combine(
            listOf(
                dao.sumConfirmed(p.today, p.tomorrow),
                dao.sumConfirmed(p.yesterday, p.today),
                dao.sumConfirmed(p.weekStart, p.tomorrow),
                dao.sumConfirmed(p.monthStart, p.tomorrow),
                dao.totalConfirmed(),
                dao.countConfirmed(),
                dao.countPending(),
                dao.observeConfirmedBetween(p.chartStart, p.tomorrow),
                dao.observeRecent(15),
                c.db.syncRuns().observeLast(),
            )
        ) { v ->
            @Suppress("UNCHECKED_CAST")
            DashboardState(
                todayMinor = v[0] as Long,
                yesterdayMinor = v[1] as Long,
                weekMinor = v[2] as Long,
                monthMinor = v[3] as Long,
                totalMinor = v[4] as Long,
                totalCount = v[5] as Int,
                pendingCount = v[6] as Int,
                chart = Reports.lastDays(p.todayDate, 14, v[7] as List<PaymentEntity>),
                recent = v[8] as List<PaymentEntity>,
                lastRun = v[9] as SyncRunEntity?,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    val settings: StateFlow<SettingsState> = c.settings.state
    val syncing = c.syncManager.running

    fun syncNow() = viewModelScope.launch { c.syncManager.syncNow("MANUAL") }
}

@Composable
fun DashboardScreen(onOpenSettings: () -> Unit, onOpenPending: () -> Unit, onOpenReports: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val syncing by vm.syncing.collectAsState()
    var selected by remember { mutableStateOf<Long?>(null) }
    val configured = settings.mailPasswordSet || settings.serverLoggedIn

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Hero(s, settings, syncing, onSync = { vm.syncNow() }, onSettings = onOpenSettings)
        }
        if (!configured) {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = onOpenSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = AppTheme.extra.warning)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mail bağlantısı qurulmayıb", style = MaterialTheme.typography.titleSmall)
                            Text("Mail.ru hesabını və ya serveri Ayarlar bölməsində təhlükəsiz şəkildə daxil edin.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Dünənki mədaxil", Fmt.azn(s.yesterdayMinor), Icons.Outlined.History, Modifier.weight(1f))
                    StatCard("Həftəlik mədaxil", Fmt.azn(s.weekMinor), Icons.Outlined.DateRange, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Aylıq mədaxil", Fmt.azn(s.monthMinor), Icons.Outlined.CalendarMonth, Modifier.weight(1f),
                        accent = AppTheme.extra.positive, onClick = onOpenReports)
                    StatCard("Ümumi mədaxil", Fmt.azn(s.totalMinor), Icons.Outlined.AccountBalanceWallet, Modifier.weight(1f),
                        accent = AppTheme.extra.positive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Əməliyyat sayı", s.totalCount.toString(), Icons.Outlined.Receipt, Modifier.weight(1f), caption = "təsdiqlənmiş")
                    StatCard("Gözləyən ödənişlər", s.pendingCount.toString(), Icons.Outlined.HourglassTop, Modifier.weight(1f),
                        accent = AppTheme.extra.warning, caption = if (s.pendingCount > 0) "yoxlamaq üçün toxunun" else "hamısı yoxlanılıb",
                        onClick = onOpenPending)
                }
                AppCard {
                    Text("Son 14 gün", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    BarChart(
                        values = s.chart.map { it.totalMinor },
                        labels = s.chart.map { Fmt.shortDay(it.date) },
                        labelEvery = 3,
                    )
                }
                SectionTitle("Son ödənişlər")
            }
        }
        if (s.recent.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.Inbox, "Hələ ödəniş yoxdur",
                    if (configured) "Sinxronizasiya düyməsinə toxunun və ya yeni bildirişləri gözləyin." else "Əvvəlcə mail bağlantısını qurun.")
            }
        } else {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    s.recent.forEachIndexed { i, p ->
                        PaymentRow(p, onClick = { selected = p.id })
                        if (i < s.recent.lastIndex) ThinDivider()
                    }
                }
            }
        }
    }
    selected?.let { PaymentDetailSheet(it, allowActions = false) { selected = null } }
}

@Composable
private fun Hero(s: DashboardState, settings: SettingsState, syncing: Boolean, onSync: () -> Unit, onSettings: () -> Unit) {
    val extra = AppTheme.extra
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PayReport AZ", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${Fmt.now().dayOfMonth} ${Fmt.MONTHS[Fmt.now().monthValue - 1]} ${Fmt.now().year}",
                        color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ayarlar", tint = Color.White) }
            }
            Spacer(Modifier.height(18.dp))
            Text("Bugünkü mədaxil", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.amount(s.todayMinor), color = Color.White, style = MoneyStyle.copy(fontSize = 40.sp))
                Spacer(Modifier.width(6.dp))
                Text("AZN", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            val diff = s.todayMinor - s.yesterdayMinor
            Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                Text(
                    (if (diff >= 0) "▲ +" else "▼ ") + Fmt.azn(diff) + " dünənə nisbətən",
                    color = if (diff >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Son sinxronizasiya: ${Fmt.relative(settings.lastSyncAt)}",
                        color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium,
                    )
                    val msg = settings.lastSyncMessage
                    if (msg != null) {
                        Text(
                            msg, maxLines = 2, style = MaterialTheme.typography.labelSmall,
                            color = if (settings.lastSyncStatus == "ERROR") Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                FilledTonalButton(onClick = onSync, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (syncing) "Yoxlanılır" else "Yenilə")
                }
            }
        }
    }
}
