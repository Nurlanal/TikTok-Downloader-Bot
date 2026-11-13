package az.sontarix.market.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import az.sontarix.market.appContainer
import az.sontarix.market.data.AppSettings
import az.sontarix.market.data.Category
import az.sontarix.market.data.Product
import az.sontarix.market.data.Stats
import az.sontarix.market.notify.ExpiryChecker
import az.sontarix.market.notify.NotificationScheduler
import az.sontarix.market.transfer.DuplicateAction
import az.sontarix.market.transfer.ExportFormat
import az.sontarix.market.transfer.ExportScope
import az.sontarix.market.transfer.ImportResult
import az.sontarix.market.transfer.ImportSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Bütün ekranlar üçün ortaq ViewModel (Activity səviyyəsində). */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    val repo = c.repository
    private val transfer = c.transfer

    val settings: StateFlow<AppSettings> = c.settings.settings

    val products: StateFlow<List<Product>> = repo.products
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loaded = MutableStateFlow(false)

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Stats> = combine(products, settings) { list, s ->
        ExpiryChecker.computeStats(list, s.warningDays)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    val history = repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentNotifications = repo.recentNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    fun toast(msg: String) { _messages.tryEmit(msg) }

    init {
        viewModelScope.launch {
            repo.products.collect { loaded.value = true }
        }
    }

    // ── Məhsullar ──
    fun delete(product: Product) = viewModelScope.launch {
        repo.delete(product)
        toast("\"${product.name}\" silindi")
    }

    fun deleteMany(list: List<Product>) = viewModelScope.launch {
        repo.deleteMany(list)
        toast("${list.size} məhsul silindi")
    }

    fun updateQuantity(product: Product, quantity: Int) = viewModelScope.launch {
        repo.save(product.copy(quantity = quantity.coerceAtLeast(0)))
    }

    // ── Kateqoriyalar ──
    fun addCategory(name: String) = viewModelScope.launch {
        if (repo.addCategory(name)) toast("Kateqoriya əlavə edildi") else toast("Bu kateqoriya artıq mövcuddur")
    }

    fun deleteCategory(cat: Category) = viewModelScope.launch { repo.deleteCategory(cat) }

    // ── Parametrlər ──
    fun updateSettings(reschedule: Boolean = true, transform: (AppSettings) -> AppSettings) {
        c.settings.update(transform)
        if (reschedule) NotificationScheduler.reschedule(getApplication())
    }

    fun clearHistory() = viewModelScope.launch { repo.clearHistory(); toast("Tarixçə təmizləndi") }
    fun clearNotificationHistory() = viewModelScope.launch { repo.clearNotificationHistory(); toast("Bildiriş tarixçəsi təmizləndi") }
    fun checkNow() { NotificationScheduler.runCheckNow(getApplication()); toast("Yoxlama başladıldı") }

    // ── İxrac ──
    /** Məhsullar ekranında seçilib ixraca göndərilən məhsullar. */
    val pendingExportSelection = MutableStateFlow<List<Long>?>(null)
    val busy = MutableStateFlow(false)
    private val _lastExport = MutableStateFlow<Pair<File, ExportFormat>?>(null)
    val lastExport = _lastExport.asStateFlow()

    fun export(scope: ExportScope, format: ExportFormat, label: String, onDone: (File) -> Unit) = viewModelScope.launch {
        busy.value = true
        try {
            val f = transfer.export(scope, format, label)
            _lastExport.value = f to format
            onDone(f)
        } catch (e: Exception) {
            toast(e.message ?: "İxrac alınmadı")
        } finally { busy.value = false }
    }

    fun shareIntent(file: File, format: ExportFormat) = transfer.shareIntent(file, format)

    fun saveCopy(file: File, uri: Uri) = viewModelScope.launch {
        runCatching { transfer.saveCopy(file, uri) }
            .onSuccess { toast("Fayl yadda saxlanıldı") }
            .onFailure { toast(it.message ?: "Yadda saxlamaq alınmadı") }
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        busy.value = true
        runCatching { transfer.backupTo(uri) }
            .onSuccess { toast("Ehtiyat nüsxə yaradıldı ($it məhsul)") }
            .onFailure { toast(it.message ?: "Ehtiyat nüsxə alınmadı") }
        busy.value = false
    }

    // ── İdxal ──
    private val _importSession = MutableStateFlow<ImportSession?>(null)
    val importSession = _importSession.asStateFlow()
    val restoreMode = MutableStateFlow(false)

    fun prepareImport(uri: Uri, restore: Boolean, onReady: () -> Unit) = viewModelScope.launch {
        busy.value = true
        _importSession.value?.let { transfer.discard(it) }
        try {
            val s = transfer.prepareImport(uri)
            if (restore && !s.isBackup) toast("Diqqət: bu fayl tam ehtiyat nüsxəsi deyil, adi ixrac faylıdır")
            restoreMode.value = restore
            _importSession.value = s
            onReady()
        } catch (e: Exception) {
            toast(e.message ?: "Fayl qəbul edilmədi")
        } finally { busy.value = false }
    }

    fun commitImport(selection: Map<Int, DuplicateAction>, onDone: (ImportResult) -> Unit) = viewModelScope.launch {
        val s = _importSession.value ?: return@launch
        busy.value = true
        try {
            val r = transfer.commitImport(s, selection)
            _importSession.value = null
            toast("İdxal tamamlandı: ${r.added} əlavə, ${r.updated} yeniləndi")
            onDone(r)
        } catch (e: Exception) {
            _importSession.value = null
            toast(e.message ?: "İdxal alınmadı")
        } finally { busy.value = false }
    }

    fun restore(onDone: () -> Unit) = viewModelScope.launch {
        val s = _importSession.value ?: return@launch
        busy.value = true
        try {
            val n = transfer.restoreBackup(s)
            toast("Bərpa tamamlandı: $n məhsul")
            onDone()
        } catch (e: Exception) {
            toast(e.message ?: "Bərpa alınmadı")
        } finally {
            _importSession.value = null
            busy.value = false
        }
    }

    fun cancelImport() {
        _importSession.value?.let { transfer.discard(it) }
        _importSession.value = null
    }

    override fun onCleared() {
        _importSession.value?.let { transfer.discard(it) }
    }
}
