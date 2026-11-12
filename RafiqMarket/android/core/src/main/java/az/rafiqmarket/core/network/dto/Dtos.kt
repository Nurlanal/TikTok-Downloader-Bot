package az.rafiqmarket.core.network.dto

import kotlinx.serialization.Serializable

// ---- Rol ----
enum class Role { CUSTOMER, COURIER, ADMIN }

// ---- Auth ----
@Serializable data class UserDto(
    val id: Long, val phone: String, val name: String = "", val role: String, val isActive: Boolean = true,
    val createdAt: String? = null,
) { val roleEnum: Role get() = runCatching { Role.valueOf(role) }.getOrDefault(Role.CUSTOMER) }

@Serializable data class OtpRequestBody(val phone: String)
@Serializable data class OtpRequestResponse(val phone: String, val isNewUser: Boolean, val expiresIn: Int)
@Serializable data class OtpVerifyBody(val phone: String, val code: String, val name: String? = null)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val expiresIn: Int, val user: UserDto)
@Serializable data class RefreshBody(val refreshToken: String)
@Serializable data class LogoutBody(val refreshToken: String?)
@Serializable data class NameBody(val name: String)
@Serializable data class FcmTokenBody(val token: String)
@Serializable data class OkDto(val ok: Boolean = true)
@Serializable data class ApiErrorDto(val code: String? = null, val message: String? = null)

// ---- Kataloq ----
@Serializable data class ProductDto(
    val id: Long, val categoryId: Long? = null, val name: String, val description: String = "",
    val price: String, val oldPrice: String? = null, val unit: String = "ədəd", val stock: Int = 0,
    val inStock: Boolean = stock > 0, val barcode: String? = null, val imageUrl: String? = null,
    val isActive: Boolean = true, val isNew: Boolean = false, val soldCount: Int = 0, val lowStockThreshold: Int = 5,
)

@Serializable data class CategoryDto(
    val id: Long, val name: String, val icon: String = "🛒", val color: String = "#2E7D32",
    val sortOrder: Int = 0, val isActive: Boolean = true, val productCount: Int? = null,
)

@Serializable data class BannerDto(
    val id: Long, val title: String, val subtitle: String = "", val imageUrl: String? = null,
    val color: String = "#1B5E20", val categoryId: Long? = null, val sortOrder: Int = 0, val isActive: Boolean = true,
)

@Serializable data class PublicSettingsDto(
    val deliveryFee: String, val minOrderAmount: String, val freeDeliveryThreshold: String? = null,
    val openTime: String, val closeTime: String, val ordersEnabled: Boolean, val zoneName: String = "Xıdırlı kəndi",
    val isOpenNow: Boolean = true, val zoneConfigured: Boolean = false,
)

@Serializable data class HomeDto(
    val categories: List<CategoryDto>, val banners: List<BannerDto>, val discounted: List<ProductDto>,
    val newProducts: List<ProductDto>, val bestSellers: List<ProductDto>, val settings: PublicSettingsDto,
)

// ---- Sifariş ----
@Serializable data class LatLngDto(val lat: Double, val lng: Double)
@Serializable data class OrderItemBody(val productId: Long, val quantity: Int)
@Serializable data class QuoteBody(val items: List<OrderItemBody>)
@Serializable data class QuoteLineDto(
    val productId: Long, val name: String, val unitPrice: String, val requestedQuantity: Int,
    val availableQuantity: Int, val ok: Boolean,
)
@Serializable data class QuoteDto(
    val lines: List<QuoteLineDto>, val subtotal: String, val deliveryFee: String, val total: String,
    val minOrderAmount: String, val freeDeliveryThreshold: String? = null, val meetsMinimum: Boolean,
)
@Serializable data class CreateOrderBody(
    val items: List<OrderItemBody>, val street: String, val house: String, val addressNote: String,
    val contactPhone: String, val contactName: String, val note: String, val paymentMethod: String,
    val lat: Double, val lng: Double,
)
@Serializable data class AddressDto(val street: String, val house: String, val note: String = "", val lat: Double, val lng: Double)
@Serializable data class PersonDto(val id: Long? = null, val name: String = "", val phone: String = "")
@Serializable data class OrderItemDto(
    val productId: Long? = null, val name: String, val unit: String, val unitPrice: String, val quantity: Int, val lineTotal: String,
)
@Serializable data class HistoryDto(val status: String, val label: String = "", val note: String? = null, val at: String)

@Serializable data class OrderDto(
    val id: Long, val orderNumber: String, val status: String, val statusLabel: String = "",
    val paymentMethod: String, val paymentStatus: String, val subtotal: String, val deliveryFee: String, val total: String,
    val address: AddressDto, val contactPhone: String, val contactName: String = "", val customerNote: String = "",
    val cancelReason: String? = null, val customer: PersonDto? = null, val courier: PersonDto? = null,
    val courierAcceptedAt: String? = null, val cashConfirmedAt: String? = null, val deliveredAt: String? = null,
    val createdAt: String, val updatedAt: String? = null, val trackingActive: Boolean = false,
    val items: List<OrderItemDto> = emptyList(), val history: List<HistoryDto> = emptyList(), val itemCount: Int? = null,
)

