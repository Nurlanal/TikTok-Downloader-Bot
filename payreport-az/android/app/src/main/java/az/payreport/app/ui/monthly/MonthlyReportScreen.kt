package az.payreport.app.ui.monthly

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.AppContainer
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.domain.MonthlyReport
import az.payreport.app.domain.Reports
import az.payreport.app.ui.common.appViewModel
import az.payreport.app.ui.components.AppCard
import az.payreport.app.ui.components.BarChart
import az.payreport.app.ui.components.SectionTitle
import az.payreport.app.ui.components.StatCard
import az.payreport.app.ui.components.ThinDivider
import az.payreport.app.ui.theme.AppTheme
import az.payreport.app.ui.theme.MoneyStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportViewModel(private val c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.from(Fmt.now()))

    val report: StateFlow<MonthlyReport?> = month.flatMapLatest { ym ->
        val (from, to) = Reports.monthRange(ym.year, ym.monthValue)
        c.db.payments().observeConfirmedBetween(from, to).map { Reports.monthly(ym.year, ym.monthValue, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun shift(delta: Long) { month.value = month.value.plusMonths(delta) }

    fun export(uri: android.net.Uri, pdf: Boolean, done: (String) -> Unit) = viewModelScope.launch {
        val r = report.value ?: return@launch
        done(runCatching { c.exporter.exportMonthly(r, uri, pdf); if (pdf) "PDF hesabat saxlanıldı" else "Excel hesabat saxlanıldı" }
            .getOrElse { "Xəta: ${it.message}" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen() {
    val vm = appViewModel { MonthlyReportViewModel(it) }
    val ym by vm.month.collectAsState()
    val r by vm.report.collectAsState()
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    val fileBase = "payreport-${ym.year}-${ym.monthValue.toString().padStart(2, '0')}"

    val xlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.export(uri, false, toast) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.export(uri, true, toast)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Aylıq hesabat") })
        MonthPicker(ym, onShift = vm::shift, onPick = { vm.month.value = it })

        val report = r
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (report == null) return@LazyColumn
            item {
                AppCard {
                    Text("Aylıq ümumi mədaxil", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.azn(report.totalMinor), style = MoneyStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                        color = AppTheme.extra.positive)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { xlsx.launch("$fileBase.xlsx") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.TableChart, null); Spacer(Modifier.width(6.dp)); Text("Excel")
                        }
                        OutlinedButton(onClick = { pdf.launch("$fileBase.pdf") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Əməliyyat sayı", report.count.toString(), Icons.Outlined.Receipt, Modifier.weight(1f))
                    StatCard("Orta ödəniş", Fmt.azn(report.averageMinor), Icons.AutoMirrored.Outlined.TrendingUp, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Fərqli nömrələr", report.byPhone.size.toString(), Icons.Outlined.Groups, Modifier.weight(1f))
                    StatCard("Ən yaxşı gün", report.bestDay?.let { Fmt.shortDay(it.date) } ?: "—", Icons.Outlined.Today, Modifier.weight(1f),
                        caption = report.bestDay?.let { Fmt.azn(it.totalMinor) })
                }
            }
            item {
                AppCard {
                    Text("Gündəlik mədaxil", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    BarChart(report.daily.map { it.totalMinor }, report.daily.map { it.date.dayOfMonth.toString() },
                        highlightLast = false, labelEvery = 5)
                }
            }
            item { SectionTitle("Ən çox ödəniş edən nömrələr") }
            item {
                AppCard {
                    if (report.topPayers.isEmpty()) Text("Bu ay təsdiqlənmiş ödəniş yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    report.topPayers.forEachIndexed { i, p ->
                        val share = if (report.totalMinor > 0) p.totalMinor * 100 / report.totalMinor else 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(PhoneUtils.format(p.phone), fontWeight = FontWeight.SemiBold)
                                Text("${p.count} ödəniş · $share%", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Fmt.azn(p.totalMinor), style = MoneyStyle, color = AppTheme.extra.positive)
                        }
                        if (i < report.topPayers.lastIndex) ThinDivider()
                    }
                }
            }
            item { SectionTitle("Telefon nömrələri üzrə məbləğlər (${report.byPhone.size})") }
            item {
                AppCard {
                    report.byPhone.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(PhoneUtils.format(p.phone), Modifier.weight(1f))
                            Text("${p.count}×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(Fmt.azn(p.totalMinor), fontWeight = FontWeight.SemiBold)
                        }
                        if (i < report.byPhone.lastIndex) ThinDivider()
                    }
                    if (report.byPhone.isEmpty()) Text("—")
                }
            }
            item { SectionTitle("Gündəlik cədvəl") }
            item {
                AppCard {
                    report.daily.filter { it.count > 0 }.forEachIndexed { i, d ->
                        if (i > 0) ThinDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${Fmt.shortDay(d.date)} · ${Fmt.WEEKDAYS_SHORT[d.date.dayOfWeek.value - 1]}", Modifier.weight(1f))
                            Text("${d.count} əməliyyat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(Fmt.azn(d.totalMinor), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (report.count == 0) Text("Məlumat yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MonthPicker(ym: YearMonth, onShift: (Long) -> Unit, onPick: (YearMonth) -> Unit) {
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    val now = YearMonth.from(Fmt.now())
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Əvvəlki ay") }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            Column {
                TextButton(onClick = { monthMenu = true }) { Text(Fmt.MONTHS[ym.monthValue - 1], style = MaterialTheme.typography.titleMedium) }
                DropdownMenu(monthMenu, { monthMenu = false }) {
                    Fmt.MONTHS.forEachIndexed { i, m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = { monthMenu = false; onPick(ym.withMonth(i + 1)) })
                    }
                }
            }
            Column {
                TextButton(onClick = { yearMenu = true }) { Text(ym.year.toString(), style = MaterialTheme.typography.titleMedium) }
                DropdownMenu(yearMenu, { yearMenu = false }) {
                    (now.year downTo now.year - 6).forEach { y ->
                        DropdownMenuItem(text = { Text(y.toString()) }, onClick = { yearMenu = false; onPick(ym.withYear(y)) })
                    }
                }
            }
        }
        IconButton(onClick = { onShift(1) }, enabled = ym < now) { Icon(Icons.Outlined.ChevronRight, "Növbəti ay") }
    }
}
