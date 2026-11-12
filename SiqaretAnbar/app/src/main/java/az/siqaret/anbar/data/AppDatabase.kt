package az.siqaret.anbar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Product::class, HistoryEntry::class, Firm::class, Invoice::class, InvoiceItem::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun historyDao(): HistoryDao
    abstract fun firmDao(): FirmDao
    abstract fun invoiceDao(): InvoiceDao

    companion object {
        const val NAME = "siqaret_anbar.db"

        /** v1 → v2: firmalar cədvəli + məhsullara firmId. Köhnə məlumatlar saxlanılır. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `firms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_firms_name` ON `firms` (`name`)")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `firmId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_firmId` ON `products` (`firmId`)")
            }
        }

        /** v2 → v3: qaimələr. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`firmId` INTEGER NOT NULL, `number` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_firmId` ON `invoices` (`firmId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_date` ON `invoices` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `invoice_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`invoiceId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, " +
                        "`boxPrice` REAL NOT NULL, `blockPrice` REAL NOT NULL, `blockCount` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_invoiceId` ON `invoice_items` (`invoiceId`)")
            }
        }

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
