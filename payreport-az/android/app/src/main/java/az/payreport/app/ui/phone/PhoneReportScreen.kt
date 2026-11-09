package az.payreport.app.ui.phone

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.AppContainer
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.data.db.PhoneTotal
import az.payreport.app.domain.PhoneReport
import az.payreport.app.domain.Reports
import az.payreport.app.ui.common.appViewModel
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PhoneUiState {
    data class Top(val items: List<PhoneTotal>) : PhoneUiState
    data class Matches(val items: List<PhoneTotal>) : PhoneUiState
    data class Report(val report: PhoneReport) : PhoneUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PhoneReportViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    private val dao = c.db.payments()

    val state: StateFlow<PhoneUiState> = query.debounce(250).flatMapLatest { q ->
        val normalized = PhoneUtils.normalize(q)
        val digits = PhoneUtils.searchDigits(q)
        when {
            normalized != null -> dao.observeByPhone(normalized).map { PhoneUiState.Report(Reports.phone(normalized, it)) }
            digits.length >= 3 -> dao.observePhoneMatches(digits).map { PhoneUiState.Matches(it) }
            q.isBlank() -> dao.observeTopPhones(0, Long.MAX_VALUE, 20).map { PhoneUiState.Top(it) }
            else -> flowOf(PhoneUiState.Matches(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhoneUiState.Top(emptyList()))

    fun export(report: PhoneReport, uri: android.net.Uri, pdf: Boolean, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { c.exporter.exportPhone(report, uri, pdf); "Hesabat saxlanıldı" }.getOrElse { "Xəta: ${it.message}" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneReportScreen() {
    val vm = appViewModel { PhoneReportViewModel(it) }
    val q by vm.query.collectAsState()
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val report = (state as? PhoneUiState.Report)?.report

    val xlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null && report != null) vm.export(report, uri, false) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && report != null) vm.export(report, uri, true) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Nömrə üzrə hesabat") })
        OutlinedTextField(
            value = q,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("055 777 66 55") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Təmizlə") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            is PhoneUiState.Top -> PhoneList("Ən çox ödəniş edən nömrələr", s.items) { vm.query.value = PhoneUtils.format(it) }
            is PhoneUiState.Matches -> PhoneList("Uyğun nömrələr", s.items) { vm.query.value = PhoneUtils.format(it) }
            is PhoneUiState.Report -> LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val r = s.report
                item {
                    AppCard {
                        Text(PhoneUtils.format(r.phone), style = MaterialTheme.typography.titleLarge)
                        Text("Ümumi ödənilən məbləğ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.azn(r.totalMinor), style = MoneyStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize), color = AppTheme.extra.positive)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { xlsx.launch("nomre-${r.phone.removePrefix("+")}.xlsx") }, label = { Text("Excel") })
                            AssistChip(onClick = { pdf.launch("nomre-${r.phone.removePrefix("+")}.pdf") }, label = { Text("PDF") })
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Ödəniş sayı", r.count.toString(), Icons.Outlined.Receipt, Modifier.weight(1f),
                            caption = if (r.pendingCount > 0) "+${r.pendingCount} gözləyən" else null)
                        StatCard("Son ödəniş", r.lastPaidAt?.let { Fmt.date(it) } ?: "—", Icons.Outlined.CalendarMonth, Modifier.weight(1f),
                            caption = r.lastPaidAt?.let { Fmt.time(it) })
                    }
                }
                if (r.monthly.isNotEmpty()) {
                    item {
                        AppCard {
                            Text("Aylıq statistika", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            val last = r.monthly.takeLast(12)
                            BarChart(last.map { it.totalMinor }, last.map { it.label })
                            Spacer(Modifier.height(8.dp))
                            r.monthly.reversed().forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(Fmt.monthYear(m.year, m.month), Modifier.weight(1f))
                                    Text("${m.count} ödəniş", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(Fmt.azn(m.totalMinor), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("Ödəniş tarixçəsi (${r.payments.size})") }
                if (r.payments.isEmpty()) item { EmptyState(Icons.Outlined.Payments, "Ödəniş tapılmadı", "Bu nömrədən hələ ödəniş qeydə alınmayıb.") }
                else item {
                    AppCard {
                        r.payments.forEachIndexed { i, p ->
                            PaymentRow(p, showPhone = false, onClick = { selected = p.id })
                            if (i < r.payments.lastIndex) ThinDivider()
                        }
                    }
                }
            }
        }
    }
    selected?.let { PaymentDetailSheet(it, allowActions = false) { selected = null } }
}

@Composable
private fun PhoneList(title: String, items: List<PhoneTotal>, onPick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
        item { SectionTitle(title) }
        if (items.isEmpty()) {
            item { EmptyState(Icons.Outlined.PersonSearch, "Nəticə yoxdur", "Telefon nömrəsini tam və ya qismən daxil edin.") }
        }
        itemsIndexed(items) { i, t ->
            AppCard(Modifier.padding(vertical = 4.dp), onClick = { onPick(t.phone) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(PhoneUtils.format(t.phone), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("${t.count} ödəniş · son: ${Fmt.date(t.lastPaidAt)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Fmt.azn(t.totalMinor), style = MoneyStyle, color = AppTheme.extra.positive)
                }
            }
        }
    }
}
