package az.payreport.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.domain.MonthlyReport
import az.payreport.app.domain.PhoneReport
import az.payreport.app.data.db.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Hesabatların Excel (.xlsx) və PDF formatında ixracı. */
class ReportExporter(private val context: Context) {

    suspend fun exportMonthly(report: MonthlyReport, uri: Uri, pdf: Boolean) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            if (pdf) monthlyPdf(report, out) else monthlyXlsx(report).write(out)
        }
    }

    suspend fun exportPhone(report: PhoneReport, uri: Uri, pdf: Boolean) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            if (pdf) phonePdf(report, out) else phoneXlsx(report).write(out)
        }
    }

    // ------------------------------------------------------------------ Excel
    companion object {
        fun monthlyXlsx(r: MonthlyReport): XlsxWriter = XlsxWriter()
            .sheet("Xülasə", listOf(32.0, 24.0)) {
                header("PayReport AZ — Aylıq hesabat", "")
                row(XlsxWriter.Text("Dövr"), XlsxWriter.Text(r.title))
                row(XlsxWriter.Text("Ümumi mədaxil"), XlsxWriter.Money(r.totalMinor, bold = true))
                row(XlsxWriter.Text("Əməliyyat sayı"), XlsxWriter.Num(r.count.toLong()))
                row(XlsxWriter.Text("Orta ödəniş"), XlsxWriter.Money(r.averageMinor))
                row(XlsxWriter.Text("Fərqli nömrə sayı"), XlsxWriter.Num(r.byPhone.size.toLong()))
                r.bestDay?.let { row(XlsxWriter.Text("Ən yaxşı gün"), XlsxWriter.Text("${Fmt.date(Fmt.startOfDay(it.date))} — ${Fmt.azn(it.totalMinor)}")) }
                row(XlsxWriter.Text("Hazırlanma tarixi"), XlsxWriter.Text(Fmt.userDateTime(System.currentTimeMillis())))
            }
            .sheet("Gündəlik mədaxil", listOf(14.0, 16.0, 18.0)) {
                header("Tarix", "Əməliyyat sayı", "Mədaxil (AZN)")
                r.daily.forEach { d ->
                    row(XlsxWriter.Text(Fmt.date(Fmt.startOfDay(d.date))), XlsxWriter.Num(d.count.toLong()), XlsxWriter.Money(d.totalMinor))
                }
                row(XlsxWriter.Text("CƏMİ", bold = true), XlsxWriter.Num(r.count.toLong(), bold = true), XlsxWriter.Money(r.totalMinor, bold = true))
            }
            .sheet("Nömrələr üzrə", listOf(8.0, 18.0, 16.0, 18.0, 20.0)) {
                header("#", "Telefon", "Ödəniş sayı", "Məbləğ (AZN)", "Son ödəniş")
                r.byPhone.forEachIndexed { i, p ->
                    row(
                        XlsxWriter.Num(i + 1L), XlsxWriter.Text(PhoneUtils.format(p.phone)), XlsxWriter.Num(p.count.toLong()),
                        XlsxWriter.Money(p.totalMinor), XlsxWriter.Text(Fmt.userDateTime(p.lastPaidAt)),
                    )
                }
            }
            .sheet("Əməliyyatlar", listOf(18.0, 18.0, 14.0, 18.0, 10.0, 30.0)) {
                header("Tarix", "Telefon", "Məbləğ (AZN)", "Əməliyyat ID", "Mənbə", "Qeyd")
                r.payments.forEach { p ->
                    row(
                        XlsxWriter.Text(Fmt.userDateTime(p.paidAt)), XlsxWriter.Text(PhoneUtils.format(p.phone)),
                        XlsxWriter.Money(p.amountMinor ?: 0), XlsxWriter.Text(p.txnId.orEmpty()),
                        XlsxWriter.Text(p.source), XlsxWriter.Text(p.note.orEmpty()),
                    )
                }
            }

        fun phoneXlsx(r: PhoneReport): XlsxWriter = XlsxWriter()
            .sheet("Xülasə", listOf(28.0, 24.0)) {
                header("PayReport AZ — Nömrə hesabatı", "")
                row(XlsxWriter.Text("Telefon"), XlsxWriter.Text(PhoneUtils.format(r.phone)))
                row(XlsxWriter.Text("Ödəniş sayı"), XlsxWriter.Num(r.count.toLong()))
                row(XlsxWriter.Text("Ümumi məbləğ"), XlsxWriter.Money(r.totalMinor, bold = true))
                row(XlsxWriter.Text("Son ödəniş"), XlsxWriter.Text(r.lastPaidAt?.let { Fmt.userDateTime(it) } ?: "—"))
            }
            .sheet("Aylıq", listOf(18.0, 14.0, 18.0)) {
                header("Ay", "Say", "Məbləğ (AZN)")
                r.monthly.forEach { row(XlsxWriter.Text(Fmt.monthYear(it.year, it.month)), XlsxWriter.Num(it.count.toLong()), XlsxWriter.Money(it.totalMinor)) }
            }
            .sheet("Tarixçə", listOf(18.0, 14.0, 14.0, 18.0, 10.0)) {
                header("Tarix", "Məbləğ (AZN)", "Status", "Əməliyyat ID", "Mənbə")
                r.payments.forEach { p ->
                    row(
                        XlsxWriter.Text(Fmt.userDateTime(p.paidAt)), XlsxWriter.Money(p.amountMinor ?: 0),
                        XlsxWriter.Text(statusAz(p.status)), XlsxWriter.Text(p.txnId.orEmpty()), XlsxWriter.Text(p.source),
                    )
                }
            }

        fun statusAz(s: String) = when (s) {
            PaymentStatus.CONFIRMED -> "Təsdiqlənib"
            PaymentStatus.PENDING -> "Gözləyir"
            PaymentStatus.CANCELLED -> "Ləğv edilib"
            else -> s
        }
    }

    // ------------------------------------------------------------------ PDF
    private class PdfBuilder(private val title: String, private val subtitle: String) {
        val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        lateinit var canvas: Canvas
        var y = 0f
        val w = 595f
        val h = 842f
        val margin = 36f

        val navy = Color.parseColor("#0B1F3A")
        val green = Color.parseColor("#16A34A")
        val gray = Color.parseColor("#64748B")
        val light = Color.parseColor("#F1F5F9")

        fun paint(size: Float, color: Int = Color.parseColor("#0F172A"), bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; textAlign = align
                typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

        fun newPage() {
            page?.let { finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pageNo).create())
            page = p
            canvas = p.canvas
            if (pageNo == 1) {
                canvas.drawRect(0f, 0f, w, 92f, Paint().apply { color = navy })
                canvas.drawRect(0f, 88f, w, 92f, Paint().apply { color = green })
                canvas.drawText("PayReport AZ", margin, 40f, paint(22f, Color.WHITE, true))
                canvas.drawText(title, margin, 62f, paint(13f, Color.parseColor("#CBD5E1")))
                canvas.drawText(subtitle, w - margin, 40f, paint(15f, Color.WHITE, true, Paint.Align.RIGHT))
                canvas.drawText("Hazırlanıb: ${Fmt.userDateTime(System.currentTimeMillis())}", w - margin, 62f,
                    paint(10f, Color.parseColor("#CBD5E1"), align = Paint.Align.RIGHT))
                y = 116f
            } else {
                canvas.drawText("PayReport AZ — $title · $subtitle", margin, 30f, paint(9f, gray))
                y = 50f
            }
        }

        private fun finishPage(p: PdfDocument.Page) {
            p.canvas.drawText("Səhifə $pageNo", w - margin, h - 20f, paint(9f, gray, align = Paint.Align.RIGHT))
            doc.finishPage(p)
        }

        fun ensure(space: Float) { if (y + space > h - 40f) newPage() }

        fun section(text: String) {
            ensure(40f)
            y += 10f
            canvas.drawText(text, margin, y + 12f, paint(13f, navy, true))
            y += 22f
        }

        fun statBoxes(items: List<Pair<String, String>>) {
            ensure(64f)
            val gap = 10f
            val bw = (w - margin * 2 - gap * (items.size - 1)) / items.size
            items.forEachIndexed { i, (label, value) ->
                val x = margin + i * (bw + gap)
                canvas.drawRoundRect(RectF(x, y, x + bw, y + 56f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light })
                canvas.drawText(label, x + 10f, y + 20f, paint(9f, gray))
                canvas.drawText(value, x + 10f, y + 42f, paint(13f, navy, true))
            }
            y += 68f
        }

        fun barChart(values: List<Long>, labels: List<String>) {
            val chartH = 120f
            ensure(chartH + 30f)
            val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
            val cw = w - margin * 2
            val bw = cw / values.size
            canvas.drawLine(margin, y + chartH, w - margin, y + chartH, Paint().apply { color = Color.parseColor("#CBD5E1") })
            values.forEachIndexed { i, v ->
                val bh = (v.toFloat() / max) * (chartH - 10f)
                val x = margin + i * bw + bw * 0.15f
                if (v > 0) canvas.drawRoundRect(RectF(x, y + chartH - bh, x + bw * 0.7f, y + chartH), 2f, 2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green })
                if (values.size <= 16 || i % 3 == 0) {
                    canvas.drawText(labels[i], margin + i * bw + bw / 2, y + chartH + 12f, paint(7f, gray, align = Paint.Align.CENTER))
                }
            }
            canvas.drawText("Maks: ${Fmt.azn(max)}", w - margin, y + 8f, paint(8f, gray, align = Paint.Align.RIGHT))
            y += chartH + 24f
        }

        /** weights — sütunların nisbi eni; rightAlign — sağa düzlənən sütunlar */
        fun table(headers: List<String>, weights: List<Float>, rows: List<List<String>>, rightAlign: Set<Int> = emptySet(), boldLast: Boolean = false) {
            val cw = w - margin * 2
            val total = weights.sum()
            val xs = weights.runningFold(0f) { acc, x -> acc + x / total * cw }.map { margin + it }
            fun drawRow(cells: List<String>, header: Boolean, zebra: Boolean, bold: Boolean) {
                ensure(20f)
                if (header) canvas.drawRect(margin, y, w - margin, y + 20f, Paint().apply { color = navy })
                else if (zebra) canvas.drawRect(margin, y, w - margin, y + 18f, Paint().apply { color = light })
                cells.forEachIndexed { i, text ->
                    val right = i in rightAlign
                    val p = paint(9f, if (header) Color.WHITE else Color.parseColor("#0F172A"), header || bold,
                        if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
                    val x = if (right) xs[i + 1] - 6f else xs[i] + 6f
                    val maxW = xs[i + 1] - xs[i] - 12f
                    canvas.drawText(ellipsize(text, p, maxW), x, y + 13f, p)
                }
                y += if (header) 20f else 18f
            }
            drawRow(headers, header = true, zebra = false, bold = true)
            rows.forEachIndexed { idx, r ->
                if (y + 18f > h - 40f) { newPage(); drawRow(headers, header = true, zebra = false, bold = true) }
                drawRow(r, header = false, zebra = idx % 2 == 1, bold = boldLast && idx == rows.lastIndex)
            }
            y += 6f
        }

        private fun ellipsize(text: String, p: Paint, maxW: Float): String {
            if (p.measureText(text) <= maxW) return text
            var t = text
            while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }

        fun note(text: String) {
            ensure(16f)
            canvas.drawText(text, margin, y + 10f, paint(9f, gray))
            y += 16f
        }

        fun writeTo(out: OutputStream) {
            page?.let { finishPage(it) }
            page = null
            doc.writeTo(out)
            doc.close()
        }
    }

    private fun monthlyPdf(r: MonthlyReport, out: OutputStream) {
        val b = PdfBuilder("Aylıq mədaxil hesabatı", r.title)
        b.newPage()
        b.statBoxes(listOf(
            "Ümumi mədaxil" to Fmt.azn(r.totalMinor),
            "Əməliyyat sayı" to r.count.toString(),
            "Orta ödəniş" to Fmt.azn(r.averageMinor),
            "Nömrə sayı" to r.byPhone.size.toString(),
        ))
        b.section("Gündəlik mədaxil qrafiki")
        b.barChart(r.daily.map { it.totalMinor }, r.daily.map { it.date.dayOfMonth.toString() })

        b.section("Ən çox ödəniş edən nömrələr")
        if (r.topPayers.isEmpty()) b.note("Bu ay təsdiqlənmiş ödəniş yoxdur.")
        else b.table(
            listOf("#", "Telefon", "Say", "Məbləğ", "Son ödəniş"), listOf(0.5f, 2f, 1f, 1.6f, 2f),
            r.topPayers.mapIndexed { i, p -> listOf("${i + 1}", PhoneUtils.format(p.phone), "${p.count}", Fmt.azn(p.totalMinor), Fmt.userDateTime(p.lastPaidAt)) },
            rightAlign = setOf(2, 3),
        )

        b.section("Gündəlik mədaxil")
        b.table(
            listOf("Tarix", "Əməliyyat sayı", "Mədaxil"), listOf(2f, 1.5f, 2f),
            r.daily.filter { it.count > 0 }.map { listOf(Fmt.date(Fmt.startOfDay(it.date)), "${it.count}", Fmt.azn(it.totalMinor)) } +
                listOf(listOf("CƏMİ", "${r.count}", Fmt.azn(r.totalMinor))),
            rightAlign = setOf(1, 2), boldLast = true,
        )

        b.section("Telefon nömrələri üzrə məbləğlər")
        b.table(
            listOf("Telefon", "Say", "Məbləğ"), listOf(2f, 1f, 2f),
            r.byPhone.map { listOf(PhoneUtils.format(it.phone), "${it.count}", Fmt.azn(it.totalMinor)) },
            rightAlign = setOf(1, 2),
        )

        b.section("Bütün əməliyyatlar")
        b.table(
            listOf("Tarix", "Telefon", "Məbləğ", "Əməliyyat ID", "Mənbə"), listOf(1.8f, 1.6f, 1.3f, 1.8f, 0.9f),
            r.payments.map { listOf(Fmt.userDateTime(it.paidAt), PhoneUtils.format(it.phone), Fmt.azn(it.amountMinor), it.txnId ?: "—", it.source) },
            rightAlign = setOf(2),
        )
        b.note("Hesabata yalnız təsdiqlənmiş əməliyyatlar daxildir. Vaxt zonası: Bakı (UTC+4).")
        b.writeTo(out)
    }

    private fun phonePdf(r: PhoneReport, out: OutputStream) {
        val b = PdfBuilder("Telefon nömrəsi üzrə hesabat", PhoneUtils.format(r.phone))
        b.newPage()
        b.statBoxes(listOf(
            "Ümumi ödənilən" to Fmt.azn(r.totalMinor),
            "Ödəniş sayı" to r.count.toString(),
            "Son ödəniş" to (r.lastPaidAt?.let { Fmt.date(it) } ?: "—"),
        ))
        if (r.monthly.isNotEmpty()) {
            b.section("Aylıq statistika")
            val last = r.monthly.takeLast(12)
            b.barChart(last.map { it.totalMinor }, last.map { it.label })
            b.table(listOf("Ay", "Say", "Məbləğ"), listOf(2f, 1f, 2f),
                r.monthly.reversed().map { listOf(Fmt.monthYear(it.year, it.month), "${it.count}", Fmt.azn(it.totalMinor)) }, setOf(1, 2))
        }
        b.section("Ödəniş tarixçəsi")
        b.table(
            listOf("Tarix", "Məbləğ", "Status", "Əməliyyat ID", "Mənbə"), listOf(1.8f, 1.3f, 1.3f, 1.8f, 0.9f),
            r.payments.map { listOf(Fmt.userDateTime(it.paidAt), Fmt.azn(it.amountMinor), statusAz(it.status), it.txnId ?: "—", it.source) },
            rightAlign = setOf(1),
        )
        b.writeTo(out)
    }
}
