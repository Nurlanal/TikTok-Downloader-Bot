package az.rafiqmarket.core.network

import az.rafiqmarket.core.BuildConfig

object Urls {
    val apiBase: String get() = BuildConfig.API_BASE_URL
    val ws: String get() = apiBase.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

    /** Server "/uploads/x.jpg" kimi nisbi yol qaytarır */
    fun resolve(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> apiBase.trimEnd('/') + "/" + path.trimStart('/')
    }
}
