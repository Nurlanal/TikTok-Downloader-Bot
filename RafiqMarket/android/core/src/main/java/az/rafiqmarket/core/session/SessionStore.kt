package az.rafiqmarket.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import az.rafiqmarket.core.network.dto.AuthResponse
import az.rafiqmarket.core.network.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rafiq_session")

data class Session(val accessToken: String, val refreshToken: String, val user: UserDto)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Sessiya tətbiqin şəxsi yaddaşında saxlanılır (allowBackup=false – ehtiyat nüsxəyə düşmür).
 * İstifadəçinin rolu yalnız serverdən gələn cavaba əsasən yenilənir.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val kAccess = stringPreferencesKey("access")
    private val kRefresh = stringPreferencesKey("refresh")
    private val kUser = stringPreferencesKey("user")
    private val kTheme = stringPreferencesKey("theme")

    val session: Flow<Session?> = context.dataStore.data.map { p ->
        val a = p[kAccess]; val r = p[kRefresh]; val u = p[kUser]
        if (a == null || r == null || u == null) null
        else runCatching { Session(a, r, json.decodeFromString(UserDto.serializer(), u)) }.getOrNull()
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[kTheme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun current(): Session? = session.first()

    /** OkHttp interceptor sinxron işləyir – yalnız orada istifadə olunur */
    fun currentBlocking(): Session? = runBlocking { current() }

    suspend fun save(auth: AuthResponse) {
        context.dataStore.edit {
            it[kAccess] = auth.accessToken
            it[kRefresh] = auth.refreshToken
            it[kUser] = json.encodeToString(UserDto.serializer(), auth.user)
        }
    }

    suspend fun updateUser(user: UserDto) {
        context.dataStore.edit { if (it[kAccess] != null) it[kUser] = json.encodeToString(UserDto.serializer(), user) }
    }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[kTheme] = mode.name } }

    suspend fun clear() {
        context.dataStore.edit { it.remove(kAccess); it.remove(kRefresh); it.remove(kUser) }
    }
}
