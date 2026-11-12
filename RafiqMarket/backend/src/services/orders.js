import { query, tx } from '../db.js';
import { config } from '../config.js';
import { badRequest, conflict, forbidden, notFound } from '../lib/errors.js';
import { fromQ, isWithinHours, localTimeHHMM, pointInPolygon, toQ } from '../lib/util.js';
import { publish } from '../realtime.js';
import { fireAndForget, notifyAdmins, notifyUser } from './notify.js';

export const ZONE_ERROR = 'Rafiq Market hazırda yalnız Xıdırlı kəndi daxilində çatdırılma həyata keçirir.';

export const STATUS_LABELS = {
  PENDING_PAYMENT: 'Ödəniş gözlənilir',
  PLACED: 'Sifariş göndərildi',
  ACCEPTED: 'Market sifarişi qəbul etdi',
  PREPARING: 'Məhsullar hazırlanır',
  COURIER_ASSIGNED: 'Kuryer təyin edildi',
  PICKED_UP: 'Kuryer sifarişi götürdü',
  ON_THE_WAY: 'Sifariş yoldadır',
  ARRIVED: 'Kuryer ünvana çatdı',
  DELIVERED: 'Sifariş təhvil verildi',
  CANCELLED: 'Sifariş ləğv edildi',
};

const CUSTOMER_MESSAGES = {
  ACCEPTED: 'Sifarişiniz qəbul edildi',
  PREPARING: 'Sifarişiniz hazırlanır',
  COURIER_ASSIGNED: 'Kuryer təyin edildi',
  PICKED_UP: 'Kuryer sifarişinizi götürdü',
  ON_THE_WAY: 'Kuryer yola düşdü',
  ARRIVED: 'Kuryer ünvana çatdı',
  DELIVERED: 'Sifarişiniz çatdırıldı',
  CANCELLED: 'Sifarişiniz ləğv edildi',
};

/** Canlı GPS izləməsinin aktiv olduğu statuslar */
export const TRACKING_STATUSES = ['PICKED_UP', 'ON_THE_WAY', 'ARRIVED'];
export const FINAL_STATUSES = ['DELIVERED', 'CANCELLED'];

// Kim hansı keçidi edə bilər
const COURIER_FLOW = { COURIER_ASSIGNED: 'PICKED_UP', PICKED_UP: 'ON_THE_WAY', ON_THE_WAY: 'ARRIVED', ARRIVED: 'DELIVERED' };
const ADMIN_FLOW = { PLACED: 'ACCEPTED', ACCEPTED: 'PREPARING', ...COURIER_FLOW };

export async function getSettings(client = { query }) {
  const { rows } = await client.query('SELECT * FROM settings WHERE id = 1');
  return rows[0];
}

export function serializeSettings(s) {
  return {
    deliveryFee: s.delivery_fee,
    minOrderAmount: s.min_order_amount,
    freeDeliveryThreshold: s.free_delivery_threshold,
    openTime: String(s.open_time).slice(0, 5),
    closeTime: String(s.close_time).slice(0, 5),
    ordersEnabled: s.orders_enabled,
    deliveryZone: s.delivery_zone || [],
    zoneName: s.zone_name,
    isOpenNow: isWithinHours(localTimeHHMM(config.timezone), String(s.open_time), String(s.close_time)),
  };
}

function deliveryFeeQ(settings, subtotalQ) {
  const thr = settings.free_delivery_threshold;
  if (thr !== null && thr !== undefined && subtotalQ >= toQ(thr)) return 0;
  return toQ(settings.delivery_fee);
}

function mergeItems(items) {
  const map = new Map();
  for (const it of items) map.set(it.productId, (map.get(it.productId) || 0) + it.quantity);
  return [...map.entries()].map(([productId, quantity]) => ({ productId, quantity })).sort((a, b) => a.productId - b.productId);
}

