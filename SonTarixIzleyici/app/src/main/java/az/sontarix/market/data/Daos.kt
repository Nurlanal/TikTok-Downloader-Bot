package az.sontarix.market.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY expiryEpochDay ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY expiryEpochDay ASC")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Query("SELECT * FROM products WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Product>

    @Query("SELECT * FROM products WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): Product?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' ORDER BY expiryEpochDay ASC")
    suspend fun getByBarcode(barcode: String): List<Product>

    /** Eyni məhsulun digər partiyaları: eyni barkod (varsa) və ya eyni ad. */
    @Query(
        """SELECT * FROM products WHERE id != :id AND (
            (:barcode != '' AND barcode = :barcode) OR
            (:barcode = '' AND name = :name COLLATE NOCASE)
        ) ORDER BY expiryEpochDay ASC"""
    )
    fun observeSiblings(id: Long, barcode: String, name: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE category = :category ORDER BY expiryEpochDay ASC")
    suspend fun getByCategory(category: String): List<Product>

    @Query("SELECT * FROM products WHERE expiryEpochDay BETWEEN :from AND :to ORDER BY expiryEpochDay ASC")
    suspend fun getByExpiryRange(from: Long, to: Long): List<Product>

    @Query("SELECT DISTINCT category FROM products WHERE category != ''")
    suspend fun usedCategories(): List<String>

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("UPDATE products SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("SELECT imageFile FROM products WHERE imageFile IS NOT NULL")
    suspend fun allImageFiles(): List<String>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Category>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface NotificationLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: NotificationLog): Long

    @Query("SELECT COUNT(*) FROM notification_log WHERE productId = :productId AND expiryEpochDay = :expiry AND threshold = :threshold")
    suspend fun exists(productId: Long, expiry: Long, threshold: Int): Int

    @Query("DELETE FROM notification_log WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("DELETE FROM notification_log WHERE productId = :productId AND expiryEpochDay != :expiry")
    suspend fun deleteStaleForProduct(productId: Long, expiry: Long)

    @Query("SELECT * FROM notification_log WHERE shown = 1 ORDER BY sentAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<NotificationLog>>

    @Query("DELETE FROM notification_log WHERE shown = 1")
    suspend fun clearShownHistory()

    @Query("DELETE FROM notification_log")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    fun observeLast(type: OperationType): Flow<HistoryEntry?>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history")
    suspend fun clear()
}
