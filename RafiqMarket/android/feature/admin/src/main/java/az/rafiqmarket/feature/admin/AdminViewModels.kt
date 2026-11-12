package az.rafiqmarket.feature.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.*
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.realtime.RealtimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Ümumi "yüklə / xəta / mesaj" vəziyyəti */
data class Load<T>(val data: T? = null, val error: String? = null, val loading: Boolean = false, val message: String? = null)

abstract class BaseAdminVm<T> : ViewModel() {
    val state = MutableStateFlow(Load<T>())
    protected abstract suspend fun fetch(): T
    fun load() = viewModelScope.launch {
        state.update { it.copy(loading = true, error = null) }
        apiCall { fetch() }
            .onSuccess { d -> state.update { it.copy(data = d, loading = false) } }
            .onFailure { e -> state.update { it.copy(error = e.message, loading = false) } }
    }
    protected fun <R> run(success: String?, reload: Boolean = true, block: suspend () -> R, after: (R) -> Unit = {}) = viewModelScope.launch {
        apiCall { block() }
            .onSuccess { r -> after(r); state.update { it.copy(message = success) }; if (reload) load() }
            .onFailure { e -> state.update { it.copy(message = e.message) } }
    }
    fun clearMessage() = state.update { it.copy(message = null) }
}

// ---------------- Statistika ----------------
@HiltViewModel
class DashboardViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : BaseAdminVm<Pair<StatsDto, OrderCountsDto>>() {
    override suspend fun fetch() = api.adminStats() to api.adminOrderCounts()
    init {
        load()
        viewModelScope.launch { realtime.events.filterIsInstance<RealtimeEvent.OrderUpdated>().debounce(1000).collect { load() } }
    }
}

// ---------------- Sifarişlər ----------------
@HiltViewModel
class AdminOrdersViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : ViewModel() {
    val groups = listOf("new" to "Yeni", "accepted" to "Qəbul edilmiş", "preparing" to "Hazırlanan", "delivering" to "Çatdırılır", "delivered" to "Çatdırılmış", "cancelled" to "Ləğv edilmiş")
    val group = MutableStateFlow("new")
    val orders = MutableStateFlow<List<OrderDto>?>(null)
    val counts = MutableStateFlow(OrderCountsDto())
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { group.collectLatest { orders.value = null; load() } }
        viewModelScope.launch { realtime.events.filterIsInstance<RealtimeEvent.OrderUpdated>().debounce(500).collect { load() } }
    }

    fun load() = viewModelScope.launch {
        error.value = null
        apiCall { api.adminOrders(group.value) }.onSuccess { orders.value = it }.onFailure { error.value = it.message }
        apiCall { api.adminOrderCounts() }.onSuccess { counts.value = it }
    }
}

@HiltViewModel
class AdminOrderViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient, handle: SavedStateHandle) : BaseAdminVm<OrderDto>() {
    val id: Long = checkNotNull(handle["orderId"])
    val couriers = MutableStateFlow<List<AdminCourierDto>>(emptyList())
    override suspend fun fetch() = api.adminOrder(id)

    init {
        load()
        viewModelScope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.OrderUpdated>().filter { it.order.id == id }.collect { e -> state.update { it.copy(data = e.order) } }
        }
    }

    fun loadCouriers() = viewModelScope.launch { apiCall { api.adminCouriers() }.onSuccess { couriers.value = it.filter { c -> c.isActive } } }
    fun status(s: String, note: String? = null) = run("Status yeniləndi", block = { api.adminStatus(id, StatusBody(s, note)) })
    fun assign(courierId: Long) = run("Kuryer təyin edildi", block = { api.adminAssign(id, AssignBody(courierId)) })
    fun confirmCash() = run("Nağd ödəniş təsdiqləndi", block = { api.adminConfirmCash(id) })
    fun patch(body: OrderPatchBody) = run("Sifariş məlumatları yeniləndi", block = { api.adminPatchOrder(id, body) })
}

