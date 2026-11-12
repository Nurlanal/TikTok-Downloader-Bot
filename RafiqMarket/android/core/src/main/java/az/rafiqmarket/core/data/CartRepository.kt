package az.rafiqmarket.core.data

import az.rafiqmarket.core.db.CartDao
import az.rafiqmarket.core.db.CartItemEntity
import az.rafiqmarket.core.network.dto.ProductDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Səbət cihazda Room bazasında saxlanılır; qiymət və stok sifarişdə serverdə yenidən yoxlanılır. */
@Singleton
class CartRepository @Inject constructor(private val dao: CartDao) {
    val items: Flow<List<CartItemEntity>> = dao.observe()
    val quantities: Flow<Map<Long, Int>> = items.map { list -> list.associate { it.productId to it.quantity } }
    val count: Flow<Int> = items.map { list -> list.sumOf { it.quantity } }
    val subtotal: Flow<BigDecimal> = items.map { list ->
        list.fold(BigDecimal.ZERO) { acc, i -> acc + BigDecimal(i.price) * BigDecimal(i.quantity) }
    }

    suspend fun add(p: ProductDto) {
        val cur = dao.get(p.id)
        val q = (cur?.quantity ?: 0) + 1
        if (q > p.stock) return
        dao.upsert(CartItemEntity(p.id, p.name, p.price, p.unit, p.imageUrl, q, p.stock))
    }

    suspend fun setQuantity(productId: Long, quantity: Int) {
        val cur = dao.get(productId) ?: return
        if (quantity <= 0) dao.delete(productId)
        else dao.upsert(cur.copy(quantity = quantity.coerceAtMost(cur.maxStock.coerceAtLeast(1))))
    }

    suspend fun decrement(productId: Long) {
        val cur = dao.get(productId) ?: return
        setQuantity(productId, cur.quantity - 1)
    }

    suspend fun increment(productId: Long) {
        val cur = dao.get(productId) ?: return
        setQuantity(productId, cur.quantity + 1)
    }

    /** Server qiymətləri ilə yenilə (quote cavabından) */
    suspend fun syncFromServer(productId: Long, price: String, available: Int) {
        val cur = dao.get(productId) ?: return
        if (available <= 0) dao.delete(productId)
        else dao.upsert(cur.copy(price = price, maxStock = available, quantity = cur.quantity.coerceAtMost(available)))
    }

    suspend fun snapshot() = dao.all()
    suspend fun clear() = dao.clear()
}
