package az.rafiqmarket.feature.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.data.CartRepository
import az.rafiqmarket.core.db.CartItemEntity
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.*
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.realtime.RealtimeEvent
import az.rafiqmarket.core.session.SessionStore
import az.rafiqmarket.core.util.OrderStatus
import az.rafiqmarket.core.util.pointInPolygon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Səbət əməliyyatları – bütün müştəri ekranlarında ortaq */
abstract class CartAwareViewModel(protected val cart: CartRepository) : ViewModel() {
    val cartQuantities: StateFlow<Map<Long, Int>> = cart.quantities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun add(p: ProductDto) = viewModelScope.launch { cart.add(p) }
    fun minus(p: ProductDto) = viewModelScope.launch { cart.decrement(p.id) }
}

// ---------------- Ana səhifə ----------------
@HiltViewModel
class HomeViewModel @Inject constructor(private val api: RafiqApi, cart: CartRepository) : CartAwareViewModel(cart) {
    val home = MutableStateFlow<HomeDto?>(null)
    val error = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)

    init { load() }

    fun load() = viewModelScope.launch {
        refreshing.value = true; error.value = null
        apiCall { api.home() }.onSuccess { home.value = it }.onFailure { error.value = it.message }
        refreshing.value = false
    }
}

// ---------------- Axtarış / kateqoriya ----------------
@OptIn(FlowPreview::class)
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val api: RafiqApi, cart: CartRepository, handle: SavedStateHandle,
) : CartAwareViewModel(cart) {
    val categoryId: Long? = handle.get<Long>("categoryId")?.takeIf { it > 0 }
    val title: String = handle.get<String>("title") ?: "Axtarış"
    val query = MutableStateFlow("")
    val products = MutableStateFlow<List<ProductDto>?>(null)
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collectLatest { q -> fetch(q) }
        }
    }

    fun retry() = viewModelScope.launch { fetch(query.value) }

    private suspend fun fetch(q: String) {
        if (categoryId == null && q.isBlank()) { products.value = emptyList(); return }
        error.value = null
        apiCall { api.products(q = q.trim().ifBlank { null }, categoryId = categoryId) }
            .onSuccess { products.value = it }
            .onFailure { error.value = it.message }
    }
}

// ---------------- Səbət ----------------
data class CartUi(val items: List<CartItemEntity> = emptyList(), val quote: QuoteDto? = null, val quoteError: String? = null, val loading: Boolean = false)

@OptIn(FlowPreview::class)
@HiltViewModel
class CartViewModel @Inject constructor(private val api: RafiqApi, private val cartRepo: CartRepository) : ViewModel() {
    val state = MutableStateFlow(CartUi())

    init {
        viewModelScope.launch {
            cartRepo.items.collect { items -> state.update { it.copy(items = items) } }
        }
        viewModelScope.launch {
            cartRepo.items.map { l -> l.map { it.productId to it.quantity } }.distinctUntilChanged().debounce(400).collectLatest { refreshQuote() }
        }
    }

    /** Qiymət, çatdırılma haqqı və stok serverdə hesablanır */
    suspend fun refreshQuote() {
        val items = cartRepo.snapshot()
        if (items.isEmpty()) { state.update { it.copy(quote = null, quoteError = null) }; return }
        state.update { it.copy(loading = true) }
        apiCall { api.quote(QuoteBody(items.map { OrderItemBody(it.productId, it.quantity) })) }
            .onSuccess { q ->
                q.lines.forEach { l -> if (!l.ok || items.first { it.productId == l.productId }.price != l.unitPrice) cartRepo.syncFromServer(l.productId, l.unitPrice, l.availableQuantity) }
                state.update { it.copy(quote = q, quoteError = null, loading = false) }
            }
            .onFailure { e -> state.update { it.copy(quoteError = e.message, loading = false) } }
    }

    fun plus(id: Long) = viewModelScope.launch { cartRepo.increment(id) }
    fun minus(id: Long) = viewModelScope.launch { cartRepo.decrement(id) }
    fun remove(id: Long) = viewModelScope.launch { cartRepo.setQuantity(id, 0) }
    fun clear() = viewModelScope.launch { cartRepo.clear() }
}