/** Səbətin server tərəfində hesablanması (qiymətlər həmişə serverdən götürülür) */
export async function quote(rawItems) {
  const items = mergeItems(rawItems);
  const settings = await getSettings();
  const ids = items.map((i) => i.productId);
  const { rows } = await query(
    'SELECT id, name, price, unit, stock, is_active, image_url FROM products WHERE id = ANY($1::bigint[])', [ids]);
  const byId = new Map(rows.map((r) => [r.id, r]));
  let subtotalQ = 0;
  const lines = items.map((it) => {
    const p = byId.get(it.productId);
    const available = p && p.is_active ? p.stock : 0;
    const qty = Math.min(it.quantity, available);
    subtotalQ += qty * toQ(p?.price ?? 0);
    return {
      productId: it.productId,
      name: p?.name ?? 'Məhsul mövcud deyil',
      unitPrice: p?.price ?? '0.00',
      requestedQuantity: it.quantity,
      availableQuantity: available,
      ok: !!p && p.is_active && p.stock >= it.quantity,
    };
  });
  const feeQ = deliveryFeeQ(settings, subtotalQ);
  return {
    lines,
    subtotal: fromQ(subtotalQ),
    deliveryFee: fromQ(feeQ),
    total: fromQ(subtotalQ + feeQ),
    minOrderAmount: settings.min_order_amount,
    freeDeliveryThreshold: settings.free_delivery_threshold,
    meetsMinimum: subtotalQ >= toQ(settings.min_order_amount),
  };
}

/**
 * Sifariş yaradır. Stok atomik şərtli UPDATE ilə azaldılır:
 *   UPDATE products SET stock = stock - q WHERE id = $1 AND stock >= q
 * Paralel sifarişlərdə eyni məhsul heç vaxt stokdan artıq satıla bilməz.
 */
