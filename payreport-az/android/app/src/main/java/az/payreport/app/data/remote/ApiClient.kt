package az.payreport.app.data.remote

import az.payreport.app.BuildConfig
import az.payreport.app.core.format.Fmt
import az.payreport.app.data.db.AuditEntity
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.SyncRunEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

@Serializable
data class RemotePayment(
    val id: Long,
    val dedupeKey: String,
    val phone: String? = null,
    val amountMinor: Long? = null,
    val currency: String = "AZN",
    val paidAt: String,
    val status: String,
    val source: String,
    val confidence: Int = 0,
    val txnId: String? = null,
    val emailMessageId: String? = null,
    val emailSubject: String? = null,
    val emailFrom: String? = null,
    val rawText: String? = null,
    val note: String? = null,
    val cancelReason: String? = null,
    val createdBy: String = "system",
    val createdAt: String,
    val updatedAt: String,
) {
    fun toEntity() = PaymentEntity(
        id = id, dedupeKey = dedupeKey, phone = phone, amountMinor = amountMinor, currency = currency,
        paidAt = Fmt.parseIso(paidAt) ?: 0, status = status, source = source, confidence = confidence, txnId = txnId,
        emailMessageId = emailMessageId, emailSubject = emailSubject, emailFrom = emailFrom, rawText = rawText,
        note = note, cancelReason = cancelReason, createdBy = createdBy,
        createdAt = Fmt.parseIso(createdAt) ?: 0, updatedAt = Fmt.parseIso(updatedAt) ?: 0,
    )
}

@Serializable data class PaymentsPage(val items: List<RemotePayment>, val serverTime: String? = null)

@Serializable
data class RemoteAudit(
    val id: Long, val paymentId: Long? = null, val action: String, val actor: String,
    val details: JsonElement? = null, val createdAt: String,
) {
    fun toEntity() = AuditEntity(id, paymentId, action, actor, details?.toString(), Fmt.parseIso(createdAt) ?: 0)
}

@Serializable data class AuditPage(val items: List<RemoteAudit>)

@Serializable
data class RemoteSyncRun(
    val id: Long, val trigger: String, val startedAt: String, val finishedAt: String? = null, val status: String,
    val fetched: Int = 0, val created: Int = 0, val pending: Int = 0, val duplicates: Int = 0, val errors: Int = 0,
    val message: String? = null,
) {
    fun toEntity() = SyncRunEntity(
        id, "SERVER-$trigger", Fmt.parseIso(startedAt) ?: 0, finishedAt?.let { Fmt.parseIso(it) }, status,
        fetched, created, pending, duplicates, errors, message,
    )
}

@Serializable data class SyncRunsPage(val items: List<RemoteSyncRun>)

@Serializable data class LoginResponse(val token: String, val expiresAt: String)

@Serializable
data class RemoteMailAccount(
    val configured: Boolean = false, val email: String? = null, val host: String? = null, val port: Int? = null,
    val mailbox: String? = null, val senderFilter: String? = null, val subjectFilter: String? = null,
    val initialDays: Int? = null, val enabled: Boolean? = null, val lastSyncAt: String? = null,
    val lastStatus: String? = null, val lastError: String? = null,
)

@Serializable data class RemoteMailStatus(val account: RemoteMailAccount, val syncRunning: Boolean = false, val ocrEnabled: Boolean = false)

@Serializable data class RemoteTestResult(val ok: Boolean, val messages: Int = 0, val unseen: Int = 0, val latencyMs: Long = 0, val error: String? = null)

@Serializable data class IngestTextResponse(val created: Int, val duplicates: Int = 0, val payments: List<RemotePayment> = emptyList())

/**
 * PayReport AZ backend REST API klienti. Release versiyada yalnız HTTPS qəbul olunur.
 */
