package az.sontarix.market

import android.app.Application
import android.content.Context
import az.sontarix.market.data.AppDatabase
import az.sontarix.market.data.ProductRepository
import az.sontarix.market.data.SettingsRepository
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.notify.NotificationScheduler
import az.sontarix.market.transfer.TransferManager

class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ProductRepository(context, db)
    val transfer = TransferManager(context, repository)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        NotificationScheduler.scheduleAll(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as App).container
