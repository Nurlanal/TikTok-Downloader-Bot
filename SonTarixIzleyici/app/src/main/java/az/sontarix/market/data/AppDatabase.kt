package az.sontarix.market.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun opTypeToString(v: OperationType): String = v.name
    @TypeConverter fun stringToOpType(v: String): OperationType = OperationType.valueOf(v)
    @TypeConverter fun opStatusToString(v: OperationStatus): String = v.name
    @TypeConverter fun stringToOpStatus(v: String): OperationStatus = OperationStatus.valueOf(v)
}

@Database(
    entities = [Product::class, Category::class, NotificationLog::class, HistoryEntry::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun historyDao(): HistoryDao

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Süd məhsulları", "Ət və toyuq", "Çörək və un məmulatları", "İçkilər",
            "Şirniyyat", "Konservlər", "Meyvə və tərəvəz", "Dondurulmuş məhsullar",
            "Ədviyyat və souslar", "Məişət kimyası", "Gigiyena", "Uşaq məhsulları", "Digər"
        )

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "sontarix.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATEGORIES.forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO categories(name) VALUES (?)", arrayOf(name))
                        }
                    }
                })
                .build().also { instance = it }
        }
    }
}
