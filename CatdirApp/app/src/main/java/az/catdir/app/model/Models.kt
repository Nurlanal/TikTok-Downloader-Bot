package az.catdir.app.model

enum class Role(val label: String) { CUSTOMER("Müştəri"), COURIER("Kuryer"), ADMIN("Admin") }

data class UserProfile(
    val uid: String,
    val name: String,
    val phone: String,
    val email: String,
    val role: Role,
    val address: String = "",
    val online: Boolean = false,
)

/** Mağaza. Bütün pullar qəpiklə (Long) saxlanılır ki, yuvarlaqlaşdırma xətası olmasın. */
data class Store(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val address: String = "",
    val phone: String = "",
    val deliveryFeeQ: Long = 0,
    val minOrderQ: Long = 0,
    val etaMinutes: Int = 30,
    val isOpen: Boolean = true,
)

data class Product(
    val id: String = "",
    val storeId: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val priceQ: Long = 0,
    val available: Boolean = true,
)

data class OrderItem(val productId: String, val name: String, val priceQ: Long, val qty: Int) {
    val totalQ: Long get() = priceQ * qty
}

enum class OrderStatus(val label: String, val short: String) {
    NEW("Sifariş göndərildi", "Yeni"),
    ACCEPTED("Mağaza qəbul etdi, hazırlanır", "Hazırlanır"),
    READY("Hazırdır, kuryer gözlənilir", "Hazırdır"),
    PICKED_UP("Kuryer yoldadır", "Yolda"),
    DELIVERED("Çatdırıldı", "Çatdırıldı"),
    CANCELLED("Ləğv edildi", "Ləğv edildi");

    val isFinal: Boolean get() = this == DELIVERED || this == CANCELLED
    val isActive: Boolean get() = !isFinal

    companion object {
        /** Müştəriyə göstərilən addımlar (ləğv istisna). */
        val FLOW = listOf(NEW, ACCEPTED, READY, PICKED_UP, DELIVERED)
        fun parse(s: String?): OrderStatus = entries.firstOrNull { it.name == s } ?: NEW
    }
}

data class Order(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val storePhone: String = "",
    val storeAddress: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotalQ: Long = 0,
    val deliveryFeeQ: Long = 0,
    val totalQ: Long = 0,
    val address: String = "",
    val note: String = "",
    val payment: String = PAYMENT_CASH,
    val status: OrderStatus = OrderStatus.NEW,
    val courierId: String? = null,
    val courierName: String = "",
    val courierPhone: String = "",
    val cancelReason: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** status adı → vaxt (millis) */
    val timeline: Map<String, Long> = emptyMap(),
) {
    val itemCount: Int get() = items.sumOf { it.qty }
    val shortId: String get() = id.takeLast(5).uppercase()

    companion object { const val PAYMENT_CASH = "cash" }
}

/** Müştərinin göndərməyə hazırladığı sifariş. */
data class OrderDraft(
    val store: Store,
    val items: List<OrderItem>,
    val address: String,
    val phone: String,
    val note: String,
)

val STORE_CATEGORIES = listOf("Market", "Restoran", "Aptek", "Gül", "Elektronika", "Geyim", "Digər")
