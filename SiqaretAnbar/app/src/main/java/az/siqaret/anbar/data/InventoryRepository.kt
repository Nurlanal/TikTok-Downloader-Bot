package az.siqaret.anbar.data

import androidx.room.withTransaction
import az.siqaret.anbar.util.Fmt
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val db: AppDatabase) {
    private val productDao = db.productDao()
    private val historyDao = db.historyDao()

    val products: Flow<List<Product>> = productDao.observeAll()
    val history: Flow<List<HistoryEntry>> = historyDao.observeAll()

    suspend fun getProduct(id: Long): Product? = productDao.getById(id)
    suspend fun allProducts(): List<Product> = productDao.getAll()
    suspend fun historySince(from: Long): List<HistoryEntry> = historyDao.getSince(from)
    suspend fun clearHistory() = historyDao.clear()

    suspend fun nameExists(name: String, excludeId: Long): Boolean =
        productDao.countByName(name.trim(), excludeId) > 0

    suspend fun addProduct(name: String, boxPrice: Double, blockPrice: Double, blocks: Int): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = productDao.insert(
                Product(
                    name = name, boxPrice = boxPrice, blockPrice = blockPrice,
                    blockCount = blocks, createdAt = now, updatedAt = now,
                )
            )
            historyDao.insert(
                HistoryEntry(
                    productId = id, productName = name, type = HistoryType.ADDED,
                    blockDelta = blocks, blocksAfter = blocks, blockPrice = blockPrice,
                    note = "Blok qiyməti ${Fmt.money(blockPrice)}", timestamp = now,
                )
            )
            id
        }

    suspend fun updateProduct(id: Long, name: String, boxPrice: Double, blockPrice: Double, blocks: Int) =
        db.withTransaction {
            val old = productDao.getById(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            val updated = old.copy(
                name = name, boxPrice = boxPrice, blockPrice = blockPrice,
                blockCount = blocks, updatedAt = now,
            )
            if (updated == old.copy(updatedAt = now)) return@withTransaction
            productDao.update(updated)

            val changes = buildList {
                if (old.name != name) add("Ad: ${old.name} → $name")
                if (old.boxPrice != boxPrice) add("Qutu: ${Fmt.money(old.boxPrice)} → ${Fmt.money(boxPrice)}")
                if (old.blockPrice != blockPrice) add("Blok: ${Fmt.money(old.blockPrice)} → ${Fmt.money(blockPrice)}")
                if (old.blockCount != blocks) add("Say: ${old.blockCount} → $blocks blok")
            }
            historyDao.insert(
                HistoryEntry(
                    productId = id, productName = name, type = HistoryType.EDITED,
                    blockDelta = blocks - old.blockCount, blocksAfter = blocks,
                    blockPrice = blockPrice, note = changes.joinToString("; "), timestamp = now,
                )
            )
        }

    suspend fun deleteProduct(product: Product) = db.withTransaction {
        productDao.delete(product)
        historyDao.insert(
            HistoryEntry(
                productId = product.id, productName = product.name, type = HistoryType.DELETED,
                blockDelta = -product.blockCount, blocksAfter = 0, blockPrice = product.blockPrice,
                note = "Məhsul siyahıdan silindi", timestamp = System.currentTimeMillis(),
            )
        )
    }

    suspend fun restoreProduct(product: Product) = db.withTransaction {
        if (productDao.getById(product.id) != null) return@withTransaction
        productDao.insert(product)
        historyDao.insert(
            HistoryEntry(
                productId = product.id, productName = product.name, type = HistoryType.RESTORED,
                blockDelta = product.blockCount, blocksAfter = product.blockCount,
                blockPrice = product.blockPrice, note = "Silinmə ləğv edildi",
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /** @return yeni blok sayı, stok kifayət etmirsə null */
    suspend fun adjustStock(id: Long, delta: Int): Int? = db.withTransaction {
        val p = productDao.getById(id) ?: return@withTransaction null
        val newCount = p.blockCount.toLong() + delta
        if (newCount < 0 || newCount > Int.MAX_VALUE) return@withTransaction null
        val now = System.currentTimeMillis()
        productDao.update(p.copy(blockCount = newCount.toInt(), updatedAt = now))
        historyDao.insert(
            HistoryEntry(
                productId = id, productName = p.name,
                type = if (delta >= 0) HistoryType.STOCK_IN else HistoryType.STOCK_OUT,
                blockDelta = delta, blocksAfter = newCount.toInt(), blockPrice = p.blockPrice,
                note = "${p.blockCount} → $newCount blok", timestamp = now,
            )
        )
        newCount.toInt()
    }
}
