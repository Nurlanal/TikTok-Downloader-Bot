package az.siqaret.anbar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import az.siqaret.anbar.util.InventoryCalc

/** Siqaret məhsulu. Ümumi qutu sayı və ümumi məbləğ həmişə real vaxtda hesablanır. */
@Entity(tableName = "products", indices = [Index("name")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val boxPrice: Double,
    val blockPrice: Double,
    val blockCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Blok sayı × 10 */
val Product.totalBoxes: Long get() = InventoryCalc.totalBoxes(blockCount)

/** Blok sayı × bir blokun qiyməti */
val Product.totalAmount: Double get() = InventoryCalc.totalAmount(blockCount, blockPrice)

enum class HistoryType(val label: String) {
    ADDED("Yeni məhsul"),
    EDITED("Redaktə"),
    DELETED("Silindi"),
    RESTORED("Bərpa edildi"),
    STOCK_IN("Stok artırıldı"),
    STOCK_OUT("Stok azaldıldı"),
}

/** Dəyişikliklər tarixçəsi. Məhsul silinsə belə tarixçə qalır (ad snapshot kimi saxlanılır). */
@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: HistoryType,
    val blockDelta: Int,
    val blocksAfter: Int,
    val blockPrice: Double,
    val note: String,
    val timestamp: Long,
)
