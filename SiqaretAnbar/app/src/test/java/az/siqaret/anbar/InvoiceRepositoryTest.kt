package az.siqaret.anbar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import az.siqaret.anbar.data.AppDatabase
import az.siqaret.anbar.data.InventoryRepository
import az.siqaret.anbar.data.InventoryRepository.InvoiceLine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InvoiceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InventoryRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = InventoryRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun invoiceCreatesAndUpdatesProducts() = runBlocking {
        val firm = repo.addFirm("Azərsun", "", "")
        val other = repo.addFirm("Başqa firma", "", "")
        // başqa firmada eyni adlı siqaret: ona toxunulmamalıdır
        repo.addProduct("Marlboro Red", 4.0, 40.0, 3, other)

        val inv1 = repo.saveInvoice(firm, "A-1", 1000L, "", listOf(
            InvoiceLine("Marlboro Red", 4.197, 41.97, 10),
            InvoiceLine("Kent", 3.2, 32.0, 5),
        ))
        var products = repo.allProducts().filter { it.firmId == firm }
        assertEquals(2, products.size)
        assertEquals(10, products.first { it.name == "Marlboro Red" }.blockCount)

        // ikinci qaimə: mövcud siqaret (hərf fərqi ilə yazılıb) — yeni məhsul yaranmamalı, stok artmalı, qiymət yenilənməli
        repo.saveInvoice(firm, "A-2", 2000L, "", listOf(InvoiceLine("marlboro red", 4.3, 43.0, 2)))
        products = repo.allProducts().filter { it.firmId == firm }
        assertEquals(2, products.size)
        val m = products.first { it.name == "Marlboro Red" }
        assertEquals(12, m.blockCount)
        assertEquals(43.0, m.blockPrice, 0.0)
        assertEquals(3, repo.allProducts().first { it.firmId == other }.blockCount)

        // qaimə 1 cəmi: 10×41.97 + 5×32 = 579.70
        val (inv, items) = repo.getInvoice(inv1)!!
        assertEquals("A-1", inv.number)
        assertEquals(579.70, az.siqaret.anbar.util.InventoryCalc.sum(items.map { it.blockCount * 0.0 + az.siqaret.anbar.util.InventoryCalc.totalAmount(it.blockCount, it.blockPrice) }), 0.0)

        // qaimə 1 silinir: Marlboro 12 → 2, Kent 5 → 0
        assertNull(repo.deleteInvoice(inv1))
        products = repo.allProducts().filter { it.firmId == firm }
        assertEquals(2, products.first { it.name == "Marlboro Red" }.blockCount)
        assertEquals(0, products.first { it.name == "Kent" }.blockCount)
        assertNull(repo.getInvoice(inv1))
    }

    @Test fun deletingInvoiceRefusedWhenStockAlreadySold() = runBlocking {
        val firm = repo.addFirm("Azərsun", "", "")
        val inv = repo.saveInvoice(firm, "", 1000L, "", listOf(InvoiceLine("Winston", 2.8, 28.0, 10)))
        val p = repo.allProducts().single()
        repo.adjustStock(p.id, -7) // 7 blok satılıb
        val err = repo.deleteInvoice(inv)
        assertNotNull(err)
        assertEquals(3, repo.allProducts().single().blockCount) // dəyişməyib
        assertNotNull(repo.getInvoice(inv))
    }

    @Test fun deletingFirmRemovesItsInvoicesButKeepsStock() = runBlocking {
        val firmId = repo.addFirm("Azərsun", "", "")
        repo.saveInvoice(firmId, "", 1000L, "", listOf(InvoiceLine("Winston", 2.8, 28.0, 10)))
        repo.deleteFirm(repo.getFirm(firmId)!!)
        assertEquals(0, repo.invoicesSince(0).first.size)
        assertEquals(10, repo.allProducts().single().blockCount)
        assertNull(repo.allProducts().single().firmId)
    }
}
