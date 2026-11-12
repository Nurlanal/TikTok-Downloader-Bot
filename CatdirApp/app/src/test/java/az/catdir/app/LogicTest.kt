package az.catdir.app

import az.catdir.app.data.BackendException
import az.catdir.app.data.DemoBackend
import az.catdir.app.model.Money
import az.catdir.app.model.Order
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderItem
import az.catdir.app.model.OrderRules
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import az.catdir.app.model.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LogicTest {
    @Test fun moneyIsExact() {
        assertEquals("0.00 ₼", Money.format(0))
        assertEquals("9.20 ₼", Money.format(920))
        assertEquals("1 234 567.05 ₼", Money.format(123456705))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1205L, Money.parse("12.05"))
        assertEquals(1200L, Money.parse("12"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertEquals("12.34", Money.sanitize("12,345"))
        assertEquals("12.50", Money.input(1250))
        assertEquals("12", Money.input(1200))
        assertEquals("0.07", Money.input(7))
        // 0.1 + 0.2 problemi yoxdur
        assertEquals(30L, Money.parse("0.1")!! + Money.parse("0.2")!!)
    }

    private val customer = UserProfile("c", "C", "1", "c@x", Role.CUSTOMER)
    private val courier = UserProfile("k", "K", "1", "k@x", Role.COURIER)
    private val courier2 = UserProfile("k2", "K2", "1", "k2@x", Role.COURIER)
    private val admin = UserProfile("a", "A", "1", "a@x", Role.ADMIN)

    @Test fun transitionsMatchServerRules() {
        val o = Order(id = "o", customerId = "c", status = OrderStatus.NEW)
        assertTrue(OrderRules.canTransition(customer, o, OrderStatus.CANCELLED))
        assertFalse(OrderRules.canTransition(customer, o, OrderStatus.ACCEPTED))
        assertTrue(OrderRules.canTransition(admin, o, OrderStatus.ACCEPTED))
        assertFalse(OrderRules.canTake(courier, o))
        val acc = o.copy(status = OrderStatus.ACCEPTED)
        assertFalse(OrderRules.canTransition(customer, acc, OrderStatus.CANCELLED))
        assertTrue(OrderRules.canTake(courier, acc))
        assertFalse(OrderRules.canTake(customer, acc))
        val mine = acc.copy(courierId = "k")
        assertFalse(OrderRules.canTake(courier2, mine))
        assertFalse(OrderRules.canTransition(courier, mine, OrderStatus.PICKED_UP)) // hələ hazır deyil
        val ready = mine.copy(status = OrderStatus.READY)
        assertTrue(OrderRules.canTransition(courier, ready, OrderStatus.PICKED_UP))
        assertFalse(OrderRules.canTransition(courier2, ready, OrderStatus.PICKED_UP))
        val road = ready.copy(status = OrderStatus.PICKED_UP)
        assertFalse(OrderRules.canTransition(admin, road, OrderStatus.CANCELLED))
        assertTrue(OrderRules.canTransition(courier, road, OrderStatus.DELIVERED))
        assertFalse(OrderRules.canTransition(admin, road.copy(status = OrderStatus.DELIVERED), OrderStatus.CANCELLED))
    }

    @Test fun checkoutValidation() {
        val store = Store("s", "S", "Market", deliveryFeeQ = 150, minOrderQ = 500)
        val items = listOf(OrderItem("p", "Süd", 210, 2))
        fun v(i: List<OrderItem> = items, addr: String = "Atatürk pr. 25", phone: String = "+994501112233", s: Store = store) =
            OrderRules.validateCheckout(OrderDraft(s, i, addr, phone, ""))
        assertEquals("Minimum sifariş 5.00 ₼. Daha 0.80 ₼ məhsul əlavə edin", v())
        assertNull(v(items + OrderItem("q", "Çay", 80, 1)))
        assertEquals("Səbət boşdur", v(emptyList()))
        assertEquals("Çatdırılma ünvanını yazın", v(items + OrderItem("q", "x", 100, 1), addr = " "))
        assertEquals("Telefon nömrəsini düzgün yazın", v(items + OrderItem("q", "x", 100, 1), phone = "12"))
        assertEquals("\"S\" hazırda bağlıdır", v(items + OrderItem("q", "x", 100, 1), s = store.copy(isOpen = false)))
        val built = OrderRules.buildOrder(OrderDraft(store, items + OrderItem("q", "Çay", 80, 1), " Ünvan 1 ", "1", ""), customer, 1000)
        assertEquals(500L, built.subtotalQ)
        assertEquals(650L, built.totalQ)
        assertEquals("Ünvan 1", built.address)
    }

    @Test fun demoLifecycleAndRace() = runTest {
        val b = DemoBackend()
        b.signInAs(Role.CUSTOMER)
        val store = b.stores().first().first { it.id == "s-market" }
        val milk = b.products(store.id).first().first { it.name == "Süd 1 l" }
        // saxta qiymət: server (demo) real qiyməti qoyur
        val id = b.placeOrder(OrderDraft(store, listOf(OrderItem(milk.id, "x", 1, 3)), "Atatürk pr. 25", "+994501112233", ""))
        val o = b.order(id).first()!!
        assertEquals(630L, o.subtotalQ)
        assertEquals(780L, o.totalQ)
        try { b.setStatus(id, OrderStatus.ACCEPTED); fail() } catch (_: BackendException) {}

        b.signInAs(Role.ADMIN)
        b.setStatus(id, OrderStatus.ACCEPTED)
        b.setStatus(id, OrderStatus.READY)

        // iki kuryer eyni anda götürməyə çalışır: yalnız biri uğurlu olur
        val couriers = b.users().first().filter { it.role == Role.COURIER }
        assertEquals(2, couriers.size)
        b.signInAs(Role.COURIER)
        val results = (1..5).map { async { runCatching { b.takeOrder(id) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        b.setStatus(id, OrderStatus.PICKED_UP)
        b.setStatus(id, OrderStatus.DELIVERED)
        assertEquals(OrderStatus.DELIVERED, b.order(id).first()!!.status)
        assertEquals(1, b.courierOrders().first().size)
        assertTrue(b.availableOrders().first().isEmpty())
    }

    @Test fun demoRegistrationAndRoles() = runTest {
        val b = DemoBackend()
        try { b.register("", "1", "x@y.az", "123456"); fail() } catch (e: BackendException) { assertEquals("Adınızı yazın", e.message) }
        try { b.register("Ə", "+994501234567", "musteri@demo.az", "123456"); fail() } catch (e: BackendException) { assertEquals("Bu e-poçt ilə hesab artıq var", e.message) }
        b.register("Nigar", "+994501234567", "nigar@x.az", "123456")
        assertEquals(Role.CUSTOMER, b.currentUser.value!!.role)
        try { b.setRole(b.currentUser.value!!.uid, Role.ADMIN); fail() } catch (_: BackendException) {}
        val uid = b.currentUser.value!!.uid
        b.signOut()
        try { b.signIn("nigar@x.az", "yanlis"); fail() } catch (e: BackendException) { assertEquals("E-poçt və ya parol səhvdir", e.message) }
        b.signInAs(Role.ADMIN)
        b.setRole(uid, Role.COURIER)
        b.signIn("nigar@x.az", "123456")
        assertEquals(Role.COURIER, b.currentUser.value!!.role)
    }

    @Test fun cartIsSingleStore() {
        val vm = AppViewModel(DemoBackend())
        val s1 = Store("s1", "A", "Market"); val s2 = Store("s2", "B", "Market")
        val p1 = az.catdir.app.model.Product("p1", "s1", "Süd", priceQ = 210)
        val p2 = az.catdir.app.model.Product("p2", "s2", "Çay", priceQ = 640)
        assertTrue(vm.addToCart(s1, p1)); assertTrue(vm.addToCart(s1, p1))
        assertEquals(2, vm.cart.value.count)
        assertEquals(420L, vm.cart.value.subtotalQ)
        assertFalse(vm.addToCart(s2, p2))
        vm.replaceCartWith(s2, p2)
        assertEquals("s2", vm.cart.value.store!!.id)
        vm.changeQty("p2", -1)
        assertTrue(vm.cart.value.isEmpty)
    }
}