// ---------------- Məhsullar ----------------
@OptIn(FlowPreview::class)
@HiltViewModel
class AdminProductsViewModel @Inject constructor(private val api: RafiqApi) : BaseAdminVm<List<ProductDto>>() {
    val query = MutableStateFlow("")
    val lowStock = MutableStateFlow(false)
    override suspend fun fetch() = api.adminProducts(q = query.value.ifBlank { null }, lowStock = if (lowStock.value) true else null)
    init { viewModelScope.launch { combine(query.debounce(300), lowStock) { a, b -> a to b }.collectLatest { load() } } }
    fun setActive(p: ProductDto, active: Boolean) = run(if (active) "Məhsul aktiv edildi" else "Məhsul passiv edildi", block = { api.setProductActive(p.id, ActiveBody(active)) })
}

data class ProductForm(
    val id: Long? = null, val name: String = "", val description: String = "", val categoryId: Long? = null,
    val price: String = "", val oldPrice: String = "", val unit: String = "ədəd", val stock: String = "0",
    val lowStockThreshold: String = "5", val barcode: String = "", val imageUrl: String? = null,
    val isActive: Boolean = true, val isNew: Boolean = true,
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val api: RafiqApi, @ApplicationContext private val context: Context, handle: SavedStateHandle,
) : ViewModel() {
    private val productId: Long? = handle.get<Long>("productId")?.takeIf { it > 0 }
    val form = MutableStateFlow(ProductForm())
    val categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val loading = MutableStateFlow(productId != null)
    val saving = MutableStateFlow(false)
    val uploading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val isEdit = productId != null

    init {
        viewModelScope.launch { apiCall { api.adminCategories() }.onSuccess { categories.value = it } }
        if (productId != null) viewModelScope.launch {
            apiCall { api.adminProducts() }.onSuccess { list ->
                list.firstOrNull { it.id == productId }?.let { p ->
                    form.value = ProductForm(p.id, p.name, p.description, p.categoryId, p.price, p.oldPrice ?: "", p.unit,
                        p.stock.toString(), p.lowStockThreshold.toString(), p.barcode ?: "", p.imageUrl, p.isActive, p.isNew)
                }
            }.onFailure { message.value = it.message }
            loading.value = false
        }
    }

    fun update(f: (ProductForm) -> ProductForm) = form.update(f)

    fun uploadImage(uri: Uri) = viewModelScope.launch {
        uploading.value = true
        apiCall {
            val bytes = withContext(Dispatchers.IO) { compressImage(context, uri) }
            val part = MultipartBody.Part.createFormData("file", "product.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            api.upload(part)
        }.onSuccess { r -> form.update { it.copy(imageUrl = r.url) }; message.value = "Şəkil yükləndi" }
            .onFailure { message.value = it.message }
        uploading.value = false
    }

    fun save() {
        val f = form.value
        val price = f.price.replace(',', '.').toBigDecimalOrNull()
        val old = f.oldPrice.replace(',', '.').takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
        val stock = f.stock.toIntOrNull()
        val err = when {
            f.name.isBlank() -> "Məhsulun adını daxil edin"
            price == null || price.signum() < 0 -> "Qiyməti düzgün daxil edin"
            f.oldPrice.isNotBlank() && old == null -> "Köhnə qiymət düzgün deyil"
            stock == null || stock < 0 -> "Stoku düzgün daxil edin"
            f.unit.isBlank() -> "Ölçü vahidini daxil edin"
            else -> null
        }
        if (err != null) { message.value = err; return }
        val body = ProductBody(
            name = f.name.trim(), description = f.description.trim(), categoryId = f.categoryId, price = price!!.toPlainString(),
            oldPrice = old?.toPlainString(), unit = f.unit.trim(), stock = stock!!, lowStockThreshold = f.lowStockThreshold.toIntOrNull() ?: 5,
            barcode = f.barcode.trim().ifBlank { null }, imageUrl = f.imageUrl, isActive = f.isActive, isNew = f.isNew,
        )
        saving.value = true
        viewModelScope.launch {
            apiCall { if (f.id == null) api.createProduct(body) else api.updateProduct(f.id, body) }
                .onSuccess { done.emit(Unit) }
                .onFailure { message.value = it.message }
            saving.value = false
        }
    }

    /** Stok düzəlişi delta ilə (paralel sifarişlərlə toqquşmur) */
    fun adjustStock(delta: Int) {
        val id = form.value.id ?: return
        viewModelScope.launch {
            apiCall { api.adjustStock(id, StockDeltaBody(delta)) }
                .onSuccess { p -> form.update { it.copy(stock = p.stock.toString()) }; message.value = "Stok: ${p.stock}" }
                .onFailure { message.value = it.message }
        }
    }

    fun delete() {
        val id = form.value.id ?: return
        viewModelScope.launch { apiCall { api.deleteProduct(id) }.onSuccess { done.emit(Unit) }.onFailure { message.value = it.message } }
    }
}

/** Şəkli 1280px-ə qədər kiçildir və JPEG-ə çevirir (server limiti 5 MB) */
fun compressImage(context: Context, uri: Uri, maxSide: Int = 1280): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Şəkil oxunmadı")
    val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    return ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
}

