package az.siqaret.anbar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Product::class, HistoryEntry::class, Firm::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun historyDao(): HistoryDao
    abstract fun firmDao(): FirmDao

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

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