@Serializable data class ZoneCheckDto(val inside: Boolean, val configured: Boolean, val message: String? = null)
@Serializable data class ZoneDto(val zone: List<LatLngDto>, val zoneName: String = "")
@Serializable data class PayResponse(val redirectUrl: String)
@Serializable data class LocationDto(
    val lat: Double, val lng: Double, val heading: Double? = null, val speed: Double? = null,
    val accuracy: Double? = null, val updatedAt: String,
)
@Serializable data class TrackingDto(
    val orderId: Long, val status: String, val active: Boolean, val courier: PersonDto? = null,
    val location: LocationDto? = null, val destination: LatLngDto,
)
@Serializable data class NotificationDto(
    val id: Long, val title: String, val body: String, val orderId: Long? = null, val isRead: Boolean = false, val createdAt: String,
)

// ---- Kuryer ----
@Serializable data class CourierStatsDto(val deliveredToday: Int, val deliveredTotal: Int, val cashCollectedToday: String)
@Serializable data class LocationBody(val lat: Double, val lng: Double, val accuracy: Double? = null, val heading: Double? = null, val speed: Double? = null)
@Serializable data class StatusBody(val status: String, val note: String? = null)

// ---- Admin ----
@Serializable data class AssignBody(val courierId: Long)
@Serializable data class AdminCourierDto(
    val id: Long, val phone: String, val name: String = "", val role: String, val isActive: Boolean,
    val deliveredToday: Int = 0, val deliveredTotal: Int = 0, val activeOrders: Int = 0, val location: LocationDto? = null,
)
@Serializable data class AdminCustomerDto(
    val id: Long, val phone: String, val name: String = "", val role: String, val isActive: Boolean,
    val orderCount: Int = 0, val totalSpent: String = "0.00", val lastOrderAt: String? = null, val createdAt: String? = null,
)
@Serializable data class UserDetailDto(val user: UserDto, val orders: List<OrderDto>)
@Serializable data class PeriodStatsDto(val revenue: String, val completed: Int, val cancelled: Int, val orders: Int = 0)
@Serializable data class PaymentStatsDto(
    val cash: String, val online: String, val cashCount: Int, val onlineCount: Int, val failedPayments: Int,
)
@Serializable data class TopProductDto(val name: String, val quantity: Int, val revenue: String)
@Serializable data class DayRevenueDto(val day: String, val revenue: String)
@Serializable data class StatsDto(
    val today: PeriodStatsDto, val week: PeriodStatsDto, val month: PeriodStatsDto, val allTime: PeriodStatsDto,
    val payments: PaymentStatsDto, val topProducts: List<TopProductDto>, val last7Days: List<DayRevenueDto>,
)
@Serializable data class ProductBody(
    val name: String, val description: String, val categoryId: Long?, val price: String, val oldPrice: String?,
    val unit: String, val stock: Int, val lowStockThreshold: Int, val barcode: String?, val imageUrl: String?,
    val isActive: Boolean, val isNew: Boolean,
)
@Serializable data class CategoryBody(val name: String, val icon: String, val color: String, val sortOrder: Int, val isActive: Boolean)
@Serializable data class BannerBody(
    val title: String, val subtitle: String, val imageUrl: String?, val color: String, val categoryId: Long?,
    val sortOrder: Int, val isActive: Boolean,
)
@Serializable data class SettingsDto(
    val deliveryFee: String, val minOrderAmount: String, val freeDeliveryThreshold: String? = null,
    val openTime: String, val closeTime: String, val ordersEnabled: Boolean,
    val deliveryZone: List<LatLngDto> = emptyList(), val zoneName: String = "Xıdırlı kəndi", val isOpenNow: Boolean = true,
)
@Serializable data class SettingsBody(
    val deliveryFee: String, val minOrderAmount: String, val freeDeliveryThreshold: String?,
    val openTime: String, val closeTime: String, val ordersEnabled: Boolean, val zoneName: String,
)
@Serializable data class ZoneBody(val polygon: List<LatLngDto>)
@Serializable data class AddCourierBody(val phone: String, val name: String)
@Serializable data class ActiveBody(val isActive: Boolean)
@Serializable data class RoleBody(val role: String)
@Serializable data class StockDeltaBody(val delta: Int)
@Serializable data class UploadResponse(val url: String)
@Serializable data class OrderPatchBody(
    val street: String? = null, val house: String? = null, val addressNote: String? = null,
    val contactPhone: String? = null, val customerNote: String? = null,
)
@Serializable data class OrderCountsDto(
    val new: Int = 0, val accepted: Int = 0, val preparing: Int = 0, val delivering: Int = 0,
    val delivered: Int = 0, val cancelled: Int = 0,
)
