package az.payreport.app.data.repo

import androidx.room.withTransaction
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PaymentParser
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.data.db.AppDatabase
import az.payreport.app.data.db.AuditEntity
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentSource
import az.payreport.app.data.db.PaymentStatus
import az.payreport.app.data.ingest.IngestItem
import az.payreport.app.data.ingest.PaymentIngestor
import az.payreport.app.data.remote.ApiClient
import az.payreport.app.data.settings.AppSettings
import az.payreport.app.data.settings.SyncMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ValidationException(message: String) : Exception(message)

data class PaymentDraft(
    val phone: String,
    val amountMinor: Long,
    val paidAt: Long,
    val txnId: String?,
    val note: String?,
)

/**
 * Admin əməliyyatları. Birbaşa rejimdə lokal bazada, server rejimində REST API üzərindən
 * icra olunur. Hər iki halda hər dəyişiklik audit tarixçəsinə yazılır; heç bir ödəniş silinmir.
 */
class PaymentRepository(
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val api: ApiClient,
) {
    private val dao = db.payments()
    private val auditDao = db.audit()
    private val ingestor = PaymentIngestor(dao, auditDao)
    private val isServer get() = settings.current.mode == SyncMode.SERVER
    private val actor = "admin (cihaz)"

    private fun details(vararg pairs: Pair<String, Any?>): String = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                null -> put(k, null as String?)
                is Number -> put(k, v)
                is Boolean -> put(k, v)
                is JsonObject -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }.toString()

    private fun validate(d: PaymentDraft): String {
        val phone = PhoneUtils.normalize(d.phone) ?: throw ValidationException("Telefon nömrəsi düzgün deyil (məs. 055 777 66 55)")
        if (d.amountMinor <= 0 || d.amountMinor > PaymentParser.MAX_AMOUNT_MINOR) throw ValidationException("Məbləğ düzgün deyil")
        return phone
    }

    suspend fun addManual(d: PaymentDraft): Long {
        val phone = validate(d)
        val txn = d.txnId?.trim()?.uppercase()?.ifEmpty { null }
        if (isServer) {
            val p = api.createPayment(phone, d.amountMinor, Fmt.iso(d.paidAt), txn, d.note?.ifBlank { null })
            dao.upsertAll(listOf(p.toEntity()))
            return p.id
        }
        return db.withTransaction {
            if (txn != null) dao.findTxn(txn)?.let { throw ValidationException("Bu əməliyyat ID artıq mövcuddur (#$it)") }
            val now = System.currentTimeMillis()
            val id = dao.insertIgnore(
                PaymentEntity(
                    dedupeKey = if (txn != null) "TXN:$txn|$phone|${d.amountMinor}" else "MAN:${UUID.randomUUID()}",
                    phone = phone, amountMinor = d.amountMinor, paidAt = d.paidAt,
                    status = PaymentStatus.CONFIRMED, source = PaymentSource.MANUAL, confidence = 100,
                    txnId = txn, note = d.note?.ifBlank { null }, createdBy = actor, createdAt = now, updatedAt = now,
                )
            )
            if (id == -1L) throw ValidationException("Təkrar ödəniş")
            auditDao.insert(AuditEntity(paymentId = id, action = "CREATE", actor = actor,
                details = details("source" to "MANUAL", "phone" to phone, "amountMinor" to d.amountMinor, "txnId" to txn)))
            id
        }
    }

    suspend fun update(id: Long, d: PaymentDraft) {
        val phone = validate(d)
        val txn = d.txnId?.trim()?.uppercase()?.ifEmpty { null }
        if (isServer) {
            val patch = buildJsonObject {
                put("phone", phone); put("amountMinor", d.amountMinor); put("paidAt", Fmt.iso(d.paidAt))
                put("txnId", txn); put("note", d.note?.ifBlank { null })
            }
            dao.upsertAll(listOf(api.updatePayment(id, patch).toEntity()))
            return
        }
        db.withTransaction {
            val before = dao.getById(id) ?: throw ValidationException("Ödəniş tapılmadı")
            if (txn != null) dao.findTxn(txn, id)?.let { throw ValidationException("Bu əməliyyat ID artıq #$it ödənişində var") }
            val after = before.copy(phone = phone, amountMinor = d.amountMinor, paidAt = d.paidAt, txnId = txn,
                note = d.note?.ifBlank { null }, updatedAt = System.currentTimeMillis())
            dao.update(after)
            val changes = buildJsonObject {
                if (before.phone != after.phone) put("phone", buildJsonObject { put("from", before.phone); put("to", after.phone) })
                if (before.amountMinor != after.amountMinor) put("amountMinor", buildJsonObject { put("from", before.amountMinor); put("to", after.amountMinor) })
                if (before.paidAt != after.paidAt) put("paidAt", buildJsonObject { put("from", Fmt.iso(before.paidAt)); put("to", Fmt.iso(after.paidAt)) })
                if (before.txnId != after.txnId) put("txnId", buildJsonObject { put("from", before.txnId); put("to", after.txnId) })
                if (before.note != after.note) put("note", buildJsonObject { put("from", before.note); put("to", after.note) })
            }
            auditDao.insert(AuditEntity(paymentId = id, action = "UPDATE", actor = actor, details = changes.toString()))
        }
    }

    suspend fun confirm(id: Long) {
        if (isServer) {
            dao.upsertAll(listOf(api.confirm(id).toEntity()))
            return
        }
        db.withTransaction {
            val p = dao.getById(id) ?: throw ValidationException("Ödəniş tapılmadı")
            if (p.status == PaymentStatus.CANCELLED) throw ValidationException("Ləğv edilmiş ödəniş təsdiqlənə bilməz")
            if (p.phone == null || p.amountMinor == null) throw ValidationException("Təsdiqdən əvvəl telefon və məbləği daxil edin")
            if (p.status == PaymentStatus.CONFIRMED) return@withTransaction
            dao.update(p.copy(status = PaymentStatus.CONFIRMED, updatedAt = System.currentTimeMillis()))
            auditDao.insert(AuditEntity(paymentId = id, action = "CONFIRM", actor = actor, details = details("from" to p.status)))
        }
    }

    suspend fun cancel(id: Long, reason: String?) {
        if (isServer) {
            dao.upsertAll(listOf(api.cancel(id, reason?.ifBlank { null }).toEntity()))
            return
        }
        db.withTransaction {
            val p = dao.getById(id) ?: throw ValidationException("Ödəniş tapılmadı")
            if (p.status == PaymentStatus.CANCELLED) return@withTransaction
            dao.update(p.copy(status = PaymentStatus.CANCELLED, cancelReason = reason?.ifBlank { null }, updatedAt = System.currentTimeMillis()))
            auditDao.insert(AuditEntity(paymentId = id, action = "CANCEL", actor = actor,
                details = details("from" to p.status, "reason" to reason, "phone" to p.phone, "amountMinor" to p.amountMinor, "txnId" to p.txnId)))
        }
    }

    /**
     * Cihazda skan edilmiş qəbz (ML Kit OCR / PDF) — nəticə həmişə admin yoxlaması üçün
     * gözləyən əməliyyatlara düşür.
     */
    suspend fun ingestScan(text: String, source: PaymentParser.SourceType, reference: String): ScanResult {
        val parsed = PaymentParser.parse(text, source)
        if (parsed.isEmpty()) return ScanResult(0, 0, parsed)
        if (isServer) {
            val res = api.ingestText(text, source.name, reference)
            dao.upsertAll(res.payments.map { it.toEntity() })
            return ScanResult(res.created, res.duplicates, parsed)
        }
        val stats = db.withTransaction {
            ingestor.ingest(parsed.map { IngestItem(it, source) }, "SCAN:$reference", System.currentTimeMillis(), actor, text, forcePending = true)
        }
        return ScanResult(stats.created + stats.pending, stats.duplicates, parsed)
    }
}

data class ScanResult(val created: Int, val duplicates: Int, val parsed: List<PaymentParser.ParsedPayment>)
