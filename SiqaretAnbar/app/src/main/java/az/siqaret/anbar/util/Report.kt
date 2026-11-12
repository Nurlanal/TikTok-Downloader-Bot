package az.siqaret.anbar.util

import az.siqaret.anbar.data.HistoryEntry
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
                totalValue = InventoryCalc.round2(products.sumOf { it.totalAmount }),
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

data class ReportData(
    val products: List<Product>,
    val history: List<HistoryEntry>,
    val period: Period,
    val generatedAt: Long,
) {
    val stats: InventoryStats get() = InventoryStats.from(products)
    val days: List<DaySummary> get() = DaySummary.group(history)
}
