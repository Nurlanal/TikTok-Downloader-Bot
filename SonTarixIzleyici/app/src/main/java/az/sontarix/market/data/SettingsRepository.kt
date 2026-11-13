package az.sontarix.market.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("İşıqlı"), DARK("Qaranlıq") }

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    /** Son istifadə tarixindən neçə gün əvvəl bildiriş göndərilsin (0 = bitdiyi gün). */
    val thresholds: List<Int> = DEFAULT_THRESHOLDS,
    val notifyExpired: Boolean = true,
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val soundEnabled: Boolean = true,
    val dailySummary: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    /** "Yaxınlaşır" (sarı) pəncərəsi — ən böyük xəbərdarlıq günü, minimum 8. */
    val warningDays: Int get() = (thresholds.maxOrNull() ?: 30).coerceAtLeast(8)

    companion object {
        val DEFAULT_THRESHOLDS = listOf(30, 15, 7, 3, 1, 0)
    }
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    private fun read(): AppSettings {
        val d = AppSettings()
        val thresholds = prefs.getString(K_THRESHOLDS, null)
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 0..365 }?.distinct()?.sortedDescending()
            ?: d.thresholds
        return AppSettings(
            notificationsEnabled = prefs.getBoolean(K_ENABLED, d.notificationsEnabled),
            thresholds = thresholds,
            notifyExpired = prefs.getBoolean(K_EXPIRED, d.notifyExpired),
            notifyHour = prefs.getInt(K_HOUR, d.notifyHour),
            notifyMinute = prefs.getInt(K_MINUTE, d.notifyMinute),
            soundEnabled = prefs.getBoolean(K_SOUND, d.soundEnabled),
            dailySummary = prefs.getBoolean(K_SUMMARY, d.dailySummary),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, null) ?: "") }
                .getOrDefault(d.themeMode)
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value).let {
            it.copy(thresholds = it.thresholds.filter { t -> t in 0..365 }.distinct().sortedDescending())
        }
        prefs.edit()
            .putBoolean(K_ENABLED, s.notificationsEnabled)
            .putString(K_THRESHOLDS, s.thresholds.joinToString(","))
            .putBoolean(K_EXPIRED, s.notifyExpired)
            .putInt(K_HOUR, s.notifyHour)
            .putInt(K_MINUTE, s.notifyMinute)
            .putBoolean(K_SOUND, s.soundEnabled)
            .putBoolean(K_SUMMARY, s.dailySummary)
            .putString(K_THEME, s.themeMode.name)
            .apply()
        _settings.value = s
    }

    /** Gündəlik hesabatın hansı gün göndərildiyini saxlayır (təkrar göndərilməsin). */
    var lastSummaryEpochDay: Long
        get() = prefs.getLong(K_LAST_SUMMARY, -1)
        set(value) { prefs.edit().putLong(K_LAST_SUMMARY, value).apply() }

    var permissionAsked: Boolean
        get() = prefs.getBoolean(K_PERM_ASKED, false)
        set(value) { prefs.edit().putBoolean(K_PERM_ASKED, value).apply() }

    private companion object {
        const val K_ENABLED = "notifications_enabled"
        const val K_THRESHOLDS = "thresholds"
        const val K_EXPIRED = "notify_expired"
        const val K_HOUR = "notify_hour"
        const val K_MINUTE = "notify_minute"
        const val K_SOUND = "sound"
        const val K_SUMMARY = "daily_summary"
        const val K_THEME = "theme"
        const val K_LAST_SUMMARY = "last_summary_day"
        const val K_PERM_ASKED = "perm_asked"
    }
}
