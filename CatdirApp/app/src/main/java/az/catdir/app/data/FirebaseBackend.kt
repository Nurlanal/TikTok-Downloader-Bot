package az.catdir.app.data

import az.catdir.app.model.Order
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderItem
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import az.catdir.app.model.UserProfile
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore strukturu:
 *  users/{uid}                      ad, telefon, e-poçt, rol (CUSTOMER/COURIER/ADMIN), ünvan, online
 *  stores/{storeId}                 mağaza
 *  stores/{storeId}/products/{pid}  məhsul
 *  orders/{orderId}                 sifariş (məhsullar daxilində siyahı kimi)
 * İcazələr firebase/firestore.rules faylında serverdə yoxlanılır.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseBackend(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : Backend {
    override val isDemo = false

    private val _user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _user.asStateFlow()
    private val _loading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _loading.asStateFlow()
    private val uidFlow = MutableStateFlow<String?>(null)
    private var profileReg: ListenerRegistration? = null

    private val usersCol get() = db.collection("users")
    private val storesCol get() = db.collection("stores")
    private val ordersCol get() = db.collection("orders")

    init {
        auth.addAuthStateListener { a ->
            val uid = a.currentUser?.uid
            if (uid == uidFlow.value && uid != null) return@addAuthStateListener
            uidFlow.value = uid
            profileReg?.remove()
            profileReg = null
            if (uid == null) {
                _user.value = null
                _loading.value = false
            } else {
                _loading.value = true
                profileReg = usersCol.document(uid).addSnapshotListener { snap, _ ->
                    val p = snap?.takeIf { it.exists() }?.toUser()
                    if (p != null) {
                        _user.value = p
                        _loading.value = false
                    }
                }
            }
        }
    }

    private fun me(): UserProfile = _user.value ?: throw BackendException("Əvvəlcə daxil olun")

    // ---------------- Hesab ----------------

    override suspend fun signIn(email: String, password: String) = wrap {
        if (email.isBlank() || password.isEmpty()) throw BackendException("E-poçt və parolu yazın")
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    override suspend fun register(name: String, phone: String, email: String, password: String) = wrap {
        validateRegistration(name, phone, email, password)?.let { throw BackendException(it) }
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = res.user?.uid ?: throw BackendException("Qeydiyyat alınmadı")
        usersCol.document(uid).set(
            mapOf(
                "name" to name.trim(), "phone" to phone.trim(), "email" to email.trim(),
                "role" to Role.CUSTOMER.name, "address" to "", "online" to false,
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
        Unit
    }

    override suspend fun signOut() { auth.signOut() }

    override suspend fun updateProfile(name: String, phone: String, address: String) = wrap {
        val u = me()
        if (name.isBlank()) throw BackendException("Adınızı yazın")
        usersCol.document(u.uid).update(mapOf("name" to name.trim(), "phone" to phone.trim(), "address" to address.trim())).await()
        Unit
    }

    override suspend fun setCourierOnline(online: Boolean) = wrap {
        usersCol.document(me().uid).update("online", online).await()
        Unit
    }

    // ---------------- Mağazalar ----------------

    override fun stores(): Flow<List<Store>> = signedIn {
        storesCol.listen { it.toStore() }.map { l -> l.sortedWith(compareBy({ !it.isOpen }, { it.name.lowercase() })) }
    }

    override fun products(storeId: String): Flow<List<Product>> = signedIn {
        storesCol.document(storeId).collection("products").listen { it.toProduct(storeId) }
            .map { l -> l.sortedWith(compareBy({ it.category }, { it.name.lowercase() })) }
    }

    override suspend fun saveStore(store: Store): String = wrap {
        validateStore(store)?.let { throw BackendException(it) }
        val ref = if (store.id.isEmpty()) storesCol.document() else storesCol.document(store.id)
        ref.set(
            mapOf(
                "name" to store.name.trim(), "category" to store.category, "description" to store.description.trim(),
                "address" to store.address.trim(), "phone" to store.phone.trim(), "deliveryFeeQ" to store.deliveryFeeQ,
                "minOrderQ" to store.minOrderQ, "etaMinutes" to store.etaMinutes, "isOpen" to store.isOpen,
            )
        ).await()
        ref.id
    }

    override suspend fun deleteStore(storeId: String) = wrap {
        val products = storesCol.document(storeId).collection("products").get().await()
        val batch = db.batch()
        products.documents.forEach { batch.delete(it.reference) }
        batch.delete(storesCol.document(storeId))
        batch.commit().await()
        Unit
    }

    override suspend fun saveProduct(product: Product): String = wrap {
        validateProduct(product)?.let { throw BackendException(it) }
        val col = storesCol.document(product.storeId).collection("products")
        val ref = if (product.id.isEmpty()) col.document() else col.document(product.id)
        ref.set(
            mapOf(
                "name" to product.name.trim(), "description" to product.description.trim(),
                "category" to product.category.trim(), "priceQ" to product.priceQ, "available" to product.available,
            )
        ).await()
        ref.id
    }

    override suspend fun deleteProduct(storeId: String, productId: String) = wrap {
        storesCol.document(storeId).collection("products").document(productId).delete().await()
        Unit
    }

    // ---------------- Sifarişlər ----------------

    override suspend fun placeOrder(draft: OrderDraft): String = wrap {
        val u = me()
        if (draft.items.none { it.qty > 0 }) throw BackendException("Səbət boşdur")
        // Yoxlama səbətdəki köhnə qiymətlərlə yox, serverdəki ən son qiymətlərlə aparılır.
        // Ən son qiymət və mövcudluğu serverdən götür
        val storeSnap = storesCol.document(draft.store.id).get().await()
        val store = storeSnap.takeIf { it.exists() }?.toStore() ?: throw BackendException("Mağaza tapılmadı")
        val products = storesCol.document(store.id).collection("products").get().await()
            .documents.mapNotNull { it.toProduct(store.id) }.associateBy { it.id }
        val items = draft.items.filter { it.qty > 0 }.map { item ->
            val p = products[item.productId] ?: throw BackendException("\"${item.name}\" artıq satışda deyil")
            if (!p.available) throw BackendException("\"${p.name}\" hazırda yoxdur")
            item.copy(name = p.name, priceQ = p.priceQ)
        }
        val fresh = draft.copy(store = store, items = items)
        OrderRules.validateCheckout(fresh)?.let { throw BackendException(it) }
        val order = OrderRules.buildOrder(fresh, u, System.currentTimeMillis())
        val ref = ordersCol.document()
        ref.set(order.toMap()).await()
        ref.id
    }

    override fun order(orderId: String): Flow<Order?> = signedIn {
        callbackFlow {
            val reg = ordersCol.document(orderId).addSnapshotListener { s, e ->
                if (e != null) close(e) else trySend(s?.takeIf { it.exists() }?.toOrder())
            }
            awaitClose { reg.remove() }
        }
    }

    override fun myOrders(): Flow<List<Order>> = signedIn { uid ->
        ordersCol.whereEqualTo("customerId", uid).listen { it.toOrder() }.map { l -> l.sortedByDescending { it.createdAt } }
    }

    override fun availableOrders(): Flow<List<Order>> = signedIn {
        ordersCol.whereEqualTo("courierId", null)
            .whereIn("status", OrderRules.COURIER_CAN_TAKE.map { it.name })
            .listen { it.toOrder() }.map { l -> l.sortedByDescending { it.createdAt } }
    }

    override fun courierOrders(): Flow<List<Order>> = signedIn { uid ->
        ordersCol.whereEqualTo("courierId", uid).listen { it.toOrder() }.map { l -> l.sortedByDescending { it.createdAt } }
    }

    override fun allOrders(): Flow<List<Order>> = signedIn {
        ordersCol.orderBy("createdAt", Query.Direction.DESCENDING).limit(300).listen { it.toOrder() }
    }

    override suspend fun takeOrder(orderId: String) = wrap {
        val u = me()
        val ref = ordersCol.document(orderId)
        db.runTransaction { tx ->
            val o = tx.get(ref).toOrder() ?: throw BackendException("Sifariş tapılmadı")
            if (!OrderRules.canTake(u, o)) throw BackendException("Bu sifarişi artıq başqa kuryer götürüb")
            tx.update(
                ref, mapOf(
                    "courierId" to u.uid, "courierName" to u.name, "courierPhone" to u.phone,
                    "updatedAt" to System.currentTimeMillis(),
                )
            )
        }.await()
        Unit
    }

    override suspend fun setStatus(orderId: String, status: OrderStatus, cancelReason: String) = wrap {
        val u = me()
        val ref = ordersCol.document(orderId)
        db.runTransaction { tx ->
            val o = tx.get(ref).toOrder() ?: throw BackendException("Sifariş tapılmadı")
            if (!OrderRules.canTransition(u, o, status))
                throw BackendException("Sifarişin vəziyyəti dəyişib, bu addım artıq mümkün deyil")
            val now = System.currentTimeMillis()
            val update = mutableMapOf<String, Any>(
                "status" to status.name, "updatedAt" to now, "timeline.${status.name}" to now,
            )
            if (status == OrderStatus.CANCELLED) update["cancelReason"] = cancelReason
            tx.update(ref, update)
        }.await()
        Unit
    }

    // ---------------- İstifadəçilər ----------------

    override fun users(): Flow<List<UserProfile>> = signedIn {
        usersCol.listen { it.toUser() }.map { l -> l.sortedWith(compareBy({ it.role.ordinal }, { it.name })) }
    }

    override suspend fun setRole(uid: String, role: Role) = wrap {
        if (uid == me().uid && role != Role.ADMIN) throw BackendException("Öz admin rolunuzu silə bilməzsiniz")
        usersCol.document(uid).set(mapOf("role" to role.name), SetOptions.merge()).await()
        Unit
    }

    // ---------------- Köməkçilər ----------------

    /** Giriş olanda sorğunu başlat, çıxanda dayandır (icazəsiz sorğu xətası olmasın). */
    private fun <T> signedIn(block: (uid: String) -> Flow<T>): Flow<T> =
        uidFlow.flatMapLatest { uid -> if (uid == null) emptyFlow() else block(uid) }

    private fun <T> Query.listen(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val reg = addSnapshotListener { s, e ->
            if (e != null) close(BackendException(firestoreMessage(e)))
            else trySend(s?.documents?.mapNotNull(map).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (e: BackendException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        // tranzaksiya daxilində atılan BackendException-ı aç
        (e.cause as? BackendException)?.let { throw it }
        throw BackendException(firestoreMessage(e))
    } catch (e: FirebaseAuthUserCollisionException) {
        throw BackendException("Bu e-poçt ilə hesab artıq var")
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw BackendException("Parol zəifdir (ən azı 6 simvol)")
    } catch (e: FirebaseAuthInvalidUserException) {
        throw BackendException("Bu e-poçt ilə hesab tapılmadı")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw BackendException("E-poçt və ya parol səhvdir")
    } catch (e: FirebaseNetworkException) {
        throw BackendException("İnternet bağlantısı yoxdur")
    } catch (e: Exception) {
        (e.cause as? BackendException)?.let { throw it }
        throw BackendException("Xəta: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun firestoreMessage(e: FirebaseFirestoreException): String = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Bu əməliyyat üçün icazəniz yoxdur"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Serverə qoşulmaq olmur. İnterneti yoxlayın"
        FirebaseFirestoreException.Code.NOT_FOUND -> "Məlumat tapılmadı"
        else -> "Server xətası: ${e.code}"
    }

    // ---------------- Sənəd ↔ model ----------------

    private fun DocumentSnapshot.long(k: String) = getLong(k) ?: 0L
    private fun DocumentSnapshot.str(k: String) = getString(k) ?: ""

    private fun DocumentSnapshot.toUser() = UserProfile(
        uid = id, name = str("name"), phone = str("phone"), email = str("email"),
        role = Role.entries.firstOrNull { it.name == getString("role") } ?: Role.CUSTOMER,
        address = str("address"), online = getBoolean("online") ?: false,
    )

    private fun DocumentSnapshot.toStore() = Store(
        id = id, name = str("name"), category = str("category"), description = str("description"),
        address = str("address"), phone = str("phone"), deliveryFeeQ = long("deliveryFeeQ"), minOrderQ = long("minOrderQ"),
        etaMinutes = long("etaMinutes").toInt(), isOpen = getBoolean("isOpen") ?: true,
    )

    private fun DocumentSnapshot.toProduct(storeId: String) = Product(
        id = id, storeId = storeId, name = str("name"), description = str("description"), category = str("category"),
        priceQ = long("priceQ"), available = getBoolean("available") ?: true,
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toOrder(): Order? {
        if (!exists()) return null
        val items = (get("items") as? List<Map<String, Any?>>).orEmpty().map {
            OrderItem(
                productId = it["productId"] as? String ?: "", name = it["name"] as? String ?: "",
                priceQ = (it["priceQ"] as? Number)?.toLong() ?: 0, qty = (it["qty"] as? Number)?.toInt() ?: 0,
            )
        }
        val timeline = (get("timeline") as? Map<String, Any?>).orEmpty()
            .mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }.toMap()
        return Order(
            id = id, customerId = str("customerId"), customerName = str("customerName"), customerPhone = str("customerPhone"),
            storeId = str("storeId"), storeName = str("storeName"), storePhone = str("storePhone"), storeAddress = str("storeAddress"),
            items = items, subtotalQ = long("subtotalQ"), deliveryFeeQ = long("deliveryFeeQ"), totalQ = long("totalQ"),
            address = str("address"), note = str("note"), payment = str("payment"),
            status = OrderStatus.parse(getString("status")), courierId = getString("courierId"),
            courierName = str("courierName"), courierPhone = str("courierPhone"), cancelReason = str("cancelReason"),
            createdAt = long("createdAt"), updatedAt = long("updatedAt"), timeline = timeline,
        )
    }

    private fun Order.toMap(): Map<String, Any?> = mapOf(
        "customerId" to customerId, "customerName" to customerName, "customerPhone" to customerPhone,
        "storeId" to storeId, "storeName" to storeName, "storePhone" to storePhone, "storeAddress" to storeAddress,
        "items" to items.map { mapOf("productId" to it.productId, "name" to it.name, "priceQ" to it.priceQ, "qty" to it.qty) },
        "subtotalQ" to subtotalQ, "deliveryFeeQ" to deliveryFeeQ, "totalQ" to totalQ,
        "address" to address, "note" to note, "payment" to payment, "status" to status.name,
        "courierId" to null, "courierName" to "", "courierPhone" to "", "cancelReason" to "",
        "createdAt" to createdAt, "updatedAt" to updatedAt, "timeline" to timeline,
    )

}
