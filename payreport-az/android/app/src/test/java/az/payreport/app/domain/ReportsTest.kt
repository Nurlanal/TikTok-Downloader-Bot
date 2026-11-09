package az.payreport.app.domain

import az.payreport.app.core.format.Fmt
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentStatus
import az.payreport.app.export.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile

class ReportsTest {

    private var seq = 0L
    private fun pay(phone: String, minor: Long, date: LocalDate, hour: Int = 12, status: String = PaymentStatus.CONFIRMED) =
        PaymentEntity(
            id = ++seq, dedupeKey = "K$seq", phone = phone, amountMinor = minor,
            paidAt = Fmt.startOfDay(date) + hour * 3_600_000L, status = status, source = "TEXT", confidence = 95, txnId = "T$seq",
        )

    private val nov = LocalDate.of(2026, 11, 1)
    private val payments = buildList {
        repeat(3) { add(pay("+994557776655", 500, nov.plusDays(8))) } // 3 x 5 AZN
        add(pay("+994501234567", 1250, nov.plusDays(8), hour = 23)) // Bakı vaxtı 23:00 — hələ 9 noyabr
        add(pay("+994705554433", 2000, nov.plusDays(1)))
        add(pay("+994705554433", 9900, nov.plusDays(2), status = PaymentStatus.PENDING)) // daxil edilmir
        add(pay("+994705554433", 7700, nov.plusDays(3), status = PaymentStatus.CANCELLED)) // daxil edilmir
    }

    @Test
    fun monthlyReportCountsOnlyConfirmed() {
        val r = Reports.monthly(2026, 11, payments)
        assertEquals(30, r.daily.size)
        assertEquals(1500L + 1250 + 2000, r.totalMinor)
        assertEquals(5, r.count)
        assertEquals(2750L, r.daily[8].totalMinor) // 9 noyabr
        assertEquals(4, r.daily[8].count)
        assertEquals("+994705554433", r.topPayers.first().phone)
        assertEquals(3, r.byPhone.first { it.phone == "+994557776655" }.count)
        assertEquals(1500L, r.byPhone.first { it.phone == "+994557776655" }.totalMinor)
    }

    @Test
    fun phoneReport() {
        val r = Reports.phone("+994705554433", payments.filter { it.phone == "+994705554433" })
        assertEquals(1, r.count)
        assertEquals(2000L, r.totalMinor)
        assertEquals(1, r.pendingCount)
        assertEquals(3, r.payments.size)
    }

    @Test
    fun hundredPaymentsOfFiveAzn() {
        val list = (1..100).map { pay("+994557776655", 500, nov.plusDays((it % 28).toLong())) }
        assertEquals(50_000L, Reports.monthly(2026, 11, list).totalMinor)
    }

    @Test
    fun amountFormatting() {
        assertEquals("1 250,50 AZN", Fmt.azn(125050))
        assertEquals("15,00 AZN", Fmt.azn(1500))
        assertEquals(550L, Fmt.parseAmountInput("5,5"))
        assertEquals(125050L, Fmt.parseAmountInput("1 250,50"))
    }

    @Test
    fun xlsxIsValidZipWithAllSheets() {
        val out = File(System.getProperty("java.io.tmpdir"), "payreport-test.xlsx")
        out.outputStream().use { ReportExporter.monthlyXlsx(Reports.monthly(2026, 11, payments)).write(it) }
        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet4.xml")))
            val wb = zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(wb.contains("Gündəlik mədaxil"))
        }
        System.getenv("XLSX_OUT")?.let { out.copyTo(File(it), overwrite = true) }
    }
}
