package az.siqaret.anbar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    /** Eyni firmada eyni adlı məhsul olmasın (fərqli firmalardan eyni marka ola bilər). */
    @Query(
        "SELECT COUNT(*) FROM products WHERE name = :name COLLATE NOCASE AND id != :excludeId " +
            "AND IFNULL(firmId, -1) = IFNULL(:firmId, -1)"
    )
    suspend fun countByName(name: String, firmId: Long?, excludeId: Long): Int

    @Query(
        "SELECT * FROM products WHERE name = :name COLLATE NOCASE " +
            "AND IFNULL(firmId, -1) = IFNULL(:firmId, -1) LIMIT 1"
    )
    suspend fun findByName(name: String, firmId: Long?): Product?

    @Query("UPDATE products SET firmId = NULL WHERE firmId = :firmId")
    suspend fun detachFirm(firmId: Long)

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE timestamp >= :from ORDER BY timestamp DESC, id DESC")
    suspend fun getSince(from: Long): List<HistoryEntry>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FirmDao {
    @Query("SELECT * FROM firms ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Firm>>

    @Query("SELECT * FROM firms ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Firm>

    @Query("SELECT * FROM firms WHERE id = :id")
    suspend fun getById(id: Long): Firm?

    @Query("SELECT COUNT(*) FROM firms WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long): Int

    @Insert
    suspend fun insert(firm: Firm): Long

    @Update
    suspend fun update(firm: Firm)

    @Delete
    suspend fun delete(firm: Firm)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoice_items ORDER BY id")
    fun observeAllItems(): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Long): Invoice?

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY id")
    suspend fun itemsOf(invoiceId: Long): List<InvoiceItem>

    @Query("SELECT * FROM invoices WHERE date >= :from ORDER BY date DESC, id DESC")
    suspend fun getSince(from: Long): List<Invoice>

    @Query("SELECT * FROM invoice_items WHERE invoiceId IN (SELECT id FROM invoices WHERE date >= :from) ORDER BY id")
    suspend fun itemsSince(from: Long): List<InvoiceItem>

    @Insert
    suspend fun insert(invoice: Invoice): Long

    @Insert
    suspend fun insertItems(items: List<InvoiceItem>)

    @Delete
    suspend fun delete(invoice: Invoice)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItems(invoiceId: Long)

    @Query("DELETE FROM invoice_items WHERE invoiceId IN (SELECT id FROM invoices WHERE firmId = :firmId)")
    suspend fun deleteItemsForFirm(firmId: Long)

    @Query("DELETE FROM invoices WHERE firmId = :firmId")
    suspend fun deleteForFirm(firmId: Long)
}
