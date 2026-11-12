package az.siqaret.anbar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.siqaret.anbar.data.HistoryEntry
import az.siqaret.anbar.data.InventoryRepository
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryStats
import az.siqaret.anbar.util.PdfExporter
import az.siqaret.anbar.util.Period
import az.siqaret.anbar.util.ReportData
import az.siqaret.anbar.util.XlsxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

enum class SortOption(val label: String) {
    NAME_ASC("Ad: A → Z"),
    NAME_DESC("Ad: Z → A"),
    PRICE_ASC("Qiymət: ucuzdan bahaya"),
    PRICE_DESC("Qiymət: bahadan ucuza"),
    AMOUNT_DESC("Ümumi məbləğ: çoxdan aza"),
    STOCK_DESC("Blok sayı: çoxdan aza"),
}

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val undoDelete: Product? = null,
    val shareUri: Uri? = null,
    val shareMime: String? = null,
)

class InventoryViewModel(
    private val app: SiqaretApp,
    private val repo: InventoryRepository,
    private val settings: ThemeSettings,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.mode
    fun setTheme(mode: ThemeMode) = settings.set(mode)

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOption.NAME_ASC)
    val reportPeriod = MutableStateFlow(Period.WEEK)
    val historyPeriod = MutableStateFlow(Period.ALL)

    private val collator: Collator = Collator.getInstance(Fmt.AZ).apply { strength = Collator.SECONDARY }

    val products: StateFlow<List<Product>> =
        repo.products.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntry>> =
        repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<InventoryStats> =
        products.map { InventoryStats.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, InventoryStats())

    val visibleProducts: StateFlow<List<Product>> =
        combine(products, query, sort) { list, q, s ->
            val filtered = if (q.isBlank()) list else list.filter { it.name.contains(q.trim(), ignoreCase = true) }
            when (s) {
                SortOption.NAME_ASC -> filtered.sortedWith { a, b -> collator.compare(a.name, b.name) }
                SortOption.NAME_DESC -> filtered.sortedWith { a, b -> collator.compare(b.name, a.name) }
                SortOption.PRICE_ASC -> filtered.sortedBy { it.blockPrice }
                SortOption.PRICE_DESC -> filtered.sortedByDescending { it.blockPrice }
                SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.totalAmount }
                SortOption.STOCK_DESC -> filtered.sortedByDescending { it.blockCount }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun say(text: String) { _messages.trySend(UiMessage(text)) }

    suspend fun getProduct(id: Long): Product? = repo.getProduct(id)

    /** Doğrulama + yadda saxlama. Xəta olarsa mətn qaytarılır, uğurlu olarsa null. */
    fun saveProduct(
        id: Long?, name: String, boxPrice: Double?, blockPrice: Double?, blocks: Int?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val n = name.trim().replace(Regex("\\s+"), " ")
            val error = when {
                n.isEmpty() -> "Siqaretin adını daxil edin"
                n.length > 60 -> "Ad çox uzundur (maks. 60 simvol)"
                blockPrice == null || blockPrice <= 0.0 -> "Bir blokun qiymətini daxil edin"
                blocks == null -> "Blok sayını daxil edin"
                repo.nameExists(n, id ?: 0L) -> "\"$n\" adlı məhsul artıq mövcuddur"
                else -> null
            }
            if (error != null) { onResult(error); return@launch }
            val box = boxPrice ?: 0.0
            if (id == null) {
                repo.addProduct(n, box, blockPrice!!, blocks!!)
                say("\"$n\" əlavə edildi")
            } else {
                repo.updateProduct(id, n, box, blockPrice!!, blocks!!)
                say("Dəyişikliklər yadda saxlanıldı")
            }
            onResult(null)
        }
    }

    fun deleteProduct(p: Product) {
        viewModelScope.launch {
            repo.deleteProduct(p)
            _messages.send(UiMessage("\"${p.name}\" silindi", actionLabel = "Geri qaytar", undoDelete = p))
        }
    }

    fun restoreProduct(p: Product) {
        viewModelScope.launch { repo.restoreProduct(p); say("\"${p.name}\" bərpa edildi") }
    }

    fun adjustStock(p: Product, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val result = repo.adjustStock(p.id, delta)
            if (result == null) say("Stokda kifayət qədər blok yoxdur")
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearHistory(); say("Tarixçə təmizləndi") }
    }

    private suspend fun buildReport(period: Period): ReportData {
        val now = System.currentTimeMillis()
        return ReportData(
            products = repo.allProducts().sortedWith { a, b -> collator.compare(a.name, b.name) },
            history = repo.historySince(period.from(now)),
            period = period,
            generatedAt = now,
        )
    }

    fun export(uri: Uri, pdf: Boolean) {
        val period = reportPeriod.value
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val data = buildReport(period)
                    app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        if (pdf) PdfExporter.write(data, out) else XlsxExporter.write(data, out)
                    } ?: error("Fayl açıla bilmədi")
                }
            }
            result.onSuccess {
                _messages.send(
                    UiMessage(
                        text = if (pdf) "PDF hesabat saxlanıldı" else "Excel hesabat saxlanıldı",
                        actionLabel = "Paylaş",
                        shareUri = uri,
                        shareMime = if (pdf) PdfExporter.MIME else XlsxExporter.MIME,
                    )
                )
            }.onFailure { say("İxrac alınmadı: ${it.message ?: "naməlum xəta"}") }
        }
    }

    fun suggestedFileName(pdf: Boolean): String =
        "Siqaret_hesabat_${Fmt.fileStamp(System.currentTimeMillis())}.${if (pdf) "pdf" else "xlsx"}"

    companion object {
        fun factory(app: SiqaretApp) = viewModelFactory {
            initializer { InventoryViewModel(app, app.repository, app.settings) }
        }
    }
}
