package az.payreport.app.data.extract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** ML Kit ilə cihazda (offline) OCR. */
class OcrEngine(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text

    suspend fun recognize(bytes: ByteArray): String {
        val bmp = decodeSampled(bytes) ?: return ""
        return recognize(bmp)
    }

    suspend fun recognize(uri: Uri): String =
        recognizer.process(InputImage.fromFilePath(context, uri)).await().text

    private fun decodeSampled(bytes: ByteArray, maxSide: Int = 2400): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

data class ExtractedText(val text: String, val viaOcr: Boolean)

/**
 * PDF-dən mətn: əvvəlcə PDFBox ilə mətn qatı oxunur; mətn yoxdursa (skan edilmiş PDF)
 * ilk səhifələr PdfRenderer ilə şəkilə çevrilib ML Kit OCR ilə tanınır.
 */
class PdfTextExtractor(private val context: Context, private val ocr: OcrEngine) {

    suspend fun extract(bytes: ByteArray): ExtractedText = withContext(Dispatchers.IO) {
        val text = runCatching {
            PDDocument.load(bytes).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        }.getOrDefault("")
        if (text.isNotBlank()) return@withContext ExtractedText(text, false)

        val tmp = File.createTempFile("receipt", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            ExtractedText(ocrPdf(tmp), true)
        } finally {
            tmp.delete()
        }
    }

    suspend fun extract(uri: Uri): ExtractedText = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        extract(bytes)
    }

    private suspend fun ocrPdf(file: File, maxPages: Int = 3): String {
        val sb = StringBuilder()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        sb.appendLine(ocr.recognize(bmp))
                        bmp.recycle()
                    }
                }
            }
        }
        return sb.toString()
    }
}

/** HTML məktubdan sadə mətn (backend-dəki htmlToText ilə eyni). */
object Html {
    fun toText(html: String): String = html
        .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|tr|li|h[1-6]|table|section)>"), "\n")
        .replace(Regex("(?i)</(td|th)>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("(?i)&nbsp;"), " ")
        .replace(Regex("(?i)&amp;"), "&")
        .replace(Regex("(?i)&lt;"), "<")
        .replace(Regex("(?i)&gt;"), ">")
        .replace(Regex("(?i)&quot;"), "\"")
        .replace(Regex("(?i)&#39;|&apos;"), "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n\\s*\\n+"), "\n")
        .trim()
}
