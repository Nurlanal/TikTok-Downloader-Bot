package az.payreport.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(p: PaymentEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<PaymentEntity>)

    @Update
    suspend fun update(p: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: Long): Flow<PaymentEntity?>

    @Query("SELECT id FROM payments WHERE txnId = :txn AND id != :excludeId LIMIT 1")
    suspend fun findTxn(txn: String, excludeId: Long = -1): Long?

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE status = 'CONFIRMED' AND paidAt >= :from AND paidAt < :to")
    fun sumConfirmed(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE status = 'CONFIRMED'")
    fun totalConfirmed(): Flow<Long>

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'CONFIRMED'")
    fun countConfirmed(): Flow<Int>

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = 'CONFIRMED' AND paidAt >= :from AND paidAt < :to ORDER BY paidAt")
    fun observeConfirmedBetween(from: Long, to: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = 'CONFIRMED' AND paidAt >= :from AND paidAt < :to ORDER BY paidAt")
    suspend fun confirmedBetween(from: Long, to: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE phone = :phone ORDER BY paidAt DESC, id DESC")
    fun observeByPhone(phone: String): Flow<List<PaymentEntity>>

    @Query(
        """SELECT phone, COUNT(*) AS cnt, SUM(amountMinor) AS total, MAX(paidAt) AS lastPaid
           FROM payments WHERE status = 'CONFIRMED' AND phone IS NOT NULL AND paidAt >= :from AND paidAt < :to
           GROUP BY phone ORDER BY total DESC, cnt DESC LIMIT :limit"""
    )
    fun observeTopPhones(from: Long, to: Long, limit: Int): Flow<List<PhoneTotal>>

    @Query(
        """SELECT phone, COUNT(*) AS cnt, SUM(amountMinor) AS total, MAX(paidAt) AS lastPaid
           FROM payments WHERE status = 'CONFIRMED' AND phone LIKE '%' || :digits || '%'
           GROUP BY phone ORDER BY total DESC LIMIT 30"""
    )
    fun observePhoneMatches(digits: String): Flow<List<PhoneTotal>>

    @Query(
        """SELECT * FROM payments
           WHERE (:status IS NULL OR status = :status)
             AND (:phoneDigits IS NULL OR phone LIKE '%' || :phoneDigits || '%')
             AND paidAt >= :from AND paidAt < :to
             AND (:q IS NULL OR txnId LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%'
                  OR emailSubject LIKE '%' || :q || '%' OR emailFrom LIKE '%' || :q || '%')
           ORDER BY paidAt DESC, id DESC LIMIT :limit"""
    )
    fun search(status: String?, phoneDigits: String?, from: Long, to: Long, q: String?, limit: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = 'PENDING' ORDER BY paidAt DESC")
    fun observePending(): Flow<List<PaymentEntity>>

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(a: AuditEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<AuditEntity>)

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntity>>

    @Query("SELECT * FROM audit_log WHERE paymentId = :paymentId ORDER BY createdAt DESC, id DESC")
    fun observeForPayment(paymentId: Long): Flow<List<AuditEntity>>

    @Query("SELECT COALESCE(MAX(id), 0) FROM audit_log")
    suspend fun maxId(): Long

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
}

@Dao
interface SyncRunDao {
    @Insert
    suspend fun insert(r: SyncRunEntity): Long

    @Update
    suspend fun update(r: SyncRunEntity)

    @Upsert
    suspend fun upsertAll(items: List<SyncRunEntity>)

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SyncRunEntity>>

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeLast(): Flow<SyncRunEntity?>

    @Query("DELETE FROM sync_runs")
    suspend fun deleteAll()
}

@Dao
interface ProcessedEmailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ProcessedEmailEntity): Long

    @Query("SELECT COUNT(*) FROM processed_emails WHERE `key` = :key")
    suspend fun countKey(key: String): Int

    @Query("SELECT COUNT(*) FROM processed_emails WHERE messageId = :messageId")
    suspend fun countMessageId(messageId: String): Int

    @Query("SELECT result AS result, COUNT(*) AS cnt FROM processed_emails GROUP BY result")
    fun observeResultCounts(): Flow<List<ResultCount>>

    @Query("DELETE FROM processed_emails")
    suspend fun deleteAll()
}

data class ResultCount(val result: String, val cnt: Int)
