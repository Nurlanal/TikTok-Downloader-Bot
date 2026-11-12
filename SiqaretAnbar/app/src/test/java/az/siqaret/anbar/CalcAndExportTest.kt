package az.siqaret.anbar

import az.siqaret.anbar.data.HistoryEntry
import az.siqaret.anbar.data.HistoryType
import az.siqaret.anbar.data.Product
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.data.totalBoxes
import az.siqaret.anbar.util.DaySummary
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.InventoryCalc
import az.siqaret.anbar.util.InventoryStats
import az.siqaret.anbar.util.Period
import az.siqaret.anbar.util.ReportData
import az.siqaret.anbar.util.XlsxExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CalcAndExportTest {
    private fun product(id: Long, name: String, block: Double, blocks: Int, box: Double = block / 10) =
        Product(id, name, box, block, blocks, 0, 0)

    @Test fun boxesAreBlocksTimesTen() {
        assertEquals(0L, InventoryCalc.totalBoxes(0))
        assertEquals(120L, InventoryCalc.totalBoxes(12))
        assertEquals(120L, product(1, "A", 25.0, 12).totalBoxes)
    }

    @Test fun amountIsBlocksTimesBlockPrice() {
        assertEquals(300.0, InventoryCalc.totalAmount(12, 25.0), 0.0)
        // ondalıq dəqiqliyi: 0.1 * 3 problemi yoxdur
        assertEquals(0.3, InventoryCalc.totalAmount(3, 0.1), 0.0)
        assertEquals(1234.5, product(1, "A", 24.69, 50).totalAmount, 0.0)
    }

    @Test fun priceLinking() {
        assertEquals(35.0, InventoryCalc.blockPriceFromBox(3.5), 0.0)
        assertEquals(3.45, InventoryCalc.boxPriceFromBlock(34.5), 0.0)
    }

    @Test fun statsAggregate() {
        val s = InventoryStats.from(listOf(product(1, "A", 25.0, 10), product(2, "B", 32.5, 4)))
        assertEquals(2, s.productCount)
        assertEquals(14L, s.totalBlocks)
        assertEquals(140L, s.totalBoxes)
        assertEquals(380.0, s.totalValue, 0.0)
    }

    @Test fun parsingAndSanitizing() {
        assertEquals(12.5, Fmt.parseDecimal("12,5")!!, 0.0)
        assertEquals(12.5, Fmt.parseDecimal(" 12.5 ")!!, 0.0)
        assertNull(Fmt.parseDecimal("abc"))
        assertNull(Fmt.parseDecimal(""))
        assertEquals("12.34", Fmt.sanitizeDecimal("12,345"))
        assertEquals("0.5", Fmt.sanitizeDecimal(",5"))
        assertEquals("1.23", Fmt.sanitizeDecimal("1.2.3"))
        assertEquals("12", Fmt.sanitizeInt("0012a"))
        assertEquals("0", Fmt.sanitizeInt("000"))
        assertEquals("25", Fmt.inputNumber(25.0))
        assertEquals("2.5", Fmt.inputNumber(2.50))
        assertEquals("1 234.50 ₼", Fmt.money(1234.5))
    }

    @Test fun daySummaryCountsInAndOut() {
        val now = System.currentTimeMillis()
        val e = listOf(
            HistoryEntry(1, 1, "A", HistoryType.STOCK_IN, 5, 15, 25.0, "", now),
            HistoryEntry(2, 1, "A", HistoryType.STOCK_OUT, -2, 13, 25.0, "", now),
            HistoryEntry(3, 2, "B", HistoryType.DELETED, -4, 0, 25.0, "", now),
        )
        val d = DaySummary.group(e).single()
        assertEquals(5L, d.blocksIn)
        assertEquals(6L, d.blocksOut)
    }

    @Test fun xlsxIsValidZipWithWellFormedXml() {
        val now = System.currentTimeMillis()
        val data = ReportData(
            products = listOf(product(1, "Marlboro <Red> & \"Gold\"", 34.0, 12), product(2, "Kent Nanotek", 30.0, 3)),
            history = listOf(HistoryEntry(1, 1, "Marlboro", HistoryType.ADDED, 12, 12, 34.0, "qeyd", now)),
            period = Period.ALL,
            generatedAt = now,
        )
        val bytes = ByteArrayOutputStream().also { XlsxExporter.write(data, it) }.toByteArray()
        System.getenv("XLSX_OUT")?.let { File(it).writeBytes(bytes) }

        val names = mutableListOf<String>()
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                val content = zip.readBytes()
                dbf.newDocumentBuilder().parse(ByteArrayInputStream(content)) // xətalı XML olarsa exception
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    val xml = String(content, Charsets.UTF_8)
                    assertTrue(xml.contains("ROUND(E5*D5,2)"))
                    assertTrue(xml.contains("SUM(G5:G6)"))
                    assertTrue(xml.contains("&lt;Red&gt; &amp;"))
                }
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        assertEquals("A", XlsxExporter.col(0))
        assertEquals("Z", XlsxExporter.col(25))
        assertEquals("AA", XlsxExporter.col(26))
    }
}
