package az.payreport.app.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.AppContainer
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.format.Periods
import az.payreport.app.core.parser.PaymentParser
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.data.db.AuditEntity
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentStatus
import az.payreport.app.data.db.ResultCount
import az.payreport.app.data.db.SyncRunEntity
import az.payreport.app.data.mail.ConnectionTestResult
import az.payreport.app.data.remote.RemoteMailStatus
import az.payreport.app.data.repo.PaymentDraft
import az.payreport.app.data.settings.SettingsState
import az.payreport.app.data.settings.SyncMode
import az.payreport.app.ui.common.AdminSession
import az.payreport.app.ui.common.appViewModel
import az.payreport.app.ui.components.AppCard
import az.payreport.app.ui.components.EmptyState
import az.payreport.app.ui.components.KeyValue
import az.payreport.app.ui.components.PaymentRow
import az.payreport.app.ui.components.ThinDivider
import az.payreport.app.ui.payment.PaymentDetailSheet
import az.payreport.app.ui.payment.PaymentEditDialog
import az.payreport.app.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminFilter(
    val status: String? = null,
    val phone: String = "",
    val query: String = "",
    val range: String = "ALL",
    val customFrom: String = "",
    val customTo: String = "",
)

data class ScanOutcome(val title: String, val text: String)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(AdminFilter())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val remoteStatus = MutableStateFlow<RemoteMailStatus?>(null)
    val scan = MutableStateFlow<ScanOutcome?>(null)

    val settings: StateFlow<SettingsState> = c.settings.state
    val syncing = c.syncManager.running

    val payments: StateFlow<List<PaymentEntity>> = filter.flatMapLatest { f ->
        val p = Periods.now()
        val (from, to) = when (f.range) {
            "TODAY" -> p.today to p.tomorrow
            "WEEK" -> p.weekStart to p.tomorrow
            "MONTH" -> p.monthStart to p.tomorrow
            "CUSTOM" -> (Fmt.parseUserDateTime("${f.customFrom} 00:00") ?: 0L) to
                ((Fmt.parseUserDateTime("${f.customTo} 00:00")?.plus(86_400_000L)) ?: Long.MAX_VALUE)
            else -> 0L to Long.MAX_VALUE
        }
        val digits = PhoneUtils.searchDigits(f.phone).ifEmpty { null }
        c.db.payments().search(f.status, digits, from, to, f.query.trim().ifEmpty { null }, 500)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pending = c.db.payments().observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val audit: StateFlow<List<AuditEntity>> = c.db.audit().observeRecent(300).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runs: StateFlow<List<SyncRunEntity>> = c.db.syncRuns().observeRecent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val emailCounts: StateFlow<List<ResultCount>> = c.db.processedEmails().observeResultCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun act(ok: String, block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        message.value = try { block(); ok } catch (e: Exception) { e.message ?: "Xəta" }
        busy.value = false
    }

    fun add(d: PaymentDraft) = act("Ödəniş əlavə edildi") { c.repository.addManual(d) }
    fun confirm(id: Long) = act("Təsdiqləndi") { c.repository.confirm(id) }
    fun confirmAllValid() = act("Uyğun gözləyənlər təsdiqləndi") {
        pending.value.filter { it.phone != null && it.amountMinor != null }.forEach { c.repository.confirm(it.id) }
    }

    fun syncNow() = viewModelScope.launch {
        val o = c.syncManager.syncNow("MANUAL")
        message.value = o.message
        refreshRemote()
    }

    fun testConnection() = viewModelScope.launch {
        busy.value = true
        if (c.settings.current.mode == SyncMode.DIRECT) {
            testResult.value = c.directMail.testConnection(c.settings.current.mail, null)
        } else {
            testResult.value = try {
                val r = c.api.testMail(kotlinx.serialization.json.buildJsonObject { })
                ConnectionTestResult(r.ok, r.messages, r.unseen, r.latencyMs, r.error)
            } catch (e: Exception) {
                ConnectionTestResult(false, error = e.message)
            }
        }
        busy.value = false
    }

    fun refreshRemote() = viewModelScope.launch {
        if (c.settings.current.mode == SyncMode.SERVER && c.settings.current.serverLoggedIn) {
            remoteStatus.value = runCatching { c.api.mailStatus() }.getOrNull()
        }
    }

    fun scanFile(uri: Uri, mime: String?) = viewModelScope.launch {
        busy.value = true
        try {
            val isPdf = mime == "application/pdf" || uri.toString().endsWith(".pdf", true)
            val (text, source) = if (isPdf) {
                val r = c.pdf.extract(uri)
                r.text to (if (r.viaOcr) PaymentParser.SourceType.IMAGE else PaymentParser.SourceType.PDF)
            } else {
                c.ocr.recognize(uri) to PaymentParser.SourceType.IMAGE
            }
            if (text.isBlank()) {
                scan.value = ScanOutcome("Mətn tapılmadı", "Sənəddə oxuna bilən mətn aşkarlanmadı.")
            } else {
                val res = c.repository.ingestScan(text, source, uri.toString())
                scan.value = if (res.parsed.isEmpty()) {
                    ScanOutcome("Ödəniş tanınmadı", "Oxunan mətn:\n\n${text.take(1200)}")
                } else {
                    ScanOutcome(
                        if (res.created > 0) "${res.created} ödəniş gözləyənlərə əlavə edildi" else "Bu qəbz artıq əlavə olunub",
                        res.parsed.joinToString("\n") { "${PhoneUtils.format(it.phone)} — ${Fmt.azn(it.amountMinor)} (dəqiqlik ${it.confidence}%)" } +
                            "\n\nYoxlayıb təsdiqləmək üçün \"Gözləyən\" bölməsinə keçin.",
                    )
                }
            }
        } catch (e: Exception) {
            scan.value = ScanOutcome("Xəta", e.message ?: "Sənəd oxunmadı")
        }
        busy.value = false
    }
}

