package az.payreport.app.data.ingest

import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PaymentParser
import az.payreport.app.data.db.AuditEntity
import az.payreport.app.data.db.AuditDao
import az.payreport.app.data.db.PaymentDao
import az.payreport.app.data.db.PaymentEntity
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Məktubun bir hissəsi (mətn, PDF, şəkil). */
data class ContentPart(val kind: PaymentParser.SourceType, val text: String, val name: String)

data class IngestItem(val parsed: PaymentParser.ParsedPayment, val source: PaymentParser.SourceType)

data class IngestStats(var created: Int = 0, var pending: Int = 0, var duplicates: Int = 0, val ids: MutableList<Long> = mutableListOf()) {
    operator fun plusAssign(o: IngestStats) {
        created += o.created; pending += o.pending; duplicates += o.duplicates; ids += o.ids
    }
}

/**
 * Parser nəticələrini lokal bazaya yazır (birbaşa rejim). Alqoritm backend-dəki
 * `services/payments.js` ilə eynidir: dedupe açarı TXN:<id>|<telefon>|<məbləğ> və ya MSG:<message-id>#<n>.
 * Telefon və məbləğ açara daxildir ki, məktubdakı sabit nömrələr (məs. "Terminal ID")
 * fərqli ödənişləri təkrar kimi birləşdirməsin.
 */
class PaymentIngestor(private val payments: PaymentDao, private val audit: AuditDao) {

    /** Əlavədəki ödəniş məktub mətnində artıq varsa (eyni nömrə + məbləğ), təkrar sayılmır. */
    fun collect(parts: List<ContentPart>): List<IngestItem> {
        val out = mutableListOf<IngestItem>()
        val bodyKeys = HashSet<String>()
        for (part in parts) {
            for (p in PaymentParser.parse(part.text, part.kind)) {
                val key = "${p.phone}|${p.amountMinor}"
                if (part.kind == PaymentParser.SourceType.TEXT) bodyKeys += key
                else if (key in bodyKeys) continue
                out += IngestItem(p, part.kind)
            }
        }
        return out
    }

    suspend fun ingest(
        items: List<IngestItem>,
        dedupeBase: String,
        fallbackTime: Long,
        actor: String,
        rawText: String?,
        messageId: String? = null,
        subject: String? = null,
        from: String? = null,
        forcePending: Boolean = false,
    ): IngestStats {
        val stats = IngestStats()
        items.forEachIndexed { i, item ->
            val p = item.parsed
            val status = if (forcePending) PaymentParser.STATUS_PENDING else p.status
            val now = System.currentTimeMillis()
            val entity = PaymentEntity(
                dedupeKey = if (p.txnId != null) "TXN:${p.txnId}|${p.phone.orEmpty()}|${p.amountMinor ?: ""}" else "$dedupeBase#$i",
                phone = p.phone,
                amountMinor = p.amountMinor,
                paidAt = Fmt.parseLocal(p.paidAt) ?: fallbackTime,
                status = status,
                source = item.source.name,
                confidence = p.confidence,
                txnId = p.txnId,
                emailMessageId = messageId,
                emailSubject = subject?.take(1000),
                emailFrom = from?.take(500),
                rawText = (rawText ?: p.rawLine).take(60_000),
                createdBy = actor,
                createdAt = now,
                updatedAt = now,
            )
            val id = payments.insertIgnore(entity)
            if (id == -1L) {
                stats.duplicates++
                return@forEachIndexed
            }
            stats.ids += id
            if (status == PaymentParser.STATUS_PENDING) stats.pending++ else stats.created++
            audit.insert(
                AuditEntity(
                    paymentId = id,
                    action = "CREATE",
                    actor = actor,
                    details = buildJsonObject {
                        put("source", item.source.name)
                        put("status", status)
                        put("confidence", p.confidence)
                        put("phone", p.phone)
                        put("amountMinor", p.amountMinor)
                        put("txnId", p.txnId)
                    }.toString(),
                )
            )
        }
        return stats
    }
}
