package az.siqaret.anbar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.siqaret.anbar.data.Firm
import az.siqaret.anbar.data.HistoryEntry
import az.siqaret.anbar.data.Invoice
import az.siqaret.anbar.data.InvoiceItem
import az.siqaret.anbar.data.InventoryRepository
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.util.FirmSummary
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryCalc
import az.siqaret.anbar.util.InventoryStats
import az.siqaret.anbar.util.InvoiceSummary
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
    /** Ana səhifə firma filtri: null = hamısı, [NO_FIRM_FILTER] = firmasız, digər = firma id. */
    val firmFilter = MutableStateFlow<Long?>(null)
    val reportPeriod = MutableStateFlow(Period.WEEK)
    val historyPeriod = MutableStateFlow(Period.ALL)

    private val collator: Collator = Collator.getInstance(Fmt.AZ).apply { strength = Collator.SECONDARY }

    val products: StateFlow<List<Product>> =
        repo.products.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val firms: StateFlow<List<Firm>> =
        repo.firms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val firmSummaries: StateFlow<List<FirmSummary>> =
        combine(products, firms) { p, f -> FirmSummary.group(p, f) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val invoiceSummaries: StateFlow<List<InvoiceSummary>> =
        combine(repo.invoices, repo.invoiceItems, firms) { inv, items, f -> InvoiceSummary.build(inv, items, f) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntry>> =
        repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<InventoryStats> =
        products.map { InventoryStats.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, InventoryStats())

    val visibleProducts: StateFlow<List<Product>> =
        combine(products, query, sort, firmFilter, firms) { list, q, s, ff, fl ->
            val ids = fl.map { it.id }.toSet()
            val byFirm = when (ff) {
                null -> list
                NO_FIRM_FILTER -> list.filter { it.firmId == null || it.firmId !in ids }
                else -> list.filter { it.firmId == ff }
            }
            val filtered = if (q.isBlank()) byFirm else byFirm.filter { it.name.contains(q.trim(), ignoreCase = true) }
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
    suspend fun getFirm(id: Long): Firm? = repo.getFirm(id)

    /** Firma yarat / redaktə et. onResult(xəta, firmaId) */
    fun saveFirm(id: Long?, name: String, phone: String, note: String, onResult: (String?, Long?) -> Unit) {
        viewModelScope.launch {
            val n = name.trim().replace(Regex("\\s+"), " ")
            val error = when {
                n.isEmpty() -> "Firmanın adını daxil edin"
                n.length > 60 -> "Ad çox uzundur (maks. 60 simvol)"
                repo.firmNameExists(n, id ?: 0L) -> "\"$n\" adlı firma artıq mövcuddur"
                else -> null
            }
            if (error != null) { onResult(error, null); return@launch }
            val newId = if (id == null) {
                repo.addFirm(n, phone.trim(), note.trim()).also { say("\"$n\" firması əlavə edildi") }
            } else {
                val old = repo.getFirm(id) ?: run { onResult("Firma tapılmadı", null); return@launch }
                repo.updateFirm(old.copy(name = n, phone = phone.trim(), note = note.trim()))
                say("Firma yeniləndi")
                id
            }
            onResult(null, newId)
        }
    }

    /** Qaimə formasının bir sətri (ekrandan gələn xam mətn dəyərləri). */
    data class InvoiceLineInput(val name: String, val boxPrice: Double?, val blockPrice: Double?, val blocks: Int?) {
        val isBlank: Boolean get() = name.isBlank() && boxPrice == null && blockPrice == null && (blocks == null || blocks == 0)
    }

    suspend fun getInvoice(id: Long): Pair<Invoice, List<InvoiceItem>>? = repo.getInvoice(id)

    /** Qaiməni doğrula və yadda saxla. onResult(xəta, qaiməId) */
    fun saveInvoice(
        firmId: Long, number: String, date: Long, note: String, lines: List<InvoiceLineInput>,
        onResult: (String?, Long?) -> Unit,
    ) {
        viewModelScope.launch {
            val filled = lines.withIndex().filterNot { it.value.isBlank }
            if (filled.isEmpty()) { onResult("Ən azı bir siqaret sətri doldurun", null); return@launch }
            val seen = HashMap<String, Int>()
            val clean = ArrayList<InventoryRepository.InvoiceLine>()
            for ((i, l) in filled) {
                val row = i + 1
                val n = l.name.trim().replace(Regex("\\s+"), " ")
                val err = when {
                    n.isEmpty() -> "Sətir $row: siqaretin adını yazın"
                    n.length > 60 -> "Sətir $row: ad çox uzundur"
                    l.blockPrice == null || l.blockPrice <= 0.0 -> "Sətir $row ($n): blok qiymətini yazın"
                    l.blocks == null || l.blocks <= 0 -> "Sətir $row ($n): blok sayını yazın"
                    seen.containsKey(n.lowercase()) -> "\"$n\" qaimədə iki dəfə yazılıb (sətir ${seen[n.lowercase()]} və $row)"
                    else -> null
                }
                if (err != null) { onResult(err, null); return@launch }
                seen[n.lowercase()] = row
                clean += InventoryRepository.InvoiceLine(n, l.boxPrice ?: 0.0, l.blockPrice!!, l.blocks!!)
            }
            val id = repo.saveInvoice(firmId, number.trim(), date, note.trim(), clean)
            val total = InventoryCalc.sum(clean.map { InventoryCalc.totalAmount(it.blocks, it.blockPrice) })
            say("Qaimə yadda saxlanıldı: ${clean.size} siqaret, ${Fmt.money(total)}")
            onResult(null, id)
        }
    }

    fun deleteInvoice(id: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val err = repo.deleteInvoice(id)
            if (err != null) say(err) else say("Qaimə silindi, bloklar stokdan çıxıldı")
            onDone(err == null)
        }
    }

    fun deleteFirm(f: Firm) {
        viewModelScope.launch {
            repo.deleteFirm(f)
            if (firmFilter.value == f.id) firmFilter.value = null
            say("\"${f.name}\" firması silindi. Onun siqaretləri \"${FirmSummary.NO_FIRM}\" bölməsinə keçdi")
        }
    }

    /** Doğrulama + yadda saxlama. Xəta olarsa mətn qaytarılır, uğurlu olarsa null. */
    fun saveProduct(
        id: Long?, name: String, boxPrice: Double?, blockPrice: Double?, blocks: Int?, firmId: Long?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val n = name.trim().replace(Regex("\\s+"), " ")
            val error = when {
                n.isEmpty() -> "Siqaretin adını daxil edin"
                n.length > 60 -> "Ad çox uzundur (maks. 60 simvol)"
                blockPrice == null || blockPrice <= 0.0 -> "Bir blokun qiymətini daxil edin"
                blocks == null -> "Blok sayını daxil edin"
                repo.nameExists(n, firmId, id ?: 0L) ->
                    if (firmId == null) "\"$n\" adlı məhsul artıq mövcuddur"
                    else "Bu firmada \"$n\" adlı məhsul artıq mövcuddur"
                else -> null
            }
            if (error != null) { onResult(error); return@launch }
            val box = boxPrice ?: 0.0
            if (id == null) {
                repo.addProduct(n, box, blockPrice!!, blocks!!, firmId)
                say("\"$n\" əlavə edildi")
            } else {
                repo.updateProduct(id, n, box, blockPrice!!, blocks!!, firmId)
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
            firms = repo.allFirms(),
            invoices = repo.invoicesSince(period.from(now)).let { (inv, items) ->
                InvoiceSummary.build(inv, items, repo.allFirms())
            },
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
        const val NO_FIRM_FILTER = -1L

        fun factory(app: SiqaretApp) = viewModelFactory {
            initializer { InventoryViewModel(app, app.repository, app.settings) }
        }
    }
}
