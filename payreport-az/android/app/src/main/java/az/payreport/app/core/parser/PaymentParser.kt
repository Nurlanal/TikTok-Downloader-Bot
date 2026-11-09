package az.payreport.app.core.parser

/**
 * Ödəniş bildirişi parseri.
 *
 * Backend-dəki `backend/src/parser/index.js` ilə eyni alqoritmdir və hər ikisi
 * `shared/parser-fixtures.json` ilə test olunur. Qaydaları dəyişəndə hər iki faylı yeniləyin.
 */
object PaymentParser {

    const val AUTO_CONFIRM_THRESHOLD = 85
    const val MAX_AMOUNT_MINOR = 100_000L * 100

    enum class SourceType { TEXT, PDF, IMAGE }

    data class ParsedPayment(
        val phone: String?,
        val amountMinor: Long?,
        val txnId: String?,
        /** Bakı yerli vaxtı "YYYY-MM-DDTHH:mm:ss"; null — mətndə tarix yoxdur. */
        val paidAt: String?,
        val confidence: Int,
        val status: String,
        val rawLine: String,
    ) {
        val isConfirmed: Boolean get() = status == STATUS_CONFIRMED
    }

    const val STATUS_CONFIRMED = "CONFIRMED"
    const val STATUS_PENDING = "PENDING"

    private const val PREFIXES = "10|12|50|51|55|60|70|77|99"
    private val PHONE_RE = Regex(
        "(?<![\\d+])(?:\\+?994[\\s-]?\\(?|\\(?0)($PREFIXES)\\)?[\\s-]?(\\d{3})[\\s-]?(\\d{2})[\\s-]?(\\d{2})(?!\\d)"
    )

    private const val NUMBER =
        "(\\d{1,3}(?:[ ,]\\d{3})+(?:\\.\\d{1,2})?|\\d{1,3}(?: \\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    private val AMOUNT_CURRENCY_RE = Regex("(?iu)(?<![\\d.,:])$NUMBER\\s*azn(?!\\p{L})")
    private val AMOUNT_PREFIX_RE = Regex("(?iu)(?<!\\p{L})azn\\s*(\\d+(?:[.,]\\d{1,2})?)(?!\\d)")
    private val AMOUNT_LABEL_RE = Regex(
        "(?iu)(?<!\\p{L})(?:məbləğ|mebleg|məbləq|meblegi|məbləği|amount|summa|cəmi|cemi)\\s*[:=\\-]?\\s*(\\d+(?:[.,]\\d{1,2})?)(?![\\d.,]|\\s*azn)"
    )

    private val LABEL_KEYWORDS = Regex("(?iu)(məbləğ|mebleg|məbləq|amount|summa|cəmi|cemi|ödəniş|odenis|ödənildi|payment)")
    private val EXCLUDE_KEYWORDS = Regex(
        "(?iu)(balans|balance|qalıq|qaliq|komissiya|commission|fee|limit|bonus|cashback|keşbek|endirim|discount|borc)"
    )

    private val TXN_RE = Regex(
        "(?iu)(?<![\\p{L}\\d])(?:əməliyyat(?:ın)?\\s*(?:id|№|nömrəsi|nomresi|kodu|no)|emeliyyat\\s*(?:id|№|no)|" +
            "transaction\\s*(?:id|no|number)|txn\\s*id|trx\\s*id|tranzaksiya\\s*(?:id|№|no)?|qəbz\\s*(?:№|nömrəsi|no)|" +
            "qebz\\s*(?:№|no)|çek\\s*(?:№|no)|rrn|reference|ref\\.?(?:\\s*no)?|id)(?!\\p{L})\\s*[:#№.]?\\s*" +
            "([A-Za-z0-9][A-Za-z0-9\\-_/]{3,40})"
    )

