package az.siqaret.anbar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import az.siqaret.anbar.util.InventoryCalc

/** Siqaret məhsulu. Ümumi paçka sayı və ümumi məbləğ həmişə real vaxtda hesablanır. */
@Entity(tableName = "products", indices = [Index("name"), Index("firmId")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Bir paçkanın qiyməti */
    val boxPrice: Double,
    val blockPrice: Double,
    val blockCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Siqaretin alındığı firma (null = firma seçilməyib) */
    val firmId: Long? = null,
)

/** Siqaretlərin alındığı firma (təchizatçı). */
@Entity(tableName = "firms", indices = [Index("name")])
data class Firm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
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

/** Firmadan alınan malın qaiməsi (faktura). */
@Entity(tableName = "invoices", indices = [Index("firmId"), Index("date")])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firmId: Long,
    val number: String,
    val date: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

val Invoice.title: String get() = if (number.isBlank()) "Qaimə #$id" else "Qaimə № $number"

/** Qaimənin bir sətri: hansı siqaretdən neçə blok, hansı qiymətə alınıb. */
@Entity(tableName = "invoice_items", indices = [Index("invoiceId")])
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val productName: String,
    val boxPrice: Double,
    val blockPrice: Double,
    val blockCount: Int,
)

val InvoiceItem.totalBoxes: Long get() = InventoryCalc.totalBoxes(blockCount)
val InvoiceItem.totalAmount: Double get() = InventoryCalc.totalAmount(blockCount, blockPrice)
