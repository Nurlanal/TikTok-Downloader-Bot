package az.payreport.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import az.payreport.app.core.security.SecureStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [PaymentEntity::class, AuditEntity::class, SyncRunEntity::class, ProcessedEmailEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun payments(): PaymentDao
    abstract fun audit(): AuditDao
    abstract fun syncRuns(): SyncRunDao
    abstract fun processedEmails(): ProcessedEmailDao

    companion object {
        private const val NAME = "payreport.db"

        /**
         * SQLCipher ilə şifrələnmiş baza. Açar təsadüfi yaradılır və Android Keystore
         * ilə qorunan [SecureStore]-da saxlanılır — kodda heç bir açar yoxdur.
         */
        fun create(context: Context, secureStore: SecureStore): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = secureStore.databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