class ApiClient(private val baseUrlProvider: () -> String, private val tokenProvider: () -> String?) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    companion object {
        fun validateBaseUrl(url: String): String? {
            val u = url.trim().trimEnd('/').toHttpUrlOrNull() ?: return "URL düzgün deyil"
            if (!u.isHttps && !BuildConfig.DEBUG) return "Təhlükəsizlik üçün yalnız HTTPS ünvanı qəbul olunur"
            return null
        }
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null, auth: Boolean = true, token: String? = null): String =
        withContext(Dispatchers.IO) {
            val base = baseUrlProvider().trimEnd('/')
            validateBaseUrl(base)?.let { throw ApiException(0, it) }
            val rb = Request.Builder().url("$base/api$path")
            if (auth) {
                val t = token ?: tokenProvider() ?: throw ApiException(401, "Serverə daxil olunmayıb")
                rb.header("Authorization", "Bearer $t")
            }
            val reqBody = body?.toString()?.toRequestBody(jsonType)
            when (method) {
                "GET" -> rb.get()
                "POST" -> rb.post(reqBody ?: "{}".toRequestBody(jsonType))
                "PATCH" -> rb.patch(reqBody ?: "{}".toRequestBody(jsonType))
                "PUT" -> rb.put(reqBody ?: "{}".toRequestBody(jsonType))
            }
            http.newCall(rb.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val err = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw ApiException(res.code, err ?: "Server xətası (${res.code})")
                }
                text
            }
        }

    suspend fun login(baseUrl: String, username: String, password: String, device: String): LoginResponse {
        val body = buildJsonObject { put("username", username); put("password", password); put("deviceName", device) }
        val text = withContext(Dispatchers.IO) {
            validateBaseUrl(baseUrl)?.let { throw ApiException(0, it) }
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/auth/login").post(body.toString().toRequestBody(jsonType)).build()
            http.newCall(req).execute().use { res ->
                val t = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val err = runCatching { json.parseToJsonElement(t).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw ApiException(res.code, err ?: "Giriş uğursuz oldu (${res.code})")
                }
                t
            }
        }
        return json.decodeFromString(text)
    }

    suspend fun logout() = runCatching { call("POST", "/auth/logout") }

    suspend fun payments(updatedSince: String, afterId: Long, limit: Int = 500): PaymentsPage =
        json.decodeFromString(call("GET", "/payments?updated_since=${enc(updatedSince)}&after_id=$afterId&limit=$limit"))

    suspend fun audit(sinceId: Long): AuditPage = json.decodeFromString(call("GET", "/audit?since_id=$sinceId&limit=1000"))

    suspend fun syncRuns(): SyncRunsPage = json.decodeFromString(call("GET", "/sync/runs?limit=50"))

    suspend fun runServerSync(): JsonObject = json.parseToJsonElement(call("POST", "/sync/run")).jsonObject

    suspend fun mailStatus(): RemoteMailStatus = json.decodeFromString(call("GET", "/mail/status"))

    suspend fun createPayment(phone: String, amountMinor: Long, paidAtIso: String, txnId: String?, note: String?): RemotePayment {
        val body = buildJsonObject {
            put("phone", phone); put("amountMinor", amountMinor); put("paidAt", paidAtIso)
            txnId?.let { put("txnId", it) }; note?.let { put("note", it) }
        }
        return json.decodeFromString(call("POST", "/payments", body))
    }

    suspend fun updatePayment(id: Long, patch: JsonObject): RemotePayment = json.decodeFromString(call("PATCH", "/payments/$id", patch))

    suspend fun confirm(id: Long): RemotePayment = json.decodeFromString(call("POST", "/payments/$id/confirm"))

    suspend fun cancel(id: Long, reason: String?): RemotePayment =
        json.decodeFromString(call("POST", "/payments/$id/cancel", buildJsonObject { reason?.let { put("reason", it) } }))

    suspend fun ingestText(text: String, source: String, reference: String): IngestTextResponse =
        json.decodeFromString(call("POST", "/payments/ingest-text", buildJsonObject {
            put("text", text); put("source", source); put("reference", reference)
        }))

    suspend fun putMailConfig(body: JsonObject): RemoteMailAccount = json.decodeFromString(call("PUT", "/mail/config", body))

    suspend fun testMail(body: JsonObject): RemoteTestResult = json.decodeFromString(call("POST", "/mail/test", body))

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
