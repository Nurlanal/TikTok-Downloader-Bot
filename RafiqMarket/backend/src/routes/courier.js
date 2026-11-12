import { Router } from 'express';
import { query } from '../db.js';
import { ah, conflict, notFound } from '../lib/errors.js';
import { idParam, parse, z } from '../lib/validate.js';
import { publish } from '../realtime.js';
import { changeStatus, confirmCash, courierRespond, getOrder, serializeOrder, TRACKING_STATUSES } from '../services/orders.js';

const r = Router();

async function myOrder(req) {
  const id = parse(idParam, req.params.id);
  const order = await getOrder(id);
  if (order.courier?.id !== req.user.id) throw notFound('Sifariş tapılmadı');
  return order;
}

const TABS = {
  new: `o.status='COURIER_ASSIGNED' AND o.courier_accepted_at IS NULL`,
  active: `(o.status IN ('PICKED_UP','ON_THE_WAY','ARRIVED') OR (o.status='COURIER_ASSIGNED' AND o.courier_accepted_at IS NOT NULL))`,
  delivered: `o.status='DELIVERED'`,
};

r.get('/orders', ah(async (req, res) => {
  const { tab } = parse(z.object({ tab: z.enum(['new', 'active', 'delivered']).default('active') }), req.query);
  const { rows } = await query(
    `SELECT o.*, cu.name AS customer_name, cu.phone AS customer_phone
     FROM orders o JOIN users cu ON cu.id=o.customer_id
     WHERE o.courier_id=$1 AND ${TABS[tab]}
     ORDER BY ${tab === 'delivered' ? 'o.delivered_at DESC' : 'o.created_at'} LIMIT 100`, [req.user.id]);
  res.json(rows.map((o) => serializeOrder(o)));
}));

r.get('/orders/:id', ah(async (req, res) => res.json(await myOrder(req))));

r.post('/orders/:id/accept', ah(async (req, res) => {
  const o = await myOrder(req);
  res.json(await courierRespond(req.user, o.id, true));
}));
r.post('/orders/:id/reject', ah(async (req, res) => {
  const o = await myOrder(req);
  res.json(await courierRespond(req.user, o.id, false));
}));
r.post('/orders/:id/status', ah(async (req, res) => {
  const o = await myOrder(req);
  const { status } = parse(z.object({ status: z.enum(['PICKED_UP', 'ON_THE_WAY', 'ARRIVED', 'DELIVERED']) }), req.body);
  res.json(await changeStatus(req.user, o.id, status));
}));
r.post('/orders/:id/confirm-cash', ah(async (req, res) => {
  const o = await myOrder(req);
  res.json(await confirmCash(req.user, o.id));
}));

r.get('/stats', ah(async (req, res) => {
  const { rows } = await query(
    `SELECT
       count(*) FILTER (WHERE status='DELIVERED' AND delivered_at >= date_trunc('day', now() AT TIME ZONE $2) AT TIME ZONE $2)::int AS today,
       count(*) FILTER (WHERE status='DELIVERED')::int AS total,
       COALESCE(sum(total) FILTER (WHERE status='DELIVERED' AND payment_method='CASH' AND delivered_at >= date_trunc('day', now() AT TIME ZONE $2) AT TIME ZONE $2),0) AS cash_today
     FROM orders WHERE courier_id=$1`, [req.user.id, process.env.TZ || 'Asia/Baku']);
  res.json({ deliveredToday: rows[0].today, deliveredTotal: rows[0].total, cashCollectedToday: Number(rows[0].cash_today).toFixed(2) });
}));

/**
 * GPS mövqeyi. Yalnız kuryerin canlı izlənən (PICKED_UP/ON_THE_WAY/ARRIVED) sifarişi olduqda qəbul edilir.
 * Yalnız son mövqe saxlanılır və yalnız həmin sifarişlərin kanalına yayımlanır.
 */
const locSchema = z.object({
  lat: z.number().min(-90).max(90), lng: z.number().min(-180).max(180),
  accuracy: z.number().min(0).max(10000).optional(), heading: z.number().optional(), speed: z.number().min(0).optional(),
});
r.post('/location', ah(async (req, res) => {
  const l = parse(locSchema, req.body);
  const { rows: orders } = await query(
    `SELECT id FROM orders WHERE courier_id=$1 AND status = ANY($2::order_status[])`, [req.user.id, TRACKING_STATUSES]);
  if (!orders.length) throw conflict('Aktiv çatdırılma yoxdur – GPS paylaşımı dayandırılmalıdır.', 'NO_ACTIVE_DELIVERY');
  const { rows } = await query(
    `INSERT INTO courier_locations (courier_id, lat, lng, accuracy, heading, speed, updated_at) VALUES ($1,$2,$3,$4,$5,$6, now())
     ON CONFLICT (courier_id) DO UPDATE SET lat=EXCLUDED.lat, lng=EXCLUDED.lng, accuracy=EXCLUDED.accuracy,
       heading=EXCLUDED.heading, speed=EXCLUDED.speed, updated_at=now()
     RETURNING updated_at`, [req.user.id, l.lat, l.lng, l.accuracy ?? null, l.heading ?? null, l.speed ?? null]);
  const payload = { courierId: req.user.id, lat: l.lat, lng: l.lng, heading: l.heading ?? null, updatedAt: rows[0].updated_at };
  for (const o of orders) publish(`order:${o.id}`, 'courier.location', { ...payload, orderId: o.id });
  publish('role:ADMIN', 'courier.location', payload);
  res.json({ ok: true, activeOrders: orders.map((o) => o.id) });
}));

export default r;
