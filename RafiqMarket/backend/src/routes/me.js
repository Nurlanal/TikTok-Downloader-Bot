import { Router } from 'express';
import { query } from '../db.js';
import { ah } from '../lib/errors.js';
import { parse, z } from '../lib/validate.js';
import { serializeUser } from './auth.js';

const r = Router();

// İstifadəçinin rolu SERVERDƏN gəlir – tətbiq interfeysi buna əsasən açılır.
r.get('/', ah(async (req, res) => {
  const { rows } = await query('SELECT * FROM users WHERE id=$1', [req.user.id]);
  res.json(serializeUser(rows[0]));
}));

r.patch('/', ah(async (req, res) => {
  const { name } = parse(z.object({ name: z.string().trim().min(2).max(120) }), req.body);
  const { rows } = await query('UPDATE users SET name=$2, updated_at=now() WHERE id=$1 RETURNING *', [req.user.id, name]);
  res.json(serializeUser(rows[0]));
}));

r.put('/fcm-token', ah(async (req, res) => {
  const { token } = parse(z.object({ token: z.string().min(10).max(4096) }), req.body);
  // Eyni cihaz başqa hesabla istifadə olunubsa, köhnə hesabdan token silinir
  await query('UPDATE users SET fcm_token=NULL WHERE fcm_token=$1 AND id<>$2', [token, req.user.id]);
  await query('UPDATE users SET fcm_token=$2 WHERE id=$1', [req.user.id, token]);
  res.json({ ok: true });
}));

r.get('/notifications', ah(async (req, res) => {
  const { rows } = await query(
    'SELECT id, title, body, order_id, is_read, created_at FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100', [req.user.id]);
  res.json(rows.map((n) => ({ id: n.id, title: n.title, body: n.body, orderId: n.order_id, isRead: n.is_read, createdAt: n.created_at })));
}));

r.post('/notifications/read', ah(async (req, res) => {
  await query('UPDATE notifications SET is_read=true WHERE user_id=$1 AND NOT is_read', [req.user.id]);
  res.json({ ok: true });
}));

export default r;
