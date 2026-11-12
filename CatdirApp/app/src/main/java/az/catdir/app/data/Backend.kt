package az.catdir.app.data

import az.catdir.app.model.Order
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import az.catdir.app.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** İstifadəçiyə göstəriləcək (Azərbaycan dilində) xəta. */
class BackendException(message: String) : Exception(message)

/**
 * Proqramın bütün məlumat əməliyyatları. İki həyata keçirmə var:
 *  - [FirebaseBackend]: real server (Firebase Auth + Firestore), bir neçə telefon arasında real vaxt;
 *  - [DemoBackend]: Firebase qoşulmayanda, hər şey bu telefonun yaddaşında.
 */
interface Backend {
    val isDemo: Boolean

    /** null = daxil olmayıb */
    val currentUser: StateFlow<UserProfile?>
    /** Başlanğıcda giriş vəziyyəti yoxlanılır */
    val isLoading: StateFlow<Boolean>

    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, phone: String, email: String, password: String)
    suspend fun signOut()
    suspend fun updateProfile(name: String, phone: String, address: String)
    suspend fun setCourierOnline(online: Boolean)

    fun stores(): Flow<List<Store>>
    fun products(storeId: String): Flow<List<Product>>
    suspend fun saveStore(store: Store): String
    suspend fun deleteStore(storeId: String)
    suspend fun saveProduct(product: Product): String
    suspend fun deleteProduct(storeId: String, productId: String)

    suspend fun placeOrder(draft: OrderDraft): String
    fun order(orderId: String): Flow<Order?>
    fun myOrders(): Flow<List<Order>>
    fun availableOrders(): Flow<List<Order>>
    fun courierOrders(): Flow<List<Order>>
    fun allOrders(): Flow<List<Order>>

    /** Kuryer sifarişi özünə götürür (eyni anda iki kuryer götürə bilməz). */
    suspend fun takeOrder(orderId: String)
    suspend fun setStatus(orderId: String, status: OrderStatus, cancelReason: String = "")

    fun users(): Flow<List<UserProfile>>
    suspend fun setRole(uid: String, role: Role)
}