@Composable
fun AdminScreen(initialTab: Int = 0, onOpenSettings: () -> Unit) {
    val c = appViewModel { AdminViewModel(it) }
    val settings by c.settings.collectAsState()
    var unlocked by remember { mutableStateOf(!settings.pinSet || AdminSession.unlocked) }
    if (!unlocked) {
        PinGate { unlocked = true; AdminSession.unlocked = true }
        return
    }
    AdminContent(c, initialTab, onOpenSettings)
}

@Composable
private fun PinGate(onUnlocked: () -> Unit) {
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as az.payreport.app.PayReportApp).container
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Admin panel", style = MaterialTheme.typography.titleLarge)
        Text("Davam etmək üçün PIN kodu daxil edin", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            pin, { pin = it.filter(Char::isDigit).take(8); error = false },
            label = { Text("PIN") }, singleLine = true, isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (error) Text("PIN yanlışdır", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (container.secureStore.verifyPin(pin)) onUnlocked() else error = true }) { Text("Daxil ol") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminContent(vm: AdminViewModel, initialTab: Int, onOpenSettings: () -> Unit) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    val msg by vm.message.collectAsState()
    val scan by vm.scan.collectAsState()
    val busy by vm.busy.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(initialTab) { tab = initialTab }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.scanFile(uri, null)
    }
    val tabs = listOf("Əməliyyatlar", "Gözləyən", "Mail", "Sinxronizasiya", "Audit")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Admin panel") }, actions = {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    TextButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(4.dp)); Text("Qəbz skan")
                    }
                })
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Ödəniş") })
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TransactionsTab(vm) { selected = it }
                1 -> PendingTab(vm) { selected = it }
                2 -> MailTab(vm, onOpenSettings)
                3 -> RunsTab(vm)
                4 -> AuditTab(vm) { selected = it }
            }
        }
    }
    if (adding) PaymentEditDialog(null, onDismiss = { adding = false }) { adding = false; vm.add(it) }
    selected?.let { PaymentDetailSheet(it, allowActions = true) { selected = null } }
    scan?.let { s ->
        AlertDialog(onDismissRequest = { vm.scan.value = null }, title = { Text(s.title) }, text = { Text(s.text) },
            confirmButton = { TextButton(onClick = { vm.scan.value = null; if (s.title.contains("gözləyənlərə")) tab = 1 }) { Text("OK") } })
    }
}

