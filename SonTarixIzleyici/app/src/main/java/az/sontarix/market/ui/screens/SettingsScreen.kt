package az.sontarix.market.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.data.AppSettings
import az.sontarix.market.data.Category
import az.sontarix.market.data.ThemeMode
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.ConfirmDialog
import az.sontarix.market.ui.components.Icon3D
import az.sontarix.market.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onCategories: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    var addDay by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { canNotify = NotificationHelper.canNotify(context); batteryOk = isIgnoringBattery(context) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = NotificationHelper.canNotify(context)
        if (!granted) openNotificationSettings(context)
    }

    fun update(reschedule: Boolean = true, f: (AppSettings) -> AppSettings) = vm.updateSettings(reschedule, f)

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Parametrlər", style = MaterialTheme.typography.titleLarge)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Group("Görünüş", Icons.Filled.Palette, StatusColors.Violet) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(selected = s.themeMode == m, onClick = { update(false) { it.copy(themeMode = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size), label = { Text(m.label) })
                        }
                    }
                }
            }
            item {
                Group("Bildiriş parametrləri", Icons.Filled.Notifications, StatusColors.Indigo) {
                    if (!canNotify) {
                        SettingRow(Icons.Filled.NotificationsActive, "Bildiriş icazəsi verilməyib", "Toxunaraq icazə verin", tint = StatusColors.Red, onClick = {
                            val needsRuntime = Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (needsRuntime) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
                        })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SwitchRow(Icons.Filled.NotificationsActive, "Bildirişlər", "Son istifadə tarixi xəbərdarlıqları", s.notificationsEnabled) { v ->
                        update { it.copy(notificationsEnabled = v) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Filled.Schedule, "Bildiriş saatı", "Hər gün %02d:%02d".format(s.notifyHour, s.notifyMinute), onClick = { showTime = true })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Xəbərdarlıq günləri", style = MaterialTheme.typography.bodyLarge)
                                Text("Tarixdən neçə gün əvvəl bildiriş gəlsin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { addDay = true }) { Icon(Icons.Filled.Add, "Gün əlavə et") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 38.dp)) {
                            s.thresholds.forEach { d ->
                                InputChip(
                                    selected = true, onClick = {},
                                    label = { Text(if (d == 0) "Bitdiyi gün" else "$d gün") },
                                    trailingIcon = {
                                        Icon(Icons.Filled.Close, "Sil", Modifier.size(18.dp).clickable {
                                            if (s.thresholds.size > 1) update { it.copy(thresholds = it.thresholds - d) }
                                            else vm.toast("Ən azı bir xəbərdarlıq günü qalmalıdır")
                                        })
                                    }
                                )
                            }
                        }
                        TextButton(onClick = { update { it.copy(thresholds = AppSettings.DEFAULT_THRESHOLDS) } }, modifier = Modifier.padding(start = 30.dp)) {
                            Text("Standart (30, 15, 7, 3, 1, 0)")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(Icons.Filled.EventBusy, "Tarix keçdikdə xəbərdarlıq", "Müddəti bitmiş məhsul üçün bildiriş", s.notifyExpired) { v ->
                        update { it.copy(notifyExpired = v) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(Icons.AutoMirrored.Filled.VolumeUp, "Bildiriş səsi", "Səsli və ya səssiz bildiriş", s.soundEnabled) { v ->
                        update(false) { it.copy(soundEnabled = v) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(Icons.Filled.Summarize, "Gündəlik ümumi hesabat", "Hər gün seçilmiş saatda statistika", s.dailySummary) { v ->
                        update { it.copy(dailySummary = v) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (!batteryOk) {
                        SettingRow(Icons.Filled.BatterySaver, "Batareya optimallaşdırması", "Bildirişlərin vaxtında gəlməsi üçün məhdudiyyəti söndürün",
                            tint = StatusColors.Orange, onClick = { requestIgnoreBattery(context) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { NotificationHelper.showTest(context, s.soundEnabled); if (!canNotify) vm.toast("Bildiriş icazəsi yoxdur") },
                            modifier = Modifier.weight(1f)) { Text("Test bildirişi") }
                        FilledTonalButton(onClick = { vm.checkNow() }, modifier = Modifier.weight(1f)) { Text("İndi yoxla") }
                    }
                }
            }
            item {
                Group("Məlumatlar", Icons.Filled.Category, StatusColors.Teal) {
                    SettingRow(Icons.Filled.Category, "Kateqoriyalar", "${categories.size} kateqoriya — idarə et", onClick = onCategories)
                }
            }
            item {
                Group("Haqqında", Icons.Filled.Info, StatusColors.Sky) {
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
                    }
                    SettingRow(Icons.Filled.Info, "Son Tarix İzləyici", "Versiya $version • Bütün məlumatlar yalnız bu telefonda saxlanılır, internet tələb olunmur.")
                }
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = s.notifyHour, initialMinute = s.notifyMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Bildiriş saatı") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { update { it.copy(notifyHour = state.hour, notifyMinute = state.minute) }; showTime = false }) { Text("Yadda saxla") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Ləğv et") } }
        )
    }
    if (addDay) {
        var text by remember { mutableStateOf("") }
        val value = text.toIntOrNull()
        AlertDialog(
            onDismissRequest = { addDay = false },
            title = { Text("Xəbərdarlıq günü əlavə et") },
            text = {
                OutlinedTextField(text, { t -> text = t.filter { it.isDigit() }.take(3) }, label = { Text("Gün sayı (0–365)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = value != null && value in 0..365, onClick = {
                    update { it.copy(thresholds = it.thresholds + value!!) }; addDay = false
                }) { Text("Əlavə et") }
            },
            dismissButton = { TextButton(onClick = { addDay = false }) { Text("Ləğv et") } }
        )
    }
}

@Composable
fun CategoriesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Category?>(null) }
    val counts = remember(products) { products.groupingBy { it.category }.eachCount() }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text("Kateqoriyalar", style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it.take(80) }, label = { Text("Yeni kateqoriya") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { vm.addCategory(newName); newName = "" }, enabled = newName.isNotBlank(), modifier = Modifier.height(56.dp)) {
                Icon(Icons.Filled.Add, null)
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { c ->
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${counts[c.name] ?: 0} məhsul", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toDelete = c }) { Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    toDelete?.let { c ->
        ConfirmDialog("Kateqoriyanı sil", "\"${c.name}\" siyahıdan silinəcək. Bu kateqoriyadakı məhsullar silinmir.", confirmText = "Sil", destructive = true,
            onConfirm = { vm.deleteCategory(c); toDelete = null }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun Group(title: String, icon: ImageVector, color: Color, content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon3D(icon, color, 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, tint: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun isIgnoringBattery(context: android.content.Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

@android.annotation.SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
