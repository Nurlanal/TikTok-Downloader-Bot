package az.sontarix.market.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateUtils {
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val fileFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    /** Təqvim günləri ilə fərq (saat qurşağından və saatdan asılı deyil). */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun format(date: LocalDate): String = date.format(dateFmt)
    fun formatEpochDay(epochDay: Long): String = format(LocalDate.ofEpochDay(epochDay))
    fun formatMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
    fun fileStamp(): String = java.time.LocalDateTime.now().format(fileFmt)

    fun parse(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), dateFmt) }.getOrNull()
        ?: runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** UTC gece yarısı millis (Material DatePicker) → LocalDate. */
    fun fromUtcMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
    fun toUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    fun remainingText(days: Long): String = when {
        days < -1 -> "${-days} gün əvvəl bitib"
        days == -1L -> "Dünən bitib"
        days == 0L -> "Bu gün bitir"
        days == 1L -> "Sabah bitir (1 gün)"
        else -> "$days gün qalıb"
    }
}

enum class ExpiryStatus(val label: String) {
    NORMAL("Normal"),
    APPROACHING("Yaxınlaşır"),
    CRITICAL("Kritik"),
    TODAY("Bu gün bitir"),
    EXPIRED("Müddəti bitib");

    companion object {
        /**
         * Yaşıl — normal, Sarı — yaxınlaşır (warningDays daxilində), Narıncı — 7 gün və az,
         * Qırmızı — müddəti bitib.
         */
        fun of(daysLeft: Long, warningDays: Int = 30): ExpiryStatus = when {
            daysLeft < 0 -> EXPIRED
            daysLeft == 0L -> TODAY
            daysLeft <= 7 -> CRITICAL
            daysLeft <= warningDays -> APPROACHING
            else -> NORMAL
        }
    }
}