export async function createOrder(user, input) {
  const settings = await getSettings();
  if (!settings.orders_enabled) throw conflict('Market hazırda sifariş qəbul etmir.', 'ORDERS_DISABLED');
  if (!isWithinHours(localTimeHHMM(config.timezone), String(settings.open_time), String(settings.close_time))) {
    throw conflict(`Sifarişlər yalnız ${String(settings.open_time).slice(0, 5)}–${String(settings.close_time).slice(0, 5)} arası qəbul edilir.`, 'CLOSED');
  }
  const zone = settings.delivery_zone;
  if (!Array.isArray(zone) || zone.length < 3) {
    throw conflict('Çatdırılma zonası hələ market tərəfindən təyin edilməyib.', 'ZONE_NOT_CONFIGURED');
  }
  if (!pointInPolygon({ lat: input.lat, lng: input.lng }, zone)) {
    throw badRequest(ZONE_ERROR, 'OUT_OF_ZONE');
  }
  if (input.paymentMethod === 'ONLINE' && !(config.epoint.publicKey && config.epoint.privateKey)) {
    throw conflict('Onlayn ödəniş hazırda aktiv deyil. Nağd ödənişi seçin.', 'ONLINE_PAYMENT_UNAVAILABLE');
  }

  const items = mergeItems(input.items);
  const lowStock = [];

  const order = await tx(async (c) => {
    let subtotalQ = 0;
    const lines = [];
    for (const it of items) {
      const { rows } = await c.query(
        `UPDATE products SET stock = stock - $2, updated_at = now()
         WHERE id = $1 AND is_active AND stock >= $2
         RETURNING id, name, unit, price, stock, low_stock_threshold`,
        [it.productId, it.quantity]);
      if (!rows[0]) {
        const p = await c.query('SELECT name, stock, is_active FROM products WHERE id = $1', [it.productId]);
        const name = p.rows[0]?.name ?? `#${it.productId}`;
        const left = p.rows[0]?.is_active ? p.rows[0].stock : 0;
        throw conflict(`"${name}" üçün kifayət qədər stok yoxdur (qalıq: ${left}).`, 'OUT_OF_STOCK',
          { productId: it.productId, available: left });
      }
      const p = rows[0];
      const lineQ = toQ(p.price) * it.quantity;
      subtotalQ += lineQ;
      lines.push({ ...p, quantity: it.quantity, lineQ });
      if (p.stock <= p.low_stock_threshold) lowStock.push(p);
    }
    if (subtotalQ < toQ(settings.min_order_amount)) {
      throw badRequest(`Minimum sifariş məbləği ${settings.min_order_amount} ₼-dir.`, 'BELOW_MINIMUM');
    }
    const feeQ = deliveryFeeQ(settings, subtotalQ);
    const seq = await c.query(`SELECT nextval('order_number_seq') AS n`);
    const orderNumber = `RM-${seq.rows[0].n}`;
    const status = input.paymentMethod === 'ONLINE' ? 'PENDING_PAYMENT' : 'PLACED';
    const { rows: [o] } = await c.query(
      `INSERT INTO orders (order_number, customer_id, status, payment_method, subtotal, delivery_fee, total,
         address_street, address_house, address_note, contact_phone, contact_name, lat, lng, customer_note)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) RETURNING id`,
      [orderNumber, user.id, status, input.paymentMethod, fromQ(subtotalQ), fromQ(feeQ), fromQ(subtotalQ + feeQ),
        input.street, input.house, input.addressNote || '', input.contactPhone, input.contactName || user.name || '',
        input.lat, input.lng, input.note || '']);
    for (const l of lines) {
      await c.query(
        `INSERT INTO order_items (order_id, product_id, product_name, unit, unit_price, quantity, line_total)
         VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [o.id, l.id, l.name, l.unit, l.price, l.quantity, fromQ(l.lineQ)]);
    }
    await c.query('INSERT INTO order_status_history (order_id, status, changed_by) VALUES ($1,$2,$3)', [o.id, status, user.id]);
    return o;
  });

  const full = await getOrder(order.id);
  if (full.status === 'PLACED') onPlaced(full);
  for (const p of lowStock) {
    fireAndForget(notifyAdmins('Stok azaldı', `"${p.name}" məhsulundan ${p.stock} ${p.unit} qalıb.`));
  }
  return full;
}

/** Sifariş marketə göndərildikdə (nağd – dərhal, onlayn – ödənişdən sonra) */
export function onPlaced(order) {
  fireAndForget(notifyAdmins('Yeni sifariş daxil oldu', `${order.orderNumber} – ${order.total} ₼`, order.id));
  publish('role:ADMIN', 'order.updated', order);
}

export async function getOrder(id) {
  const { rows } = await query(
    `SELECT o.*, cu.name AS customer_name, cu.phone AS customer_phone,
            co.name AS courier_name, co.phone AS courier_phone
     FROM orders o
     JOIN users cu ON cu.id = o.customer_id
     LEFT JOIN users co ON co.id = o.courier_id
     WHERE o.id = $1`, [id]);
  const o = rows[0];
  if (!o) throw notFound('Sifariş tapılmadı');
  const [items, history] = await Promise.all([
    query('SELECT * FROM order_items WHERE order_id = $1 ORDER BY id', [id]),
    query('SELECT status, note, created_at FROM order_status_history WHERE order_id = $1 ORDER BY created_at, id', [id]),
  ]);
  return serializeOrder(o, items.rows, history.rows);
}

export function serializeOrder(o, items = [], history = []) {
  return {
    id: o.id,
    orderNumber: o.order_number,
    status: o.status,
    statusLabel: STATUS_LABELS[o.status],
    paymentMethod: o.payment_method,
    paymentStatus: o.payment_status,
    subtotal: o.subtotal,
    deliveryFee: o.delivery_fee,
    total: o.total,
    address: {
      street: o.address_street, house: o.address_house, note: o.address_note,
      lat: o.lat, lng: o.lng,
    },
    contactPhone: o.contact_phone,
    contactName: o.contact_name,
    customerNote: o.customer_note,
    cancelReason: o.cancel_reason,
    customer: o.customer_name !== undefined ? { id: o.customer_id, name: o.customer_name, phone: o.customer_phone } : null,
    courier: o.courier_id ? { id: o.courier_id, name: o.courier_name, phone: o.courier_phone } : null,
    courierAcceptedAt: o.courier_accepted_at,
    cashConfirmedAt: o.cash_confirmed_at,
    deliveredAt: o.delivered_at,
    createdAt: o.created_at,
    updatedAt: o.updated_at,
    trackingActive: TRACKING_STATUSES.includes(o.status),
    items: items.map((i) => ({
      productId: i.product_id, name: i.product_name, unit: i.unit,
      unitPrice: i.unit_price, quantity: i.quantity, lineTotal: i.line_total,
    })),
    history: history.map((h) => ({ status: h.status, label: STATUS_LABELS[h.status], note: h.note, at: h.created_at })),
  };
}

async function restock(c, orderId) {
  await c.query(
    `UPDATE products p SET stock = p.stock + oi.quantity, updated_at = now()
     FROM order_items oi WHERE oi.order_id = $1 AND oi.product_id = p.id`, [orderId]);
}

/** Bütün dəyişikliklərdən sonra maraqlı tərəflərə yayım */
async function broadcast(orderId, prev) {
  const order = await getOrder(orderId);
  publish(`user:${order.customer.id}`, 'order.updated', order);
  publish(`order:${order.id}`, 'order.updated', order);
  if (order.courier) publish(`user:${order.courier.id}`, 'order.updated', order);
  if (prev?.courier_id && prev.courier_id !== order.courier?.id) publish(`user:${prev.courier_id}`, 'order.updated', order);
  if (order.status !== 'PENDING_PAYMENT') publish('role:ADMIN', 'order.updated', order);
  return order;
}

/**
 * Status keçidi. Rol və cari statusa görə serverdə yoxlanılır.
 */
export async function changeStatus(actor, orderId, newStatus, note) {
  const prev = await tx(async (c) => {
    const { rows } = await c.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [orderId]);
    const o = rows[0];
    if (!o) throw notFound('Sifariş tapılmadı');
    const from = o.status;
    if (FINAL_STATUSES.includes(from)) throw conflict('Bu sifariş artıq bağlanıb.', 'ORDER_CLOSED');

    if (newStatus === 'CANCELLED') {
      if (actor.role === 'CUSTOMER') {
        if (o.customer_id !== actor.id) throw notFound('Sifariş tapılmadı');
        if (!['PLACED', 'PENDING_PAYMENT'].includes(from)) {
          throw conflict('Market sifarişi qəbul etdikdən sonra ləğv etmək üçün market ilə əlaqə saxlayın.', 'CANNOT_CANCEL');
        }
      } else if (actor.role !== 'ADMIN') {
        throw forbidden();
      }
      await restock(c, o.id);
      const payStatus = o.payment_status === 'PAID' ? 'REFUND_REQUIRED' : o.payment_status === 'PENDING' ? 'FAILED' : o.payment_status;
      await c.query(
        `UPDATE orders SET status='CANCELLED', cancel_reason=$2, payment_status=$3, updated_at=now() WHERE id=$1`,
        [o.id, note || null, payStatus]);
    } else {
      if (actor.role === 'COURIER') {
        if (o.courier_id !== actor.id) throw notFound('Sifariş tapılmadı');
        if (COURIER_FLOW[from] !== newStatus) throw conflict(`"${STATUS_LABELS[from]}" statusundan "${STATUS_LABELS[newStatus]}" statusuna keçmək olmaz.`, 'BAD_TRANSITION');
        if (from === 'COURIER_ASSIGNED' && !o.courier_accepted_at) throw conflict('Əvvəlcə sifarişi qəbul edin.', 'NOT_ACCEPTED');
      } else if (actor.role === 'ADMIN') {
        if (ADMIN_FLOW[from] !== newStatus) throw conflict(`"${STATUS_LABELS[from]}" statusundan "${STATUS_LABELS[newStatus]}" statusuna keçmək olmaz.`, 'BAD_TRANSITION');
      } else {
        throw forbidden();
      }
      if (newStatus === 'DELIVERED') {
        if (o.payment_method === 'CASH' && !o.cash_confirmed_at) {
          throw conflict('Nağd ödənişi aldığınızı təsdiqləyin.', 'CASH_NOT_CONFIRMED');
        }
        if (o.payment_method === 'ONLINE' && o.payment_status !== 'PAID') {
          throw conflict('Onlayn ödəniş təsdiqlənməyib.', 'NOT_PAID');
        }
        await c.query(
          `UPDATE products p SET sold_count = p.sold_count + oi.quantity
           FROM order_items oi WHERE oi.order_id = $1 AND oi.product_id = p.id`, [o.id]);
      }
      await c.query(
        `UPDATE orders SET status=$2::order_status, updated_at=now(), delivered_at = CASE WHEN $2::order_status='DELIVERED' THEN now() ELSE delivered_at END
         WHERE id=$1`, [o.id, newStatus]);
    }
    await c.query('INSERT INTO order_status_history (order_id, status, changed_by, note) VALUES ($1,$2,$3,$4)',
      [o.id, newStatus, actor.id, note || null]);
    return o;
  });

  const order = await broadcast(orderId, prev);
  if (CUSTOMER_MESSAGES[newStatus]) {
    fireAndForget(notifyUser(order.customer.id, CUSTOMER_MESSAGES[newStatus], `Sifariş ${order.orderNumber}`, order.id));
  }
  if (newStatus === 'CANCELLED' && order.courier) {
    fireAndForget(notifyUser(order.courier.id, 'Sifariş ləğv edildi', `Sifariş ${order.orderNumber} ləğv edildi`, order.id));
  }
  if (newStatus === 'DELIVERED' && actor.role === 'COURIER') {
    fireAndForget(notifyAdmins('Kuryer sifarişi tamamladı', `${order.courier.name || 'Kuryer'} ${order.orderNumber} sifarişini çatdırdı`, order.id));
  }
  return order;
}

/** Admin kuryer təyin edir (və ya dəyişir) */
export async function assignCourier(admin, orderId, courierId) {
  const prev = await tx(async (c) => {
    const cr = await c.query(`SELECT id, is_active FROM users WHERE id = $1 AND role = 'COURIER'`, [courierId]);
    if (!cr.rows[0]) throw badRequest('Kuryer tapılmadı', 'COURIER_NOT_FOUND');
    if (!cr.rows[0].is_active) throw conflict('Kuryer hesabı deaktivdir', 'COURIER_INACTIVE');
    const { rows } = await c.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [orderId]);
    const o = rows[0];
    if (!o) throw notFound('Sifariş tapılmadı');
    if (!['ACCEPTED', 'PREPARING', 'COURIER_ASSIGNED'].includes(o.status)) {
      throw conflict('Kuryer yalnız qəbul edilmiş və ya hazırlanan sifarişə təyin edilə bilər.', 'BAD_TRANSITION');
    }
    await c.query(
      `UPDATE orders SET courier_id=$2, status='COURIER_ASSIGNED', courier_accepted_at=NULL, updated_at=now() WHERE id=$1`,
      [o.id, courierId]);
    await c.query('INSERT INTO order_status_history (order_id, status, changed_by) VALUES ($1,$2,$3)', [o.id, 'COURIER_ASSIGNED', admin.id]);
    return o;
  });
  const order = await broadcast(orderId, prev);
  fireAndForget(notifyUser(courierId, 'Yeni sifariş təyin edildi', `${order.orderNumber} – ${order.address.street}, ${order.address.house}`, order.id));
  if (prev.courier_id && prev.courier_id !== courierId) {
    fireAndForget(notifyUser(prev.courier_id, 'Sifariş ləğv edildi', `${order.orderNumber} başqa kuryerə təyin edildi`, order.id));
  }
  if (prev.status !== 'COURIER_ASSIGNED') {
    fireAndForget(notifyUser(order.customer.id, CUSTOMER_MESSAGES.COURIER_ASSIGNED, `Sifariş ${order.orderNumber}`, order.id));
  }
  return order;
}

/** Kuryer təyinatı qəbul edir və ya imtina edir */
export async function courierRespond(courier, orderId, accept) {
  const prev = await tx(async (c) => {
    const { rows } = await c.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [orderId]);
    const o = rows[0];
    if (!o || o.courier_id !== courier.id) throw notFound('Sifariş tapılmadı');
    if (o.status !== 'COURIER_ASSIGNED') throw conflict('Bu sifariş artıq qəbul edilib və ya bağlanıb.', 'BAD_TRANSITION');
    if (accept) {
      await c.query('UPDATE orders SET courier_accepted_at = now(), updated_at=now() WHERE id=$1', [o.id]);
      await c.query(`INSERT INTO order_status_history (order_id, status, changed_by, note) VALUES ($1,'COURIER_ASSIGNED',$2,'Kuryer sifarişi qəbul etdi')`, [o.id, courier.id]);
    } else {
      await c.query(`UPDATE orders SET courier_id=NULL, courier_accepted_at=NULL, status='PREPARING', updated_at=now() WHERE id=$1`, [o.id]);
      await c.query(`INSERT INTO order_status_history (order_id, status, changed_by, note) VALUES ($1,'PREPARING',$2,'Kuryer imtina etdi')`, [o.id, courier.id]);
    }
    return o;
  });
  const order = await broadcast(orderId, prev);
  if (!accept) fireAndForget(notifyAdmins('Kuryer imtina etdi', `${order.orderNumber} üçün yeni kuryer təyin edin`, order.id));
  return order;
}

/** Nağd ödənişin alındığını təsdiqləmə (kuryer – öz sifarişi; admin – istənilən) */
export async function confirmCash(actor, orderId) {
  const prev = await tx(async (c) => {
    const { rows } = await c.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [orderId]);
    const o = rows[0];
    if (!o) throw notFound('Sifariş tapılmadı');
    if (actor.role === 'COURIER' && o.courier_id !== actor.id) throw notFound('Sifariş tapılmadı');
    if (o.payment_method !== 'CASH') throw conflict('Bu sifariş nağd ödənişli deyil.', 'NOT_CASH');
    if (o.cash_confirmed_at) return o;
    if (actor.role === 'COURIER' && !['ON_THE_WAY', 'ARRIVED'].includes(o.status)) {
      throw conflict('Nağd ödəniş yalnız ünvanda təsdiqlənə bilər.', 'BAD_TRANSITION');
    }
    await c.query(`UPDATE orders SET cash_confirmed_at = now(), payment_status='PAID', updated_at=now() WHERE id=$1`, [o.id]);
    return o;
  });
  return broadcast(orderId, prev);
}

/** Admin sifariş məlumatlarını dəyişir (ünvan qeydi, əlaqə nömrəsi və s.) */
export async function updateOrderDetails(admin, orderId, patch) {
  const fields = { addressNote: 'address_note', contactPhone: 'contact_phone', customerNote: 'customer_note', house: 'address_house', street: 'address_street' };
  const sets = [];
  const vals = [orderId];
  for (const [k, col] of Object.entries(fields)) {
    if (patch[k] !== undefined) { vals.push(patch[k]); sets.push(`${col} = $${vals.length}`); }
  }
  if (!sets.length) throw badRequest('Dəyişiklik yoxdur');
  const { rows } = await query(`UPDATE orders SET ${sets.join(', ')}, updated_at=now() WHERE id=$1 AND status NOT IN ('DELIVERED','CANCELLED') RETURNING courier_id`, vals);
  if (!rows[0]) throw conflict('Sifariş tapılmadı və ya artıq bağlanıb', 'ORDER_CLOSED');
  const order = await broadcast(orderId);
  if (order.courier) fireAndForget(notifyUser(order.courier.id, 'Sifariş məlumatları dəyişdirildi', order.orderNumber, order.id));
  return order;
}

/** Ödəniş nəticəsi (YALNIZ server tərəfindən təsdiqlənmiş callback/status sorğusundan çağırılır) */
export async function applyPaymentResult(orderId, success) {
  const changed = await tx(async (c) => {
    const { rows } = await c.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [orderId]);
    const o = rows[0];
    if (!o || o.status !== 'PENDING_PAYMENT') return null; // idempotent
    if (success) {
      await c.query(`UPDATE orders SET status='PLACED', payment_status='PAID', updated_at=now() WHERE id=$1`, [o.id]);
      await c.query(`INSERT INTO order_status_history (order_id, status, note) VALUES ($1,'PLACED','Onlayn ödəniş təsdiqləndi')`, [o.id]);
    } else {
      await restock(c, o.id);
      await c.query(`UPDATE orders SET status='CANCELLED', payment_status='FAILED', cancel_reason='Ödəniş uğursuz oldu', updated_at=now() WHERE id=$1`, [o.id]);
      await c.query(`INSERT INTO order_status_history (order_id, status, note) VALUES ($1,'CANCELLED','Ödəniş uğursuz oldu')`, [o.id]);
    }
    return o;
  });
  if (!changed) return null;
  const order = await broadcast(orderId, changed);
  if (success) {
    onPlaced(order);
  } else {
    fireAndForget(notifyAdmins('Ödəniş uğursuz oldu', `${order.orderNumber} – ${order.total} ₼`, order.id));
    fireAndForget(notifyUser(order.customer.id, 'Ödəniş uğursuz oldu', `Sifariş ${order.orderNumber} ləğv edildi`, order.id));
  }
  return order;
}