@Composable
private fun TransactionsTab(vm: AdminViewModel, onSelect: (Long) -> Unit) {
    val f by vm.filter.collectAsState()
    val items by vm.payments.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f.phone, { vm.filter.value = f.copy(phone = it) }, Modifier.weight(1f), label = { Text("Telefon") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(f.query, { vm.filter.value = f.copy(query = it) }, Modifier.weight(1f), label = { Text("ID / qeyd") },
                    singleLine = true, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ManageSearch, null) })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null to "Hamısı", PaymentStatus.CONFIRMED to "Təsdiqlənmiş", PaymentStatus.PENDING to "Gözləyən", PaymentStatus.CANCELLED to "Ləğv")
                    .forEach { (s, l) -> FilterChip(f.status == s, { vm.filter.value = f.copy(status = s) }, { Text(l) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "Bütün dövr", "TODAY" to "Bu gün", "WEEK" to "Bu həftə", "MONTH" to "Bu ay", "CUSTOM" to "Tarix aralığı")
                    .forEach { (r, l) -> FilterChip(f.range == r, { vm.filter.value = f.copy(range = r) }, { Text(l) }) }
            }
            if (f.range == "CUSTOM") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(f.customFrom, { vm.filter.value = f.copy(customFrom = it) }, Modifier.weight(1f),
                        label = { Text("Başlanğıc") }, placeholder = { Text("01.11.2026") }, singleLine = true)
                    OutlinedTextField(f.customTo, { vm.filter.value = f.copy(customTo = it) }, Modifier.weight(1f),
                        label = { Text("Son") }, placeholder = { Text("30.11.2026") }, singleLine = true)
                }
            }
            val total = items.filter { it.status == PaymentStatus.CONFIRMED }.sumOf { it.amountMinor ?: 0 }
            Text("${items.size} nəticə · təsdiqlənmiş cəmi ${Fmt.azn(total)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (items.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.ManageSearch, "Nəticə yoxdur", "Filtrləri dəyişin və ya yeni ödəniş əlavə edin.") }
        items(items, key = { it.id }) { p ->
            PaymentRow(p, onClick = { onSelect(p.id) })
            ThinDivider()
        }
    }
}

@Composable
private fun PendingTab(vm: AdminViewModel, onSelect: (Long) -> Unit) {
    val items by vm.pending.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HourglassTop, null, tint = AppTheme.extra.warning)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${items.size} gözləyən əməliyyat", style = MaterialTheme.typography.titleMedium)
                        Text("Tanıma dəqiq olmadıqda ödəniş avtomatik təsdiqlənmir və mədaxilə daxil edilmir.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (items.any { it.phone != null && it.amountMinor != null }) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.confirmAllValid() }) { Text("Tam məlumatlı olanların hamısını təsdiqlə") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (items.isEmpty()) item { EmptyState(Icons.Outlined.TaskAlt, "Gözləyən yoxdur", "Bütün ödənişlər yoxlanılıb.") }
        items(items, key = { it.id }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PaymentRow(p, onClick = { onSelect(p.id) }) }
                if (p.phone != null && p.amountMinor != null) {
                    TextButton(onClick = { vm.confirm(p.id) }) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = AppTheme.extra.positive)
                    }
                }
            }
            Text("Dəqiqlik: ${p.confidence}% · ${p.emailSubject ?: p.source}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 56.dp, bottom = 6.dp))
            ThinDivider()
        }
    }
}

