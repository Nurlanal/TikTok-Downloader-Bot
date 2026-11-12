package az.rafiqmarket.core.network

import az.rafiqmarket.core.BuildConfig
import az.rafiqmarket.core.network.dto.AuthResponse
import az.rafiqmarket.core.network.dto.RefreshBody
import az.rafiqmarket.core.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class AuthInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.encodedPath.startsWith("/api/auth/otp")) return chain.proceed(req)
        val token = store.currentBlocking()?.accessToken ?: return chain.proceed(req)
        return chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * 401 alındıqda refresh token ilə yeni access token alır (rotasiya).
 * Uğursuz olarsa sessiya silinir və tətbiq giriş ekranına qayıdır.
 */
class TokenAuthenticator(
    private val store: SessionStore,
    private val json: Json,
    private val baseClient: OkHttpClient,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.startsWith("/api/auth/")) return null
        if (responseCount(response) >= 2) return null
        synchronized(lock) {
            val current = store.currentBlocking() ?: return null
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Başqa sorğu artıq yeniləyibsə, yeni tokenlə təkrarla
            if (sentToken != null && sentToken != current.accessToken) {
                return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            }
            val body = json.encodeToString(RefreshBody.serializer(), RefreshBody(current.refreshToken))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(BuildConfig.API_BASE_URL + "api/auth/refresh").post(body).build()
            val refreshed = runCatching {
                baseClient.newCall(req).execute().use { r ->
                    if (r.code == 401 || r.code == 403) { runBlocking { store.clear() }; return null }
                    if (!r.isSuccessful) return null
                    json.decodeFromString(AuthResponse.serializer(), r.body!!.string())
                }
            }.getOrNull() ?: return null
            runBlocking { store.save(refreshed) }
            return response.request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()
        }
    }

    private fun responseCount(r: Response): Int {
        var c = 1; var p = r.priorResponse
        while (p != null) { c++; p = p.priorResponse }
        return c
    }
}
