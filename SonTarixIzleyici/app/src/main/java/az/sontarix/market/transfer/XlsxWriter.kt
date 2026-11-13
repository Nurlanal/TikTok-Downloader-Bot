package az.sontarix.market.transfer

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Xarici kitabxana olmadan minimal, Excel/Google Sheets/LibreOffice ilə uyğun .xlsx yaradıcısı.
 * Stil indeksləri: 0 adi, 1 başlıq, 2 yaşıl, 3 sarı, 4 narıncı, 5 qırmızı, 6 başlıq-sətri (qalın).
 */
class XlsxWriter {
    sealed interface Cell
    data class Text(val value: String, val style: Int = 0) : Cell
    data class Num(val value: Double, val style: Int = 0) : Cell

    private val rows = mutableListOf<List<Cell>>()
    private var widths: List<Double> = emptyList()

    fun columnWidths(w: List<Double>) { widths = w }
    fun addRow(cells: List<Cell>) { rows += cells }

    fun write(out: OutputStream, sheetName: String) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${esc(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
</workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheetXml())
        }
    }

    private fun sheetXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (widths.isNotEmpty()) {
            append("<cols>")
            widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        rows.forEachIndexed { r, cells ->
            append("""<row r="${r + 1}">""")
            cells.forEachIndexed { c, cell ->
                val ref = colName(c) + (r + 1)
                when (cell) {
                    is Text -> append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                    is Num -> append("""<c r="$ref" s="${cell.style}"><v>${if (cell.value % 1.0 == 0.0) cell.value.toLong() else cell.value}</v></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (rows.isNotEmpty()) append("""<autoFilter ref="A1:${colName((rows.first().size - 1).coerceAtLeast(0))}${rows.size}"/>""")
        append("</worksheet>")
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun esc(s: String): String = buildString(s.length) {
        s.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> {}
                else -> append(ch)
            }
        }
    }

    companion object {
        private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
<fills count="7">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF4F46E5"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDCFCE7"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFEF9C3"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFEDD5"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFEE2E2"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFD1D5DB"/></left><right style="thin"><color rgb="FFD1D5DB"/></right><top style="thin"><color rgb="FFD1D5DB"/></top><bottom style="thin"><color rgb="FFD1D5DB"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="7">
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="0" fillId="3" borderId="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="0" fillId="4" borderId="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="0" fillId="5" borderId="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="0" fillId="6" borderId="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
    }
}