@Composable
private fun MailTab(vm: AdminViewModel, onOpenSettings: () -> Unit) {
    val s by vm.settings.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val test by vm.testResult.collectAsState()
    val counts by vm.emailCounts.collectAsState()
    val remote by vm.remoteStatus.collectAsState()
    LaunchedEffect(s.mode) { vm.refreshRemote() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard {
                Text("Mail bağlantısının vəziyyəti", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                KeyValue("İş rejimi", if (s.mode == SyncMode.DIRECT) "Cihazda birbaşa IMAP" else "Server (backend API)")
                if (s.mode == SyncMode.DIRECT) {
                    KeyValue("Hesab", s.mail.email.ifEmpty { "konfiqurasiya edilməyib" })
                    KeyValue("Server", "${s.mail.host}:${s.mail.port} (SSL/TLS)")
                    KeyValue("Qovluq", s.mail.mailbox)
                    KeyValue("Şifrə", if (s.mailPasswordSet) "Keystore ilə şifrələnib ✓" else "daxil edilməyib")
                } else {
                    KeyValue("Server", s.serverUrl.ifEmpty { "—" })
                    val a = remote?.account
                    KeyValue("Mail hesabı", a?.email ?: "—")
                    KeyValue("Server statusu", when (a?.lastStatus) { "OK" -> "İşləyir ✓"; "ERROR" -> "Xəta"; null -> "—"; else -> "Yoxlanılmayıb" },
                        if (a?.lastStatus == "ERROR") AppTheme.extra.danger else AppTheme.extra.positive)
                    a?.lastError?.let { KeyValue("Son xəta", it, AppTheme.extra.danger) }
                    a?.lastSyncAt?.let { iso -> Fmt.parseIso(iso)?.let { KeyValue("Serverdə son yoxlama", Fmt.dateTime(it)) } }
                    remote?.let { KeyValue("Server OCR", if (it.ocrEnabled) "aktiv" else "deaktiv") }
                }
                KeyValue("Son sinxronizasiya", Fmt.relative(s.lastSyncAt))
                KeyValue("Nəticə", when (s.lastSyncStatus) { "OK" -> "Uğurlu"; "ERROR" -> "Xəta"; else -> "—" },
                    if (s.lastSyncStatus == "ERROR") AppTheme.extra.danger else AppTheme.extra.positive)
                s.lastSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.syncNow() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Sync, null); Spacer(Modifier.width(6.dp)); Text(if (syncing) "Gedir…" else "Sinxronlaşdır")
                    }
                    OutlinedButton(onClick = { vm.testConnection() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.NetworkCheck, null); Spacer(Modifier.width(6.dp)); Text("Yoxla")
                    }
                }
                TextButton(onClick = onOpenSettings) { Text("Bağlantı ayarlarını dəyiş") }
            }
        }
        test?.let { t ->
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (t.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                            tint = if (t.ok) AppTheme.extra.positive else AppTheme.extra.danger)
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.ok) "Bağlantı uğurludur" else "Bağlantı alınmadı", style = MaterialTheme.typography.titleSmall)
                    }
                    if (t.ok) Text("Qutuda ${t.messages} məktub (${t.unread} oxunmamış) · ${t.latencyMs} ms", style = MaterialTheme.typography.bodySmall)
                    t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.danger) }
                }
            }
        }
        if (s.mode == SyncMode.DIRECT && counts.isNotEmpty()) {
            item {
                AppCard {
                    Text("Emal olunmuş məktublar", style = MaterialTheme.typography.titleMedium)
                    counts.forEach {
                        KeyValue(
                            when (it.result) {
                                "PAYMENT" -> "Ödəniş tapılıb"; "PENDING" -> "Gözləyənə düşüb"; "IGNORED" -> "Ödəniş deyil / filtr"
                                "DUPLICATE" -> "Təkrar"; "ERROR" -> "Xəta"; else -> it.result
                            },
                            it.cnt.toString(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunsTab(vm: AdminViewModel) {
    val runs by vm.runs.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
        if (runs.isEmpty()) item { EmptyState(Icons.Outlined.Sync, "Tarixçə boşdur", "Hələ sinxronizasiya aparılmayıb.") }
        items(runs, key = { it.id }) { r ->
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (r.status) { "SUCCESS" -> Icons.Outlined.CheckCircle; "RUNNING" -> Icons.Outlined.Sync; else -> Icons.Outlined.ErrorOutline },
                        null, Modifier.size(18.dp),
                        tint = when (r.status) { "SUCCESS" -> AppTheme.extra.positive; "RUNNING" -> AppTheme.extra.warning; else -> AppTheme.extra.danger },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.dateTime(r.startedAt), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (r.trigger.contains("MANUAL")) "Əl ilə" else "Avtomatik", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Oxundu ${r.fetched} · yeni ${r.created} · gözləyən ${r.pending} · təkrar ${r.duplicates} · xəta ${r.errors}",
                    style = MaterialTheme.typography.bodySmall)
                r.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ThinDivider()
        }
    }
}

@Composable
private fun AuditTab(vm: AdminViewModel, onSelect: (Long) -> Unit) {
    val items by vm.audit.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
        item {
            Text("Bütün dəyişikliklər daimi saxlanılır. Ləğv edilmiş ödənişlər silinmir.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        if (items.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.ManageSearch, "Audit boşdur", "Hələ heç bir əməliyyat qeydə alınmayıb.") }
        items(items, key = { it.id }) { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row {
                    Text(
                        when (a.action) {
                            "CREATE" -> "Yaradıldı"; "UPDATE" -> "Redaktə"; "CONFIRM" -> "Təsdiq"; "CANCEL" -> "Ləğv"
                            "LOGIN" -> "Giriş"; "LOGIN_FAILED" -> "Uğursuz giriş"; "MAIL_CONFIG" -> "Mail ayarı"; else -> a.action
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = if (a.action == "CANCEL" || a.action == "LOGIN_FAILED") AppTheme.extra.danger else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    a.paymentId?.let { id -> TextButton(onClick = { onSelect(id) }) { Text("#$id") } }
                }
                Text("${a.actor} · ${Fmt.dateTime(a.createdAt)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.details?.let { Text(it.take(300), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            }
            ThinDivider()
        }
    }
}
