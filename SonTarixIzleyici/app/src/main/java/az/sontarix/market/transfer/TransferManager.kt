package az.sontarix.market.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import az.sontarix.market.appContainer
import az.sontarix.market.data.Category
import az.sontarix.market.data.HistoryEntry
import az.sontarix.market.data.OperationStatus
import az.sontarix.market.data.OperationType
import az.sontarix.market.data.Product
import az.sontarix.market.data.ProductRepository
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.notify.NotificationScheduler
import az.sontarix.market.util.DateUtils
import az.sontarix.market.util.ExpiryStatus
import az.sontarix.market.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    ZIP("ZIP (şəkillər + məlumat)", "zip", "application/zip"),
    JSON("JSON (məlumat)", "json", "application/json"),
    EXCEL("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("PDF hesabatı", "pdf", "application/pdf")
}

sealed interface ExportScope {
    data object All : ExportScope
    data class Selected(val ids: List<Long>) : ExportScope
    data class ByCategory(val category: String) : ExportScope
    data class ByDateRange(val from: LocalDate, val to: LocalDate) : ExportScope
}

/** Təkrar məhsul tapıldıqda istifadəçinin seçimi. */
enum class DuplicateAction(val label: String) {
    KEEP_EXISTING("Mövcud məhsulu saxla"),
    UPDATE("Məlumatları yenilə"),
    ADD_SEPARATE("Ayrı məhsul kimi əlavə et"),
    SKIP("İdxal etmə")
}

data class ImportItem(
    val index: Int,
    val uid: String,
    val name: String,
    val barcode: String,
    val category: String,
    val quantity: Int,
    val expiry: LocalDate,
    val batchNumber: String,
    val note: String,
    /** Staging qovluğundakı yoxlanılmış şəkil. */
    val stagedImage: File?,
    val duplicate: Product?,
    val duplicateReasons: List<String>,
    /** Eyni barkodla, lakin fərqli tarix/partiya ilə mövcud məhsul (yeni partiya). */
    val otherBatches: Int
)

data class ImportSession(
    val id: String,
    val sourceName: String,
    val format: String,
    val isBackup: Boolean,
    val items: List<ImportItem>,
    val categories: List<String>,
    val warnings: List<String>,
    val stagingDir: File
)

class ImportException(message: String) : Exception(message)

data class ImportResult(val added: Int, val updated: Int, val skipped: Int, val failed: Int)

class TransferManager(private val context: Context, private val repo: ProductRepository) {

    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val importRoot get() = File(context.cacheDir, "import").apply { mkdirs() }

    // ───────────────────────────── İXRAC ─────────────────────────────

    suspend fun productsFor(scope: ExportScope): List<Product> = when (scope) {
        ExportScope.All -> repo.getAll()
        is ExportScope.Selected -> repo.getByIds(scope.ids).sortedBy { it.expiryEpochDay }
        is ExportScope.ByCategory -> repo.getByCategory(scope.category)
        is ExportScope.ByDateRange -> repo.getByExpiryRange(scope.from.toEpochDay(), scope.to.toEpochDay())
    }

    /** Faylı keş qovluğunda yaradır və geri qaytarır (paylaşmaq və ya yadda saxlamaq üçün). */
    suspend fun export(scope: ExportScope, format: ExportFormat, scopeLabel: String): File = withContext(Dispatchers.IO) {
        cleanOldExports()
        val products = productsFor(scope)
        val file = File(exportDir, "mehsullar_${DateUtils.fileStamp()}.${format.ext}")
        try {
            if (products.isEmpty()) throw ImportException("Seçilmiş meyarlara uyğun məhsul tapılmadı")
            FileOutputStream(file).use { out -> writeFormat(products, format, out, backup = false) }
            repo.addHistory(
                HistoryEntry(
                    type = if (format == ExportFormat.PDF) OperationType.REPORT else OperationType.EXPORT,
                    status = OperationStatus.SUCCESS, itemCount = products.size, format = format.name,
                    fileName = file.name, detail = scopeLabel
                )
            )
            file
        } catch (e: Exception) {
            file.delete()
            repo.addHistory(
                HistoryEntry(type = OperationType.EXPORT, status = OperationStatus.FAILED, itemCount = 0,
                    format = format.name, detail = "$scopeLabel — ${e.message ?: "naməlum xəta"}")
            )
            throw e
        }
    }

