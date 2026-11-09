package az.payreport.app.core.format

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Bakı vaxtı və AZN formatlaşdırması. */
object Fmt {
    val ZONE: ZoneId = ZoneId.of("Asia/Baku")

    val MONTHS = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun",
        "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr",
    )
    private val MONTHS_SHORT = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avq", "sen", "okt", "noy", "dek")
    val WEEKDAYS_SHORT = listOf("B.e", "Ç.a", "Ç", "C.a", "C", "Ş", "B")

    /** 125050 -> "1 250,50" */
    fun amount(minor: Long?): String {
        if (minor == null) return "—"
        val neg = minor < 0
        val abs = kotlin.math.abs(minor)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val frac = (abs % 100).toString().padStart(2, '0')
        return (if (neg) "-" else "") + "$whole,$frac"
    }

    fun azn(minor: Long?): String = if (minor == null) "—" else "${amount(minor)} AZN"

    /** "5", "5.5", "5,50", "1 250,50" -> qəpik */
    fun parseAmountInput(s: String): Long? {
        val clean = s.trim().replace(" ", "").replace(',', '.')
        if (!Regex("^\\d{1,7}(\\.\\d{1,2})?$").matches(clean)) return null
        val parts = clean.split('.')
        val minor = parts[0].toLong() * 100 + ((parts.getOrNull(1) ?: "") + "00").take(2).toLong()
        return if (minor > 0) minor else null
    }

    fun dateTime(epochMs: Long): String {
        val z = Instant.ofEpochMilli(epochMs).atZone(ZONE)
        return "%02d %s %d, %02d:%02d".format(z.dayOfMonth, MONTHS_SHORT[z.monthValue - 1], z.year, z.hour, z.minute)
    }

    fun date(epochMs: Long): String {
        val z = Instant.ofEpochMilli(epochMs).atZone(ZONE)
        return "%02d.%02d.%d".format(z.dayOfMonth, z.monthValue, z.year)
    }

    fun time(epochMs: Long): String {
        val z = Instant.ofEpochMilli(epochMs).atZone(ZONE)
        return "%02d:%02d".format(z.hour, z.minute)
    }

    fun monthYear(year: Int, month: Int) = "${MONTHS[month - 1]} $year"

    fun shortDay(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

    fun toLocalDate(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate()

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)

    /** Parser-in qaytardığı "YYYY-MM-DDTHH:mm:ss" (Bakı) -> epoch millis */
    fun parseLocal(local: String?): Long? = runCatching {
        LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli()
    }.getOrNull()

    fun parseIso(iso: String?): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

    fun iso(epochMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

    /** "dd.MM.yyyy HH:mm" -> epoch */
    fun parseUserDateTime(s: String): Long? = runCatching {
        val f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        LocalDateTime.parse(s.trim(), f).atZone(ZONE).toInstant().toEpochMilli()
    }.getOrNull()

    fun userDateTime(epochMs: Long): String {
        val z = Instant.ofEpochMilli(epochMs).atZone(ZONE)
        return "%02d.%02d.%d %02d:%02d".format(z.dayOfMonth, z.monthValue, z.year, z.hour, z.minute)
    }

    fun relative(epochMs: Long): String {
        if (epochMs <= 0) return "heç vaxt"
        val diff = (System.currentTimeMillis() - epochMs) / 1000
        return when {
            diff < 60 -> "indicə"
            diff < 3600 -> "${diff / 60} dəq əvvəl"
            diff < 86400 -> "${diff / 3600} saat əvvəl"
            else -> dateTime(epochMs)
        }
    }
}

/** Dashboard dövrləri (Bakı vaxtı ilə). */
data class Periods(
    val today: Long,
    val tomorrow: Long,
    val yesterday: Long,
    val weekStart: Long,
    val monthStart: Long,
    val chartStart: Long,
    val todayDate: LocalDate,
) {
    companion object {
        fun now(): Periods {
            val d = Fmt.now().toLocalDate()
            return Periods(
                today = Fmt.startOfDay(d),
                tomorrow = Fmt.startOfDay(d.plusDays(1)),
                yesterday = Fmt.startOfDay(d.minusDays(1)),
                weekStart = Fmt.startOfDay(d.minusDays((d.dayOfWeek.value - 1).toLong())),
                monthStart = Fmt.startOfDay(d.withDayOfMonth(1)),
                chartStart = Fmt.startOfDay(d.minusDays(13)),
                todayDate = d,
            )
        }
    }
}
