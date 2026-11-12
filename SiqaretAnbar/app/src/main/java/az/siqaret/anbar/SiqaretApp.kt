package az.siqaret.anbar

import android.app.Application
import az.siqaret.anbar.data.AppDatabase
import az.siqaret.anbar.data.InventoryRepository

class SiqaretApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: InventoryRepository by lazy { InventoryRepository(database) }
    val settings: ThemeSettings by lazy { ThemeSettings(this) }
}
