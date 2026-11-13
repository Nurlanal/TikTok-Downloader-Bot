package az.sontarix.market.transfer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextUtils
import android.text.TextPaint
import az.sontarix.market.data.Product
import az.sontarix.market.notify.ExpiryChecker
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import az.sontarix.market.util.ImageStorage
import java.io.OutputStream

/** Android-in daxili PdfDocument API-si ilə A4 PDF hesabatı. */
object PdfReportWriter {
    private const val W = 595
    private const val H = 842
    private const val M = 32f
    private const val ROW_H = 40f

    private data class Col(val title: String, val width: Float)

    private val cols = listOf(
        Col("", 36f), Col("Məhsul", 140f), Col("Barkod", 88f), Col("Kateqoriya", 78f),
        Col("Say", 34f), Col("Partiya", 54f), Col("Son tarix", 60f), Col("Qalan", 41f)
    )

    fun write(context: Context, products: List<Product>, title: String, warningDays: Int, out: OutputStream) {
        val doc = PdfDocument()
        val today = DateUtils.today()
        val stats = ExpiryChecker.computeStats(products, warningDays, today)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(30, 27, 75) }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 0.7f }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = 0f

        fun drawHeader() {
            fill.color = Color.rgb(79, 70, 229)
            canvas.drawRect(M, y, W - M, y + 22f, fill)
            var x = M
            cols.forEach { c ->
                canvas.drawText(c.title, x + 4f, y + 15f, headPaint)
                x += c.width
            }
            y += 22f
        }

        fun newPage() {
            page?.let { finishPage(doc, it, pageNo, subPaint) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page!!.canvas
            y = M
            if (pageNo == 1) {
                canvas.drawText(title, M, y + 18f, titlePaint)
                y += 30f
                canvas.drawText("Tarix: ${DateUtils.formatMillis(System.currentTimeMillis())} • Məhsul sayı: ${products.size} • Ümumi miqdar: ${stats.totalQuantity}", M, y, subPaint)
                y += 16f
                val boxes = listOf(
                    Triple("Ümumi", stats.total, Color.rgb(79, 70, 229)),
                    Triple("Normal", stats.normal, Color.rgb(22, 163, 74)),
                    Triple("Yaxınlaşan", stats.approaching, Color.rgb(202, 138, 4)),
                    Triple("Bu gün", stats.today, Color.rgb(234, 88, 12)),
                    Triple("Keçmiş", stats.expired, Color.rgb(220, 38, 38))
                )
                val bw = (W - 2 * M - 4 * 8f) / 5
                boxes.forEachIndexed { i, (label, value, color) ->
                    val left = M + i * (bw + 8f)
                    fill.color = color
                    canvas.drawRoundRect(RectF(left, y, left + bw, y + 46f), 8f, 8f, fill)
                    val vp = TextPaint(headPaint).apply { textSize = 18f }
                    canvas.drawText(value.toString(), left + 8f, y + 24f, vp)
                    canvas.drawText(label, left + 8f, y + 39f, TextPaint(headPaint).apply { typeface = Typeface.DEFAULT })
                }
                y += 60f
            }
            drawHeader()
        }

        newPage()
        products.forEachIndexed { index, p ->
            if (y + ROW_H > H - M - 16f) newPage()
            val days = p.daysLeft(today)
            val status = ExpiryStatus.of(days, warningDays)
            fill.color = when (status) {
                ExpiryStatus.NORMAL -> Color.rgb(240, 253, 244)
                ExpiryStatus.APPROACHING -> Color.rgb(254, 252, 232)
                ExpiryStatus.CRITICAL, ExpiryStatus.TODAY -> Color.rgb(255, 247, 237)
                ExpiryStatus.EXPIRED -> Color.rgb(254, 242, 242)
            }
            canvas.drawRect(M, y, W - M, y + ROW_H, fill)
            canvas.drawLine(M, y + ROW_H, W - M, y + ROW_H, line)
            var x = M
            ImageStorage.loadThumbnail(context, p.imageFile, 96)?.let { bmp ->
                canvas.drawBitmap(bmp, null, RectF(x + 3f, y + 4f, x + 33f, y + ROW_H - 6f), null)
            } ?: run {
                cellPaint.color = Color.GRAY
                canvas.drawText("${index + 1}", x + 8f, y + 24f, cellPaint)
                cellPaint.color = Color.BLACK
            }
            x += cols[0].width
            val values = listOf(
                p.name, p.barcode.ifBlank { "—" }, p.category.ifBlank { "—" }, p.quantity.toString(),
                p.batchNumber.ifBlank { "—" }, DateUtils.format(p.expiryDate),
                if (days < 0) "Bitib" else "$days gün"
            )
            values.forEachIndexed { i, v ->
                val w = cols[i + 1].width
                val txt = TextUtils.ellipsize(v, cellPaint, w - 6f, TextUtils.TruncateAt.END).toString()
                if (i == values.lastIndex) cellPaint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(txt, x + 3f, y + 24f, cellPaint)
                cellPaint.typeface = Typeface.DEFAULT
                x += w
            }
            y += ROW_H
        }
        if (products.isEmpty()) {
            canvas.drawText("Məhsul yoxdur.", M, y + 20f, cellPaint)
        }
        page?.let { finishPage(doc, it, pageNo, subPaint) }
        doc.writeTo(out)
        doc.close()
    }

    private fun finishPage(doc: PdfDocument, page: PdfDocument.Page, no: Int, paint: TextPaint) {
        page.canvas.drawText("Son Tarix İzləyici • Səhifə $no", M, H - 16f, paint)
        doc.finishPage(page)
    }
}
