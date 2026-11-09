package az.payreport.app.data.mail

import androidx.room.withTransaction
import az.payreport.app.core.parser.PaymentParser.SourceType
import az.payreport.app.core.security.SecureStore
import az.payreport.app.data.db.AppDatabase
import az.payreport.app.data.db.ProcessedEmailEntity
import az.payreport.app.data.db.SyncRunEntity
import az.payreport.app.data.extract.Html
import az.payreport.app.data.extract.OcrEngine
import az.payreport.app.data.extract.PdfTextExtractor
import az.payreport.app.data.ingest.ContentPart
import az.payreport.app.data.ingest.IngestStats
import az.payreport.app.data.ingest.PaymentIngestor
import az.payreport.app.data.settings.AppSettings
import az.payreport.app.data.settings.MailConfig
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.MimeMessage
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

data class ConnectionTestResult(val ok: Boolean, val messages: Int = 0, val unread: Int = 0, val latencyMs: Long = 0, val error: String? = null)

/**
 * Cihazdan birbaşa Mail.ru IMAP (imap.mail.ru:993, SSL/TLS) ilə sinxronizasiya.
 *
 * - Poçt qutusu YALNIZ-OXUMA rejimində açılır, məktublar heç vaxt dəyişdirilmir və ya silinmir
 *   (mail.imaps.peek=true — "oxunmuş" bayrağı belə qoyulmur).
 * - Hər məktub UIDVALIDITY:UID və Message-ID ilə qeyd olunur, ikinci dəfə hesablanmır.
 * - Mail şifrəsi Android Keystore ilə şifrələnmiş [SecureStore]-dan oxunur.
 */
