package az.payreport.app.domain

import az.payreport.app.core.format.Fmt
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentStatus
import java.time.LocalDate
import java.time.YearMonth

data class DayTotal(val date: LocalDate, val count: Int, val totalMinor: Long)

data class PhoneSummary(val phone: String, val count: Int, val totalMinor: Long, val lastPaidAt: Long)

data class MonthlyReport(
    val year: Int,
    val month: Int,
    val totalMinor: Long,
    val count: Int,
    val averageMinor: Long,
    val daily: List<DayTotal>,
    val byPhone: List<PhoneSummary>,
    val payments: List<PaymentEntity>,
) {
    val title: String get() = Fmt.monthYear(year, month)
    val topPayers: List<PhoneSummary> get() = byPhone.take(10)
    val bestDay: DayTotal? get() = daily.maxByOrNull { it.totalMinor }?.takeIf { it.totalMinor > 0 }
}

data class PhoneReport(
    val phone: String,
    val count: Int,
    val totalMinor: Long,
    val lastPaidAt: Long?,
    val pendingCount: Int,
    val monthly: List<MonthTotal>,
    val payments: List<PaymentEntity>,
)

data class MonthTotal(val year: Int, val month: Int, val count: Int, val totalMinor: Long) {
    val label: String get() = "${Fmt.MONTHS[month - 1].take(3)} ${year % 100}"
}

object Reports {

    fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val ym = YearMonth.of(year, month)
        return Fmt.startOfDay(ym.atDay(1)) to Fmt.startOfDay(ym.plusMonths(1).atDay(1))
    }

    /** Yalnız təsdiqlənmiş ödənişlərdən aylıq hesabat qurur. */
    fun monthly(year: Int, month: Int, payments: List<PaymentEntity>): MonthlyReport {
        val confirmed = payments.filter { it.status == PaymentStatus.CONFIRMED && it.amountMinor != null }
        val ym = YearMonth.of(year, month)
        val byDay = confirmed.groupBy { Fmt.toLocalDate(it.paidAt) }
        val daily = (1..ym.lengthOfMonth()).map { d ->
            val date = ym.atDay(d)
            val list = byDay[date].orEmpty()
            DayTotal(date, list.size, list.sumOf { it.amountMinor!! })
        }
        val byPhone = confirmed.filter { it.phone != null }.groupBy { it.phone!! }
            .map { (phone, list) -> PhoneSummary(phone, list.size, list.sumOf { it.amountMinor!! }, list.maxOf { it.paidAt }) }
            .sortedWith(compareByDescending<PhoneSummary> { it.totalMinor }.thenByDescending { it.count })
        val total = confirmed.sumOf { it.amountMinor!! }
        return MonthlyReport(
            year, month, total, confirmed.size,
            if (confirmed.isEmpty()) 0 else total / confirmed.size,
            daily, byPhone, confirmed.sortedBy { it.paidAt },
        )
    }

    fun phone(phone: String, payments: List<PaymentEntity>): PhoneReport {
        val confirmed = payments.filter { it.status == PaymentStatus.CONFIRMED && it.amountMinor != null }
        val monthly = confirmed.groupBy { Fmt.toLocalDate(it.paidAt).let { d -> d.year to d.monthValue } }
            .map { (k, list) -> MonthTotal(k.first, k.second, list.size, list.sumOf { it.amountMinor!! }) }
            .sortedWith(compareBy({ it.year }, { it.month }))
        return PhoneReport(
            phone = phone,
            count = confirmed.size,
            totalMinor = confirmed.sumOf { it.amountMinor!! },
            lastPaidAt = confirmed.maxOfOrNull { it.paidAt },
            pendingCount = payments.count { it.status == PaymentStatus.PENDING },
            monthly = monthly,
            payments = payments.sortedByDescending { it.paidAt },
        )
    }

    /** Dashboard qrafiki üçün son N günün cəmi. */
    fun lastDays(today: LocalDate, days: Int, payments: List<PaymentEntity>): List<DayTotal> {
        val byDay = payments.filter { it.status == PaymentStatus.CONFIRMED }.groupBy { Fmt.toLocalDate(it.paidAt) }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val list = byDay[d].orEmpty()
            DayTotal(d, list.size, list.sumOf { it.amountMinor ?: 0 })
        }
    }
}
