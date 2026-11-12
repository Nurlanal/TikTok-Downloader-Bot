package az.catdir.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import az.catdir.app.data.BackendException
import az.catdir.app.data.FirebaseBackend
import az.catdir.app.model.OrderDraft
import az.catdir.app.model.OrderItem
import az.catdir.app.model.OrderStatus
import az.catdir.app.model.Product
import az.catdir.app.model.Role
import az.catdir.app.model.Store
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Real FirebaseBackend-i Firebase emulyatorlarına (Auth + Firestore, firebase/firestore.rules ilə) qarşı yoxlayır.
 * İşə salmaq: cd firebase && npx firebase emulators:exec --only auth,firestore --project catdir-test \
 *   "cd .. && FIREBASE_EMULATOR=1 ./gradlew testDebugUnitTest --tests '*FirebaseEmulatorTest*'"
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirebaseEmulatorTest {

    /** Firebase cavabları əsas axına (main looper) gəlir: gözləyərkən onu fırladırıq. */
    private fun <T> await(block: suspend () -> T): T {
        val job = CoroutineScope(Dispatchers.IO).async { withTimeout(30_000) { block() } }
        while (!job.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        @Suppress("OPT_IN_USAGE")
        job.getCompletionExceptionOrNull()?.let { throw it }
        @Suppress("OPT_IN_USAGE")
        return job.getCompleted()
    }

    private fun waitUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) fail("Gözləmə bitdi: $what")
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
    }

    /** Emulyatorun admin REST API-si ilə (qaydalardan yan keçərək) rol təyin et — real layihədə bunu Firebase konsolunda edirsiniz. */
    private fun forceRole(uid: String, role: Role) {
        val url = URL("http://127.0.0.1:8080/v1/projects/catdir-test/databases/(default)/documents:commit")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer owner")
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        val body = """{"writes":[{"update":{"name":"projects/catdir-test/databases/(default)/documents/users/$uid",""" +
            """"fields":{"role":{"stringValue":"${role.name}"}}},"updateMask":{"fieldPaths":["role"]}}]}"""
        c.outputStream.use { it.write(body.toByteArray()) }
        assertEquals(200, c.responseCode)
    }

    @Test
    fun fullOrderLifecycleAgainstEmulators() {
        assumeTrue("Emulyator işləmir", System.getenv("FIREBASE_EMULATOR") == "1")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = FirebaseApp.initializeApp(
            ctx,
            FirebaseOptions.Builder().setProjectId("catdir-test").setApplicationId("1:1234567890:android:0123456789abcdef")
                .setApiKey("fake-api-key-for-emulator").build(),
            "emu-" + UUID.randomUUID(),
        )
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        val db = FirebaseFirestore.getInstance(app).apply {
            useEmulator("127.0.0.1", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
        }
        val backend = FirebaseBackend(auth, db)
        val tag = UUID.randomUUID().toString().take(6)
        fun signedInAs(email: String) = waitUntil("profil: $email") { backend.currentUser.value?.email == email }

        // 1) Admin: qeydiyyat (müştəri kimi yaranır) → konsoldan admin edilir
        val adminEmail = "admin$tag@test.az"
        await { backend.register("Admin", "+994120000000", adminEmail, "parol123") }
        signedInAs(adminEmail)
        assertEquals(Role.CUSTOMER, backend.currentUser.value!!.role)
        // müştəri mağaza yarada bilməz (server qaydası)
        try { await { backend.saveStore(Store(name = "X", category = "Market")) }; fail("icazə olmamalı idi") } catch (e: BackendException) {
            assertEquals("Bu əməliyyat üçün icazəniz yoxdur", e.message)
        }
        val adminUid = backend.currentUser.value!!.uid
        forceRole(adminUid, Role.ADMIN)
        waitUntil("admin rolu") { backend.currentUser.value?.role == Role.ADMIN }

        val storeId = await { backend.saveStore(Store(name = "Təzə Market $tag", category = "Market", deliveryFeeQ = 150, minOrderQ = 300, etaMinutes = 25)) }
        val milkId = await { backend.saveProduct(Product(storeId = storeId, name = "Süd 1 l", category = "Süd", priceQ = 210)) }
        val breadId = await { backend.saveProduct(Product(storeId = storeId, name = "Çörək", category = "Çörək", priceQ = 60)) }
        await { backend.signOut() }

        // 2) Kuryer qeydiyyatı
        val courierEmail = "kuryer$tag@test.az"
        await { backend.register("Elvin", "+994550000000", courierEmail, "parol123") }
        signedInAs(courierEmail)
        val courierUid = backend.currentUser.value!!.uid
        await { backend.signOut() }

        // 3) Müştəri: mağazaya baxır, sifariş verir (qiyməti saxtalaşdırmağa çalışır)
        val custEmail = "musteri$tag@test.az"
        await { backend.register("Aysel", "+994501112233", custEmail, "parol123") }
        signedInAs(custEmail)
        val store = await { backend.stores().first { l -> l.any { it.id == storeId } } }.first { it.id == storeId }
        val products = await { backend.products(storeId).first { it.size == 2 } }
        assertEquals(listOf("Süd 1 l", "Çörək").sorted(), products.map { it.name }.sorted())
        val orderId = await {
            backend.placeOrder(
                OrderDraft(
                    store,
                    listOf(OrderItem(milkId, "Süd 1 l", priceQ = 1, qty = 2), OrderItem(breadId, "Çörək", 60, 1)), // süd qiyməti saxta: 1 qəpik
                    "Atatürk pr. 25, mənzil 14", "+994501112233", "Zəngi basın",
                )
            )
        }
        val placed = await { backend.order(orderId).first { it != null } }!!
        assertEquals(2 * 210L + 60, placed.subtotalQ)  // serverdəki qiymət götürülüb
        assertEquals(480L + 150, placed.totalQ)
        assertEquals(OrderStatus.NEW, placed.status)
        assertEquals(1, await { backend.myOrders().first { it.isNotEmpty() } }.size)
        // müştəri öz sifarişini qəbul edə bilməz
        try { await { backend.setStatus(orderId, OrderStatus.ACCEPTED) }; fail() } catch (_: BackendException) {}
        await { backend.signOut() }

        // 4) Admin: kuryer rolu verir, sifarişi qəbul edir və hazır edir
        await { backend.signIn(adminEmail, "parol123") }
        signedInAs(adminEmail)
        await { backend.setRole(courierUid, Role.COURIER) }
        assertTrue(await { backend.allOrders().first { l -> l.any { it.id == orderId } } }.isNotEmpty())
        await { backend.setStatus(orderId, OrderStatus.ACCEPTED) }
        await { backend.setStatus(orderId, OrderStatus.READY) }
        await { backend.signOut() }

        // 5) Kuryer: xəttə çıxır, sifarişi görür, götürür, çatdırır
        await { backend.signIn(courierEmail, "parol123") }
        signedInAs(courierEmail)
        assertEquals(Role.COURIER, backend.currentUser.value!!.role)
        await { backend.setCourierOnline(true) }
        waitUntil("online") { backend.currentUser.value?.online == true }
        val available = await { backend.availableOrders().first { l -> l.any { it.id == orderId } } }
        assertEquals("Aysel", available.first { it.id == orderId }.customerName)
        await { backend.takeOrder(orderId) }
        try { await { backend.takeOrder(orderId) }; fail() } catch (e: BackendException) {
            assertEquals("Bu sifarişi artıq başqa kuryer götürüb", e.message)
        }
        await { backend.setStatus(orderId, OrderStatus.PICKED_UP) }
        await { backend.setStatus(orderId, OrderStatus.DELIVERED) }
        assertEquals(1, await { backend.courierOrders().first { it.isNotEmpty() } }.size)
        await { backend.signOut() }

        // 6) Müştəri nəticəni görür
        await { backend.signIn(custEmail, "parol123") }
        signedInAs(custEmail)
        val done = await { backend.order(orderId).first { it?.status == OrderStatus.DELIVERED } }!!
        assertEquals("Elvin", done.courierName)
        assertTrue(done.timeline.keys.containsAll(OrderStatus.FLOW.map { it.name }))

        // 7) Səhv parol
        await { backend.signOut() }
        try { await { backend.signIn(custEmail, "sehv-parol") }; fail() } catch (e: BackendException) {
            assertEquals("E-poçt və ya parol səhvdir", e.message)
        }
    }
}
