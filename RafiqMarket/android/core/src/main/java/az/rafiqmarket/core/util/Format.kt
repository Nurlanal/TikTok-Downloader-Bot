package az.rafiqmarket.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BAKU: ZoneId = ZoneId.of("Asia/Baku")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

fun money(v: String?): String = money(v?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
fun money(v: BigDecimal): String = v.setScale(2, RoundingMode.HALF_UP).toPlainString() + " ₼"

private fun parse(iso: String?): OffsetDateTime? = iso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

fun formatDateTime(iso: String?): String = parse(iso)?.atZoneSameInstant(BAKU)?.format(dateTimeFmt) ?: ""
fun formatTime(iso: String?): String = parse(iso)?.atZoneSameInstant(BAKU)?.format(timeFmt) ?: ""

/** "3 dəq əvvəl" */
fun ago(iso: String?): String {
    val t = parse(iso) ?: return ""
    val sec = java.time.Duration.between(t.toInstant(), java.time.Instant.now()).seconds.coerceAtLeast(0)
    return when {
        sec < 60 -> "$sec san əvvəl"
        sec < 3600 -> "${sec / 60} dəq əvvəl"
        sec < 86400 -> "${sec / 3600} saat əvvəl"
        else -> formatDateTime(iso)
    }
}

fun secondsSince(iso: String?): Long {
    val t = parse(iso) ?: return Long.MAX_VALUE
    return java.time.Duration.between(t.toInstant(), java.time.Instant.now()).seconds
}

/** +994501234567 -> +994 50 123 45 67 */
fun prettyPhone(p: String): String {
    val d = p.filter { it.isDigit() }
    if (d.length != 12 || !d.startsWith("994")) return p
    return "+994 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8, 10)} ${d.substring(10, 12)}"
}

fun parseColor(hex: String, fallback: Long = 0xFF2E7D32): Long = runCatching {
    val h = hex.removePrefix("#")
    when (h.length) {
        6 -> 0xFF000000 or h.toLong(16)
        8 -> h.toLong(16)
        else -> fallback
    }
}.getOrDefault(fallback)

object OrderStatus {
    const val PENDING_PAYMENT = "PENDING_PAYMENT"
    const val PLACED = "PLACED"
    const val ACCEPTED = "ACCEPTED"
    const val PREPARING = "PREPARING"
    const val COURIER_ASSIGNED = "COURIER_ASSIGNED"
    const val PICKED_UP = "PICKED_UP"
    const val ON_THE_WAY = "ON_THE_WAY"
    const val ARRIVED = "ARRIVED"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"

    /** Müştəriyə göstərilən 8 mərhələ */
    val STEPS = listOf(PLACED, ACCEPTED, PREPARING, COURIER_ASSIGNED, PICKED_UP, ON_THE_WAY, ARRIVED, DELIVERED)

    val LABELS = mapOf(
        PENDING_PAYMENT to "Ödəniş gözlənilir",
        PLACED to "Sifariş göndərildi",
        ACCEPTED to "Market sifarişi qəbul etdi",
        PREPARING to "Məhsullar hazırlanır",
        COURIER_ASSIGNED to "Kuryer təyin edildi",
        PICKED_UP to "Kuryer sifarişi götürdü",
        ON_THE_WAY to "Sifariş yoldadır",
        ARRIVED to "Kuryer ünvana çatdı",
        DELIVERED to "Sifariş təhvil verildi",
        CANCELLED to "Ləğv edildi",
    )

    fun label(s: String) = LABELS[s] ?: s
    fun isFinal(s: String) = s == DELIVERED || s == CANCELLED
}

fun paymentLabel(method: String) = if (method == "CASH") "Nağd ödəniş" else "Onlayn kart"
fun paymentStatusLabel(s: String) = when (s) {
    "PAID" -> "Ödənilib"
    "PENDING" -> "Gözlənilir"
    "FAILED" -> "Uğursuz"
    "REFUND_REQUIRED" -> "Geri qaytarılmalıdır"
    else -> s
}
