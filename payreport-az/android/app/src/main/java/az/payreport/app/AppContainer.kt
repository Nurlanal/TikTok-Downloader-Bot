package az.payreport.app

import android.content.Context
import az.payreport.app.core.security.SecureStore
import az.payreport.app.data.db.AppDatabase
import az.payreport.app.data.extract.OcrEngine
import az.payreport.app.data.extract.PdfTextExtractor
import az.payreport.app.data.mail.DirectMailSync
import az.payreport.app.data.remote.ApiClient
import az.payreport.app.data.repo.PaymentRepository
import az.payreport.app.data.repo.SyncManager
import az.payreport.app.data.settings.AppSettings
import az.payreport.app.export.ReportExporter

/** Sadə asılılıq konteyneri (manual DI). */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val secureStore = SecureStore(app)
    val settings = AppSettings(app)
    val db: AppDatabase by lazy { AppDatabase.create(app, secureStore) }
    val ocr = OcrEngine(app)
    val pdf = PdfTextExtractor(app, ocr)
    val api = ApiClient({ settings.current.serverUrl }, { secureStore.getString(SecureStore.API_TOKEN) })
    val directMail by lazy { DirectMailSync(db, settings, secureStore, ocr, pdf) }
    val repository by lazy { PaymentRepository(db, settings, api) }
    val syncManager by lazy { SyncManager(app, db, settings, directMail, api) }
    val exporter = ReportExporter(app)
}
