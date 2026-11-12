import { Router } from 'express';
import multer from 'multer';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { config } from '../config.js';
import { query } from '../db.js';
import { ah, badRequest, conflict, notFound } from '../lib/errors.js';
import { normalizePhone } from '../lib/util.js';
import { idParam, latLng, money, parse, z } from '../lib/validate.js';
import { serializeUser } from './auth.js';
import { serializeBanner, serializeCategory, serializeProduct } from './catalog.js';
import {
  assignCourier, changeStatus, confirmCash, getOrder, getSettings, serializeOrder, serializeSettings, updateOrderDetails,
} from '../services/orders.js';

const r = Router();
const TZ = () => config.timezone;

// ---------------- Şəkil yükləmə ----------------
fs.mkdirSync(config.uploadDir, { recursive: true });
const MIME = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };
const upload = multer({
  storage: multer.diskStorage({
    destination: config.uploadDir,
    filename: (_req, file, cb) => cb(null, crypto.randomBytes(16).toString('hex') + MIME[file.mimetype]),
  }),
  limits: { fileSize: 5 * 1024 * 1024, files: 1 },
  fileFilter: (_req, file, cb) => (MIME[file.mimetype] ? cb(null, true) : cb(badRequest('Yalnız JPG, PNG və WEBP şəkillər qəbul edilir'))),
});

r.post('/uploads', upload.single('file'), ah(async (req, res) => {
  if (!req.file) throw badRequest('Fayl seçilməyib');
  // Fayl imzasını (magic bytes) yoxlayırıq – yalnız MIME başlığına etibar etmirik
  const head = fs.readFileSync(req.file.path).subarray(0, 12);
  const ok = (head[0] === 0xff && head[1] === 0xd8) || head.subarray(0, 4).toString('hex') === '89504e47'
    || (head.subarray(0, 4).toString() === 'RIFF' && head.subarray(8, 12).toString() === 'WEBP');
  if (!ok) { fs.unlinkSync(req.file.path); throw badRequest('Fayl şəkil deyil'); }
  res.status(201).json({ url: `/uploads/${path.basename(req.file.path)}` });
}));

// ---------------- Kateqoriyalar ----------------
const categorySchema = z.object({
  name: z.string().trim().min(1).max(80),
  icon: z.string().trim().min(1).max(16).default('🛒'),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/).default('#2E7D32'),
  sortOrder: z.number().int().default(0),
  isActive: z.boolean().default(true),
});

r.get('/categories', ah(async (_req, res) => {
  const { rows } = await query(`SELECT c.*, (SELECT count(*) FROM products p WHERE p.category_id=c.id)::int AS product_count
    FROM categories c ORDER BY sort_order, name`);
  res.json(rows.map((c) => ({ ...serializeCategory(c), productCount: c.product_count })));
}));
r.post('/categories', ah(async (req, res) => {
  const c = parse(categorySchema, req.body);
  const { rows } = await query('INSERT INTO categories (name, icon, color, sort_order, is_active) VALUES ($1,$2,$3,$4,$5) RETURNING *',
    [c.name, c.icon, c.color, c.sortOrder, c.isActive]);
  res.status(201).json(serializeCategory(rows[0]));
}));
r.put('/categories/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const c = parse(categorySchema, req.body);
  const { rows } = await query('UPDATE categories SET name=$2, icon=$3, color=$4, sort_order=$5, is_active=$6 WHERE id=$1 RETURNING *',
    [id, c.name, c.icon, c.color, c.sortOrder, c.isActive]);
  if (!rows[0]) throw notFound();
  res.json(serializeCategory(rows[0]));
}));
r.delete('/categories/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  await query('DELETE FROM categories WHERE id=$1', [id]);
  res.json({ ok: true });
}));

// ---------------- Məhsullar ----------------
const productSchema = z.object({
  name: z.string().trim().min(1).max(160),
  description: z.string().trim().max(2000).default(''),
  categoryId: z.number().int().positive().nullable().default(null),
  price: money,
  oldPrice: money.nullable().default(null),
  unit: z.string().trim().min(1).max(20).default('ədəd'),
  stock: z.number().int().min(0).max(1_000_000),
  lowStockThreshold: z.number().int().min(0).default(5),
  barcode: z.string().trim().max(64).nullable().default(null).transform((v) => (v ? v : null)),
  imageUrl: z.string().trim().max(500).nullable().default(null),
  isActive: z.boolean().default(true),
  isNew: z.boolean().default(false),
});

