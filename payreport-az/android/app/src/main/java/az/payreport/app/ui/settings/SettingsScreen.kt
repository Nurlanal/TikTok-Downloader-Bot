package az.payreport.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.AppContainer
import az.payreport.app.BuildConfig
import az.payreport.app.core.security.SecureStore
import az.payreport.app.data.remote.ApiClient
import az.payreport.app.data.settings.MailConfig
import az.payreport.app.data.settings.SettingsState
import az.payreport.app.data.settings.SyncMode
import az.payreport.app.ui.common.AdminSession
import az.payreport.app.ui.common.appViewModel
import az.payreport.app.ui.components.AppCard
import az.payreport.app.ui.components.KeyValue
import az.payreport.app.ui.theme.AppTheme
import az.payreport.app.work.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettingsViewModel(private val c: AppContainer, private val app: android.content.Context) : ViewModel() {
    val state: StateFlow<SettingsState> = c.settings.state
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    private fun reschedule() {
        val s = c.settings.current
        SyncWorker.schedule(app, s.syncIntervalMinutes, s.autoSync && (s.mailPasswordSet || s.serverLoggedIn))
    }

    private fun act(block: suspend () -> String) = viewModelScope.launch {
        busy.value = true
        message.value = try { block() } catch (e: Exception) { e.message ?: "Xəta baş verdi" }
        busy.value = false
    }

    fun setMode(mode: SyncMode) = act {
        c.syncManager.clearLocalData()
        c.settings.setMode(mode)
        reschedule()
        "İş rejimi dəyişdirildi: ${if (mode == SyncMode.DIRECT) "Cihazda birbaşa" else "Server"}"
    }

    fun saveMail(cfg: MailConfig, password: String) = act {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cfg.email.trim()).matches()) error("Email ünvanı düzgün deyil")
        val old = c.settings.current.mail
        c.settings.setMail(cfg)
        if (old.email != cfg.email.trim() || old.mailbox != cfg.mailbox.trim() || old.host != cfg.host.trim()) c.settings.resetImapCursor()
        if (password.isNotEmpty()) {
            c.secureStore.putString(SecureStore.MAIL_PASSWORD, password)
            c.settings.setMailPasswordSet(true)
        }
        reschedule()
        "Mail ayarları saxlanıldı (şifrə Android Keystore ilə şifrələnib)"
    }

    fun testMail(cfg: MailConfig, password: String) = act {
        val r = c.directMail.testConnection(cfg, password.ifEmpty { null })
        if (r.ok) "✓ Bağlantı uğurludur: ${r.messages} məktub, ${r.unread} oxunmamış (${r.latencyMs} ms)" else "✗ ${r.error}"
    }

    fun forgetMailPassword() = act {
        c.secureStore.putString(SecureStore.MAIL_PASSWORD, null)
        c.settings.setMailPasswordSet(false)
        reschedule()
        "Mail şifrəsi cihazdan silindi"
    }

    fun serverLogin(url: String, user: String, password: String) = act {
        ApiClient.validateBaseUrl(url)?.let { error(it) }
        val res = c.api.login(url.trim().trimEnd('/'), user.trim(), password, "${Build.MANUFACTURER} ${Build.MODEL}")
        c.secureStore.putString(SecureStore.API_TOKEN, res.token)
        c.settings.setServer(url, user, true)
        reschedule()
        "Serverə daxil oldunuz. Token cihazda şifrələnmiş saxlanılır."
    }

    fun serverLogout() = act {
        c.api.logout()
        c.secureStore.putString(SecureStore.API_TOKEN, null)
        c.settings.setServer(c.settings.current.serverUrl, c.settings.current.serverUser, false)
        reschedule()
        "Serverdən çıxış edildi"
    }

    private fun mailJson(cfg: MailConfig, password: String) = buildJsonObject {
        put("email", cfg.email.trim()); put("host", cfg.host.trim()); put("port", cfg.port); put("mailbox", cfg.mailbox.trim())
        put("senderFilter", cfg.senderFilter); put("subjectFilter", cfg.subjectFilter); put("initialDays", cfg.initialDays)
        if (password.isNotEmpty()) put("password", password)
    }

    fun pushMailToServer(cfg: MailConfig, password: String) = act {
        val a = c.api.putMailConfig(mailJson(cfg, password))
        "Serverdə mail hesabı yeniləndi: ${a.email} (şifrə serverdə AES-256 ilə şifrələnir)"
    }

    fun testServerMail(cfg: MailConfig, password: String) = act {
        val r = c.api.testMail(mailJson(cfg, password))
        if (r.ok) "✓ Server Mail.ru-ya qoşuldu: ${r.messages} məktub (${r.latencyMs} ms)" else "✗ ${r.error}"
    }

    fun setSync(interval: Int, auto: Boolean, notifications: Boolean) {
        c.settings.setSync(interval, auto, notifications)
        reschedule()
    }

    fun setPin(pin: String?) = act {
        if (pin != null && pin.length < 4) error("PIN ən azı 4 rəqəm olmalıdır")
        c.secureStore.setPin(pin)
        c.settings.setPinSet(pin != null)
        AdminSession.unlocked = pin == null
        if (pin == null) "Admin PIN silindi" else "Admin PIN təyin edildi"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)?) {
    val ctx = LocalContext.current.applicationContext
    val vm = appViewModel { SettingsViewModel(it, ctx) }
    val s by vm.state.collectAsState()
    val msg by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    var confirmMode by remember { mutableStateOf<SyncMode?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ayarlar") },
            navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("‹ Geri") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (msg != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg!!, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.message.value = null }) { Text("OK") }
                    }
                }
            }
            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())

            AppCard {
                Text("İş rejimi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = s.mode == SyncMode.DIRECT, onClick = { if (s.mode != SyncMode.DIRECT) confirmMode = SyncMode.DIRECT },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                    ) { Text("Cihazda") }
                    SegmentedButton(
                        selected = s.mode == SyncMode.SERVER, onClick = { if (s.mode != SyncMode.SERVER) confirmMode = SyncMode.SERVER },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Outlined.Cloud, null) },
                    ) { Text("Server") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.mode == SyncMode.DIRECT)
                        "Tətbiq Mail.ru-ya özü qoşulur (imap.mail.ru:993, SSL/TLS). Server tələb olunmur; məlumatlar cihazda şifrələnmiş bazada saxlanılır."
                    else
                        "Mail.ru-nu 24/7 backend server oxuyur; tətbiq məlumatları HTTPS API ilə alır. Bir neçə cihaz üçün tövsiyə olunur.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MailSection(s, vm)
            if (s.mode == SyncMode.SERVER) ServerSection(s, vm)
            SyncSection(s, vm)
            SecuritySection(s, vm)

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null); Spacer(Modifier.width(8.dp))
                    Text("Haqqında", style = MaterialTheme.typography.titleMedium)
                }
                KeyValue("Versiya", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValue("Paket", BuildConfig.APPLICATION_ID)
                KeyValue("Baza", "SQLCipher AES-256 (açar Keystore-da)")
                KeyValue("Valyuta / vaxt", "AZN / Bakı (UTC+4)")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmMode?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmMode = null },
            title = { Text("İş rejimini dəyiş") },
            text = {
                Text(
                    "Rejim dəyişəndə cihazdakı lokal məlumatlar (ödənişlər, audit, tarixçə) təmizlənir. " +
                        "Server rejimində məlumatlar serverdən yenidən yüklənir. Davam edilsin?"
                )
            },
            confirmButton = { TextButton(onClick = { confirmMode = null; vm.setMode(m) }) { Text("Dəyiş") } },
            dismissButton = { TextButton(onClick = { confirmMode = null }) { Text("Ləğv") } },
        )
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, placeholder: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MailSection(s: SettingsState, vm: SettingsViewModel) {
    var email by remember(s.mail) { mutableStateOf(s.mail.email) }
    var password by remember { mutableStateOf("") }
    var host by remember(s.mail) { mutableStateOf(s.mail.host) }
    var port by remember(s.mail) { mutableStateOf(s.mail.port.toString()) }
    var mailbox by remember(s.mail) { mutableStateOf(s.mail.mailbox) }
    var sender by remember(s.mail) { mutableStateOf(s.mail.senderFilter) }
    var subject by remember(s.mail) { mutableStateOf(s.mail.subjectFilter) }
    var days by remember(s.mail) { mutableStateOf(s.mail.initialDays.toString()) }
    var advanced by remember { mutableStateOf(false) }
    val cfg = MailConfig(email, host, port.toIntOrNull() ?: 993, mailbox, sender, subject, days.toIntOrNull() ?: 30)
    val server = s.mode == SyncMode.SERVER

    AppCard {
        Text(if (server) "Mail.ru hesabı (serverdə)" else "Mail.ru hesabı", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Surface(color = AppTheme.extra.warningContainer, shape = MaterialTheme.shapes.small) {
            Text(
                "Mail.ru → Ayarlar → Təhlükəsizlik → \"Xarici tətbiqlər üçün parollar\" bölməsində ayrıca parol yaradın və onu buraya daxil edin. " +
                    "Əsas hesab şifrənizi istifadə etməyin.",
                Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, placeholder = { Text("hesab@mail.ru") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        PasswordField(password, { password = it }, "Tətbiq parolu",
            if (!server && s.mailPasswordSet) "•••••• (saxlanılıb — dəyişmək üçün yazın)" else "xarici tətbiq parolu")
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Əlavə ayarları gizlət" else "Əlavə ayarlar (server, filtrlər)") }
        if (advanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("IMAP server") }, singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(mailbox, { mailbox = it }, label = { Text("Qovluq") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sender, { sender = it }, label = { Text("Göndərən filtri (vergüllə)") },
                placeholder = { Text("bank.az, noreply@pay.az") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subject, { subject = it }, label = { Text("Mövzu filtri (vergüllə)") },
                placeholder = { Text("ödəniş, payment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("İlk sinxronizasiya: son neçə gün") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Text("SSL/TLS həmişə aktivdir. Məktublar yalnız oxunur — dəyişdirilmir və silinmir.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (server) {
                Button(onClick = { vm.pushMailToServer(cfg, password); password = "" }, enabled = s.serverLoggedIn, modifier = Modifier.weight(1f)) {
                    Text("Serverə saxla")
                }
                OutlinedButton(onClick = { vm.testServerMail(cfg, password) }, enabled = s.serverLoggedIn, modifier = Modifier.weight(1f)) {
                    Text("Yoxla")
                }
            } else {
                Button(onClick = { vm.saveMail(cfg, password); password = "" }, modifier = Modifier.weight(1f)) { Text("Saxla") }
                OutlinedButton(onClick = { vm.testMail(cfg, password) }, modifier = Modifier.weight(1f)) { Text("Bağlantını yoxla") }
            }
        }
        if (!server && s.mailPasswordSet) {
            TextButton(onClick = { vm.forgetMailPassword() }) { Text("Saxlanmış şifrəni sil", color = AppTheme.extra.danger) }
        }
    }
}

@Composable
private fun ServerSection(s: SettingsState, vm: SettingsViewModel) {
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl.ifEmpty { "https://" }) }
    var user by remember(s.serverUser) { mutableStateOf(s.serverUser) }
    var pass by remember { mutableStateOf("") }
    AppCard {
        Text("Backend server", style = MaterialTheme.typography.titleMedium)
        if (s.serverLoggedIn) {
            KeyValue("Server", s.serverUrl)
            KeyValue("İstifadəçi", s.serverUser)
            KeyValue("Status", "Daxil olunub ✓", AppTheme.extra.positive)
            OutlinedButton(onClick = { vm.serverLogout() }) { Text("Çıxış") }
        } else {
            OutlinedTextField(url, { url = it }, label = { Text("Server ünvanı (HTTPS)") }, placeholder = { Text("https://api.sizin-domen.az") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("İstifadəçi adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PasswordField(pass, { pass = it }, "Şifrə", "admin şifrəsi")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.serverLogin(url, user, pass); pass = "" }, enabled = url.length > 8 && user.isNotBlank() && pass.isNotBlank()) {
                Text("Daxil ol")
            }
        }
    }
}

@Composable
private fun SyncSection(s: SettingsState, vm: SettingsViewModel) {
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    AppCard {
        Text("Avtomatik sinxronizasiya", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fon rejimində yoxla", Modifier.weight(1f))
            Switch(s.autoSync, { vm.setSync(s.syncIntervalMinutes, it, s.notifications) })
        }
        Text("Interval", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15 to "15 dəq", 30 to "30 dəq", 60 to "1 saat", 180 to "3 saat").forEach { (m, l) ->
                FilterChip(s.syncIntervalMinutes == m, { vm.setSync(m, s.autoSync, s.notifications) }, { Text(l) }, enabled = s.autoSync)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yeni ödəniş bildirişləri", Modifier.weight(1f))
            Switch(s.notifications, {
                vm.setSync(s.syncIntervalMinutes, s.autoSync, it)
                if (it && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        Text("Android fon tapşırıqlarını ən tez 15 dəqiqədən bir icra edir. Dərhal yoxlamaq üçün panelde \"Yenilə\" düyməsindən istifadə edin.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecuritySection(s: SettingsState, vm: SettingsViewModel) {
    var pin by remember { mutableStateOf("") }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(8.dp))
            Text("Təhlükəsizlik", style = MaterialTheme.typography.titleMedium)
        }
        Text("Admin paneli (ödəniş əlavə/redaktə/ləğv) PIN ilə qorunur. PIN PBKDF2 heşi kimi şifrələnmiş saxlanılır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text(if (s.pinSet) "Yeni PIN" else "Admin PIN (4-8 rəqəm)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setPin(pin); pin = "" }, enabled = pin.length >= 4) { Text(if (s.pinSet) "PIN-i dəyiş" else "PIN təyin et") }
            if (s.pinSet) OutlinedButton(onClick = { vm.setPin(null) }) { Text("PIN-i sil") }
        }
    }
}
