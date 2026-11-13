package az.sontarix.market.data

import android.content.Context
import androidx.room.withTransaction
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.notify.NotificationScheduler
import az.sontarix.market.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProductRepository(
    private val context: Context,
    val db: AppDatabase
) {
    private val productDao = db.productDao()
    private val categoryDao = db.categoryDao()
    private val logDao = db.notificationLogDao()
    private val historyDao = db.historyDao()

    val products: Flow<List<Product>> = productDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val history: Flow<List<HistoryEntry>> = historyDao.observeAll()
    val recentNotifications: Flow<List<NotificationLog>> = logDao.observeRecent()

    fun observeProduct(id: Long) = productDao.observeById(id)
    fun observeSiblings(p: Product) = productDao.observeSiblings(p.id, p.barcode, p.name)
    fun lastOperation(type: OperationType) = historyDao.observeLast(type)

    suspend fun getProduct(id: Long) = productDao.getById(id)
    suspend fun getAll() = productDao.getAll()
    suspend fun getByIds(ids: List<Long>) = productDao.getByIds(ids)
    suspend fun getByCategory(c: String) = productDao.getByCategory(c)
    suspend fun getByExpiryRange(from: Long, to: Long) = productDao.getByExpiryRange(from, to)
    suspend fun findByBarcode(barcode: String) = productDao.getByBarcode(barcode.trim())

    suspend fun save(product: Product): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = if (product.id == 0L) {
            productDao.insert(product.copy(createdAt = now, updatedAt = now))
        } else {
            val old = productDao.getById(product.id)
            productDao.update(product.copy(updatedAt = now))
            if (old != null && old.imageFile != product.imageFile) ImageStorage.delete(context, old.imageFile)
            if (old != null && old.expiryEpochDay != product.expiryEpochDay) {
                // Tarix dəyişib — köhnə bildirişləri ləğv et, yeni tarix üçün yenidən planlaşdır
                logDao.deleteForProduct(product.id)
                NotificationHelper.cancelForProduct(context, product.id)
            }
            product.id
        }
        if (product.category.isNotBlank()) categoryDao.insert(Category(name = product.category.trim()))
        NotificationScheduler.runCheckNow(context)
        id
    }

    suspend fun delete(product: Product) = withContext(Dispatchers.IO) {
        productDao.delete(product) // notification_log kaskadla silinir
        ImageStorage.delete(context, product.imageFile)
        NotificationHelper.cancelForProduct(context, product.id)
    }

    suspend fun deleteMany(products: List<Product>) = withContext(Dispatchers.IO) {
        db.withTransaction { productDao.deleteByIds(products.map { it.id }) }
        products.forEach {
            ImageStorage.delete(context, it.imageFile)
            NotificationHelper.cancelForProduct(context, it.id)
        }
    }

    suspend fun addCategory(name: String): Boolean = withContext(Dispatchers.IO) {
        val n = name.trim()
        n.isNotEmpty() && categoryDao.insert(Category(name = n)) > 0
    }

    suspend fun deleteCategory(c: Category) = withContext(Dispatchers.IO) { categoryDao.delete(c) }

    suspend fun addHistory(entry: HistoryEntry) = withContext(Dispatchers.IO) { historyDao.insert(entry) }
    suspend fun clearHistory() = withContext(Dispatchers.IO) { historyDao.clear() }
    suspend fun clearNotificationHistory() = withContext(Dispatchers.IO) { logDao.clearShownHistory() }
}
