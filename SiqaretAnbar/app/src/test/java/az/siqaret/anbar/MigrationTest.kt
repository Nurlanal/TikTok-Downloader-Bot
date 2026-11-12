package az.siqaret.anbar

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import az.siqaret.anbar.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v1.0.0 bazası (firmalarsız) yeni versiyaya keçəndə məlumatlar itməməlidir. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun v1DataSurvivesUpgrade() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration_test.db"
        val file = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `boxPrice` REAL NOT NULL, `blockPrice` REAL NOT NULL, `blockCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `type` TEXT NOT NULL, `blockDelta` INTEGER NOT NULL, `blocksAfter` INTEGER NOT NULL, `blockPrice` REAL NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_timestamp` ON `history` (`timestamp`)")
            db.execSQL("INSERT INTO products (name, boxPrice, blockPrice, blockCount, createdAt, updatedAt) VALUES ('Marlboro', 3.4, 34, 12, 1, 1)")
            db.execSQL("INSERT INTO history (productId, productName, type, blockDelta, blocksAfter, blockPrice, note, timestamp) VALUES (1, 'Marlboro', 'ADDED', 12, 12, 34, '', 1)")
            db.version = 1
        }

        val room = AppDatabase.create(ctx, name)
        try {
            val products = room.productDao().getAll()
            assertEquals(1, products.size)
            assertEquals("Marlboro", products[0].name)
            assertEquals(12, products[0].blockCount)
            assertNull(products[0].firmId)
            assertEquals(1, room.historyDao().getSince(0).size)

            // yeni funksiya işləyir
            val firmId = room.firmDao().insert(az.siqaret.anbar.data.Firm(name = "Azərsun"))
            room.productDao().update(products[0].copy(firmId = firmId))
            assertEquals(firmId, room.productDao().getById(1)!!.firmId)
        } finally {
            room.close()
        }
    }
}
