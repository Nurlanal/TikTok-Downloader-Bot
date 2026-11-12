import { Router } from 'express';
import { query } from '../db.js';
import { ah, notFound } from '../lib/errors.js';
import { idParam, parse, z } from '../lib/validate.js';
import { getSettings, serializeSettings } from '../services/orders.js';

const r = Router();

export function serializeProduct(p) {
  return {
    id: p.id, categoryId: p.category_id, name: p.name, description: p.description,
    price: p.price, oldPrice: p.old_price, unit: p.unit, stock: p.stock,
    inStock: p.stock > 0, barcode: p.barcode, imageUrl: p.image_url, isActive: p.is_active,
    isNew: p.is_new, soldCount: p.sold_count, lowStockThreshold: p.low_stock_threshold,
  };
}
export const serializeCategory = (c) => ({ id: c.id, name: c.name, icon: c.icon, color: c.color, sortOrder: c.sort_order, isActive: c.is_active });
export const serializeBanner = (b) => ({ id: b.id, title: b.title, subtitle: b.subtitle, imageUrl: b.image_url, color: b.color, categoryId: b.category_id, sortOrder: b.sort_order, isActive: b.is_active });

const ACTIVE = 'p.is_active AND (c.id IS NULL OR c.is_active)';
const FROM = 'FROM products p LEFT JOIN categories c ON c.id = p.category_id';

r.get('/home', ah(async (_req, res) => {
  const [cats, banners, discounted, fresh, best, settings] = await Promise.all([
    query('SELECT * FROM categories WHERE is_active ORDER BY sort_order, name'),
    query('SELECT * FROM banners WHERE is_active ORDER BY sort_order, id DESC'),
    query(`SELECT p.* ${FROM} WHERE ${ACTIVE} AND p.old_price IS NOT NULL AND p.old_price > p.price ORDER BY (p.old_price - p.price)/p.old_price DESC LIMIT 20`),
    query(`SELECT p.* ${FROM} WHERE ${ACTIVE} AND p.is_new ORDER BY p.created_at DESC LIMIT 20`),
    query(`SELECT p.* ${FROM} WHERE ${ACTIVE} AND p.sold_count > 0 ORDER BY p.sold_count DESC LIMIT 20`),
    getSettings(),
  ]);
  const s = serializeSettings(settings);
  res.json({
    categories: cats.rows.map(serializeCategory),
    banners: banners.rows.map(serializeBanner),
    discounted: discounted.rows.map(serializeProduct),
    newProducts: fresh.rows.map(serializeProduct),
    bestSellers: best.rows.map(serializeProduct),
    settings: { ...s, deliveryZone: undefined, zoneConfigured: s.deliveryZone.length >= 3 },
  });
}));

r.get('/products', ah(async (req, res) => {
  const q = parse(z.object({
    q: z.string().trim().max(80).optional(),
    categoryId: z.coerce.number().int().positive().optional(),
    limit: z.coerce.number().int().min(1).max(100).default(50),
    offset: z.coerce.number().int().min(0).default(0),
  }), req.query);
  const where = [ACTIVE];
  const vals = [];
  if (q.q) { vals.push(`%${q.q.toLowerCase()}%`); where.push(`(lower(p.name) LIKE $${vals.length} OR p.barcode = $${vals.length + 1})`); vals.push(q.q); }
  if (q.categoryId) { vals.push(q.categoryId); where.push(`p.category_id = $${vals.length}`); }
  vals.push(q.limit, q.offset);
  const { rows } = await query(
    `SELECT p.* ${FROM} WHERE ${where.join(' AND ')} ORDER BY p.stock > 0 DESC, p.name LIMIT $${vals.length - 1} OFFSET $${vals.length}`, vals);
  res.json(rows.map(serializeProduct));
}));

r.get('/products/:id', ah(async (req, res) => {
  const id = parse(idParam, req.params.id);
  const { rows } = await query(`SELECT p.* ${FROM} WHERE p.id=$1 AND ${ACTIVE}`, [id]);
  if (!rows[0]) throw notFound('Məhsul tapılmadı');
  res.json(serializeProduct(rows[0]));
}));

export default r;