    private val DATE_DMY_RE =
        Regex("(?<!\\d)(\\d{1,2})[./](\\d{1,2})[./](\\d{4})(?:[\\sT,]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?(?!\\d)")
    private val DATE_ISO_RE =
        Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?:[\\sT]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?(?!\\d)")
    private val TIME_RE = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])")
    private val MANAT_RE = Regex("(?iu)(?<!\\p{L})(?:manat|man\\.)(?!\\p{L})")
    private val CLAUSE_SPLIT_RE = Regex("(, |; |\\. |[;|!?])")

    fun normalizeText(text: String): String = text
        .replace(Regex("\\r\\n?"), "\n")
        .replace(Regex("[\\u00A0\\u2007\\u202F\\t]"), " ")
        .replace("İ", "I")
        .replace("ı", "i")
        .replace("₼", " AZN")
        .replace(MANAT_RE, "AZN")
        .replace(Regex(" {2,}"), " ")

    fun amountToMinor(raw: String): Long? {
        var s = raw.replace(Regex("\\s"), "")
        s = if (Regex("^\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?$").matches(s)) s.replace(",", "") else s.replaceFirst(',', '.')
        val parts = s.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = ((parts.getOrNull(1) ?: "") + "00").take(2).toLongOrNull() ?: return null
        return whole * 100 + frac
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    private fun validDate(y: Int, mo: Int, d: Int) = y in 2000..2100 && mo in 1..12 && d in 1..31

    private fun timeOf(h: String?, mi: String?, s: String?): String? {
        if (h.isNullOrEmpty() || mi.isNullOrEmpty()) return null
        val hh = h.toInt()
        val mm = mi.toInt()
        val ss = if (s.isNullOrEmpty()) 0 else s.toInt()
        if (hh > 23 || mm > 59 || ss > 59) return null
        return "${pad(hh)}:${pad(mm)}:${pad(ss)}"
    }

    private data class FoundDate(val index: Int, val date: String, val time: String?)

    private fun findDate(text: String): FoundDate? {
        val found = mutableListOf<FoundDate>()
        for (m in DATE_DMY_RE.findAll(text)) {
            val g = m.groupValues
            val d = g[1].toInt(); val mo = g[2].toInt(); val y = g[3].toInt()
            if (!validDate(y, mo, d)) continue
            found += FoundDate(m.range.first, "$y-${pad(mo)}-${pad(d)}", timeOf(g[4], g[5], g[6]))
        }
        for (m in DATE_ISO_RE.findAll(text)) {
            val g = m.groupValues
            val y = g[1].toInt(); val mo = g[2].toInt(); val d = g[3].toInt()
            if (!validDate(y, mo, d)) continue
            found += FoundDate(m.range.first, "$y-${pad(mo)}-${pad(d)}", timeOf(g[4], g[5], g[6]))
        }
        return found.minByOrNull { it.index }
    }

    private fun findTime(text: String): String? {
        for (m in TIME_RE.findAll(text)) {
            val t = timeOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (t != null) return t
        }
        return null
    }

    private fun stripDates(text: String) = text.replace(DATE_DMY_RE, " ").replace(DATE_ISO_RE, " ")

    private fun resolveDateTime(scope: String, full: String): String? {
        val d = findDate(scope) ?: return null
        val time = d.time ?: findTime(stripDates(scope)) ?: findTime(stripDates(full))
        return "${d.date}T${time ?: "00:00:00"}"
    }

    private fun maskDates(text: String): String = text
        .replace(DATE_DMY_RE) { "#".repeat(it.value.length) }
        .replace(DATE_ISO_RE) { "#".repeat(it.value.length) }
        .replace(TIME_RE) { "#".repeat(it.value.length) }

    private fun findPhones(text: String): List<String> =
        PHONE_RE.findAll(maskDates(text)).map { m ->
            val g = m.groupValues
            "+994${g[1]}${g[2]}${g[3]}${g[4]}"
        }.toList()

    private fun maskPhones(text: String): String = maskDates(text).replace(PHONE_RE) { "§".repeat(it.value.length) }

    private fun findTxnIds(text: String): List<String> {
        val ids = mutableListOf<String>()
        for (m in TXN_RE.findAll(text)) {
            val v = m.groupValues[1].replace(Regex("[.\\-_/]+$"), "").uppercase()
            if (!v.any { it.isDigit() } || v.length < 4) continue
            if (v !in ids) ids += v
        }
        return ids
    }

    private data class Amount(val index: Int, val minor: Long, val excluded: Boolean)

    private fun findAmounts(line: String): List<Amount> {
        val masked = maskPhones(line)
        val clauses = mutableListOf<IntRange>()
        var start = 0
        for (m in CLAUSE_SPLIT_RE.findAll(masked)) {
            clauses += start until m.range.first
            start = m.range.last + 1
        }
        clauses += start until masked.length
        fun clauseOf(idx: Int): String {
            val r = clauses.firstOrNull { idx in it } ?: return masked
            return masked.substring(r.first, r.last + 1)
        }

        val found = mutableListOf<Amount>()
        val taken = mutableListOf<IntRange>()
        fun add(range: IntRange, raw: String) {
            if (taken.any { range.first <= it.last && range.last >= it.first }) return
            taken += range
            val minor = amountToMinor(raw) ?: return
            if (minor <= 0) return
            found += Amount(range.first, minor, EXCLUDE_KEYWORDS.containsMatchIn(clauseOf(range.first)))
        }
        AMOUNT_CURRENCY_RE.findAll(masked).forEach { add(it.range, it.groupValues[1]) }
        AMOUNT_PREFIX_RE.findAll(masked).forEach { add(it.range, it.groupValues[1]) }
        AMOUNT_LABEL_RE.findAll(masked).forEach { add(it.range, it.groupValues[1]) }
        return found.sortedBy { it.index }
    }

    private fun finalize(
        phone: String?, amountMinor: Long?, txnId: String?, paidAt: String?, rawLine: String,
        source: SourceType, base: Int, bonus: Int,
    ): ParsedPayment {
        var confidence = base + bonus
        if (source == SourceType.IMAGE) confidence -= 15
        confidence = confidence.coerceIn(0, 99)
        if (amountMinor != null && amountMinor > MAX_AMOUNT_MINOR) confidence = minOf(confidence, 40)
        val valid = phone != null && amountMinor != null && amountMinor > 0 && amountMinor <= MAX_AMOUNT_MINOR
        return ParsedPayment(
            phone = phone,
            amountMinor = amountMinor,
            txnId = txnId,
            paidAt = paidAt,
            confidence = confidence,
            status = if (valid && confidence >= AUTO_CONFIRM_THRESHOLD) STATUS_CONFIRMED else STATUS_PENDING,
            rawLine = rawLine,
        )
    }

    fun parse(text: String, source: SourceType = SourceType.TEXT): List<ParsedPayment> {
        val t = normalizeText(text)
        val lines = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val docTxnIds = findTxnIds(t)
        val docDate = resolveDateTime(t, t)

        // 1-ci mərhələ: eyni sətirdə nömrə + məbləğ
        data class Pair(val phone: String, val amount: Long, var txn: String?, val paidAt: String?, val raw: String)
        val pairs = mutableListOf<Pair>()
        for (line in lines) {
            val phones = findPhones(line)
            if (phones.size != 1) continue
            val amounts = findAmounts(line).filter { !it.excluded }
            if (amounts.isEmpty()) continue
            pairs += Pair(phones[0], amounts[0].minor, findTxnIds(line).firstOrNull(), resolveDateTime(line, line) ?: docDate, line)
        }
        if (pairs.isNotEmpty()) {
            if (pairs.size == 1 && pairs[0].txn == null && docTxnIds.size == 1) pairs[0].txn = docTxnIds[0]
            return pairs.map {
                finalize(it.phone, it.amount, it.txn, it.paidAt, it.raw, source, 95, if (it.txn != null) 5 else 0)
            }
        }

        // 2-ci mərhələ: bütün sənəd üzrə (qəbz, PDF, OCR)
        val phones = findPhones(t).distinct()
        data class Candidate(val minor: Long, val labelled: Boolean)
        val candidates = mutableListOf<Candidate>()
        for (line in lines) {
            val labelled = LABEL_KEYWORDS.containsMatchIn(line)
            findAmounts(line).filter { !it.excluded }.forEach { candidates += Candidate(it.minor, labelled) }
        }
        val distinct = candidates.map { it.minor }.distinct()
        val labelledDistinct = candidates.filter { it.labelled }.map { it.minor }.distinct()

        var amount: Long? = null
        var ambiguous = false
        var labelled = false
        when {
            labelledDistinct.size == 1 -> { amount = labelledDistinct[0]; labelled = true }
            labelledDistinct.isEmpty() && distinct.size == 1 -> amount = distinct[0]
            distinct.isNotEmpty() -> { amount = labelledDistinct.firstOrNull() ?: distinct[0]; ambiguous = true }
        }
        // Məbləğ olmadan qeyd yaradılmır (məs. imzadakı telefon nömrəsi)
        if (amount == null) return emptyList()

        val phone = phones.firstOrNull()
        val base = when {
            phones.size == 1 && !ambiguous -> 85
            phone != null -> 50
            else -> 30
        }
        val txnId = if (docTxnIds.size == 1) docTxnIds[0] else null
        var bonus = 0
        if (base == 85) {
            if (labelled) bonus += 5
            if (txnId != null) bonus += 5
        }
        return listOf(finalize(phone, amount, txnId, docDate, lines.take(12).joinToString(" | "), source, base, bonus))
    }
}