// ---------------- Kateqoriyalar və bannerlər ----------------
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val api: RafiqApi) : BaseAdminVm<List<CategoryDto>>() {
    override suspend fun fetch() = api.adminCategories()
    init { load() }
    fun save(id: Long?, body: CategoryBody) = run("Yadda saxlanıldı", block = { if (id == null) api.createCategory(body) else api.updateCategory(id, body) })
    fun delete(id: Long) = run("Silindi", block = { api.deleteCategory(id) })
}

@HiltViewModel
class BannersViewModel @Inject constructor(private val api: RafiqApi, @ApplicationContext private val context: Context) : BaseAdminVm<List<BannerDto>>() {
    val categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val uploadedUrl = MutableStateFlow<String?>(null)
    override suspend fun fetch() = api.adminBanners()
    init { load(); viewModelScope.launch { apiCall { api.adminCategories() }.onSuccess { categories.value = it } } }
    fun save(id: Long?, body: BannerBody) = run("Yadda saxlanıldı", block = { if (id == null) api.createBanner(body) else api.updateBanner(id, body) })
    fun delete(id: Long) = run("Silindi", block = { api.deleteBanner(id) })
    fun upload(uri: Uri) = viewModelScope.launch {
        apiCall {
            val bytes = withContext(Dispatchers.IO) { compressImage(context, uri, 1600) }
            api.upload(MultipartBody.Part.createFormData("file", "banner.jpg", bytes.toRequestBody("image/jpeg".toMediaType())))
        }.onSuccess { uploadedUrl.value = it.url }.onFailure { e -> state.update { it.copy(message = e.message) } }
    }
}

// ---------------- Kuryerlər ----------------
@HiltViewModel
class CouriersViewModel @Inject constructor(private val api: RafiqApi, realtime: RealtimeClient) : BaseAdminVm<List<AdminCourierDto>>() {
    override suspend fun fetch() = api.adminCouriers()
    init {
        load()
        viewModelScope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.CourierLocation>().collect { e ->
                state.update { s ->
                    s.copy(data = s.data?.map { c ->
                        if (c.id == e.location.courierId) c.copy(location = LocationDto(e.location.lat, e.location.lng, e.location.heading, updatedAt = e.location.updatedAt)) else c
                    })
                }
            }
        }
    }
    fun add(phone: String, name: String) = run("Kuryer əlavə edildi", block = { api.addCourier(AddCourierBody("+994" + phone.filter { it.isDigit() }, name.trim())) })
    fun setActive(id: Long, active: Boolean) = run(if (active) "Kuryer aktivləşdirildi" else "Kuryer deaktiv edildi", block = { api.setUserActive(id, ActiveBody(active)) })
}

// ---------------- Müştərilər ----------------
@OptIn(FlowPreview::class)
@HiltViewModel
class CustomersViewModel @Inject constructor(private val api: RafiqApi) : BaseAdminVm<List<AdminCustomerDto>>() {
    val query = MutableStateFlow("")
    override suspend fun fetch() = api.adminCustomers(query.value.ifBlank { null })
    init { viewModelScope.launch { query.debounce(300).collectLatest { load() } } }
}

