package az.rafiqmarket.core.realtime

import android.util.Log
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.Urls
import az.rafiqmarket.core.network.dto.NotificationDto
import az.rafiqmarket.core.network.dto.OrderDto
import az.rafiqmarket.core.session.SessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class CourierLocationEvent(
    val orderId: Long? = null, val courierId: Long, val lat: Double, val lng: Double,
    val heading: Double? = null, val updatedAt: String,
)

sealed interface RealtimeEvent {
    data class OrderUpdated(val order: OrderDto) : RealtimeEvent
    data class CourierLocation(val location: CourierLocationEvent) : RealtimeEvent
    data class Notification(val notification: NotificationDto) : RealtimeEvent
}

/**
 * WebSocket ilə real vaxt yenilənmələri. Bağlantı kəsildikdə avtomatik yenidən qoşulur.
 */
@Singleton
class RealtimeClient @Inject constructor(
    @Named("ws") private val client: OkHttpClient,
    private val store: SessionStore,
    private val api: RafiqApi,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var wanted = false
    private var attempt = 0
    private val orderSubs = ConcurrentHashMap.newKeySet<Long>()

    @Synchronized
    fun connect() {
        wanted = true
        if (socket != null) return
        scope.launch { open() }
    }

    private suspend fun open() {
        val token = store.current()?.accessToken ?: return
        val req = Request.Builder().url(Urls.ws).header("Authorization", "Bearer $token").build()
        synchronized(this) {
            if (socket != null || !wanted) return
            socket = client.newWebSocket(req, listener)
        }
    }

    @Synchronized
    fun disconnect() {
        wanted = false
        reconnectJob?.cancel()
        socket?.close(1000, "bye")
        socket = null
        orderSubs.clear()
        _connected.value = false
    }

    fun subscribeOrder(orderId: Long) {
        orderSubs.add(orderId)
        socket?.send("""{"type":"subscribe_order","orderId":$orderId}""")
    }

    fun unsubscribeOrder(orderId: Long) {
        orderSubs.remove(orderId)
        socket?.send("""{"type":"unsubscribe_order","orderId":$orderId}""")
    }

    private fun scheduleReconnect(authFailed: Boolean) {
        synchronized(this) { socket = null }
        _connected.value = false
        if (!wanted) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (authFailed) runCatching { api.me() } // TokenAuthenticator tokeni yeniləyir
            val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
            attempt++
            delay(delayMs)
            open()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            _connected.value = true
            orderSubs.forEach { webSocket.send("""{"type":"subscribe_order","orderId":$it}""") }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { parseRealtimeEvent(json, text)?.let { _events.tryEmit(it) } }
                .onFailure { Log.w("Realtime", "parse: ${it.message}") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleReconnect(code == 4401)
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            scheduleReconnect(response?.code == 401)
    }
}

/** Server WebSocket mesajını hadisəyə çevirir (unit testlə yoxlanılır) */
fun parseRealtimeEvent(json: Json, text: String): RealtimeEvent? {
    val obj = json.parseToJsonElement(text).jsonObject
    val data = obj["data"] ?: return null
    return when (obj["type"]?.jsonPrimitive?.content) {
        "order.updated" -> RealtimeEvent.OrderUpdated(json.decodeFromJsonElement(OrderDto.serializer(), data))
        "courier.location" -> RealtimeEvent.CourierLocation(json.decodeFromJsonElement(CourierLocationEvent.serializer(), data))
        "notification" -> {
            val o = data.jsonObject
            RealtimeEvent.Notification(
                NotificationDto(
                    id = o["id"]!!.jsonPrimitive.long,
                    title = o["title"]!!.jsonPrimitive.content,
                    body = o["body"]!!.jsonPrimitive.content,
                    orderId = o["order_id"]?.jsonPrimitive?.longOrNull,
                    createdAt = o["created_at"]?.jsonPrimitive?.content ?: "",
                )
            )
        }
        else -> null
    }
}
