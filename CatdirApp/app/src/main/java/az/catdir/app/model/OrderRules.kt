package az.catdir.app.model

/**
 * Sifarişin həyat dövrü və kimin nə edə biləcəyi. Eyni qaydalar Firestore təhlükəsizlik
 * qaydalarında (firebase/firestore.rules) server tərəfdə də yoxlanılır.
 *
 *  NEW ──admin──▶ ACCEPTED ──admin──▶ READY ──kuryer──▶ PICKED_UP ──kuryer──▶ DELIVERED
 *   │ müştəri/admin            │admin           │admin
 *   ▼                          ▼                ▼
 *  CANCELLED
 */
object OrderRules {
    val COURIER_CAN_TAKE = setOf(OrderStatus.ACCEPTED, OrderStatus.READY)

    fun subtotal(items: List<OrderItem>): Long = items.sumOf { it.totalQ }

    fun canTransition(user: UserProfile, order: Order, to: OrderStatus): Boolean = when (user.role) {
        Role.CUSTOMER -> order.customerId == user.uid && order.status == OrderStatus.NEW && to == OrderStatus.CANCELLED
        Role.ADMIN -> when (to) {
            OrderStatus.ACCEPTED -> order.status == OrderStatus.NEW
            OrderStatus.READY -> order.status == OrderStatus.ACCEPTED
            OrderStatus.CANCELLED -> order.status.isActive && order.status != OrderStatus.PICKED_UP
            else -> false
        }
        Role.COURIER -> order.courierId == user.uid && when (to) {
            OrderStatus.PICKED_UP -> order.status == OrderStatus.READY
            OrderStatus.DELIVERED -> order.status == OrderStatus.PICKED_UP
            else -> false
        }
    }

    fun canTake(user: UserProfile, order: Order): Boolean =
        user.role == Role.COURIER && order.courierId == null && order.status in COURIER_CAN_TAKE

    /** Sifariş göndərilməzdən əvvəl yoxlama. Xəta mətni və ya null. */
    fun validateCheckout(draft: OrderDraft): String? {
        val subtotal = subtotal(draft.items)
        return when {
            draft.items.isEmpty() || draft.items.all { it.qty <= 0 } -> "Səbət boşdur"
            !draft.store.isOpen -> "\"${draft.store.name}\" hazırda bağlıdır"
            subtotal < draft.store.minOrderQ -> "Minimum sifariş ${Money.format(draft.store.minOrderQ)}. " +
                "Daha ${Money.format(draft.store.minOrderQ - subtotal)} məhsul əlavə edin"
            draft.address.isBlank() -> "Çatdırılma ünvanını yazın"
            draft.address.trim().length < 5 -> "Ünvanı daha dəqiq yazın (küçə, ev, mənzil)"
            draft.phone.filter { it.isDigit() }.length < 7 -> "Telefon nömrəsini düzgün yazın"
            else -> null
        }
    }

    fun buildOrder(draft: OrderDraft, customer: UserProfile, now: Long): Order {
        val items = draft.items.filter { it.qty > 0 }
        val subtotal = subtotal(items)
        return Order(
            customerId = customer.uid,
            customerName = customer.name,
            customerPhone = draft.phone.trim(),
            storeId = draft.store.id,
            storeName = draft.store.name,
            storePhone = draft.store.phone,
            storeAddress = draft.store.address,
            items = items,
            subtotalQ = subtotal,
            deliveryFeeQ = draft.store.deliveryFeeQ,
            totalQ = subtotal + draft.store.deliveryFeeQ,
            address = draft.address.trim(),
            note = draft.note.trim(),
            status = OrderStatus.NEW,
            createdAt = now,
            updatedAt = now,
            timeline = mapOf(OrderStatus.NEW.name to now),
        )
    }
}
