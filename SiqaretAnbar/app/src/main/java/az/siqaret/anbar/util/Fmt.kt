package az.siqaret.anbar.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    @Suppress("DEPRECATION")
    val AZ: Locale = Locale("az", "AZ")

    private fun symbols() = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
        decimalSeparator = '.'
    }

    fun number(v: Double): String =
        DecimalFormat("#,##0.00", symbols()).apply { roundingMode = RoundingMode.HALF_UP }.format(v)

    fun money(v: Double): String = number(v) + " ₼"
    fun moneyAzn(v: Double): String = number(v) + " AZN"
    fun count(v: Long): String = DecimalFormat("#,##0", symbols()).format(v)
    fun count(v: Int): String = count(v.toLong())
    fun signed(v: Int): String = if (v > 0) "+${count(v)}" else if (v < 0) "−${count(-v)}" else "0"

    /** "12,5" və ya "12.5" qəbul edir. */
    fun parseDecimal(s: String): Double? {
        val t = s.trim().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    /** Daxiletmə sahəsi üçün: yalnız rəqəmlər, bir ayırıcı və maksimum 2 onluq rəqəm. */
    fun sanitizeDecimal(s: String): String {
        val sb = StringBuilder()
        var sep = false
        var decimals = 0
        for (ch in s) {
            when {
                ch.isDigit() -> {
                    if (sep) { if (decimals >= 2) continue; decimals++ }
                    if (sb.length < 12) sb.append(ch)
                }
                (ch == '.' || ch == ',') && !sep -> { sep = true; sb.append(if (sb.isEmpty()) "0." else ".") }
            }
        }
        return sb.toString()
    }

    fun sanitizeInt(s: String): String = s.filter { it.isDigit() }.take(7).trimStart('0').ifEmpty {
        if (s.any { it.isDigit() }) "0" else ""
    }

    fun inputNumber(v: Double): String =
        BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", AZ).format(Date(ms))
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", AZ).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", AZ).format(Date(ms))
    fun fileStamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(ms))
    fun longDate(ms: Long): String = SimpleDateFormat("d MMMM yyyy, EEEE", AZ).format(Date(ms))

    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayTitle(ms: Long, now: Long = System.currentTimeMillis()): String {
        val today = dayStart(now)
        val day = dayStart(ms)
        val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        return when (day) {
            today -> "Bu gün · ${date(ms)}"
            yesterday -> "Dünən · ${date(ms)}"
            else -> longDate(ms)
        }
    }
}
