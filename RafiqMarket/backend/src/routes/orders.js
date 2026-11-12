import { Router } from 'express';
import { query } from '../db.js';
import { ah, notFound } from '../lib/errors.js';
import { idParam, latLng, parse, z } from '../lib/validate.js';
import { normalizePhone, pointInPolygon } from '../lib/util.js';
import { changeStatus, createOrder, getOrder, getSettings, quote, serializeOrder, TRACKING_STATUSES, ZONE_ERROR } from '../services/orders.js';
import { reconcile, startPayment } from '../services/payments.js';

const r = Router();

const itemsSchema = z.array(z.object({
  productId: z.number().int().positive(),
  quantity: z.number().int().min(1).max(999),
})).min(1).max(100);

const orderSchema = latLng.extend({
  items: itemsSchema,
  street: z.string().trim().min(2).max(160),
  house: z.string().trim().min(1).max(40),
  addressNote: z.string().trim().max(300).optional(),
  contactPhone: z.string(),
  contactName: z.string().trim().max(120).optional(),
  note: z.string().trim().max(300).optional(),
  paymentMethod: z.enum(['CASH', 'ONLINE']),
});

async function ownOrder(req) {
  const id = parse(idParam, req.params.id);
  const order = await getOrder(id);
  if (order.customer.id !== req.user.id) throw notFound('Sifariş tapılmadı');
  return order;
}

r.get('/delivery-zone', ah(async (_req, res) => {
  const s = await getSettings();
  res.json({ zone: s.delivery_zone || [], zoneName: s.zone_name });
}));

r.post('/delivery-zone/check', ah(async (req, res) => {
  const p = parse(latLng, req.body);
  const s = await getSettings();
  const zone = s.delivery_zone || [];
  const inside = zone.length >= 3 && pointInPolygon(p, zone);
  res.json({ inside, configured: zone.length >= 3, message: inside ? null : ZONE_ERROR });
}));

r.post('/orders/quote', ah(async (req, res) => {
  const { items } = parse(z.object({ items: itemsSchema }), req.body);
  res.json(await quote(items));
}));

r.post('/orders', ah(async (req, res) => {
  const input = parse(orderSchema, req.body);
  input.contactPhone = normalizePhone(input.contactPhone);
  const order = await createOrder(req.user, input);
  res.status(201).json(order);
}));

r.get('/orders', ah(async (req, res) => {
  const { rows } = await query(
    `SELECT o.*, (SELECT count(*) FROM order_items i WHERE i.order_id=o.id)::int AS item_count
     FROM orders o WHERE customer_id=$1 ORDER BY created_at DESC LIMIT 100`, [req.user.id]);
  res.json(rows.map((o) => ({ ...serializeOrder(o), itemCount: o.item_count })));
}));

r.get('/orders/:id', ah(async (req, res) => res.json(await ownOrder(req))));

r.post('/orders/:id/cancel', ah(async (req, res) => {
  const order = await ownOrder(req);
  res.json(await changeStatus(req.user, order.id, 'CANCELLED', 'Müştəri ləğv etdi'));
}));

r.post('/orders/:id/pay', ah(async (req, res) => {
  const order = await ownOrder(req);
  res.json(await startPayment(req.user, order.id));
}));

// Ödəniş səhifəsindən qayıtdıqda: server provayderdən statusu yoxlayır
r.post('/orders/:id/payment/refresh', ah(async (req, res) => {
  const order = await ownOrder(req);
  if (order.status === 'PENDING_PAYMENT') await reconcile(order.id);
  res.json(await getOrder(order.id));
}));

// Canlı izləmə: müştəri YALNIZ öz sifarişinə təyin edilmiş kuryeri görür
r.get('/orders/:id/tracking', ah(async (req, res) => {
  const order = await ownOrder(req);
  const active = TRACKING_STATUSES.includes(order.status) && !!order.courier;
  let location = null;
  if (active) {
    const { rows } = await query('SELECT lat, lng, heading, speed, accuracy, updated_at FROM courier_locations WHERE courier_id=$1', [order.courier.id]);
    if (rows[0]) location = { lat: rows[0].lat, lng: rows[0].lng, heading: rows[0].heading, speed: rows[0].speed, accuracy: rows[0].accuracy, updatedAt: rows[0].updated_at };
  }
  res.json({
    orderId: order.id, status: order.status, active,
    courier: order.courier ? { name: order.courier.name, phone: order.courier.phone } : null,
    location, destination: { lat: order.address.lat, lng: order.address.lng },
  });
}));

export default r;
