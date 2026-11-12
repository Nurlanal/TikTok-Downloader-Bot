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

    @Test fun kopecksSurvivePriceLinking() {
        // İstifadəçinin nümunəsi: 10 blok × 41.97 = 419.70 (420 yox)
        assertEquals(4.197, InventoryCalc.boxPriceFromBlock(41.97), 0.0)
        assertEquals(41.97, InventoryCalc.blockPriceFromBox(4.197), 0.0)
        assertEquals(419.7, InventoryCalc.totalAmount(10, 41.97), 0.0)
        assertEquals("419.70 ₼", Fmt.money(InventoryCalc.totalAmount(10, 41.97)))
        assertEquals("4.197", Fmt.inputNumber(InventoryCalc.boxPriceFromBlock(41.97)))
        assertEquals("4.197 ₼", Fmt.price(4.197))
        assertEquals("4.20 ₼", Fmt.price(4.2))
        assertEquals("4.197", Fmt.sanitizeDecimal("4,1975", maxDecimals = 3))

        // Bütün qiymətlər 0.01 ... 300.00: blok → paçka → blok heç vaxt dəyişməməlidir
        for (cents in 1..30_000) {
            val block = cents / 100.0
            val back = InventoryCalc.blockPriceFromBox(
                Fmt.parseDecimal(Fmt.inputNumber(InventoryCalc.boxPriceFromBlock(block)))!!
            )
            assertEquals("blok $block", block, back, 0.0)
            assertEquals(java.math.BigDecimal.valueOf(cents.toLong()).multiply(java.math.BigDecimal.valueOf(37)).movePointLeft(2).toDouble(),
                InventoryCalc.totalAmount(37, block), 0.0)
        }
    }

    @Test fun sumIsExact() {
        // 0.1 + 0.2 kimi double xətaları cəmdə görünməməlidir
        assertEquals(0.3, InventoryCalc.sum(listOf(0.1, 0.2)), 0.0)
        assertEquals(1000.01, InventoryCalc.sum(List(100_001) { 0.01 }), 0.0)
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

    @Test fun firmSummaryGroupsProducts() {
        val f1 = az.siqaret.anbar.data.Firm(1, "A firma")
        val f2 = az.siqaret.anbar.data.Firm(2, "B firma")
        val list = listOf(
            product(1, "X", 30.0, 2).copy(firmId = 1),
            product(2, "Y", 20.0, 5).copy(firmId = 1),
            product(3, "Z", 10.0, 1),
        )
        val g = az.siqaret.anbar.util.FirmSummary.group(list, listOf(f1, f2))
        assertEquals(listOf("A firma", "B firma", "Firmasız"), g.map { it.name })
        assertEquals(7L, g[0].stats.totalBlocks)
        assertEquals(70L, g[0].stats.totalBoxes)
        assertEquals(160.0, g[0].stats.totalValue, 0.0)
        assertEquals(0, g[1].stats.productCount)
        assertEquals(10.0, g[2].stats.totalValue, 0.0)
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
            products = listOf(
                product(1, "Marlboro <Red> & \"Gold\"", 34.0, 12).copy(firmId = 7),
                product(2, "Kent Nanotek", 30.0, 3),
            ),
            firms = listOf(az.siqaret.anbar.data.Firm(7, "Azərsun MMC", "+994 50 000 00 00")),
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
                    assertTrue(xml.contains("ROUND(F5*E5,2)"))
                    assertTrue(xml.contains("Azərsun MMC"))
                    assertTrue(xml.contains("SUM(H5:H6)"))
                    assertTrue(xml.contains("&lt;Red&gt; &amp;"))
                }
            }
        }
        assertTrue(names.contains("xl/worksheets/sheet4.xml"))
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        assertEquals("A", XlsxExporter.col(0))
        assertEquals("Z", XlsxExporter.col(25))
        assertEquals("AA", XlsxExporter.col(26))
    }
}
