package az.siqaret.anbar.util

import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.data.totalBoxes
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Xarici kitabxana olmadan real .xlsx (Office Open XML) faylı yaradır.
 * Excel-də hesablamalar formul kimi saxlanılır (Blok × Qiymət, SUM və s.).
 */
object XlsxExporter {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // Stil indeksləri (styles.xml -> cellXfs)
    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_TEXT = 2
    private const val S_MONEY = 3
    private const val S_INT = 4
    private const val S_TOTAL_LABEL = 5
    private const val S_TOTAL_MONEY = 6
    private const val S_TOTAL_INT = 7
    private const val S_TITLE = 8
    private const val S_SUB = 9

    private sealed interface Cell { val style: Int }
    private data class Str(val v: String, override val style: Int = S_TEXT) : Cell
    private data class Num(val v: Double, override val style: Int = S_MONEY) : Cell
    private data class Formula(val f: String, val cached: Double, override val style: Int) : Cell
    private data object Empty : Cell { override val style = S_DEFAULT }

    fun write(data: ReportData, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes())
            put("_rels/.rels", rootRels())
            put("docProps/app.xml", appProps())
            put("xl/workbook.xml", workbook())
            put("xl/_rels/workbook.xml.rels", workbookRels())
            put("xl/styles.xml", styles())
            put("xl/worksheets/sheet1.xml", productsSheet(data))
            put("xl/worksheets/sheet2.xml", historySheet(data))
            put("xl/worksheets/sheet3.xml", dailySheet(data))
        }
    }

    // ---------------- Sheets ----------------

    private fun productsSheet(data: ReportData): String {
        val rows = mutableListOf<List<Cell>>()
        rows += listOf(Str("Siqaret anbarı — stok hesabatı", S_TITLE))
        rows += listOf(Str("Tarix: ${Fmt.dateTime(data.generatedAt)}", S_SUB))
        rows.add(emptyList())
        rows += listOf("№", "Siqaretin adı", "Qutu qiyməti", "Blok qiyməti", "Blok sayı", "Qutu sayı", "Ümumi məbləğ")
            .map { Str(it, S_HEADER) }
        val first = rows.size + 1
        data.products.forEachIndexed { i, p ->
            val r = rows.size + 1
            rows += listOf(
                Num((i + 1).toDouble(), S_INT),
                Str(p.name),
                Num(p.boxPrice),
                Num(p.blockPrice),
                Num(p.blockCount.toDouble(), S_INT),
                Formula("E$r*${InventoryCalc.BOXES_PER_BLOCK}", p.totalBoxes.toDouble(), S_INT),
                Formula("ROUND(E$r*D$r,2)", p.totalAmount, S_MONEY),
            )
        }
        val last = rows.size
        val s = data.stats
        val hasRows = data.products.isNotEmpty()
        fun sum(col: Char, cached: Double, style: Int): Cell =
            if (hasRows) Formula("SUM($col$first:$col$last)", cached, style) else Num(0.0, style)
        rows += listOf(
            Str("", S_TOTAL_LABEL), Str("CƏMİ", S_TOTAL_LABEL), Str("", S_TOTAL_LABEL), Str("", S_TOTAL_LABEL),
            sum('E', s.totalBlocks.toDouble(), S_TOTAL_INT),
            sum('F', s.totalBoxes.toDouble(), S_TOTAL_INT),
            sum('G', s.totalValue, S_TOTAL_MONEY),
        )
        rows.add(emptyList())
        rows += listOf(Empty, Str("Ümumi məhsul sayı", S_TOTAL_LABEL), Num(s.productCount.toDouble(), S_TOTAL_INT))
        rows += listOf(Empty, Str("Ümumi blok sayı", S_TOTAL_LABEL), Num(s.totalBlocks.toDouble(), S_TOTAL_INT))
        rows += listOf(Empty, Str("Ümumi qutu sayı", S_TOTAL_LABEL), Num(s.totalBoxes.toDouble(), S_TOTAL_INT))
        rows += listOf(Empty, Str("Ümumi stok dəyəri", S_TOTAL_LABEL), Num(s.totalValue, S_TOTAL_MONEY))
        return sheet(rows, listOf(6.0, 32.0, 14.0, 14.0, 12.0, 12.0, 18.0), freezeRow = 4)
    }

    private fun historySheet(data: ReportData): String {
        val rows = mutableListOf<List<Cell>>()
        rows += listOf(Str("Dəyişikliklər tarixçəsi — ${data.period.label}", S_TITLE))
        rows.add(emptyList())
        rows += listOf("Tarix", "Saat", "Məhsul", "Əməliyyat", "Dəyişiklik (blok)", "Sonrakı blok sayı", "Blok qiyməti", "Qeyd")
            .map { Str(it, S_HEADER) }
        data.history.forEach { h ->
            rows += listOf(
                Str(Fmt.date(h.timestamp)), Str(Fmt.time(h.timestamp)), Str(h.productName), Str(h.type.label),
                Num(h.blockDelta.toDouble(), S_INT), Num(h.blocksAfter.toDouble(), S_INT),
                Num(h.blockPrice), Str(h.note),
            )
        }
        return sheet(rows, listOf(12.0, 8.0, 28.0, 16.0, 16.0, 16.0, 14.0, 44.0), freezeRow = 3)
    }

    private fun dailySheet(data: ReportData): String {
        val rows = mutableListOf<List<Cell>>()
        rows += listOf(Str("Gündəlik xülasə — ${data.period.label}", S_TITLE))
        rows.add(emptyList())
        rows += listOf("Tarix", "Əməliyyat sayı", "Daxil olan blok", "Çıxan blok", "Fərq (blok)").map { Str(it, S_HEADER) }
        data.days.forEach { d ->
            val r = rows.size + 1
            rows += listOf(
                Str(Fmt.date(d.dayStart)), Num(d.entries.size.toDouble(), S_INT),
                Num(d.blocksIn.toDouble(), S_INT), Num(d.blocksOut.toDouble(), S_INT),
                Formula("C$r-D$r", (d.blocksIn - d.blocksOut).toDouble(), S_INT),
            )
        }
        return sheet(rows, listOf(14.0, 16.0, 16.0, 14.0, 14.0), freezeRow = 3)
    }

    private fun sheet(rows: List<List<Cell>>, widths: List<Double>, freezeRow: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="$freezeRow" topLeftCell="A${freezeRow + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        rows.forEachIndexed { ri, row ->
            val r = ri + 1
            if (row.isEmpty()) return@forEachIndexed
            append("""<row r="$r">""")
            row.forEachIndexed { ci, cell ->
                val ref = "${col(ci)}$r"
                when (cell) {
                    is Str -> append("""<c r="$ref" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.v)}</t></is></c>""")
                    is Num -> append("""<c r="$ref" s="${cell.style}"><v>${num(cell.v)}</v></c>""")
                    is Formula -> append("""<c r="$ref" s="${cell.style}"><f>${esc(cell.f)}</f><v>${num(cell.cached)}</v></c>""")
                    Empty -> {}
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        append("""<pageMargins left="0.5" right="0.5" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        append("</worksheet>")
    }

    internal fun col(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString()
        else java.math.BigDecimal.valueOf(v).toPlainString()

    internal fun esc(s: String): String = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch == '\'' -> append("&apos;")
            ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r' -> {}
            else -> append(ch)
        }
    }

    // ---------------- Package parts ----------------

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Siqaret Anbarı</Application></Properties>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Məhsullar" sheetId="1" r:id="rId1"/>
<sheet name="Tarixçə" sheetId="2" r:id="rId2"/>
<sheet name="Gündəlik" sheetId="3" r:id="rId3"/>
</sheets>
<calcPr calcId="191029" fullCalcOnLoad="1"/>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00 &quot;₼&quot;"/></numFmts>
<fonts count="5">
<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="16"/><color rgb="FF312E81"/><name val="Calibri"/><family val="2"/></font>
<font><i/><sz val="10"/><color rgb="FF6B7280"/><name val="Calibri"/><family val="2"/></font>
</fonts>
<fills count="4">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF4F46E5"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFEEF0FF"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFD0D5DD"/></left><right style="thin"><color rgb="FFD0D5DD"/></right><top style="thin"><color rgb="FFD0D5DD"/></top><bottom style="thin"><color rgb="FFD0D5DD"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="10">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="164" fontId="2" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="3" fontId="2" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