    private suspend fun writeFormat(products: List<Product>, format: ExportFormat, out: OutputStream, backup: Boolean) {
        when (format) {
            ExportFormat.ZIP -> writeZip(products, out, backup)
            ExportFormat.JSON -> out.write(buildJson(products, embedImages = true, backup = backup).toString(2).toByteArray())
            ExportFormat.EXCEL -> writeExcel(products, out)
            ExportFormat.PDF -> PdfReportWriter.write(
                context, products, "Məhsulların son istifadə tarixi hesabatı",
                context.appContainer.settings.current.warningDays, out
            )
        }
    }

    /** Tam ehtiyat nüsxəsi (bütün məhsullar, şəkillər və kateqoriyalar) — SAF ilə seçilmiş yerə. */
    suspend fun backupTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val products = repo.getAll()
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { writeZip(products, it, backup = true) }
                ?: throw ImportException("Fayl yazıla bilmədi")
            repo.addHistory(HistoryEntry(type = OperationType.BACKUP, status = OperationStatus.SUCCESS,
                itemCount = products.size, format = "ZIP", fileName = displayName(uri) ?: "", detail = "Tam ehtiyat nüsxəsi"))
            products.size
        } catch (e: Exception) {
            repo.addHistory(HistoryEntry(type = OperationType.BACKUP, status = OperationStatus.FAILED,
                itemCount = 0, format = "ZIP", detail = e.message ?: "Xəta"))
            throw e
        }
    }

    /** Keşdəki faylı istifadəçinin seçdiyi yerə (SAF) kopyalayır. */
    suspend fun saveCopy(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw ImportException("Fayl yazıla bilmədi")
    }

    fun shareIntent(file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Məhsul siyahısı — ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Son Tarix İzləyici tətbiqindən ixrac edilmiş məhsullar. Faylı tətbiqdə \"İdxal\" bölməsindən açın.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Faylı paylaş").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private suspend fun buildJson(products: List<Product>, embedImages: Boolean, backup: Boolean, zipImageNames: Map<Long, String> = emptyMap()): JSONObject {
        val arr = JSONArray()
        products.forEach { p ->
            val o = JSONObject()
                .put("uid", p.uid)
                .put("name", p.name)
                .put("barcode", p.barcode)
                .put("category", p.category)
                .put("quantity", p.quantity)
                .put("expiryDate", p.expiryDate.toString())
                .put("batchNumber", p.batchNumber)
                .put("note", p.note)
                .put("createdAt", p.createdAt)
                .put("updatedAt", p.updatedAt)
            zipImageNames[p.id]?.let { o.put("image", it) }
            if (embedImages) {
                ImageStorage.fileFor(context, p.imageFile)?.let { f ->
                    o.put("imageType", ImageStorage.sniff(f) ?: "jpg")
                    o.put("imageBase64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            }
            arr.put(o)
        }
        val root = JSONObject()
            .put("format", FORMAT_ID)
            .put("version", FORMAT_VERSION)
            .put("kind", if (backup) "backup" else "export")
            .put("exportedAt", DateUtils.formatMillis(System.currentTimeMillis()))
            .put("count", products.size)
            .put("products", arr)
        if (backup) root.put("categories", JSONArray(repo.db.categoryDao().getAll().map { it.name }))
        return root
    }

    private suspend fun writeZip(products: List<Product>, out: OutputStream, backup: Boolean) {
        ZipOutputStream(out).use { zip ->
            val names = mutableMapOf<Long, String>()
            products.forEach { p ->
                val f = ImageStorage.fileFor(context, p.imageFile) ?: return@forEach
                val ext = ImageStorage.sniff(f) ?: return@forEach
                val entryName = "images/${p.uid}.$ext"
                zip.putNextEntry(ZipEntry(entryName))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                names[p.id] = entryName
            }
            zip.putNextEntry(ZipEntry("products.json"))
            zip.write(buildJson(products, embedImages = false, backup = backup, zipImageNames = names).toString(2).toByteArray())
            zip.closeEntry()
        }
    }

    private fun writeExcel(products: List<Product>, out: OutputStream) {
        val warning = context.appContainer.settings.current.warningDays
        val x = XlsxWriter()
        x.columnWidths(listOf(5.0, 32.0, 18.0, 22.0, 8.0, 14.0, 14.0, 12.0, 16.0, 40.0))
        x.addRow(listOf("№", "Məhsulun adı", "Barkod", "Kateqoriya", "Say", "Partiya", "Son istifadə tarixi", "Qalan gün", "Status", "Qeyd")
            .map { XlsxWriter.Text(it, 1) })
        val today = DateUtils.today()
        products.forEachIndexed { i, p ->
            val d = p.daysLeft(today)
            val st = ExpiryStatus.of(d, warning)
            val style = when (st) {
                ExpiryStatus.NORMAL -> 2; ExpiryStatus.APPROACHING -> 3
                ExpiryStatus.CRITICAL, ExpiryStatus.TODAY -> 4; ExpiryStatus.EXPIRED -> 5
            }
            x.addRow(listOf(
                XlsxWriter.Num((i + 1).toDouble()), XlsxWriter.Text(p.name), XlsxWriter.Text(p.barcode),
                XlsxWriter.Text(p.category), XlsxWriter.Num(p.quantity.toDouble()), XlsxWriter.Text(p.batchNumber),
                XlsxWriter.Text(DateUtils.format(p.expiryDate)), XlsxWriter.Num(d.toDouble()),
                XlsxWriter.Text(st.label, style), XlsxWriter.Text(p.note)
            ))
        }
        x.write(out, "Məhsullar")
    }

    private fun cleanOldExports() {
        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
        exportDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    // ───────────────────────────── İDXAL ─────────────────────────────

    /**
     * 1) Faylı oxuyur, 2) formatı və təhlükəsizliyi yoxlayır, 3) məhsulları və şəkilləri
     * müvəqqəti qovluğa çıxarır, 4) təkrarları müəyyən edir. Bazaya heç nə yazılmır.
     */
    suspend fun prepareImport(uri: Uri): ImportSession = withContext(Dispatchers.IO) {
        cleanImportStaging()
        val sessionId = UUID.randomUUID().toString()
        val dir = File(importRoot, sessionId).apply { mkdirs() }
        val name = displayName(uri) ?: "fayl"
        try {
            val source = File(dir, "source.bin")
            val input = context.contentResolver.openInputStream(uri) ?: throw ImportException("Fayl açıla bilmədi")
            input.use { copyLimited(it, source, MAX_SOURCE_BYTES, "Fayl çox böyükdür (maks. ${MAX_SOURCE_BYTES / 1024 / 1024} MB)") }
            val head = ByteArray(4)
            val n = source.inputStream().use { it.read(head) }
            val isZip = n >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()
            val warnings = mutableListOf<String>()
            val json: JSONObject
            val imagesDir = File(dir, "images").apply { mkdirs() }
            if (isZip) {
                json = extractZip(source, dir, warnings)
            } else {
                val text = source.readText(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
                if (!text.startsWith("{")) throw ImportException("Dəstəklənməyən fayl formatı. Yalnız ZIP və JSON faylları qəbul edilir.")
                json = runCatching { JSONObject(text) }.getOrElse { throw ImportException("JSON faylı zədələnib və ya səhv formatdadır") }
            }
            source.delete()
            val session = parseJson(json, dir, imagesDir, isZip, warnings, sessionId, name)
            if (session.items.isEmpty()) throw ImportException("Faylda etibarlı məhsul tapılmadı")
            session
        } catch (e: Exception) {
            dir.deleteRecursively()
            repo.addHistory(HistoryEntry(type = OperationType.IMPORT, status = OperationStatus.FAILED, itemCount = 0,
                format = "-", fileName = name, detail = e.message ?: "Fayl yoxlanışdan keçmədi"))
            throw if (e is ImportException) e else ImportException("Fayl oxunarkən xəta: ${e.message}")
        }
    }

    /** ZIP təhlükəsizliyi: yol keçidi (../), zip-bomba, icazəsiz fayl növləri və ölçü limitləri yoxlanılır. */
    private fun extractZip(source: File, dir: File, warnings: MutableList<String>): JSONObject {
        var total = 0L
        var count = 0
        var json: JSONObject? = null
        val canonicalRoot = dir.canonicalPath + File.separator
        ZipInputStream(source.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                count++
                if (count > MAX_ENTRIES) throw ImportException("ZIP faylında həddən artıq fayl var")
                val raw = entry.name
                if (entry.isDirectory) continue
                if (raw.contains("..") || raw.startsWith("/") || raw.contains('\\') || raw.contains(':') || raw.length > 200) {
                    throw ImportException("ZIP faylında təhlükəli fayl yolu aşkarlandı: $raw")
                }
                val isJson = raw == "products.json"
                val isImage = IMAGE_ENTRY.matches(raw)
                if (!isJson && !isImage) { warnings += "Naməlum fayl ötürüldü: $raw"; continue }
                val target = File(dir, raw)
                if (!target.canonicalPath.startsWith(canonicalRoot)) throw ImportException("ZIP faylında təhlükəli fayl yolu aşkarlandı")
                target.parentFile?.mkdirs()
                val limit = if (isJson) MAX_JSON_BYTES else MAX_IMAGE_BYTES
                val written = copyLimited(zis, target, limit, "ZIP daxilində çox böyük fayl: $raw")
                total += written
                if (total > MAX_UNCOMPRESSED) throw ImportException("ZIP faylının açılmış həcmi çox böyükdür")
                if (isJson) {
                    json = runCatching { JSONObject(target.readText(Charsets.UTF_8)) }
                        .getOrElse { throw ImportException("products.json zədələnib və ya səhv formatdadır") }
                    target.delete()
                }
            }
        }
        return json ?: throw ImportException("ZIP faylında products.json tapılmadı. Bu fayl Son Tarix İzləyici ixracı deyil.")
    }

    private suspend fun parseJson(
        root: JSONObject, dir: File, imagesDir: File, isZip: Boolean, warnings: MutableList<String>,
        sessionId: String, sourceName: String
    ): ImportSession {
        if (root.optString("format") != FORMAT_ID) throw ImportException("Bu fayl Son Tarix İzləyici formatında deyil")
        val version = root.optInt("version", 0)
        if (version < 1 || version > FORMAT_VERSION) throw ImportException("Faylın versiyası dəstəklənmir ($version)")
        val arr = root.optJSONArray("products") ?: throw ImportException("Faylda məhsul siyahısı yoxdur")
        if (arr.length() > MAX_PRODUCTS) throw ImportException("Faylda həddən artıq məhsul var (maks. $MAX_PRODUCTS)")
        val items = mutableListOf<ImportItem>()
        val seenUids = HashSet<String>()
        val existingNoBarcode = repo.getAll().filter { it.barcode.isEmpty() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { warnings += "Sətir ${i + 1}: səhv format, ötürüldü"; continue }
            val name = o.optString("name").trim().take(200)
            val expiry = runCatching { LocalDate.parse(o.optString("expiryDate").trim()) }.getOrNull()
                ?: DateUtils.parse(o.optString("expiryDate"))
            if (name.isEmpty()) { warnings += "Sətir ${i + 1}: məhsul adı yoxdur, ötürüldü"; continue }
            if (expiry == null || expiry.year !in 1990..2200) { warnings += "\"$name\": son istifadə tarixi səhvdir, ötürüldü"; continue }
            val quantity = o.optInt("quantity", 1).coerceIn(0, 10_000_000)
            var uid = o.optString("uid").trim().takeIf { UID_RE.matches(it) } ?: UUID.randomUUID().toString()
            if (!seenUids.add(uid)) uid = UUID.randomUUID().toString().also { seenUids.add(it) }
            val barcode = o.optString("barcode").trim().take(64)
            val batch = o.optString("batchNumber").trim().take(64)

            // Şəkil
            var image: File? = null
            if (isZip) {
                val rel = o.optString("image").takeIf { IMAGE_ENTRY.matches(it) }
                val f = rel?.let { File(dir, it) }
                if (f != null && f.exists()) {
                    if (ImageStorage.isValidImage(f)) image = f else warnings += "\"$name\": şəkil zədələnib, şəkilsiz idxal ediləcək"
                }
            } else {
                val b64 = o.optString("imageBase64")
                if (b64.isNotEmpty()) {
                    val f = File(imagesDir, "$uid.img")
                    val ok = runCatching {
                        if (b64.length > MAX_IMAGE_BYTES * 4 / 3 + 16) error("too big")
                        f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                        ImageStorage.isValidImage(f)
                    }.getOrDefault(false)
                    if (ok) image = f else { f.delete(); warnings += "\"$name\": şəkil zədələnib, şəkilsiz idxal ediləcək" }
                }
            }

            // Təkrar yoxlaması: ID, barkod + partiya + tarix
            val reasons = mutableListOf<String>()
            var dup: Product? = repo.db.productDao().getByUid(uid)
            if (dup != null) reasons += "Eyni məhsul ID-si"
            var otherBatches = 0
            if (barcode.isNotEmpty()) {
                val sameBarcode = repo.findByBarcode(barcode)
                val exact = sameBarcode.firstOrNull { it.expiryEpochDay == expiry.toEpochDay() && it.batchNumber.equals(batch, true) }
                if (exact != null) {
                    if (dup == null) dup = exact
                    if (dup?.id == exact.id) {
                        reasons += "Eyni barkod"
                        reasons += "Eyni son istifadə tarixi"
                        if (batch.isNotEmpty()) reasons += "Eyni partiya nömrəsi"
                    }
                }
                otherBatches = sameBarcode.count { it.id != dup?.id }
            } else if (dup == null) {
                // Barkodsuz məhsullar: ad + tarix + partiya üst-üstə düşürsə
                val byName = existingNoBarcode.firstOrNull {
                    it.barcode.isEmpty() && it.name.equals(name, true) && it.expiryEpochDay == expiry.toEpochDay() && it.batchNumber.equals(batch, true)
                }
                if (byName != null) { dup = byName; reasons += "Eyni ad, tarix və partiya" }
            }

            items += ImportItem(
                index = i, uid = uid, name = name, barcode = barcode,
                category = o.optString("category").trim().take(80), quantity = quantity, expiry = expiry,
                batchNumber = batch, note = o.optString("note").trim().take(2000),
                stagedImage = image, duplicate = dup, duplicateReasons = reasons.distinct(), otherBatches = otherBatches
            )
        }
        val cats = root.optJSONArray("categories")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() && s.length <= 80 } } }
            ?: emptyList()
        return ImportSession(sessionId, sourceName, if (isZip) "ZIP" else "JSON", root.optString("kind") == "backup",
            items, cats, warnings, dir)
    }

    /**
     * Seçilmiş məhsulları vahid tranzaksiya ilə bazaya yazır. Hər hansı xəta olarsa,
     * tranzaksiya geri qaytarılır və kopyalanmış şəkillər silinir — mövcud məlumatlar dəyişmir.
     */
    suspend fun commitImport(session: ImportSession, selection: Map<Int, DuplicateAction>): ImportResult = withContext(Dispatchers.IO) {
        val dao = repo.db.productDao()
        val logDao = repo.db.notificationLogDao()
        val copiedImages = mutableListOf<String>()
        val replacedImages = mutableListOf<String>()
        val updatedIds = mutableListOf<Long>()
        var added = 0; var updated = 0; var skipped = 0
        try {
            val chosen = session.items.filter { selection.containsKey(it.index) }
            skipped = session.items.size - chosen.size
            // Şəkilləri əvvəlcədən kopyala (tranzaksiyadan kənar fayl əməliyyatları)
            val imageNames = mutableMapOf<Int, String?>()
            chosen.forEach { item ->
                val action = selection[item.index]
                if (action == DuplicateAction.SKIP || action == DuplicateAction.KEEP_EXISTING) return@forEach
                imageNames[item.index] = item.stagedImage?.let { ImageStorage.importFile(context, it) }?.also { copiedImages += it }
            }
            repo.db.withTransaction {
                session.categories.forEach { repo.db.categoryDao().insert(Category(name = it)) }
                chosen.forEach { item ->
                    val action = selection[item.index] ?: DuplicateAction.SKIP
                    val dup = item.duplicate
                    val img = imageNames[item.index]
                    when {
                        action == DuplicateAction.SKIP || (dup != null && action == DuplicateAction.KEEP_EXISTING) -> skipped++
                        dup != null && action == DuplicateAction.UPDATE -> {
                            dao.update(dup.copy(
                                name = item.name, barcode = item.barcode, category = item.category,
                                quantity = item.quantity, expiryEpochDay = item.expiry.toEpochDay(),
                                batchNumber = item.batchNumber, note = item.note,
                                imageFile = img ?: dup.imageFile, updatedAt = System.currentTimeMillis()
                            ))
                            if (img != null) dup.imageFile?.let { replacedImages += it }
                            if (dup.expiryEpochDay != item.expiry.toEpochDay()) logDao.deleteForProduct(dup.id)
                            updatedIds += dup.id
                            updated++
                        }
                        else -> {
                            val uidTaken = dao.getByUid(item.uid) != null
                            dao.insert(Product(
                                uid = if (uidTaken) UUID.randomUUID().toString() else item.uid,
                                name = item.name, barcode = item.barcode, category = item.category,
                                quantity = item.quantity, expiryEpochDay = item.expiry.toEpochDay(),
                                note = item.note, batchNumber = item.batchNumber, imageFile = img
                            ))
                            added++
                        }
                    }
                    if (item.category.isNotBlank()) repo.db.categoryDao().insert(Category(name = item.category))
                }
            }
            // Uğurlu tranzaksiyadan sonra əvəzlənmiş köhnə şəkilləri sil
            replacedImages.forEach { ImageStorage.delete(context, it) }
            updatedIds.forEach { NotificationHelper.cancelForProduct(context, it) }
            val status = if (added + updated == 0) OperationStatus.CANCELLED
                else if (session.warnings.isNotEmpty()) OperationStatus.PARTIAL else OperationStatus.SUCCESS
            repo.addHistory(HistoryEntry(type = OperationType.IMPORT, status = status, itemCount = added + updated,
                format = session.format, fileName = session.sourceName,
                detail = "Əlavə: $added • Yenilənən: $updated • Ötürülən: $skipped" +
                    if (session.warnings.isNotEmpty()) " • Xəbərdarlıq: ${session.warnings.size}" else ""))
            NotificationScheduler.runCheckNow(context)
            ImportResult(added, updated, skipped, 0)
        } catch (e: Exception) {
            copiedImages.forEach { ImageStorage.delete(context, it) }
            repo.addHistory(HistoryEntry(type = OperationType.IMPORT, status = OperationStatus.FAILED, itemCount = 0,
                format = session.format, fileName = session.sourceName,
                detail = "İdxal yarımçıq qaldı, mövcud məlumatlar dəyişdirilmədi: ${e.message}"))
            throw ImportException("İdxal alınmadı, mövcud məlumatlar qorundu: ${e.message}")
        } finally {
            session.stagingDir.deleteRecursively()
        }
    }

    /** Ehtiyat nüsxədən tam bərpa: bütün cari məlumatlar atomik şəkildə əvəz olunur. */
    suspend fun restoreBackup(session: ImportSession): Int = withContext(Dispatchers.IO) {
        val dao = repo.db.productDao()
        val copied = mutableListOf<String>()
        try {
            val names = session.items.associate { it.index to it.stagedImage?.let { f -> ImageStorage.importFile(context, f) }?.also { n -> copied += n } }
            val oldImages = dao.allImageFiles()
            val oldIds = dao.getAll().map { it.id }
            repo.db.withTransaction {
                repo.db.notificationLogDao().deleteAll()
                dao.deleteAll()
                if (session.categories.isNotEmpty()) {
                    repo.db.categoryDao().deleteAll()
                    session.categories.forEach { repo.db.categoryDao().insert(Category(name = it)) }
                }
                session.items.forEach { item ->
                    dao.insert(Product(uid = item.uid, name = item.name, barcode = item.barcode, category = item.category,
                        quantity = item.quantity, expiryEpochDay = item.expiry.toEpochDay(), note = item.note,
                        batchNumber = item.batchNumber, imageFile = names[item.index]))
                    if (item.category.isNotBlank()) repo.db.categoryDao().insert(Category(name = item.category))
                }
            }
            oldImages.forEach { ImageStorage.delete(context, it) }
            oldIds.forEach { NotificationHelper.cancelForProduct(context, it) }
            repo.addHistory(HistoryEntry(type = OperationType.RESTORE, status = OperationStatus.SUCCESS,
                itemCount = session.items.size, format = session.format, fileName = session.sourceName,
                detail = "Bütün məlumatlar ehtiyat nüsxədən bərpa edildi"))
            NotificationScheduler.runCheckNow(context)
            session.items.size
        } catch (e: Exception) {
            copied.forEach { ImageStorage.delete(context, it) }
            repo.addHistory(HistoryEntry(type = OperationType.RESTORE, status = OperationStatus.FAILED, itemCount = 0,
                format = session.format, fileName = session.sourceName, detail = "Bərpa alınmadı, əvvəlki məlumatlar qorundu: ${e.message}"))
            throw ImportException("Bərpa alınmadı, əvvəlki məlumatlar qorundu: ${e.message}")
        } finally {
            session.stagingDir.deleteRecursively()
        }
    }

    fun discard(session: ImportSession) { session.stagingDir.deleteRecursively() }

    private fun cleanImportStaging() {
        val cutoff = System.currentTimeMillis() - 6 * 3600_000L
        importRoot.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun copyLimited(input: InputStream, target: File, limit: Long, error: String): Long {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        FileOutputStream(target).use { out ->
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                total += r
                if (total > limit) { out.close(); target.delete(); throw ImportException(error) }
                out.write(buf, 0, r)
            }
        }
        return total
    }

    companion object {
        const val FORMAT_ID = "sontarix-market-export"
        const val FORMAT_VERSION = 1
        private const val MAX_SOURCE_BYTES = 300L * 1024 * 1024
        private const val MAX_UNCOMPRESSED = 600L * 1024 * 1024
        private const val MAX_JSON_BYTES = 60L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_ENTRIES = 20_000
        private const val MAX_PRODUCTS = 20_000
        private val IMAGE_ENTRY = Regex("^images/[A-Za-z0-9_\\-]{1,80}\\.(jpg|jpeg|png|webp)$")
        private val UID_RE = Regex("^[A-Za-z0-9\\-]{8,64}$")
    }
}
