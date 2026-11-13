package az.sontarix.market.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import az.sontarix.market.util.DateUtils
import java.time.LocalDate

/**
 * Hər sətir bir məhsul partiyasıdır. Eyni məhsulun (eyni barkod/ad) fərqli son istifadə
 * tarixli partiyaları ayrıca sətirlər kimi saxlanılır və ayrıca izlənilir.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["barcode"]),
        Index(value = ["expiryEpochDay"]),
        Index(value = ["category"]),
        Index(value = ["name"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Qlobal unikal ID — ixrac/idxal zamanı təkrar yoxlaması üçün. */
    val uid: String,
    val name: String,
    val barcode: String = "",
    val category: String = "",
    val quantity: Int = 1,
    val expiryEpochDay: Long,
    val note: String = "",
    val batchNumber: String = "",
    /** filesDir/images daxilindəki fayl adı (tam yol deyil). */
    val imageFile: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val expiryDate: LocalDate get() = LocalDate.ofEpochDay(expiryEpochDay)
    fun daysLeft(today: LocalDate = LocalDate.now()): Long = DateUtils.daysBetween(today, expiryDate)
}

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/**
 * Göndərilmiş bildirişlərin jurnalı. (productId, expiryEpochDay, threshold) unikal olduğu üçün
 * eyni bildiriş təkrar göndərilmir. Məhsul silinəndə jurnal da kaskadla silinir.
 * Tarix dəyişdikdə expiryEpochDay fərqli olduğundan yeni tarix üçün bildirişlər yenidən aktivləşir.
 */
@Entity(
    tableName = "notification_log",
    indices = [Index(value = ["productId", "expiryEpochDay", "threshold"], unique = true), Index("productId")],
    foreignKeys = [ForeignKey(
        entity = Product::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val expiryEpochDay: Long,
    /** Gün həddi; -1 = müddəti keçib. */
    val threshold: Int,
    val sentAt: Long = System.currentTimeMillis(),
    val title: String = "",
    val message: String = "",
    /** true – istifadəçiyə real göstərildi; false – sadəcə köhnə hədd kimi qeyd edildi. */
    val shown: Boolean = true
)

enum class OperationType(val label: String) {
    EXPORT("İxrac"), IMPORT("İdxal"), BACKUP("Ehtiyat nüsxə"), RESTORE("Bərpa"), REPORT("Hesabat")
}

enum class OperationStatus(val label: String) {
    SUCCESS("Uğurlu"), PARTIAL("Qismən"), FAILED("Xəta"), CANCELLED("Ləğv edildi")
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: OperationType,
    val status: OperationStatus,
    val itemCount: Int,
    val format: String,
    val fileName: String = "",
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/** Dashboard üçün statistika. */
data class Stats(
    val total: Int = 0,
    val normal: Int = 0,
    val approaching: Int = 0,
    val week: Int = 0,
    val today: Int = 0,
    val expired: Int = 0,
    val totalQuantity: Long = 0
)
