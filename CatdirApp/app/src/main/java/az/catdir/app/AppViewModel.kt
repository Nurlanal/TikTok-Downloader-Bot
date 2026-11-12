package az.catdir.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.catdir.app.data.Backend
import az.catdir.app.data.BackendException
import az.catdir.app.data.DemoBackend
import az.catdir.app.model.Order
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderItem
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import az.catdir.app.model.UserProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Səbət yalnız bir mağazadan ola bilər. */
data class Cart(val store: Store? = null, val lines: List<OrderItem> = emptyList()) {
    val count: Int get() = lines.sumOf { it.qty }
    val subtotalQ: Long get() = OrderRules.subtotal(lines)
    val totalQ: Long get() = subtotalQ + (store?.deliveryFeeQ ?: 0)
    fun qtyOf(productId: String) = lines.firstOrNull { it.productId == productId }?.qty ?: 0
    val isEmpty: Boolean get() = lines.isEmpty()
}

class AppViewModel(val backend: Backend) : ViewModel() {
    val user: StateFlow<UserProfile?> = backend.currentUser
    val loading: StateFlow<Boolean> = backend.isLoading

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    fun say(text: String) { _messages.trySend(text) }

    /** Backend əməliyyatı: xəta olarsa mesaj göstər. */
    fun act(success: String? = null, onDone: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            val ok = try {
                block(); true
            } catch (e: BackendException) {
                say(e.message ?: "Xəta"); false
            } catch (e: Exception) {
                say("Xəta: ${e.message ?: e.javaClass.simpleName}"); false
            }
            if (ok && success != null) say(success)
            onDone(ok)
        }
    }

    /** Siyahı axınları: server xətası proqramı çökdürməsin. */
    fun <T> safe(flow: Flow<List<T>>): Flow<List<T>> = flow.catch { e -> say(e.message ?: "Xəta"); emit(emptyList()) }

    val stores: StateFlow<List<Store>> =
        safe(backend.stores()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun products(storeId: String) = safe(backend.products(storeId))
    fun order(orderId: String): Flow<Order?> = backend.order(orderId).catch { e -> say(e.message ?: "Xəta"); emit(null) }
    fun myOrders() = safe(backend.myOrders())
    fun availableOrders() = safe(backend.availableOrders())
    fun courierOrders() = safe(backend.courierOrders())
    fun allOrders() = safe(backend.allOrders())
    fun users() = safe(backend.users())

    // ---------------- Hesab ----------------

    fun signIn(email: String, password: String, onDone: (Boolean) -> Unit) = act(onDone = onDone) { backend.signIn(email, password) }
    fun register(name: String, phone: String, email: String, password: String, onDone: (Boolean) -> Unit) =
        act("Xoş gəldiniz!", onDone) { backend.register(name, phone, email, password) }
    fun signOut() = act { clearCart(); backend.signOut() }
    fun demoSignIn(role: Role) { (backend as? DemoBackend)?.signInAs(role); clearCart() }
    fun updateProfile(name: String, phone: String, address: String, onDone: (Boolean) -> Unit = {}) =
        act("Profil yadda saxlanıldı", onDone) { backend.updateProfile(name, phone, address) }
    fun setOnline(online: Boolean) = act(if (online) "Siz xəttdəsiniz, sifarişləri görürsünüz" else "Xəttdən çıxdınız") {
        backend.setCourierOnline(online)
    }

    // ---------------- Səbət ----------------

    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    /** @return false, əgər səbətdə başqa mağazanın malı varsa (UI soruşmalıdır). */
    fun addToCart(store: Store, product: Product): Boolean {
        val c = _cart.value
        if (c.store != null && c.store.id != store.id && !c.isEmpty) return false
        _cart.value = c.copy(store = store, lines = addLine(if (c.store?.id == store.id) c.lines else emptyList(), product))
        return true
    }

    fun replaceCartWith(store: Store, product: Product) {
        _cart.value = Cart(store, addLine(emptyList(), product))
    }

    private fun addLine(lines: List<OrderItem>, p: Product): List<OrderItem> =
        if (lines.any { it.productId == p.id }) lines.map { if (it.productId == p.id) it.copy(qty = it.qty + 1) else it }
        else lines + OrderItem(p.id, p.name, p.priceQ, 1)

    fun changeQty(productId: String, delta: Int) = _cart.update { c ->
        val lines = c.lines.map { if (it.productId == productId) it.copy(qty = it.qty + delta) else it }.filter { it.qty > 0 }
        if (lines.isEmpty()) Cart() else c.copy(lines = lines)
    }

    fun clearCart() { _cart.value = Cart() }

    fun placeOrder(address: String, phone: String, note: String, onDone: (String?) -> Unit) {
        val c = _cart.value
        val store = c.store ?: run { say("Səbət boşdur"); onDone(null); return }
        // Yoxlama backend-də, serverdəki ən son qiymət və mağaza məlumatı ilə aparılır
        val draft = OrderDraft(store, c.lines, address, phone, note)
        viewModelScope.launch {
            try {
                val id = backend.placeOrder(draft)
                clearCart()
                say("Sifariş göndərildi!")
                onDone(id)
            } catch (e: BackendException) {
                say(e.message ?: "Xəta"); onDone(null)
            } catch (e: Exception) {
                say("Xəta: ${e.message}"); onDone(null)
            }
        }
    }

    // ---------------- Sifariş əməliyyatları ----------------

    fun takeOrder(id: String, onDone: (Boolean) -> Unit = {}) = act("Sifariş sizindir. Mağazaya gedin", onDone) { backend.takeOrder(id) }
    fun setStatus(id: String, status: OrderStatus, reason: String = "", onDone: (Boolean) -> Unit = {}) =
        act(
            when (status) {
                OrderStatus.ACCEPTED -> "Sifariş qəbul edildi"
                OrderStatus.READY -> "Sifariş hazırdır, kuryerlər görür"
                OrderStatus.PICKED_UP -> "Yaxşı yol! Müştəriyə çatdırın"
                OrderStatus.DELIVERED -> "Çatdırıldı. Təşəkkürlər!"
                OrderStatus.CANCELLED -> "Sifariş ləğv edildi"
                OrderStatus.NEW -> null
            },
            onDone,
        ) { backend.setStatus(id, status, reason) }

    // ---------------- Admin ----------------

    fun saveStore(s: Store, onDone: (Boolean) -> Unit) = act("Mağaza yadda saxlanıldı", onDone) { backend.saveStore(s) }
    fun deleteStore(id: String, onDone: (Boolean) -> Unit = {}) = act("Mağaza silindi", onDone) { backend.deleteStore(id) }
    fun saveProduct(p: Product, onDone: (Boolean) -> Unit) = act("Məhsul yadda saxlanıldı", onDone) { backend.saveProduct(p) }
    fun deleteProduct(p: Product) = act("Məhsul silindi") { backend.deleteProduct(p.storeId, p.id) }
    fun setRole(uid: String, role: Role) = act("Rol dəyişdirildi: ${role.label}") { backend.setRole(uid, role) }

    companion object {
        fun factory(backend: Backend) = viewModelFactory { initializer { AppViewModel(backend) } }
    }
}