class DirectMailSync(
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val secureStore: SecureStore,
    private val ocr: OcrEngine,
    private val pdf: PdfTextExtractor,
) {
    private val ingestor = PaymentIngestor(db.payments(), db.audit())

    private fun session(cfg: MailConfig): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", cfg.host)
            put("mail.imaps.port", cfg.port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "60000")
            put("mail.imaps.writetimeout", "30000")
            put("mail.imaps.peek", "true")
            put("mail.imaps.partialfetch", "false")
        }
        return Session.getInstance(props)
    }

    private fun connect(cfg: MailConfig, password: String): Store {
        val store = session(cfg).getStore("imaps")
        store.connect(cfg.host.trim(), cfg.port, cfg.email.trim().lowercase(), password)
        return store
    }

    suspend fun testConnection(cfg: MailConfig, password: String?): ConnectionTestResult = withContext(Dispatchers.IO) {
        // Kopyalanan parolda təsadüfi boşluqlar ola bilər; Mail.ru tətbiq parollarında boşluq olmur
        val pass = (password?.takeIf { it.isNotBlank() } ?: secureStore.getString(SecureStore.MAIL_PASSWORD))?.let(::cleanPassword)
        if (cfg.email.isBlank() || pass.isNullOrEmpty()) return@withContext ConnectionTestResult(false, error = "Email və şifrə daxil edilməyib")
        val started = System.currentTimeMillis()
        var store: Store? = null
        try {
            store = connect(cfg, pass)
            val folder = store.getFolder(cfg.mailbox)
            folder.open(Folder.READ_ONLY)
            val res = ConnectionTestResult(true, folder.messageCount, folder.unreadMessageCount, System.currentTimeMillis() - started)
            folder.close(false)
            res
        } catch (e: Exception) {
            ConnectionTestResult(false, error = humanize(e))
        } finally {
            runCatching { store?.close() }
        }
    }

    suspend fun sync(trigger: String, maxMessages: Int = 300): SyncRunEntity = withContext(Dispatchers.IO) {
        val cfg = settings.current.mail
        val started = System.currentTimeMillis()
        val runId = db.syncRuns().insert(SyncRunEntity(trigger = trigger, startedAt = started, finishedAt = null, status = "RUNNING"))
        val password = secureStore.getString(SecureStore.MAIL_PASSWORD)?.let(::cleanPassword)
        if (cfg.email.isBlank() || password.isNullOrEmpty()) {
            return@withContext finish(runId, trigger, started, "FAILED", IngestStats(), 0, 0, "Mail hesabı konfiqurasiya edilməyib (Ayarlar → Mail.ru)")
        }

        val totals = IngestStats()
        var fetched = 0
        var errors = 0
        var store: Store? = null
        try {
            store = connect(cfg, password)
            val folder = store.getFolder(cfg.mailbox) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val uidValidity = folder.uidValidity
                var lastUid = settings.imapLastUid
                if (settings.imapUidValidity != uidValidity) lastUid = 0 // Message-ID ilə təkrarlar yenə bloklanır

                val messages: Array<Message> = if (lastUid > 0) {
                    folder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID)
                } else {
                    val since = Date(System.currentTimeMillis() - cfg.initialDays * 24L * 3600 * 1000)
                    folder.search(ReceivedDateTerm(ComparisonTerm.GE, since))
                }
                val ordered = messages
                    .map { it to folder.getUID(it) }
                    .filter { it.second > lastUid }
                    .sortedBy { it.second }
                    .take(maxMessages)

                for ((msg, uid) in ordered) {
                    fetched++
                    try {
                        totals += processMessage(msg as MimeMessage, uidValidity, uid, cfg)
                    } catch (e: Exception) {
                        errors++
                        db.processedEmails().insert(
                            ProcessedEmailEntity("$uidValidity:$uid", null, null, null, null, "ERROR", 0, e.message?.take(500))
                        )
                    }
                    lastUid = maxOf(lastUid, uid)
                    settings.imapLastUid = lastUid
                    settings.imapUidValidity = uidValidity
                }
                settings.imapUidValidity = uidValidity
            } finally {
                runCatching { folder.close(false) }
            }
            val msg = "$fetched məktub oxundu, ${totals.created} təsdiqlənmiş, ${totals.pending} gözləyən, ${totals.duplicates} təkrar"
            finish(runId, trigger, started, "SUCCESS", totals, fetched, errors, msg)
        } catch (e: Exception) {
            finish(runId, trigger, started, "FAILED", totals, fetched, errors + 1, humanize(e))
        } finally {
            runCatching { store?.close() }
        }
    }

    private suspend fun finish(
        runId: Long, trigger: String, started: Long, status: String, s: IngestStats, fetched: Int, errors: Int, message: String,
    ): SyncRunEntity {
        val run = SyncRunEntity(
            id = runId, trigger = trigger, startedAt = started, finishedAt = System.currentTimeMillis(), status = status,
            fetched = fetched, created = s.created, pending = s.pending, duplicates = s.duplicates, errors = errors, message = message,
        )
        db.syncRuns().update(run)
        return run
    }

    private suspend fun processMessage(msg: MimeMessage, uidValidity: Long, uid: Long, cfg: MailConfig): IngestStats {
        val key = "$uidValidity:$uid"
        if (db.processedEmails().countKey(key) > 0) return IngestStats(duplicates = 1)

        val messageId = msg.messageID ?: "<uid-$uidValidity-$uid@${cfg.email}>"
        val from = runCatching { msg.from?.joinToString { it.toString() } }.getOrNull().orEmpty()
        val subject = runCatching { msg.subject }.getOrNull().orEmpty()
        val received = (msg.sentDate ?: msg.receivedDate ?: Date()).time

        fun record(result: String, created: Int, error: String? = null) =
            ProcessedEmailEntity(key, messageId, from.take(500), subject.take(1000), received, result, created, error)

        if (db.processedEmails().countMessageId(messageId) > 0) {
            db.processedEmails().insert(record("DUPLICATE", 0))
            return IngestStats(duplicates = 1)
        }
        if (!matches(from, cfg.senderFilter) || !matches(subject, cfg.subjectFilter)) {
            db.processedEmails().insert(record("IGNORED", 0))
            return IngestStats()
        }

        val parts = mutableListOf<ContentPart>()
        val errors = mutableListOf<String>()
        val bodies = Bodies()
        collectParts(msg, parts, errors, bodies)
        val body = bodies.plain.ifBlank { Html.toText(bodies.html) }
        if (body.isNotBlank()) parts.add(0, ContentPart(SourceType.TEXT, body, "body"))

        val items = ingestor.collect(parts)
        val rawText = parts.joinToString("\n") { if (it.kind == SourceType.TEXT) it.text else "--- ${it.kind}: ${it.name} ---\n${it.text}" }

        return db.withTransaction {
            val stats = ingestor.ingest(items, "MSG:$messageId", received, "mail-worker", rawText, messageId, subject, from)
            val result = when {
                items.isEmpty() && errors.isNotEmpty() -> "ERROR"
                items.isEmpty() -> "IGNORED"
                items.all { it.parsed.status == "PENDING" } -> "PENDING"
                else -> "PAYMENT"
            }
            db.processedEmails().insert(record(result, stats.created + stats.pending, errors.joinToString("; ").take(500).ifEmpty { null }))
            stats
        }
    }

    private class Bodies(var plain: String = "", var html: String = "")

    private suspend fun collectParts(part: Part, out: MutableList<ContentPart>, errors: MutableList<String>, bodies: Bodies) {
        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { part.fileName }.getOrNull()
        val isAttachment = Part.ATTACHMENT.equals(disposition, true) || !fileName.isNullOrEmpty()
        when {
            part.isMimeType("multipart/*") -> {
                val mp = part.content as Multipart
                for (i in 0 until mp.count) collectParts(mp.getBodyPart(i), out, errors, bodies)
            }
            part.isMimeType("message/rfc822") -> collectParts(part.content as Part, out, errors, bodies)
            part.isMimeType("text/plain") && !isAttachment -> bodies.plain += (part.content as? String).orEmpty() + "\n"
            part.isMimeType("text/html") && !isAttachment -> bodies.html += (part.content as? String).orEmpty() + "\n"
            part.isMimeType("application/pdf") || fileName?.endsWith(".pdf", true) == true -> {
                try {
                    val res = pdf.extract(part.inputStream.use { it.readBytes() })
                    if (res.text.isNotBlank()) {
                        out += ContentPart(if (res.viaOcr) SourceType.IMAGE else SourceType.PDF, res.text, fileName ?: "pdf")
                    }
                } catch (e: Exception) {
                    errors += "${fileName ?: "pdf"}: ${e.message}"
                }
            }
            part.isMimeType("image/*") -> {
                try {
                    val text = ocr.recognize(part.inputStream.use { it.readBytes() })
                    if (text.isNotBlank()) out += ContentPart(SourceType.IMAGE, text, fileName ?: "image")
                } catch (e: Exception) {
                    errors += "${fileName ?: "image"}: ${e.message}"
                }
            }
        }
    }

    private fun matches(value: String, filter: String): Boolean {
        if (filter.isBlank()) return true
        val v = value.lowercase()
        return filter.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { v.contains(it) }
    }

    companion object {
        fun cleanPassword(p: String): String = p.filterNot { it.isWhitespace() }
    }

    private fun humanize(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is AuthenticationFailedException -> {
                val reply = msg.trim()
                val hint = when {
                    Regex("(?i)application password|app password|пароль для внешн").containsMatchIn(reply) ->
                        "Mail.ru xarici tətbiq parolu tələb edir — əsas hesab şifrəsi qəbul olunmur."
                    Regex("(?i)disabled|not allowed|imap.*(off|запрещ|отключ)|доступ").containsMatchIn(reply) ->
                        "Hesabda IMAP girişi bağlıdır: Mail.ru → Ayarlar → \"Poçt proqramları\" bölməsində IMAP ilə girişi aktiv edin."
                    else ->
                        "Email və ya tətbiq parolu yanlışdır. Parolu yaradarkən giriş növü \"IMAP / tam giriş\" seçilməlidir və IMAP aktiv olmalıdır."
                }
                if (reply.isNotEmpty()) "$hint\nServer cavabı: ${reply.take(200)}" else hint
            }
            e.cause is java.net.UnknownHostException || e is java.net.UnknownHostException -> "Server tapılmadı. İnternet bağlantısını yoxlayın."
            e.cause is java.net.SocketTimeoutException -> "Bağlantı vaxtı bitdi."
            e.cause is javax.net.ssl.SSLException -> "SSL/TLS xətası: ${e.cause?.message}"
            e is MessagingException && e.nextException != null -> humanize(e.nextException)
            msg.isNotBlank() -> msg.take(400)
            else -> e.javaClass.simpleName
        }
    }
}