r.get('/products', ah(async (req, res) => {
  const q = parse(z.object({ q: z.string().trim().optional(), categoryId: z.coerce.number().int().optional(), lowStock: z.enum(['true', 'false']).optional() }), req.query);
  const where = ['TRUE'];
  const vals = [];
  if (q.q) { vals.push(`%${q.q.toLowerCase()}%`, q.q); where.push(`(lower(name) LIKE $${vals.length - 1} OR barcode = $${vals.length})`); }
  if (q.categoryId) { vals.push(q.categoryId); where.push(`category_id=$${vals.length}`); }
  if (q.lowStock === 'true') where.push('stock <= low_stock_threshold');
  const { rows } = await query(`SELECT * FROM products WHERE ${where.join(' AND ')} ORDER BY name LIMIT 500`, vals);
  res.json(rows.map(serializeProduct));
}));

function productValues(p) {
  return [p.name, p.description, p.categoryId, p.price, p.oldPrice, p.unit, p.stock, p.lowStockThreshold, p.barcode, p.imageUrl, p.isActive, p.isNew];
}
function mapUniqueError(e) {
  if (e.code === '23505') throw conflict('Bu barkod artıq başqa məhsulda istifadə olunur', 'DUPLICATE_BARCODE');
  if (e.code === '23503') throw badRequest('Kateqoriya tapılmadı');
  throw e;
}

r.post('/products', ah(async (req, res) => {
  const p = parse(productSchema, req.body);
  const { rows } = await query(
    `INSERT INTO products (name, description, category_id, price, old_price, unit, stock, low_stock_threshold, barcode, image_url, is_active, is_new)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING *`, productValues(p)).catch(mapUniqueError);
  res.status(201).json(serializeProduct(rows[0]));
}));
r.put('/products/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const p = parse(productSchema, req.body);
  const { rows } = await query(
    `UPDATE products SET name=$2, description=$3, category_id=$4, price=$5, old_price=$6, unit=$7, stock=$8,
       low_stock_threshold=$9, barcode=$10, image_url=$11, is_active=$12, is_new=$13, updated_at=now()
     WHERE id=$1 RETURNING *`, [id, ...productValues(p)]).catch(mapUniqueError);
  if (!rows[0]) throw notFound();
  res.json(serializeProduct(rows[0]));
}));
// Stok düzəlişi: nisbi (delta) – paralel sifarişlərlə toqquşmasın
r.post('/products/:id/stock', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { delta } = parse(z.object({ delta: z.number().int().min(-1_000_000).max(1_000_000) }), req.body);
  const { rows } = await query('UPDATE products SET stock = stock + $2, updated_at=now() WHERE id=$1 AND stock + $2 >= 0 RETURNING *', [id, delta]);
  if (!rows[0]) throw conflict('Stok mənfi ola bilməz', 'NEGATIVE_STOCK');
  res.json(serializeProduct(rows[0]));
}));
r.patch('/products/:id/active', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { isActive } = parse(z.object({ isActive: z.boolean() }), req.body);
  const { rows } = await query('UPDATE products SET is_active=$2, updated_at=now() WHERE id=$1 RETURNING *', [id, isActive]);
  if (!rows[0]) throw notFound();
  res.json(serializeProduct(rows[0]));
}));
r.delete('/products/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  // Sifariş tarixçəsi order_items-də məhsul adı ilə qorunur (product_id NULL olur)
  await query('DELETE FROM products WHERE id=$1', [id]);
  res.json({ ok: true });
}));

// ---------------- Bannerlər ----------------
const bannerSchema = z.object({
  title: z.string().trim().min(1).max(120),
  subtitle: z.string().trim().max(200).default(''),
  imageUrl: z.string().trim().max(500).nullable().default(null),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/).default('#1B5E20'),
  categoryId: z.number().int().positive().nullable().default(null),
  sortOrder: z.number().int().default(0),
  isActive: z.boolean().default(true),
});
r.get('/banners', ah(async (_req, res) => {
  const { rows } = await query('SELECT * FROM banners ORDER BY sort_order, id DESC');
  res.json(rows.map(serializeBanner));
}));
r.post('/banners', ah(async (req, res) => {
  const b = parse(bannerSchema, req.body);
  const { rows } = await query('INSERT INTO banners (title, subtitle, image_url, color, category_id, sort_order, is_active) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *',
    [b.title, b.subtitle, b.imageUrl, b.color, b.categoryId, b.sortOrder, b.isActive]);
  res.status(201).json(serializeBanner(rows[0]));
}));
r.put('/banners/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const b = parse(bannerSchema, req.body);
  const { rows } = await query('UPDATE banners SET title=$2, subtitle=$3, image_url=$4, color=$5, category_id=$6, sort_order=$7, is_active=$8 WHERE id=$1 RETURNING *',
    [id, b.title, b.subtitle, b.imageUrl, b.color, b.categoryId, b.sortOrder, b.isActive]);
  if (!rows[0]) throw notFound();
  res.json(serializeBanner(rows[0]));
}));
r.delete('/banners/:id', ah(async (req, res) => {
  await query('DELETE FROM banners WHERE id=$1', [parse(idParam, req.params.id)]);
  res.json({ ok: true });
}));

