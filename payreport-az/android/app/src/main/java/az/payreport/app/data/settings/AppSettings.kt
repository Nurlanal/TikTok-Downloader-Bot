package az.payreport.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncMode { DIRECT, SERVER }

data class MailConfig(
    val email: String = "",
    val host: String = "imap.mail.ru",
    val port: Int = 993,
    val mailbox: String = "INBOX",
    val senderFilter: String = "",
    val subjectFilter: String = "",
    val initialDays: Int = 30,
)

data class SettingsState(
    val mode: SyncMode = SyncMode.DIRECT,
    val mail: MailConfig = MailConfig(),
    val mailPasswordSet: Boolean = false,
    val serverUrl: String = "",
    val serverUser: String = "",
    val serverLoggedIn: Boolean = false,
    val syncIntervalMinutes: Int = 15,
    val autoSync: Boolean = true,
    val notifications: Boolean = true,
    val pinSet: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSyncStatus: String = "NEVER",
    val lastSyncMessage: String? = null,
)

/** Həssas olmayan tənzimləmələr. Şifrələr və tokenlər [az.payreport.app.core.security.SecureStore]-dadır. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val current: SettingsState get() = _state.value

    private fun load() = SettingsState(
        mode = runCatching { SyncMode.valueOf(prefs.getString("mode", SyncMode.DIRECT.name)!!) }.getOrDefault(SyncMode.DIRECT),
        mail = MailConfig(
            email = prefs.getString("mail_email", "") ?: "",
            host = prefs.getString("mail_host", "imap.mail.ru") ?: "imap.mail.ru",
            port = prefs.getInt("mail_port", 993),
            mailbox = prefs.getString("mail_mailbox", "INBOX") ?: "INBOX",
            senderFilter = prefs.getString("mail_sender_filter", "") ?: "",
            subjectFilter = prefs.getString("mail_subject_filter", "") ?: "",
            initialDays = prefs.getInt("mail_initial_days", 30),
        ),
        mailPasswordSet = prefs.getBoolean("mail_password_set", false),
        serverUrl = prefs.getString("server_url", "") ?: "",
        serverUser = prefs.getString("server_user", "") ?: "",
        serverLoggedIn = prefs.getBoolean("server_logged_in", false),
        syncIntervalMinutes = prefs.getInt("sync_interval", 15),
        autoSync = prefs.getBoolean("auto_sync", true),
        notifications = prefs.getBoolean("notifications", true),
        pinSet = prefs.getBoolean("pin_set", false),
        lastSyncAt = prefs.getLong("last_sync_at", 0),
        lastSyncStatus = prefs.getString("last_sync_status", "NEVER") ?: "NEVER",
        lastSyncMessage = prefs.getString("last_sync_message", null),
    )

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    fun setMode(mode: SyncMode) = edit { putString("mode", mode.name) }

    fun setMail(c: MailConfig) = edit {
        putString("mail_email", c.email.trim())
        putString("mail_host", c.host.trim())
        putInt("mail_port", c.port)
        putString("mail_mailbox", c.mailbox.trim().ifEmpty { "INBOX" })
        putString("mail_sender_filter", c.senderFilter.trim())
        putString("mail_subject_filter", c.subjectFilter.trim())
        putInt("mail_initial_days", c.initialDays.coerceIn(1, 3650))
    }

    fun setMailPasswordSet(v: Boolean) = edit { putBoolean("mail_password_set", v) }

    fun setServer(url: String, user: String, loggedIn: Boolean) = edit {
        putString("server_url", url.trim().trimEnd('/'))
        putString("server_user", user.trim())
        putBoolean("server_logged_in", loggedIn)
    }

    fun setSync(intervalMinutes: Int, auto: Boolean, notifications: Boolean) = edit {
        putInt("sync_interval", intervalMinutes.coerceAtLeast(15))
        putBoolean("auto_sync", auto)
        putBoolean("notifications", notifications)
    }

    fun setPinSet(v: Boolean) = edit { putBoolean("pin_set", v) }

    fun setLastSync(at: Long, status: String, message: String?) = edit {
        putLong("last_sync_at", at)
        putString("last_sync_status", status)
        putString("last_sync_message", message)
    }

    // ---------------------------------------------- IMAP kursoru (birbaşa rejim)
    var imapUidValidity: Long
        get() = prefs.getLong("imap_uid_validity", -1)
        set(v) = prefs.edit().putLong("imap_uid_validity", v).apply()

    var imapLastUid: Long
        get() = prefs.getLong("imap_last_uid", 0)
        set(v) = prefs.edit().putLong("imap_last_uid", v).apply()

    fun resetImapCursor() = prefs.edit().remove("imap_uid_validity").remove("imap_last_uid").apply()

    // ---------------------------------------------- Server kursoru
    var serverCursorUpdatedAt: String
        get() = prefs.getString("server_cursor_updated", "1970-01-01T00:00:00.000Z")!!
        set(v) = prefs.edit().putString("server_cursor_updated", v).apply()

    var serverCursorId: Long
        get() = prefs.getLong("server_cursor_id", 0)
        set(v) = prefs.edit().putLong("server_cursor_id", v).apply()

    fun resetServerCursor() = prefs.edit().remove("server_cursor_updated").remove("server_cursor_id").apply()
}
