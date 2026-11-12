package az.siqaret.anbar.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.data.totalBoxes
import java.io.OutputStream

/** Android-un daxili PdfDocument API-si ilə A4 PDF hesabat. */
object PdfExporter {
    const val MIME = "application/pdf"

    fun write(data: ReportData, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Writer(doc, data).render()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Col(val title: String, val width: Float, val alignRight: Boolean = false)

    private class Writer(private val doc: PdfDocument, private val data: ReportData) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 36f
        private val contentW = pageW - margin * 2
        private val bottomLimit = pageH - margin - 24f

        private val primary = Color.rgb(79, 70, 229)
        private val dark = Color.rgb(17, 24, 39)
        private val muted = Color.rgb(107, 114, 128)
        private val zebra = Color.rgb(245, 246, 250)
        private val tint = Color.rgb(238, 240, 255)
        private val green = Color.rgb(22, 163, 74)
        private val red = Color.rgb(220, 38, 38)

        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = dark }
        private val bold = TextPaint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private var page: PdfDocument.Page? = null
        private lateinit var c: Canvas
        private var y = 0f
        private var pageNo = 0

        fun render() {
            newPage()
            header()
            summary()
            productsTable()
            firmsTable()
            invoicesSection()
            history()
            finishPage()
        }

        private fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            c = page!!.canvas
            y = margin
        }

        private fun finishPage() {
            val p = page ?: return
            val foot = TextPaint(text).apply { textSize = 8f; color = muted }
            c.drawText("Siqaret Anbarı · ${Fmt.dateTime(data.generatedAt)}", margin, pageH - margin + 8f, foot)
            val label = "Səhifə $pageNo"
            c.drawText(label, pageW - margin - foot.measureText(label), pageH - margin + 8f, foot)
            doc.finishPage(p)
            page = null
        }

        private fun ensure(space: Float, onNewPage: (() -> Unit)? = null) {
            if (y + space > bottomLimit) {
                newPage()
                onNewPage?.invoke()
            }
        }

        private fun header() {
            fill.color = primary
            c.drawRoundRect(RectF(margin, y, pageW - margin, y + 64f), 14f, 14f, fill)
            val title = TextPaint(bold).apply { textSize = 18f; color = Color.WHITE }
            val sub = TextPaint(text).apply { textSize = 10f; color = Color.argb(220, 255, 255, 255) }
            c.drawText("Siqaret anbarı — stok hesabatı", margin + 16f, y + 28f, title)
            c.drawText("Tarix: ${Fmt.dateTime(data.generatedAt)}   ·   Tarixçə dövrü: ${data.period.label}", margin + 16f, y + 48f, sub)
            y += 80f
        }

        private fun summary() {
            val s = data.stats
            val items = listOf(
                "Məhsul sayı" to Fmt.count(s.productCount),
                "Ümumi blok" to Fmt.count(s.totalBlocks),
                "Ümumi paçka" to Fmt.count(s.totalBoxes),
                "Stok dəyəri" to Fmt.moneyAzn(s.totalValue),
            )
            val gap = 8f
            val w = (contentW - gap * 3) / 4
            val label = TextPaint(text).apply { textSize = 8.5f; color = muted }
            val value = TextPaint(bold).apply { textSize = 13f }
            items.forEachIndexed { i, (l, v) ->
                val x = margin + i * (w + gap)
                fill.color = tint
                c.drawRoundRect(RectF(x, y, x + w, y + 50f), 10f, 10f, fill)
                c.drawText(l, x + 10f, y + 18f, label)
                c.drawText(ellipsize(v, value, w - 20f), x + 10f, y + 38f, value)
            }
            y += 66f
        }

        private fun sectionTitle(t: String) {
            ensure(40f)
            val p = TextPaint(bold).apply { textSize = 13f; color = primary }
            c.drawText(t, margin, y + 12f, p)
            y += 22f
        }

        private fun tableHeader(cols: List<Col>) {
            fill.color = primary
            c.drawRoundRect(RectF(margin, y, margin + contentW, y + 22f), 6f, 6f, fill)
            val p = TextPaint(bold).apply { textSize = 8.5f; color = Color.WHITE }
            drawRow(cols, cols.map { it.title }, y + 15f, p)
            y += 22f
        }

        private fun drawRow(cols: List<Col>, values: List<String>, baseline: Float, paint: TextPaint, colors: List<Int?>? = null) {
            var x = margin
            cols.forEachIndexed { i, col ->
                val p = colors?.getOrNull(i)?.let { TextPaint(paint).apply { color = it } } ?: paint
                val t = ellipsize(values[i], p, col.width - 8f)
                val tx = if (col.alignRight) x + col.width - 4f - p.measureText(t) else x + 4f
                c.drawText(t, tx, baseline, p)
                x += col.width
            }
        }

        private fun productsTable() {
            sectionTitle("Məhsulların siyahısı")
            val cols = listOf(
                Col("№", 22f), Col("Siqaretin adı", 128f), Col("Firma", 92f), Col("Paçka qiy.", 54f, true),
                Col("Blok qiy.", 56f, true), Col("Blok", 40f, true), Col("Paçka", 46f, true),
                Col("Məbləğ", contentW - 438f, true),
            )
            tableHeader(cols)
            if (data.products.isEmpty()) {
                c.drawText("Məhsul yoxdur", margin + 4f, y + 16f, TextPaint(text).apply { color = muted })
                y += 24f
            }
            data.products.forEachIndexed { i, p ->
                ensure(20f) { tableHeader(cols) }
                if (i % 2 == 1) {
                    fill.color = zebra
                    c.drawRect(margin, y, margin + contentW, y + 18f, fill)
                }
                drawRow(
                    cols,
                    listOf(
                        "${i + 1}", p.name, data.firmName(p).ifEmpty { "—" }, Fmt.priceNumber(p.boxPrice), Fmt.number(p.blockPrice),
                        Fmt.count(p.blockCount), Fmt.count(p.totalBoxes), Fmt.number(p.totalAmount),
                    ),
                    y + 12.5f, text,
                )
                y += 18f
            }
            ensure(26f) { tableHeader(cols) }
            val s = data.stats
            fill.color = tint
            c.drawRoundRect(RectF(margin, y + 2f, margin + contentW, y + 24f), 6f, 6f, fill)
            drawRow(
                cols,
                listOf("", "CƏMİ", "", "", "", Fmt.count(s.totalBlocks), Fmt.count(s.totalBoxes), Fmt.moneyAzn(s.totalValue)),
                y + 17f, bold,
            )
            y += 40f
        }

        private fun firmsTable() {
            val list = data.firmSummaries
            if (list.isEmpty() || list.all { it.firm == null }) return
            sectionTitle("Firmalar üzrə xülasə")
            val cols = listOf(
                Col("Firma", 170f), Col("Telefon", 95f), Col("Növ", 50f, true),
                Col("Blok", 55f, true), Col("Paçka", 60f, true), Col("Stok dəyəri", contentW - 430f, true),
            )
            tableHeader(cols)
            list.forEachIndexed { i, f ->
                ensure(20f) { tableHeader(cols) }
                if (i % 2 == 1) {
                    fill.color = zebra
                    c.drawRect(margin, y, margin + contentW, y + 18f, fill)
                }
                drawRow(
                    cols,
                    listOf(
                        f.name, f.firm?.phone.orEmpty(), Fmt.count(f.stats.productCount),
                        Fmt.count(f.stats.totalBlocks), Fmt.count(f.stats.totalBoxes), Fmt.number(f.stats.totalValue),
                    ),
                    y + 12.5f, text,
                )
                y += 18f
            }
            y += 22f
        }

        private fun invoicesSection() {
            val list = data.invoices
            if (list.isEmpty()) return
            sectionTitle("Qaimələr (${data.period.label}) · cəmi ${Fmt.moneyAzn(InventoryCalc.sum(list.map { it.totalAmount }))}")
            val cols = listOf(
                Col("№", 22f), Col("Siqaret", 170f), Col("Paçka qiy.", 64f, true), Col("Blok qiy.", 64f, true),
                Col("Blok", 50f, true), Col("Paçka", 55f, true), Col("Məbləğ", contentW - 425f, true),
            )
            list.forEach { inv ->
                ensure(64f)
                fill.color = tint
                c.drawRoundRect(RectF(margin, y, margin + contentW, y + 22f), 6f, 6f, fill)
                val hp = TextPaint(bold).apply { textSize = 10f }
                c.drawText(ellipsize("${inv.title} · ${Fmt.date(inv.invoice.date)} · ${inv.firmName}", hp, contentW - 140f), margin + 8f, y + 15f, hp)
                val total = Fmt.moneyAzn(inv.totalAmount)
                c.drawText(total, margin + contentW - 8f - hp.measureText(total), y + 15f, hp)
                y += 26f
                tableHeader(cols)
                inv.items.forEachIndexed { i, it ->
                    ensure(20f) { tableHeader(cols) }
                    if (i % 2 == 1) {
                        fill.color = zebra
                        c.drawRect(margin, y, margin + contentW, y + 18f, fill)
                    }
                    drawRow(
                        cols,
                        listOf(
                            "${i + 1}", it.productName, Fmt.priceNumber(it.boxPrice), Fmt.number(it.blockPrice),
                            Fmt.count(it.blockCount), Fmt.count(it.blockCount.toLong() * InventoryCalc.BOXES_PER_BLOCK),
                            Fmt.number(InventoryCalc.totalAmount(it.blockCount, it.blockPrice)),
                        ),
                        y + 12.5f, text,
                    )
                    y += 18f
                }
                y += 12f
            }
            y += 10f
        }

        private fun history() {
            sectionTitle("Gündəlik dəyişikliklərin tarixçəsi (${data.period.label})")
            val days = data.days
            if (days.isEmpty()) {
                c.drawText("Bu dövrdə dəyişiklik qeydə alınmayıb", margin + 4f, y + 12f, TextPaint(text).apply { color = muted })
                y += 24f
                return
            }
            val cols = listOf(
                Col("Saat", 40f), Col("Məhsul", 140f), Col("Əməliyyat", 92f),
                Col("Dəyişiklik", 62f, true), Col("Sonra", 50f, true), Col("Qeyd", contentW - 384f),
            )
            days.forEach { d ->
                ensure(64f)
                fill.color = tint
                c.drawRoundRect(RectF(margin, y, margin + contentW, y + 22f), 6f, 6f, fill)
                val dayPaint = TextPaint(bold).apply { textSize = 10f }
                c.drawText(Fmt.longDate(d.dayStart), margin + 8f, y + 15f, dayPaint)
                val info = "Daxil: +${Fmt.count(d.blocksIn)} blok   Çıxış: −${Fmt.count(d.blocksOut)} blok   Əməliyyat: ${d.entries.size}"
                val ip = TextPaint(text).apply { textSize = 8.5f; color = muted }
                c.drawText(info, margin + contentW - 8f - ip.measureText(info), y + 15f, ip)
                y += 26f
                tableHeader(cols)
                d.entries.forEachIndexed { i, h ->
                    ensure(20f) { tableHeader(cols) }
                    if (i % 2 == 1) {
                        fill.color = zebra
                        c.drawRect(margin, y, margin + contentW, y + 18f, fill)
                    }
                    val deltaColor = when {
                        h.blockDelta > 0 -> green
                        h.blockDelta < 0 -> red
                        else -> null
                    }
                    drawRow(
                        cols,
                        listOf(
                            Fmt.time(h.timestamp), h.productName, h.type.label,
                            "${Fmt.signed(h.blockDelta)} blok", Fmt.count(h.blocksAfter), h.note,
                        ),
                        y + 12.5f, text, listOf(null, null, null, deltaColor, null, muted),
                    )
                    y += 18f
                }
                y += 12f
            }
        }

        private fun ellipsize(s: String, p: TextPaint, w: Float): String =
            TextUtils.ellipsize(s, p, w, TextUtils.TruncateAt.END).toString()
    }
}