// ---------------- Sifarişlər ----------------
const STATUS_GROUPS = {
  new: ['PLACED'],
  accepted: ['ACCEPTED'],
  preparing: ['PREPARING', 'COURIER_ASSIGNED'],
  delivering: ['PICKED_UP', 'ON_THE_WAY', 'ARRIVED'],
  delivered: ['DELIVERED'],
  cancelled: ['CANCELLED'],
};
r.get('/orders', ah(async (req, res) => {
  const q = parse(z.object({ group: z.enum(Object.keys(STATUS_GROUPS)).optional(), customerId: z.coerce.number().int().optional() }), req.query);
  const where = [`o.status <> 'PENDING_PAYMENT'`];
  const vals = [];
  if (q.group) { vals.push(STATUS_GROUPS[q.group]); where.push(`o.status = ANY($${vals.length}::order_status[])`); }
  if (q.customerId) { vals.push(q.customerId); where.push(`o.customer_id = $${vals.length}`); }
  const { rows } = await query(
    `SELECT o.*, cu.name AS customer_name, cu.phone AS customer_phone, co.name AS courier_name, co.phone AS courier_phone
     FROM orders o JOIN users cu ON cu.id=o.customer_id LEFT JOIN users co ON co.id=o.courier_id
     WHERE ${where.join(' AND ')} ORDER BY o.created_at DESC LIMIT 200`, vals);
  res.json(rows.map((o) => serializeOrder(o)));
}));
r.get('/orders/counts', ah(async (_req, res) => {
  const { rows } = await query(`SELECT status, count(*)::int AS n FROM orders GROUP BY status`);
  const by = Object.fromEntries(rows.map((x) => [x.status, x.n]));
  res.json(Object.fromEntries(Object.entries(STATUS_GROUPS).map(([g, sts]) => [g, sts.reduce((a, s) => a + (by[s] || 0), 0)])));
}));
r.get('/orders/:id', ah(async (req, res) => res.json(await getOrder(parse(idParam, req.params.id)))));
r.post('/orders/:id/status', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { status, note } = parse(z.object({
    status: z.enum(['ACCEPTED', 'PREPARING', 'PICKED_UP', 'ON_THE_WAY', 'ARRIVED', 'DELIVERED', 'CANCELLED']),
    note: z.string().trim().max(300).optional(),
  }), req.body);
  res.json(await changeStatus(req.user, id, status, note));
}));
r.post('/orders/:id/assign', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { courierId } = parse(z.object({ courierId: z.number().int().positive() }), req.body);
  res.json(await assignCourier(req.user, id, courierId));
}));
r.post('/orders/:id/confirm-cash', ah(async (req, res) => res.json(await confirmCash(req.user, parse(idParam, req.params.id)))));
r.patch('/orders/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const patch = parse(z.object({
    street: z.string().trim().min(2).max(160).optional(), house: z.string().trim().min(1).max(40).optional(),
    addressNote: z.string().trim().max(300).optional(), contactPhone: z.string().optional(), customerNote: z.string().trim().max(300).optional(),
  }), req.body);
  if (patch.contactPhone) patch.contactPhone = normalizePhone(patch.contactPhone);
  res.json(await updateOrderDetails(req.user, id, patch));
}));

// ---------------- İstifadəçilər: kuryerlər və müştərilər ----------------
r.get('/couriers', ah(async (_req, res) => {
  const { rows } = await query(
    `SELECT u.*,
       (SELECT count(*) FROM orders o WHERE o.courier_id=u.id AND o.status='DELIVERED'
          AND o.delivered_at >= date_trunc('day', now() AT TIME ZONE $1) AT TIME ZONE $1)::int AS delivered_today,
       (SELECT count(*) FROM orders o WHERE o.courier_id=u.id AND o.status IN ('COURIER_ASSIGNED','PICKED_UP','ON_THE_WAY','ARRIVED'))::int AS active_orders,
       (SELECT count(*) FROM orders o WHERE o.courier_id=u.id AND o.status='DELIVERED')::int AS delivered_total,
       cl.lat, cl.lng, cl.updated_at AS location_at
     FROM users u LEFT JOIN courier_locations cl ON cl.courier_id=u.id
     WHERE u.role='COURIER' ORDER BY u.is_active DESC, u.name`, [TZ()]);
  res.json(rows.map((u) => ({
    ...serializeUser(u), deliveredToday: u.delivered_today, deliveredTotal: u.delivered_total, activeOrders: u.active_orders,
    location: u.lat != null && u.active_orders > 0 ? { lat: u.lat, lng: u.lng, updatedAt: u.location_at } : null,
  })));
}));

