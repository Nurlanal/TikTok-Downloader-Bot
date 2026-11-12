package az.rafiqmarket.core.network

import az.rafiqmarket.core.network.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface RafiqApi {
    // ---- Auth ----
    @POST("api/auth/otp/request") suspend fun requestOtp(@Body body: OtpRequestBody): OtpRequestResponse
    @POST("api/auth/otp/verify") suspend fun verifyOtp(@Body body: OtpVerifyBody): AuthResponse
    @POST("api/auth/logout") suspend fun logout(@Body body: LogoutBody): OkDto

    // ---- Profil ----
    @GET("api/me") suspend fun me(): UserDto
    @PATCH("api/me") suspend fun updateName(@Body body: NameBody): UserDto
    @PUT("api/me/fcm-token") suspend fun putFcmToken(@Body body: FcmTokenBody): OkDto
    @GET("api/me/notifications") suspend fun notifications(): List<NotificationDto>
    @POST("api/me/notifications/read") suspend fun markNotificationsRead(): OkDto

    // ---- Kataloq ----
    @GET("api/catalog/home") suspend fun home(): HomeDto
    @GET("api/catalog/products") suspend fun products(
        @Query("q") q: String? = null, @Query("categoryId") categoryId: Long? = null,
        @Query("limit") limit: Int = 100, @Query("offset") offset: Int = 0,
    ): List<ProductDto>

    // ---- Müştəri ----
    @GET("api/customer/delivery-zone") suspend fun deliveryZone(): ZoneDto
    @POST("api/customer/delivery-zone/check") suspend fun checkZone(@Body body: LatLngDto): ZoneCheckDto
    @POST("api/customer/orders/quote") suspend fun quote(@Body body: QuoteBody): QuoteDto
    @POST("api/customer/orders") suspend fun createOrder(@Body body: CreateOrderBody): OrderDto
    @GET("api/customer/orders") suspend fun myOrders(): List<OrderDto>
    @GET("api/customer/orders/{id}") suspend fun myOrder(@Path("id") id: Long): OrderDto
    @POST("api/customer/orders/{id}/cancel") suspend fun cancelMyOrder(@Path("id") id: Long): OrderDto
    @POST("api/customer/orders/{id}/pay") suspend fun pay(@Path("id") id: Long): PayResponse
    @POST("api/customer/orders/{id}/payment/refresh") suspend fun refreshPayment(@Path("id") id: Long): OrderDto
    @GET("api/customer/orders/{id}/tracking") suspend fun tracking(@Path("id") id: Long): TrackingDto

    // ---- Kuryer ----
    @GET("api/courier/orders") suspend fun courierOrders(@Query("tab") tab: String): List<OrderDto>
    @GET("api/courier/orders/{id}") suspend fun courierOrder(@Path("id") id: Long): OrderDto
    @POST("api/courier/orders/{id}/accept") suspend fun courierAccept(@Path("id") id: Long): OrderDto
    @POST("api/courier/orders/{id}/reject") suspend fun courierReject(@Path("id") id: Long): OrderDto
    @POST("api/courier/orders/{id}/status") suspend fun courierStatus(@Path("id") id: Long, @Body body: StatusBody): OrderDto
    @POST("api/courier/orders/{id}/confirm-cash") suspend fun courierConfirmCash(@Path("id") id: Long): OrderDto
    @GET("api/courier/stats") suspend fun courierStats(): CourierStatsDto
    @POST("api/courier/location") suspend fun sendLocation(@Body body: LocationBody): OkDto

    // ---- Admin ----
    @GET("api/admin/stats") suspend fun adminStats(): StatsDto
    @GET("api/admin/orders") suspend fun adminOrders(@Query("group") group: String? = null, @Query("customerId") customerId: Long? = null): List<OrderDto>
    @GET("api/admin/orders/counts") suspend fun adminOrderCounts(): OrderCountsDto
    @GET("api/admin/orders/{id}") suspend fun adminOrder(@Path("id") id: Long): OrderDto
    @POST("api/admin/orders/{id}/status") suspend fun adminStatus(@Path("id") id: Long, @Body body: StatusBody): OrderDto
    @POST("api/admin/orders/{id}/assign") suspend fun adminAssign(@Path("id") id: Long, @Body body: AssignBody): OrderDto
    @POST("api/admin/orders/{id}/confirm-cash") suspend fun adminConfirmCash(@Path("id") id: Long): OrderDto
    @PATCH("api/admin/orders/{id}") suspend fun adminPatchOrder(@Path("id") id: Long, @Body body: OrderPatchBody): OrderDto

    @GET("api/admin/products") suspend fun adminProducts(@Query("q") q: String? = null, @Query("categoryId") categoryId: Long? = null, @Query("lowStock") lowStock: Boolean? = null): List<ProductDto>
    @POST("api/admin/products") suspend fun createProduct(@Body body: ProductBody): ProductDto
    @PUT("api/admin/products/{id}") suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductBody): ProductDto
    @POST("api/admin/products/{id}/stock") suspend fun adjustStock(@Path("id") id: Long, @Body body: StockDeltaBody): ProductDto
    @PATCH("api/admin/products/{id}/active") suspend fun setProductActive(@Path("id") id: Long, @Body body: ActiveBody): ProductDto
    @DELETE("api/admin/products/{id}") suspend fun deleteProduct(@Path("id") id: Long): OkDto
    @Multipart @POST("api/admin/uploads") suspend fun upload(@Part file: MultipartBody.Part): UploadResponse

    @GET("api/admin/categories") suspend fun adminCategories(): List<CategoryDto>
    @POST("api/admin/categories") suspend fun createCategory(@Body body: CategoryBody): CategoryDto
    @PUT("api/admin/categories/{id}") suspend fun updateCategory(@Path("id") id: Long, @Body body: CategoryBody): CategoryDto
    @DELETE("api/admin/categories/{id}") suspend fun deleteCategory(@Path("id") id: Long): OkDto

    @GET("api/admin/banners") suspend fun adminBanners(): List<BannerDto>
    @POST("api/admin/banners") suspend fun createBanner(@Body body: BannerBody): BannerDto
    @PUT("api/admin/banners/{id}") suspend fun updateBanner(@Path("id") id: Long, @Body body: BannerBody): BannerDto
    @DELETE("api/admin/banners/{id}") suspend fun deleteBanner(@Path("id") id: Long): OkDto

    @GET("api/admin/couriers") suspend fun adminCouriers(): List<AdminCourierDto>
    @POST("api/admin/couriers") suspend fun addCourier(@Body body: AddCourierBody): UserDto
    @GET("api/admin/customers") suspend fun adminCustomers(@Query("q") q: String? = null): List<AdminCustomerDto>
    @GET("api/admin/users/{id}") suspend fun adminUser(@Path("id") id: Long): UserDetailDto
    @PATCH("api/admin/users/{id}/active") suspend fun setUserActive(@Path("id") id: Long, @Body body: ActiveBody): UserDto
    @PATCH("api/admin/users/{id}/role") suspend fun setUserRole(@Path("id") id: Long, @Body body: RoleBody): UserDto

    @GET("api/admin/settings") suspend fun adminSettings(): SettingsDto
    @PUT("api/admin/settings") suspend fun saveSettings(@Body body: SettingsBody): SettingsDto
    @PUT("api/admin/settings/zone") suspend fun saveZone(@Body body: ZoneBody): SettingsDto
    @DELETE("api/admin/settings/zone") suspend fun clearZone(): SettingsDto
}
