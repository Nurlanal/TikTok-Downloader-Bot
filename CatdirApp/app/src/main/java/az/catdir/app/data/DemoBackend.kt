package az.catdir.app.data

import az.catdir.app.model.Order
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import az.catdir.app.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Firebase qoşulmayanda işləyən yerli backend. Bütün rollar eyni telefonda sınaqdan keçirilə bilər:
 * müştəri kimi sifariş ver → admin kimi qəbul et → kuryer kimi çatdır.
 * Server qaydaları ilə eyni [OrderRules] istifadə olunur.
 */
class DemoBackend(seed: Boolean = true, private val clock: () -> Long = System::currentTimeMillis) : Backend {
    override val isDemo = true

    private val mutex = Mutex()
    private val users = MutableStateFlow<List<UserProfile>>(emptyList())
    private val storesFlow = MutableStateFlow<List<Store>>(emptyList())
    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())
    private val ordersFlow = MutableStateFlow<List<Order>>(emptyList())
    private val passwords = HashMap<String, String>()

    private val currentUid = MutableStateFlow<String?>(null)
    private val _user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _user.asStateFlow()
    override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

    init {
        if (seed) seedDemo()
    }

    private fun id() = UUID.randomUUID().toString().replace("-", "").take(20)
    private fun refreshUser() { _user.value = users.value.firstOrNull { it.uid == currentUid.value } }
    private fun me(): UserProfile = _user.value ?: throw BackendException("Əvvəlcə daxil olun")
    private fun requireRole(vararg roles: Role): UserProfile {
        val u = me()
        if (u.role !in roles) throw BackendException("Bu əməliyyat üçün icazəniz yoxdur")
        return u
    }

    // ---------------- Hesab ----------------

    /** Demo: parolsuz, rola görə hazır istifadəçi ilə giriş. */
    fun signInAs(role: Role) {
        currentUid.value = users.value.first { it.role == role }.uid
        refreshUser()
    }

    override suspend fun signIn(email: String, password: String) {
        val u = users.value.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
        if (u == null || passwords[u.uid] != password) throw BackendException("E-poçt və ya parol səhvdir")
        currentUid.value = u.uid
        refreshUser()
    }

    override suspend fun register(name: String, phone: String, email: String, password: String) {
        validateRegistration(name, phone, email, password)?.let { throw BackendException(it) }
        if (users.value.any { it.email.equals(email.trim(), ignoreCase = true) })
            throw BackendException("Bu e-poçt ilə hesab artıq var")
        val u = UserProfile(id(), name.trim(), phone.trim(), email.trim(), Role.CUSTOMER)
        users.update { it + u }
        passwords[u.uid] = password
        currentUid.value = u.uid
        refreshUser()
    }

    override suspend fun signOut() { currentUid.value = null; refreshUser() }

    override suspend fun updateProfile(name: String, phone: String, address: String) {
        val u = me()
        if (name.isBlank()) throw BackendException("Adınızı yazın")
        users.update { l -> l.map { if (it.uid == u.uid) it.copy(name = name.trim(), phone = phone.trim(), address = address.trim()) else it } }
        refreshUser()
    }

    override suspend fun setCourierOnline(online: Boolean) {
        val u = requireRole(Role.COURIER)
        users.update { l -> l.map { if (it.uid == u.uid) it.copy(online = online) else it } }
        refreshUser()
    }

    // ---------------- Mağazalar ----------------

    override fun stores(): Flow<List<Store>> = storesFlow.map { l -> l.sortedWith(compareBy({ !it.isOpen }, { it.name.lowercase() })) }

    override fun products(storeId: String): Flow<List<Product>> =
        productsFlow.map { l -> l.filter { it.storeId == storeId }.sortedWith(compareBy({ it.category }, { it.name.lowercase() })) }

    override suspend fun saveStore(store: Store): String {
        requireRole(Role.ADMIN)
        validateStore(store)?.let { throw BackendException(it) }
        val s = if (store.id.isEmpty()) store.copy(id = id()) else store
        storesFlow.update { l -> l.filterNot { it.id == s.id } + s }
        return s.id
    }

    override suspend fun deleteStore(storeId: String) {
        requireRole(Role.ADMIN)
        storesFlow.update { l -> l.filterNot { it.id == storeId } }
        productsFlow.update { l -> l.filterNot { it.storeId == storeId } }
    }

    override suspend fun saveProduct(product: Product): String {
        requireRole(Role.ADMIN)
        validateProduct(product)?.let { throw BackendException(it) }
        val p = if (product.id.isEmpty()) product.copy(id = id()) else product
        productsFlow.update { l -> l.filterNot { it.id == p.id } + p }
        return p.id
    }

    override suspend fun deleteProduct(storeId: String, productId: String) {
        requireRole(Role.ADMIN)
        productsFlow.update { l -> l.filterNot { it.id == productId } }
    }

    // ---------------- Sifarişlər ----------------

    override suspend fun placeOrder(draft: OrderDraft): String = mutex.withLock {
        val u = me()
        if (draft.items.none { it.qty > 0 }) throw BackendException("Səbət boşdur")
        // Qiymətləri serverdəki (burada yaddaşdakı) məhsullardan götür — müştəri qiyməti dəyişə bilməz
        val current = productsFlow.value.filter { it.storeId == draft.store.id }.associateBy { it.id }
        val items = draft.items.map { item ->
            val p = current[item.productId] ?: throw BackendException("\"${item.name}\" artıq satışda deyil")
            if (!p.available) throw BackendException("\"${p.name}\" hazırda yoxdur")
            item.copy(name = p.name, priceQ = p.priceQ)
        }
        val store = storesFlow.value.firstOrNull { it.id == draft.store.id } ?: throw BackendException("Mağaza tapılmadı")
        val fresh = draft.copy(items = items, store = store)
        OrderRules.validateCheckout(fresh)?.let { throw BackendException(it) }
        val order = OrderRules.buildOrder(fresh, u, clock()).copy(id = id())
        ordersFlow.update { it + order }
        order.id
    }

    override fun order(orderId: String): Flow<Order?> = ordersFlow.map { l -> l.firstOrNull { it.id == orderId } }

    private fun Flow<List<Order>>.newestFirst() = map { l -> l.sortedByDescending { it.createdAt } }

    override fun myOrders(): Flow<List<Order>> =
        combine(ordersFlow, currentUid) { l, uid -> l.filter { it.customerId == uid } }.newestFirst()

    override fun availableOrders(): Flow<List<Order>> =
        ordersFlow.map { l -> l.filter { it.courierId == null && it.status in OrderRules.COURIER_CAN_TAKE } }.newestFirst()

    override fun courierOrders(): Flow<List<Order>> =
        combine(ordersFlow, currentUid) { l, uid -> l.filter { it.courierId != null && it.courierId == uid } }.newestFirst()

    override fun allOrders(): Flow<List<Order>> = ordersFlow.newestFirst()

    override suspend fun takeOrder(orderId: String) = mutex.withLock {
        val u = requireRole(Role.COURIER)
        val o = ordersFlow.value.firstOrNull { it.id == orderId } ?: throw BackendException("Sifariş tapılmadı")
        if (!OrderRules.canTake(u, o)) throw BackendException("Bu sifarişi artıq başqa kuryer götürüb")
        replace(o.copy(courierId = u.uid, courierName = u.name, courierPhone = u.phone, updatedAt = clock()))
    }

    override suspend fun setStatus(orderId: String, status: OrderStatus, cancelReason: String) = mutex.withLock {
        val u = me()
        val o = ordersFlow.value.firstOrNull { it.id == orderId } ?: throw BackendException("Sifariş tapılmadı")
        if (!OrderRules.canTransition(u, o, status)) throw BackendException("Sifarişin vəziyyəti dəyişib, bu addım artıq mümkün deyil")
        val now = clock()
        replace(
            o.copy(
                status = status, updatedAt = now, timeline = o.timeline + (status.name to now),
                cancelReason = if (status == OrderStatus.CANCELLED) cancelReason else o.cancelReason,
            )
        )
    }

    private fun replace(o: Order) = ordersFlow.update { l -> l.map { if (it.id == o.id) o else it } }

    // ---------------- İstifadəçilər ----------------

    override fun users(): Flow<List<UserProfile>> = users.map { l -> l.sortedWith(compareBy({ it.role.ordinal }, { it.name })) }

    override suspend fun setRole(uid: String, role: Role) {
        val admin = requireRole(Role.ADMIN)
        if (uid == admin.uid && role != Role.ADMIN) throw BackendException("Öz admin rolunuzu silə bilməzsiniz")
        users.update { l -> l.map { if (it.uid == uid) it.copy(role = role) else it } }
        refreshUser()
    }

    // ---------------- Demo məlumatlar ----------------

    private fun seedDemo() {
        users.value = listOf(
            UserProfile("demo-customer", "Aysel Məmmədova", "+994 50 111 22 33", "musteri@demo.az", Role.CUSTOMER, "Nərimanov r., Atatürk pr. 25, mənzil 14"),
            UserProfile("demo-courier", "Elvin Həsənov", "+994 55 444 55 66", "kuryer@demo.az", Role.COURIER, online = true),
            UserProfile("demo-courier2", "Rauf Quliyev", "+994 70 777 88 99", "kuryer2@demo.az", Role.COURIER),
            UserProfile("demo-admin", "Admin", "+994 12 000 00 00", "admin@demo.az", Role.ADMIN),
        )
        users.value.forEach { passwords[it.uid] = "demo123" }
        val s1 = Store("s-market", "Təzə Market", "Market", "Gündəlik ərzaq, içki və məişət malları", "Nəsimi r., Rəşid Behbudov 12", "+994 12 555 10 10", 150, 500, 25)
        val s2 = Store("s-cafe", "Dadlı Ev Mətbəxi", "Restoran", "Ev yeməkləri, şorbalar, şirniyyat", "Yasamal r., Şərifzadə 40", "+994 12 555 20 20", 200, 800, 40)
        val s3 = Store("s-pharmacy", "Şəfa Aptek", "Aptek", "Dərman olmayan sağlamlıq və gigiyena məhsulları", "Xətai r., Babək pr. 7", "+994 12 555 30 30", 100, 0, 30)
        val s4 = Store("s-flowers", "Gül Evi", "Gül", "Buketlər və dibçək gülləri", "Səbail r., Neftçilər pr. 90", "+994 12 555 40 40", 300, 1500, 60, isOpen = false)
        storesFlow.value = listOf(s1, s2, s3, s4)
        fun p(store: Store, name: String, cat: String, price: Long, desc: String = "") =
            Product(id(), store.id, name, desc, cat, price)
        productsFlow.value = listOf(
            p(s1, "Çörək (ağ)", "Çörək", 60), p(s1, "Süd 1 l", "Süd məhsulları", 210), p(s1, "Qatıq 500 q", "Süd məhsulları", 180),
            p(s1, "Yumurta (10 əd.)", "Ərzaq", 350), p(s1, "Su 1.5 l", "İçkilər", 90), p(s1, "Çay 250 q", "Ərzaq", 640),
            p(s2, "Mərci şorbası", "Şorbalar", 450, "Limon və göyərti ilə"), p(s2, "Dolma (yarpaq)", "Əsas yeməklər", 1050, "Qatıqla, 10 ədəd"),
            p(s2, "Plov (toyuqla)", "Əsas yeməklər", 1200), p(s2, "Paxlava (4 ədəd)", "Şirniyyat", 600),
            p(s3, "Diş məcunu", "Gigiyena", 420), p(s3, "Əl antiseptiki", "Gigiyena", 350), p(s3, "Vitamin C", "Vitaminlər", 890),
            p(s4, "Qızılgül buketi (11)", "Buketlər", 4500), p(s4, "Orxideya dibçəkdə", "Dibçək", 3800),
        )
    }
}

/** Qeydiyyat sahələrinin ümumi yoxlanması (hər iki backend üçün). */
fun validateRegistration(name: String, phone: String, email: String, password: String): String? = when {
    name.isBlank() -> "Adınızı yazın"
    phone.filter { it.isDigit() }.length < 7 -> "Telefon nömrəsini düzgün yazın"
    !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) -> "E-poçt ünvanı düzgün deyil"
    password.length < 6 -> "Parol ən azı 6 simvol olmalıdır"
    else -> null
}

fun validateStore(s: Store): String? = when {
    s.name.isBlank() -> "Mağazanın adını yazın"
    s.category.isBlank() -> "Kateqoriya seçin"
    s.deliveryFeeQ < 0 || s.minOrderQ < 0 -> "Məbləğ mənfi ola bilməz"
    s.etaMinutes !in 5..240 -> "Çatdırılma vaxtı 5–240 dəqiqə olmalıdır"
    else -> null
}

fun validateProduct(p: Product): String? = when {
    p.storeId.isBlank() -> "Mağaza seçilməyib"
    p.name.isBlank() -> "Məhsulun adını yazın"
    p.priceQ <= 0 -> "Qiyməti yazın"
    else -> null
}