@HiltViewModel
class UserDetailViewModel @Inject constructor(private val api: RafiqApi, handle: SavedStateHandle) : BaseAdminVm<UserDetailDto>() {
    private val id: Long = checkNotNull(handle["userId"])
    override suspend fun fetch() = api.adminUser(id)
    init { load() }
    fun setActive(active: Boolean) = run(if (active) "Hesab aktivləşdirildi" else "Hesab bloklandı", block = { api.setUserActive(id, ActiveBody(active)) })
    fun setRole(role: String) = run("Rol dəyişdirildi", block = { api.setUserRole(id, RoleBody(role)) })
}

// ---------------- Parametrlər və zona ----------------
data class SettingsForm(
    val deliveryFee: String = "", val minOrderAmount: String = "", val freeEnabled: Boolean = false, val freeThreshold: String = "",
    val openTime: String = "09:00", val closeTime: String = "22:00", val ordersEnabled: Boolean = true, val zoneName: String = "Xıdırlı kəndi",
    val zonePoints: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val api: RafiqApi) : ViewModel() {
    val form = MutableStateFlow<SettingsForm?>(null)
    val message = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    init { load() }

    fun load() = viewModelScope.launch {
        error.value = null
        apiCall { api.adminSettings() }.onSuccess { form.value = it.toForm() }.onFailure { error.value = it.message }
    }

    private fun SettingsDto.toForm() = SettingsForm(deliveryFee, minOrderAmount, freeDeliveryThreshold != null, freeDeliveryThreshold ?: "",
        openTime, closeTime, ordersEnabled, zoneName, deliveryZone.size)

    fun update(f: (SettingsForm) -> SettingsForm) = form.update { it?.let(f) }

    fun save() {
        val f = form.value ?: return
        val time = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val fee = f.deliveryFee.replace(',', '.').toBigDecimalOrNull()
        val min = f.minOrderAmount.replace(',', '.').toBigDecimalOrNull()
        val free = f.freeThreshold.replace(',', '.').toBigDecimalOrNull()
        val err = when {
            fee == null || fee.signum() < 0 -> "Çatdırılma haqqı düzgün deyil"
            min == null || min.signum() < 0 -> "Minimum məbləğ düzgün deyil"
            f.freeEnabled && (free == null || free.signum() <= 0) -> "Pulsuz çatdırılma limiti düzgün deyil"
            !time.matches(f.openTime) || !time.matches(f.closeTime) -> "Saatı SS:DD formatında yazın (məs. 09:00)"
            else -> null
        }
        if (err != null) { message.value = err; return }
        saving.value = true
        viewModelScope.launch {
            apiCall {
                api.saveSettings(SettingsBody(fee!!.toPlainString(), min!!.toPlainString(), if (f.freeEnabled) free!!.toPlainString() else null,
                    f.openTime, f.closeTime, f.ordersEnabled, f.zoneName.trim().ifBlank { "Xıdırlı kəndi" }))
            }.onSuccess { form.value = it.toForm(); message.value = "Parametrlər yadda saxlanıldı" }.onFailure { message.value = it.message }
            saving.value = false
        }
    }
}

@HiltViewModel
class ZoneEditorViewModel @Inject constructor(private val api: RafiqApi) : ViewModel() {
    val points = MutableStateFlow<List<LatLngDto>>(emptyList())
    val loaded = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            apiCall { api.adminSettings() }.onSuccess { points.value = it.deliveryZone }.onFailure { message.value = it.message }
            loaded.value = true
        }
    }

    fun add(lat: Double, lng: Double) = points.update { it + LatLngDto(lat, lng) }
    fun move(i: Int, lat: Double, lng: Double) = points.update { l -> l.mapIndexed { idx, p -> if (idx == i) LatLngDto(lat, lng) else p } }
    fun undo() = points.update { it.dropLast(1) }
    fun clear() = points.update { emptyList() }

    fun save() {
        if (points.value.size < 3) { message.value = "Zona üçün ən azı 3 nöqtə qoyun"; return }
        saving.value = true
        viewModelScope.launch {
            apiCall { api.saveZone(ZoneBody(points.value)) }
                .onSuccess { message.value = "Çatdırılma zonası yadda saxlanıldı (${it.deliveryZone.size} nöqtə)" }
                .onFailure { message.value = it.message }
            saving.value = false
        }
    }
}
