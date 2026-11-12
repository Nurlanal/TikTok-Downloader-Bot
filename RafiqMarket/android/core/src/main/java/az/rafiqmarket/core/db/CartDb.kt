package az.rafiqmarket.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val productId: Long,
    val name: String,
    val price: String,
    val unit: String,
    val imageUrl: String?,
    val quantity: Int,
    val maxStock: Int,
)

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY name") fun observe(): Flow<List<CartItemEntity>>
    @Query("SELECT * FROM cart_items") suspend fun all(): List<CartItemEntity>
    @Query("SELECT * FROM cart_items WHERE productId = :id") suspend fun get(id: Long): CartItemEntity?
    @Upsert suspend fun upsert(item: CartItemEntity)
    @Query("DELETE FROM cart_items WHERE productId = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM cart_items") suspend fun clear()
}

@Database(entities = [CartItemEntity::class], version = 1, exportSchema = false)
abstract class RafiqDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
}