// Kuryer əlavə et: nömrə ilə yeni hesab yaradılır və ya mövcud istifadəçiyə kuryer rolu verilir
r.post('/couriers', ah(async (req, res) => {
  const b = parse(z.object({ phone: z.string(), name: z.string().trim().min(2).max(120) }), req.body);
  const phone = normalizePhone(b.phone);
  const ex = await query('SELECT * FROM users WHERE phone=$1', [phone]);
  if (ex.rows[0]?.role === 'ADMIN') throw conflict('Bu nömrə admin hesabına aiddir', 'IS_ADMIN');
  const { rows } = await query(
    `INSERT INTO users (phone, name, role, is_active) VALUES ($1,$2,'COURIER',true)
     ON CONFLICT (phone) DO UPDATE SET role='COURIER', name=EXCLUDED.name, is_active=true, updated_at=now() RETURNING *`, [phone, b.name]);
  res.status(201).json(serializeUser(rows[0]));
}));

r.get('/customers', ah(async (req, res) => {
  const { q } = parse(z.object({ q: z.string().trim().optional() }), req.query);
  const vals = [];
  let where = `u.role='CUSTOMER'`;
  if (q) { vals.push(`%${q.toLowerCase()}%`); where += ` AND (lower(u.name) LIKE $1 OR u.phone LIKE $1)`; }
  const { rows } = await query(
    `SELECT u.*, count(o.id)::int AS order_count,
       COALESCE(sum(o.total) FILTER (WHERE o.status='DELIVERED'),0) AS total_spent, max(o.created_at) AS last_order_at
     FROM users u LEFT JOIN orders o ON o.customer_id=u.id
     WHERE ${where} GROUP BY u.id ORDER BY last_order_at DESC NULLS LAST, u.created_at DESC LIMIT 500`, vals);
  res.json(rows.map((u) => ({ ...serializeUser(u), orderCount: u.order_count, totalSpent: Number(u.total_spent).toFixed(2), lastOrderAt: u.last_order_at, createdAt: u.created_at })));
}));

r.get('/users/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { rows } = await query('SELECT * FROM users WHERE id=$1', [id]);
  if (!rows[0]) throw notFound('İstifadəçi tapılmadı');
  const orders = await query('SELECT * FROM orders WHERE customer_id=$1 OR courier_id=$1 ORDER BY created_at DESC LIMIT 100', [id]);
  res.json({ user: { ...serializeUser(rows[0]), createdAt: rows[0].created_at }, orders: orders.rows.map((o) => serializeOrder(o)) });
}));

// Hesab statusu (aktiv/deaktiv)
r.patch('/users/:id/active', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { isActive } = parse(z.object({ isActive: z.boolean() }), req.body);
  if (id === req.user.id) throw conflict('Öz hesabınızı deaktiv edə bilməzsiniz', 'SELF');
  const { rows } = await query('UPDATE users SET is_active=$2, updated_at=now() WHERE id=$1 RETURNING *', [id, isActive]);
  if (!rows[0]) throw notFound();
  if (!isActive) await query('UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=$1 AND revoked_at IS NULL', [id]);
  res.json(serializeUser(rows[0]));
}));

// Rol təyini – YALNIZ admin
r.patch('/users/:id/role', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { role } = parse(z.object({ role: z.enum(['CUSTOMER', 'COURIER', 'ADMIN']) }), req.body);
  if (id === req.user.id) throw conflict('Öz rolunuzu dəyişə bilməzsiniz', 'SELF');
  const { rows } = await query('UPDATE users SET role=$2, updated_at=now() WHERE id=$1 RETURNING *', [id, role]);
  if (!rows[0]) throw notFound();
  res.json(serializeUser(rows[0]));
}));

