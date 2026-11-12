package az.siqaret.anbar.util

import az.siqaret.anbar.data.Firm
import az.siqaret.anbar.data.HistoryEntry
import az.siqaret.anbar.data.Invoice
import az.siqaret.anbar.data.InvoiceItem
import az.siqaret.anbar.data.title
import az.siqaret.anbar.data.HistoryType
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.totalAmount
import java.util.Calendar

data class InventoryStats(
    val productCount: Int = 0,
    val totalBlocks: Long = 0,
    val totalBoxes: Long = 0,
    val totalValue: Double = 0.0,
) {
    companion object {
        fun from(products: List<Product>): InventoryStats {
            val blocks = products.sumOf { it.blockCount.toLong() }
            return InventoryStats(
                productCount = products.size,
                totalBlocks = blocks,
                totalBoxes = blocks * InventoryCalc.BOXES_PER_BLOCK,
                totalValue = InventoryCalc.sum(products.map { it.totalAmount }),
            )
        }
    }
}

enum class Period(val label: String) {
    TODAY("Bu gün"), WEEK("7 gün"), MONTH("30 gün"), ALL("Hamısı");

    fun from(now: Long = System.currentTimeMillis()): Long = when (this) {
        TODAY -> Fmt.dayStart(now)
        WEEK -> daysAgo(now, 6)
        MONTH -> daysAgo(now, 29)
        ALL -> 0L
    }

    private fun daysAgo(now: Long, days: Int) = Calendar.getInstance().apply {
        timeInMillis = Fmt.dayStart(now); add(Calendar.DAY_OF_YEAR, -days)
    }.timeInMillis
}

/** Bir günün xülasəsi. */
data class DaySummary(
    val dayStart: Long,
    val entries: List<HistoryEntry>,
) {
    /** Daxil olan bloklar (artırma, yeni məhsul, bərpa, redaktədə artım). */
    val blocksIn: Long get() = entries.filter { it.blockDelta > 0 && it.type != HistoryType.DELETED }.sumOf { it.blockDelta.toLong() }
    /** Çıxan bloklar (azaltma, redaktədə azalma, silinmə). */
    val blocksOut: Long get() = entries.filter { it.blockDelta < 0 }.sumOf { -it.blockDelta.toLong() }

    companion object {
        /** Girişlər yeni → köhnə sıralanmış olmalıdır. */
        fun group(entries: List<HistoryEntry>): List<DaySummary> =
            entries.groupBy { Fmt.dayStart(it.timestamp) }
                .map { (day, list) -> DaySummary(day, list) }
                .sortedByDescending { it.dayStart }
    }
}

/** Bir firmadan alınan siqaretlərin xülasəsi. firm == null -> "Firmasız". */
data class FirmSummary(val firm: Firm?, val products: List<Product>) {
    val name: String get() = firm?.name ?: NO_FIRM
    val stats: InventoryStats = InventoryStats.from(products)

    companion object {
        const val NO_FIRM = "Firmasız"

        /** Firmalar ada görə, sonda "Firmasız" (yalnız belə məhsul varsa). */
        fun group(products: List<Product>, firms: List<Firm>): List<FirmSummary> {
            val byFirm = products.groupBy { it.firmId }
            val known = firms.map { FirmSummary(it, byFirm[it.id].orEmpty()) }
            val ids = firms.map { it.id }.toSet()
            val orphans = products.filter { it.firmId == null || it.firmId !in ids }
            return if (orphans.isEmpty()) known else known + FirmSummary(null, orphans)
        }
    }
}

/** Qaimə + sətirləri + cəmlər. */
data class InvoiceSummary(val invoice: Invoice, val items: List<InvoiceItem>, val firmName: String) {
    val title: String get() = invoice.title
    val totalBlocks: Long = items.sumOf { it.blockCount.toLong() }
    val totalBoxes: Long = totalBlocks * InventoryCalc.BOXES_PER_BLOCK
    val totalAmount: Double = InventoryCalc.sum(items.map { InventoryCalc.totalAmount(it.blockCount, it.blockPrice) })

    companion object {
        fun build(invoices: List<Invoice>, items: List<InvoiceItem>, firms: List<Firm>): List<InvoiceSummary> {
            val byInvoice = items.groupBy { it.invoiceId }
            val names = firms.associate { it.id to it.name }
            return invoices.map { InvoiceSummary(it, byInvoice[it.id].orEmpty(), names[it.firmId] ?: "—") }
        }
    }
}

data class ReportData(
    val products: List<Product>,
    val history: List<HistoryEntry>,
    val period: Period,
    val generatedAt: Long,
    val firms: List<Firm> = emptyList(),
    val invoices: List<InvoiceSummary> = emptyList(),
) {
    val stats: InventoryStats get() = InventoryStats.from(products)
    val days: List<DaySummary> get() = DaySummary.group(history)
    val firmSummaries: List<FirmSummary> get() = FirmSummary.group(products, firms)
    private val firmNames: Map<Long, String> by lazy { firms.associate { it.id to it.name } }
    fun firmName(p: Product): String = p.firmId?.let { firmNames[it] } ?: ""
}
