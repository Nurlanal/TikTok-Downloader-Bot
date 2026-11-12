package az.rafiqmarket.core.ui.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.data.AuthRepository
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.NotificationDto
import az.rafiqmarket.core.network.dto.Role
import az.rafiqmarket.core.network.dto.UserDto
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.realtime.RealtimeEvent
import az.rafiqmarket.core.session.SessionStore
import az.rafiqmarket.core.session.ThemeMode
import az.rafiqmarket.core.ui.components.*
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.ago
import az.rafiqmarket.core.util.prettyPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val store: SessionStore,
) : ViewModel() {
    val user: StateFlow<UserDto?> = store.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val theme: StateFlow<ThemeMode> = store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val message = MutableStateFlow<String?>(null)

    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }
    fun saveName(name: String) = viewModelScope.launch {
        apiCall { auth.updateName(name.trim()) }
            .onSuccess { message.value = "Ad yeniləndi" }
            .onFailure { message.value = it.message }
    }
    fun logout() = viewModelScope.launch { auth.logout() }
}

fun roleLabel(r: Role) = when (r) { Role.CUSTOMER -> "Müştəri"; Role.COURIER -> "Kuryer"; Role.ADMIN -> "Admin" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    extra: @Composable ColumnScope.() -> Unit = {},
    onNotifications: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    var editName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    MessageEffect(msg, snack) { vm.message.value = null }

    Scaffold(topBar = { TopAppBar(title = { Text("Profil") }) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon3D("👤", MaterialTheme.colorScheme.primary, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.name?.ifBlank { "Adsız" } ?: "", style = MaterialTheme.typography.titleLarge)
                        Text(user?.phone?.let(::prettyPhone) ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        user?.let { AssistChip(onClick = {}, label = { Text(roleLabel(it.roleEnum)) }) }
                    }
                    IconButton(onClick = { editName = true }) { Icon(Icons.Outlined.Edit, "Adı dəyiş") }
                }
            }
            extra()
            ListItem(
                headlineContent = { Text("Bildirişlər") },
                leadingContent = { Icon(Icons.Outlined.Notifications, null) },
                modifier = Modifier.clickable(onClick = onNotifications),
            )
            Text("Görünüş", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Açıq", ThemeMode.DARK to "Tünd").forEachIndexed { i, (m, l) ->
                    SegmentedButton(selected = theme == m, onClick = { vm.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
            ListItem(
                headlineContent = { Text("Market ilə əlaqə") },
                supportingContent = { Text("Sualınız varsa zəng edin") },
                leadingContent = { Icon(Icons.Outlined.SupportAgent, null) },
            )
            OutlinedButton(
                onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RafiqColors.Red),
            ) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Hesabdan çıx") }
            Text("Rafiq Market • Xıdırlı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
    if (editName) {
        var name by remember { mutableStateOf(user?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Adınız") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.saveName(name); editName = false }, enabled = name.trim().length >= 2) { Text("Yadda saxla") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("İmtina") } },
        )
    }
    if (confirmLogout) ConfirmDialog("Çıxış", "Hesabdan çıxmaq istəyirsiniz?", "Çıx", vm::logout) { confirmLogout = false }
}

// ---------------- Bildirişlər ----------------
@HiltViewModel
class NotificationsViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : ViewModel() {
    val items = MutableStateFlow<List<NotificationDto>?>(null)
    val error = MutableStateFlow<String?>(null)

    init {
        load()
        viewModelScope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.Notification>().collect { e ->
                items.value = listOf(e.notification) + (items.value ?: emptyList())
            }
        }
    }

    fun load() = viewModelScope.launch {
        error.value = null
        apiCall { api.notifications() }
            .onSuccess { items.value = it; runCatching { api.markNotificationsRead() } }
            .onFailure { error.value = it.message }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenOrder: ((Long) -> Unit)? = null, vm: NotificationsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Bildirişlər") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                error != null && items == null -> ErrorBox(error!!, vm::load)
                items == null -> LoadingBox()
                items!!.isEmpty() -> EmptyState("🔔", "Bildiriş yoxdur")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items!!, key = { it.id }) { n ->
                        Card(
                            Modifier.fillMaxWidth().clickable(enabled = n.orderId != null && onOpenOrder != null) { n.orderId?.let { onOpenOrder?.invoke(it) } },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (n.isRead) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(n.title, fontWeight = FontWeight.SemiBold)
                                Text(n.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ago(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Telefon zəngi */
fun dial(context: android.content.Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Google Maps naviqasiyası (marşrut) */
fun navigateTo(context: android.content.Context, lat: Double, lng: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d")).setPackage("com.google.android.apps.maps")
    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving"))
    runCatching { context.startActivity(nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
