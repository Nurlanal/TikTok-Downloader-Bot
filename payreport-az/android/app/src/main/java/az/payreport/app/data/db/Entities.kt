package az.payreport.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object PaymentStatus {
    const val CONFIRMED = "CONFIRMED"
    const val PENDING = "PENDING"
    const val CANCELLED = "CANCELLED"
}

object PaymentSource {
    const val TEXT = "TEXT"
    const val PDF = "PDF"
    const val IMAGE = "IMAGE"
    const val MANUAL = "MANUAL"
}

/**
 * Ödəniş. Server rejimində [id] serverdəki ID ilə eynidir.
 * Təkrarların qarşısı [dedupeKey] unikal indeksi ilə alınır.
 */
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["phone"]),
        Index(value = ["paidAt"]),
        Index(value = ["status", "paidAt"]),
        Index(value = ["txnId"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val phone: String?,
    val amountMinor: Long?,
    val currency: String = "AZN",
    /** UTC epoch millis */
    val paidAt: Long,
    val status: String,
    val source: String,
    val confidence: Int,
    val txnId: String?,
    val emailMessageId: String? = null,
    val emailSubject: String? = null,
    val emailFrom: String? = null,
    val rawText: String? = null,
    val note: String? = null,
    val cancelReason: String? = null,
    val createdBy: String = "system",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audit_log", indices = [Index(value = ["paymentId"]), Index(value = ["createdAt"])])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long?,
    val action: String,
    val actor: String,
    val details: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sync_runs", indices = [Index(value = ["startedAt"])])
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val fetched: Int = 0,
    val created: Int = 0,
    val pending: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val message: String? = null,
)

/** Birbaşa (cihazda IMAP) rejimində emal olunmuş məktublar. */
@Entity(tableName = "processed_emails", indices = [Index(value = ["messageId"])])
data class ProcessedEmailEntity(
    /** "<uidValidity>:<uid>" */
    @PrimaryKey val key: String,
    val messageId: String?,
    val fromAddr: String?,
    val subject: String?,
    val receivedAt: Long?,
    val result: String,
    val paymentsCreated: Int,
    val error: String?,
    val processedAt: Long = System.currentTimeMillis(),
)

data class PhoneTotal(
    val phone: String,
    @ColumnInfo(name = "cnt") val count: Int,
    @ColumnInfo(name = "total") val totalMinor: Long,
    @ColumnInfo(name = "lastPaid") val lastPaidAt: Long,
)