// ---------------- Sifarişin rəsmiləşdirilməsi ----------------
data class CheckoutUi(
    val street: String = "", val house: String = "", val addressNote: String = "", val contactPhone: String = "",
    val contactName: String = "", val note: String = "", val paymentMethod: String = "CASH",
    val lat: Double? = null, val lng: Double? = null, val zone: List<LatLngDto> = emptyList(), val zoneName: String = "Xıdırlı kəndi",
    val zoneLoaded: Boolean = false, val insideZone: Boolean? = null, val quote: QuoteDto? = null,
    val submitting: Boolean = false, val error: String? = null,
)

sealed interface CheckoutResult {
    data class Placed(val orderId: Long) : CheckoutResult
    data class Pay(val orderId: Long, val url: String) : CheckoutResult
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val api: RafiqApi, private val cart: CartRepository, store: SessionStore,
) : ViewModel() {
    val state = MutableStateFlow(CheckoutUi())
    val result = MutableSharedFlow<CheckoutResult>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            store.current()?.user?.let { u -> state.update { it.copy(contactPhone = u.phone.removePrefix("+994"), contactName = u.name) } }
        }
        loadZone()
        viewModelScope.launch {
            val items = cart.snapshot()
            if (items.isNotEmpty()) apiCall { api.quote(QuoteBody(items.map { OrderItemBody(it.productId, it.quantity) })) }
                .onSuccess { q -> state.update { it.copy(quote = q) } }
        }
    }

    fun loadZone() = viewModelScope.launch {
        apiCall { api.deliveryZone() }
            .onSuccess { z -> state.update { it.copy(zone = z.zone, zoneName = z.zoneName, zoneLoaded = true) } }
            .onFailure { e -> state.update { it.copy(error = e.message) } }
    }

    fun update(f: (CheckoutUi) -> CheckoutUi) = state.update { f(it).copy(error = null) }

    fun setLocation(lat: Double, lng: Double) = state.update {
        it.copy(lat = lat, lng = lng, insideZone = pointInPolygon(lat, lng, it.zone), error = null)
    }

    fun submit() {
        val s = state.value
        val err = when {
            s.street.trim().length < 2 -> "Küçə və ya məhəlləni daxil edin"
            s.house.isBlank() -> "Ev nömrəsini daxil edin"
            s.contactPhone.filter { it.isDigit() }.length != 9 -> "Telefon nömrəsini tam daxil edin"
            s.lat == null || s.lng == null -> "Xəritədə ünvanı seçin"
            s.zone.size < 3 -> "Çatdırılma zonası hələ market tərəfindən təyin edilməyib."
            s.insideZone == false -> ZONE_MESSAGE
            else -> null
        }
        if (err != null) { state.update { it.copy(error = err) }; return }
        state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val items = cart.snapshot()
            if (items.isEmpty()) { state.update { it.copy(submitting = false, error = "Səbət boşdur") }; return@launch }
            apiCall {
                api.createOrder(
                    CreateOrderBody(
                        items = items.map { OrderItemBody(it.productId, it.quantity) },
                        street = s.street.trim(), house = s.house.trim(), addressNote = s.addressNote.trim(),
                        contactPhone = "+994" + s.contactPhone.filter { it.isDigit() }, contactName = s.contactName.trim(),
                        note = s.note.trim(), paymentMethod = s.paymentMethod, lat = s.lat!!, lng = s.lng!!,
                    )
                )
            }.onSuccess { order ->
                cart.clear()
                if (order.status == OrderStatus.PENDING_PAYMENT) {
                    apiCall { api.pay(order.id) }
                        .onSuccess { result.emit(CheckoutResult.Pay(order.id, it.redirectUrl)) }
                        .onFailure { result.emit(CheckoutResult.Placed(order.id)) }
                } else {
                    result.emit(CheckoutResult.Placed(order.id))
                }
                state.update { it.copy(submitting = false) }
            }.onFailure { e -> state.update { it.copy(submitting = false, error = e.message) } }
        }
    }

    companion object {
        const val ZONE_MESSAGE = "Rafiq Market hazırda yalnız Xıdırlı kəndi daxilində çatdırılma həyata keçirir."
    }
}

