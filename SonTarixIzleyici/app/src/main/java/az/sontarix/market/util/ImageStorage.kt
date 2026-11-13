package az.sontarix.market.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Məhsul şəkillərinin tətbiqin şəxsi qovluğunda saxlanılması.
 * Şəkillər EXIF istiqamətinə görə düzəldilir, 2048px-dən böyük olduqda keyfiyyəti qorumaqla
 * kiçildilir və 92% keyfiyyətlə JPEG olaraq saxlanılır.
 */
object ImageStorage {
    private const val MAX_DIM = 2048
    private const val QUALITY = 92

    fun imagesDir(context: Context): File = File(context.filesDir, "images").apply { mkdirs() }

    fun fileFor(context: Context, name: String?): File? =
        name?.takeIf { it.isNotBlank() }?.let { File(imagesDir(context), it) }?.takeIf { it.exists() }

    /** Kamera üçün müvəqqəti fayl və onun content:// URI-si. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** URI-dən (kamera/qalereya) şəkli oxuyub saxlayır, yeni fayl adını qaytarır. */
    fun saveFromUri(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = resolver.openInputStream(uri)?.use { readOrientation(it) } ?: ExifInterface.ORIENTATION_NORMAL
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val bitmap = rotate(scaleDown(decoded), orientation)
        writeJpeg(context, bitmap)
    }.getOrNull()

    /** Etibarlı şəkil faylını (idxal) yoxlayıb tətbiq qovluğuna kopyalayır. */
    fun importFile(context: Context, source: File): String? = runCatching {
        if (!isValidImage(source)) return null
        val ext = when (sniff(source)) { "png" -> "png"; "webp" -> "webp"; else -> "jpg" }
        val name = "${UUID.randomUUID()}.$ext"
        source.copyTo(File(imagesDir(context), name), overwrite = true)
        name
    }.getOrNull()

    fun delete(context: Context, name: String?) {
        fileFor(context, name)?.delete()
    }

    /** Faylın həqiqətən şəkil olduğunu imza (magic bytes) və dekod ilə yoxlayır. */
    fun isValidImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L || file.length() > 25L * 1024 * 1024) return false
        if (sniff(file) == null) return false
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth in 1..20000 && o.outHeight in 1..20000
    }

    fun sniff(file: File): String? {
        val h = ByteArray(12)
        val n = file.inputStream().use { it.read(h) }
        if (n < 4) return null
        return when {
            h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte() -> "jpg"
            h[0] == 0x89.toByte() && h[1] == 'P'.code.toByte() && h[2] == 'N'.code.toByte() && h[3] == 'G'.code.toByte() -> "png"
            n >= 12 && String(h, 0, 4, Charsets.US_ASCII) == "RIFF" && String(h, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> null
        }
    }

    /** Bildiriş və PDF üçün kiçik ölçülü bitmap. */
    fun loadThumbnail(context: Context, name: String?, size: Int = 256): Bitmap? = runCatching {
        val f = fileFor(context, name) ?: return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b)
        var s = 1
        while (b.outWidth / (s * 2) >= size && b.outHeight / (s * 2) >= size) s *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()

    private fun writeJpeg(context: Context, bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        FileOutputStream(File(imagesDir(context), name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        return name
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w / (s * 2) >= MAX_DIM && h / (s * 2) >= MAX_DIM) s *= 2
        return s
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= MAX_DIM) return src
        val r = MAX_DIM.toFloat() / max
        return Bitmap.createScaledBitmap(src, (src.width * r).toInt(), (src.height * r).toInt(), true)
    }

    private fun readOrientation(stream: InputStream): Int =
        runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
