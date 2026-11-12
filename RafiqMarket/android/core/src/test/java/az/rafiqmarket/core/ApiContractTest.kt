package az.rafiqmarket.core

import az.rafiqmarket.core.di.CoreModule
import az.rafiqmarket.core.network.dto.*
import az.rafiqmarket.core.realtime.RealtimeEvent
import az.rafiqmarket.core.realtime.parseRealtimeEvent
import az.rafiqmarket.core.util.OrderStatus
import az.rafiqmarket.core.util.pointInPolygon
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.*
import org.junit.Test

/**
 * Müqavilə testi: fixtures/ qovluğundakı JSON-lar REAL backend-dən götürülüb
 * (backend/test/capture-fixtures.mjs). Android DTO-ları onları xətasız oxumalıdır.
 */
class ApiContractTest {
    private val json = CoreModule.json()
    private fun read(name: String) = javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText()
    private fun <T> decode(name: String, s: KSerializer<T>): T = json.decodeFromString(s, read(name))

    @Test fun auth() {
        val a = decode("auth", AuthResponse.serializer())
        assertEquals(Role.CUSTOMER, a.user.roleEnum)
        assertTrue(a.refreshToken.length > 20)
    }

    @Test fun catalog() {
        val h = decode("home", HomeDto.serializer())
        assertEquals(1, h.categories.size)
        assertEquals("1.80", h.newProducts.first().price)
        assertEquals("2.20", h.discounted.first().oldPrice)
        assertTrue(h.settings.zoneConfigured)
        assertEquals(1, h.banners.size)
        decode("products", ListSerializer(ProductDto.serializer()))
        val q = decode("quote", QuoteDto.serializer())
        assertEquals("9.00", q.subtotal); assertEquals("1.50", q.deliveryFee); assertEquals("10.50", q.total)
    }

    @Test fun zone() {
        val z = decode("zone", ZoneDto.serializer())
        assertEquals(4, z.zone.size)
        assertTrue(pointInPolygon(40.005, 49.005, z.zone))
        assertFalse(pointInPolygon(40.05, 49.05, z.zone))
        assertTrue(decode("zone_check", ZoneCheckDto.serializer()).inside)
        assertEquals(4, decode("settings", SettingsDto.serializer()).deliveryZone.size)
    }

    @Test fun orders() {
        val created = decode("order_created", OrderDto.serializer())
        assertEquals(OrderStatus.PLACED, created.status)
        assertTrue(created.orderNumber.startsWith("RM-"))
        val d = decode("order_detail", OrderDto.serializer())
        assertEquals(OrderStatus.PICKED_UP, d.status)
        assertTrue(d.trackingActive)
        assertEquals("Elvin", d.courier?.name)
        assertTrue(d.history.isNotEmpty())
        assertEquals(1, decode("my_orders", ListSerializer(OrderDto.serializer())).first().itemCount)
        val t = decode("tracking", TrackingDto.serializer())
        assertTrue(t.active)
        assertEquals(40.004, t.location!!.lat, 1e-9)
    }

    @Test fun courierAndAdmin() {
        decode("courier_orders", ListSerializer(OrderDto.serializer()))
        decode("courier_stats", CourierStatsDto.serializer())
        val st = decode("admin_stats", StatsDto.serializer())
        assertEquals(7, st.last7Days.size)
        decode("admin_counts", OrderCountsDto.serializer())
        decode("admin_orders", ListSerializer(OrderDto.serializer()))
        val couriers = decode("admin_couriers", ListSerializer(AdminCourierDto.serializer()))
        assertNotNull(couriers.first().location)
        decode("admin_customers", ListSerializer(AdminCustomerDto.serializer()))
        decode("admin_user", UserDetailDto.serializer())
        decode("admin_categories", ListSerializer(CategoryDto.serializer()))
        decode("admin_products", ListSerializer(ProductDto.serializer()))
        decode("notifications", ListSerializer(NotificationDto.serializer()))
        val err = decode("error", ApiErrorDto.serializer())
        assertEquals("OUT_OF_STOCK", err.code)
    }

    @Test fun websocketEvents() {
        val arr = json.parseToJsonElement(read("ws_events")).jsonArray
        val events = arr.mapNotNull { parseRealtimeEvent(json, it.toString()) }
        assertTrue(events.any { it is RealtimeEvent.OrderUpdated })
        assertTrue(events.any { it is RealtimeEvent.Notification && it.notification.title.isNotBlank() })
        val loc = events.filterIsInstance<RealtimeEvent.CourierLocation>().first()
        assertEquals(49.004, loc.location.lng, 1e-9)
        assertNotNull(loc.location.orderId)
    }
}
