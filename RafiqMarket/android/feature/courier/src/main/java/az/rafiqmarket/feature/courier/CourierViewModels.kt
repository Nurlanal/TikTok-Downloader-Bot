package az.rafiqmarket.feature.courier

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.CourierStatsDto
import az.rafiqmarket.core.network.dto.OrderDto
import az.rafiqmarket.core.network.dto.StatusBody
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.realtime.RealtimeEvent
import az.rafiqmarket.core.util.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val TRACKING_STATUSES = setOf(OrderStatus.PICKED_UP, OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED)

@HiltViewModel
class CourierHomeViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : ViewModel() {
    val tabs = listOf("new" to "Yeni", "active" to "Aktiv", "delivered" to "Çatdırılmış")
    val orders = MutableStateFlow<Map<String, List<OrderDto>>>(emptyMap())
    val stats = MutableStateFlow<CourierStatsDto?>(null)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    /** Canlı izlənməli sifariş varmı – GPS xidmətini idarə edir */
    val needsTracking = orders.map { m -> m["active"].orEmpty().any { it.status in TRACKING_STATUSES } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refresh()
        viewModelScope.launch {
            realtime.events.filter { it is RealtimeEvent.OrderUpdated || it is RealtimeEvent.Notification }.collect { refresh() }
        }
    }

    fun refresh() = viewModelScope.launch {
        loading.value = true
        error.value = null
        val result = mutableMapOf<String, List<OrderDto>>()
        for ((tab, _) in tabs) {
            apiCall { api.courierOrders(tab) }.onSuccess { result[tab] = it }.onFailure { error.value = it.message }
        }
        if (result.isNotEmpty()) orders.value = orders.value + result
        apiCall { api.courierStats() }.onSuccess { stats.value = it }
        loading.value = false
    }
}

data class CourierOrderUi(val order: OrderDto? = null, val error: String? = null, val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class CourierOrderViewModel @Inject constructor(
    private val api: RafiqApi, realtime: RealtimeClient, handle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = checkNotNull(handle["orderId"])
    val state = MutableStateFlow(CourierOrderUi())

    init {
        load()
        viewModelScope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.OrderUpdated>().filter { it.order.id == id }.collect { e ->
                state.update { it.copy(order = e.order) }
            }
        }
    }

    fun load() = viewModelScope.launch {
        apiCall { api.courierOrder(id) }.onSuccess { o -> state.update { it.copy(order = o, error = null) } }
            .onFailure { e -> state.update { it.copy(error = e.message) } }
    }

    private fun act(success: String?, block: suspend () -> OrderDto) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        apiCall { block() }
            .onSuccess { o -> state.update { it.copy(order = o, busy = false, message = success) } }
            .onFailure { e -> state.update { it.copy(busy = false, message = e.message) } }
    }

    fun accept() = act("Sifariş qəbul edildi") { api.courierAccept(id) }
    fun reject() = act("Sifarişdən imtina edildi") { api.courierReject(id) }
    fun confirmCash() = act("Nağd ödəniş təsdiqləndi") { api.courierConfirmCash(id) }
    fun advance(next: String) = act(null) { api.courierStatus(id, StatusBody(next)) }
    fun clearMessage() = state.update { it.copy(message = null) }
}

/** Kuryer üçün növbəti addım */
fun nextCourierStep(o: OrderDto): Pair<String, String>? = when (o.status) {
    OrderStatus.COURIER_ASSIGNED -> if (o.courierAcceptedAt != null) OrderStatus.PICKED_UP to "Sifarişi götürdüm" else null
    OrderStatus.PICKED_UP -> OrderStatus.ON_THE_WAY to "Yola düşdüm"
    OrderStatus.ON_THE_WAY -> OrderStatus.ARRIVED to "Ünvana çatdım"
    OrderStatus.ARRIVED -> OrderStatus.DELIVERED to "Təhvil verdim"
    else -> null
}
