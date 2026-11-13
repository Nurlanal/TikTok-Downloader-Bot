package az.sontarix.market

import az.sontarix.market.data.AppSettings
import az.sontarix.market.data.Product
import az.sontarix.market.notify.ExpiryChecker
import az.sontarix.market.transfer.XlsxWriter
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ExpiryLogicTest {
    private val settings = AppSettings()

    @Test fun statusColors() {
        assertEquals(ExpiryStatus.EXPIRED, ExpiryStatus.of(-1))
        assertEquals(ExpiryStatus.TODAY, ExpiryStatus.of(0))
        assertEquals(ExpiryStatus.CRITICAL, ExpiryStatus.of(7))
        assertEquals(ExpiryStatus.APPROACHING, ExpiryStatus.of(8))
        assertEquals(ExpiryStatus.APPROACHING, ExpiryStatus.of(30))
        assertEquals(ExpiryStatus.NORMAL, ExpiryStatus.of(31))
    }

    @Test fun thresholdSelection() {
        assertNull(ExpiryChecker.thresholdFor(45, settings))
        assertEquals(30, ExpiryChecker.thresholdFor(30, settings))
        assertEquals(30, ExpiryChecker.thresholdFor(20, settings))
        assertEquals(15, ExpiryChecker.thresholdFor(15, settings))
        assertEquals(7, ExpiryChecker.thresholdFor(5, settings))
        assertEquals(3, ExpiryChecker.thresholdFor(3, settings))
        assertEquals(1, ExpiryChecker.thresholdFor(1, settings))
        assertEquals(0, ExpiryChecker.thresholdFor(0, settings))
        assertEquals(-1, ExpiryChecker.thresholdFor(-4, settings))
        assertNull(ExpiryChecker.thresholdFor(-4, settings.copy(notifyExpired = false)))
        assertEquals(10, ExpiryChecker.thresholdFor(9, settings.copy(thresholds = listOf(10, 2))))
    }

    @Test fun daysBetweenAcrossMonthsAndLeapYears() {
        assertEquals(1, DateUtils.daysBetween(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29)))
        assertEquals(366, DateUtils.daysBetween(LocalDate.of(2028, 1, 1), LocalDate.of(2029, 1, 1)))
        assertEquals(-3, DateUtils.daysBetween(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 17)))
    }

    @Test fun dateParsing() {
        assertEquals(LocalDate.of(2026, 11, 20), DateUtils.parse("20.11.2026"))
        assertEquals(LocalDate.of(2026, 11, 20), DateUtils.parse("2026-11-20"))
        assertNull(DateUtils.parse("32.13.2026"))
        assertEquals("20.11.2026", DateUtils.format(LocalDate.of(2026, 11, 20)))
    }

    @Test fun stats() {
        val today = LocalDate.of(2026, 11, 13)
        fun p(days: Long) = Product(uid = "u$days", name = "x", quantity = 2, expiryEpochDay = today.plusDays(days).toEpochDay())
        val s = ExpiryChecker.computeStats(listOf(p(-2), p(0), p(3), p(20), p(60)), 30, today)
        assertEquals(5, s.total); assertEquals(1, s.expired); assertEquals(1, s.today)
        assertEquals(2, s.approaching); assertEquals(1, s.week); assertEquals(1, s.normal); assertEquals(10, s.totalQuantity)
    }

    @Test fun xlsxIsValidZip() {
        val x = XlsxWriter()
        x.addRow(listOf(XlsxWriter.Text("Ad", 1), XlsxWriter.Text("Say", 1)))
        x.addRow(listOf(XlsxWriter.Text("Süd <1L> & qatıq"), XlsxWriter.Num(20.0)))
        val out = ByteArrayOutputStream()
        x.write(out, "Məhsullar")
        val names = mutableListOf<String>()
        var sheet = ""
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") sheet = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        assertTrue(sheet.contains("Süd &lt;1L&gt; &amp; qatıq"))
        assertTrue(sheet.contains("<v>20</v>"))
    }
}