// ---------------- Sifarişlər ----------------
@HiltViewModel
class OrdersViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : ViewModel() {
    val orders = MutableStateFlow<List<OrderDto>?>(null)
    val error = MutableStateFlow<String?>(null)

    init {
        load()
        viewModelScope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.OrderUpdated>().collect { e ->
                orders.update { list -> list?.map { if (it.id == e.order.id) e.order.copy(itemCount = it.itemCount) else it } }
            }
        }
    }

    fun load() = viewModelScope.launch {
        error.value = null
        apiCall { api.myOrders() }.onSuccess { orders.value = it }.onFailure { error.value = it.message }
    }
}

data class TrackUi(
    val order: OrderDto? = null, val tracking: TrackingDto? = null, val courierLat: Double? = null, val courierLng: Double? = null,
    val locationAt: String? = null, val error: String? = null, val message: String? = null, val busy: Boolean = false,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val api: RafiqApi, private val realtime: RealtimeClient, handle: SavedStateHandle,
) : ViewModel() {
    val orderId: Long = checkNotNull(handle["orderId"])
    val state = MutableStateFlow(TrackUi())
    private var poll: Job? = null

    init {
        realtime.subscribeOrder(orderId)
        load()
        viewModelScope.launch {
            realtime.events.collect { e ->
                when (e) {
                    is RealtimeEvent.OrderUpdated -> if (e.order.id == orderId) {
                        state.update { it.copy(order = e.order) }
                        if (e.order.trackingActive) loadTracking() else stopTracking()
                    }
                    is RealtimeEvent.CourierLocation -> if (e.location.orderId == orderId) {
                        state.update { it.copy(courierLat = e.location.lat, courierLng = e.location.lng, locationAt = e.location.updatedAt) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun load() = viewModelScope.launch {
        apiCall { api.myOrder(orderId) }
            .onSuccess { o -> state.update { it.copy(order = o, error = null) }; if (o.trackingActive) loadTracking() }
            .onFailure { e -> state.update { it.copy(error = e.message) } }
    }

    /** Ödəniş səhifəsindən qayıtdıqda server provayderdən statusu yoxlayır */
    fun onResume() = viewModelScope.launch {
        val o = state.value.order ?: return@launch
        if (o.status == OrderStatus.PENDING_PAYMENT) {
            apiCall { api.refreshPayment(orderId) }.onSuccess { r -> state.update { it.copy(order = r) } }
        } else load()
    }

    private fun loadTracking() {
        viewModelScope.launch {
            apiCall { api.tracking(orderId) }.onSuccess { t ->
                state.update { it.copy(tracking = t, courierLat = t.location?.lat ?: it.courierLat, courierLng = t.location?.lng ?: it.courierLng, locationAt = t.location?.updatedAt ?: it.locationAt) }
            }
        }
        // WebSocket kəsilsə belə mövqe 15 saniyədən bir yenilənir
        if (poll?.isActive != true) poll = viewModelScope.launch {
            while (true) {
                delay(15_000)
                if (state.value.order?.trackingActive != true) break
                apiCall { api.tracking(orderId) }.onSuccess { t ->
                    if (t.location != null) state.update { it.copy(tracking = t, courierLat = t.location!!.lat, courierLng = t.location!!.lng, locationAt = t.location!!.updatedAt) }
                }
            }
        }
    }

    private fun stopTracking() { poll?.cancel() }

    fun cancel() = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        apiCall { api.cancelMyOrder(orderId) }
            .onSuccess { o -> state.update { it.copy(order = o, busy = false, message = "Sifariş ləğv edildi") } }
            .onFailure { e -> state.update { it.copy(busy = false, message = e.message) } }
    }

    fun pay(onUrl: (String) -> Unit) = viewModelScope.launch {
        apiCall { api.pay(orderId) }.onSuccess { onUrl(it.redirectUrl) }.onFailure { e -> state.update { it.copy(message = e.message) } }
    }

    fun clearMessage() = state.update { it.copy(message = null) }

    override fun onCleared() { realtime.unsubscribeOrder(orderId) }
}
