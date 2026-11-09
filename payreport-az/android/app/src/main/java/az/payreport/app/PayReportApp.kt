package az.payreport.app

import android.app.Application
import az.payreport.app.work.SyncWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PayReportApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PDFBoxResourceLoader.init(applicationContext)
        val s = container.settings.current
        val configured = s.mailPasswordSet || s.serverLoggedIn
        SyncWorker.schedule(this, s.syncIntervalMinutes, s.autoSync && configured)
    }
}
