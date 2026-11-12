package az.rafiqmarket.core.network

import az.rafiqmarket.core.network.dto.ApiErrorDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Server xətalarını istifadəçiyə göstəriləcək Azərbaycan dilli mesaja çevirir */
class AppException(val code: String?, override val message: String, val httpStatus: Int? = null) : Exception(message)

private val errorJson = Json { ignoreUnknownKeys = true }

fun Throwable.toAppException(): AppException = when (this) {
    is AppException -> this
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val dto = body?.let { runCatching { errorJson.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull() }
        AppException(dto?.code, dto?.message ?: when (code()) {
            401 -> "Sessiyanın vaxtı bitib. Yenidən daxil olun."
            403 -> "Bu əməliyyat üçün icazəniz yoxdur."
            404 -> "Tapılmadı."
            else -> "Server xətası (${code()})."
        }, code())
    }
    is IOException -> AppException("NETWORK", "İnternet bağlantısı yoxdur və ya server əlçatan deyil.")
    else -> AppException(null, message ?: "Naməlum xəta")
}

/** ViewModel-lərdə: runCatching-dən fərqli olaraq ləğv edilmə istisnasını udmur */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toAppException())
}

val Throwable.userMessage: String get() = toAppException().message
