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