// ---------------- Statistika ----------------
r.get('/stats', ah(async (_req, res) => {
  const tz = TZ();
  const periods = {
    today: `date_trunc('day', now() AT TIME ZONE $1) AT TIME ZONE $1`,
    week: `date_trunc('week', now() AT TIME ZONE $1) AT TIME ZONE $1`,
    month: `date_trunc('month', now() AT TIME ZONE $1) AT TIME ZONE $1`,
  };
  const out = {};
  for (const [k, since] of Object.entries(periods)) {
    const { rows: [s] } = await query(
      `SELECT COALESCE(sum(total) FILTER (WHERE status='DELIVERED'),0) AS revenue,
              count(*) FILTER (WHERE status='DELIVERED')::int AS completed,
              count(*) FILTER (WHERE status='CANCELLED')::int AS cancelled,
              count(*) FILTER (WHERE status <> 'PENDING_PAYMENT')::int AS total
       FROM orders WHERE created_at >= ${since}`, [tz]);
    out[k] = { revenue: Number(s.revenue).toFixed(2), completed: s.completed, cancelled: s.cancelled, orders: s.total };
  }
  const { rows: [all] } = await query(
    `SELECT COALESCE(sum(total) FILTER (WHERE status='DELIVERED'),0) AS revenue,
            count(*) FILTER (WHERE status='DELIVERED')::int AS completed,
            count(*) FILTER (WHERE status='CANCELLED')::int AS cancelled,
            COALESCE(sum(total) FILTER (WHERE status='DELIVERED' AND payment_method='CASH'),0) AS cash,
            COALESCE(sum(total) FILTER (WHERE status='DELIVERED' AND payment_method='ONLINE'),0) AS online,
            count(*) FILTER (WHERE status='DELIVERED' AND payment_method='CASH')::int AS cash_count,
            count(*) FILTER (WHERE status='DELIVERED' AND payment_method='ONLINE')::int AS online_count,
            count(*) FILTER (WHERE payment_status='FAILED')::int AS failed_payments
     FROM orders`);
  const { rows: top } = await query(
    `SELECT i.product_name AS name, sum(i.quantity)::int AS quantity, sum(i.line_total) AS revenue
     FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.status='DELIVERED'
     GROUP BY i.product_name ORDER BY quantity DESC LIMIT 10`);
  const { rows: daily } = await query(
    `SELECT to_char(d, 'DD.MM') AS day, COALESCE(sum(o.total),0) AS revenue
     FROM generate_series((now() AT TIME ZONE $1)::date - 6, (now() AT TIME ZONE $1)::date, '1 day') d
     LEFT JOIN orders o ON o.status='DELIVERED' AND (o.delivered_at AT TIME ZONE $1)::date = d
     GROUP BY d ORDER BY d`, [tz]);
  res.json({
    ...out,
    allTime: { revenue: Number(all.revenue).toFixed(2), completed: all.completed, cancelled: all.cancelled },
    payments: {
      cash: Number(all.cash).toFixed(2), online: Number(all.online).toFixed(2),
      cashCount: all.cash_count, onlineCount: all.online_count, failedPayments: all.failed_payments,
    },
    topProducts: top.map((t) => ({ name: t.name, quantity: t.quantity, revenue: Number(t.revenue).toFixed(2) })),
    last7Days: daily.map((d) => ({ day: d.day, revenue: Number(d.revenue).toFixed(2) })),
  });
}));

// ---------------- Çatdırılma parametrləri ----------------
r.get('/settings', ah(async (_req, res) => res.json(serializeSettings(await getSettings()))));

const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'SS:DD formatı');
r.put('/settings', ah(async (req, res) => {
  const s = parse(z.object({
    deliveryFee: money, minOrderAmount: money, freeDeliveryThreshold: money.nullable(),
    openTime: hhmm, closeTime: hhmm, ordersEnabled: z.boolean(), zoneName: z.string().trim().min(1).max(80).default('Xıdırlı kəndi'),
  }), req.body);
  await query(
    `UPDATE settings SET delivery_fee=$1, min_order_amount=$2, free_delivery_threshold=$3, open_time=$4, close_time=$5,
       orders_enabled=$6, zone_name=$7, updated_at=now() WHERE id=1`,
    [s.deliveryFee, s.minOrderAmount, s.freeDeliveryThreshold, s.openTime, s.closeTime, s.ordersEnabled, s.zoneName]);
  res.json(serializeSettings(await getSettings()));
}));

// Admin xəritədə çəkdiyi çatdırılma zonasını saxlayır
r.put('/settings/zone', ah(async (req, res) => {
  const { polygon } = parse(z.object({ polygon: z.array(latLng).min(3).max(500) }), req.body);
  await query('UPDATE settings SET delivery_zone=$1::jsonb, updated_at=now() WHERE id=1', [JSON.stringify(polygon)]);
  res.json(serializeSettings(await getSettings()));
}));
r.delete('/settings/zone', ah(async (_req, res) => {
  await query('UPDATE settings SET delivery_zone=NULL, updated_at=now() WHERE id=1');
  res.json(serializeSettings(await getSettings()));
}));

export default r;
