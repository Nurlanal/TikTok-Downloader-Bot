import { query } from '../db.js';
import { publish } from '../realtime.js';
import { sendPush } from './push.js';

/** Bildirişi bazaya yazır, WebSocket ilə göndərir və FCM push edir. */
export async function notifyUser(userId, title, body, orderId = null) {
  const { rows } = await query(
    `INSERT INTO notifications (user_id, title, body, order_id) VALUES ($1,$2,$3,$4)
     RETURNING id, title, body, order_id, is_read, created_at`,
    [userId, title, body, orderId],
  );
  publish(`user:${userId}`, 'notification', rows[0]);
  const u = await query('SELECT fcm_token FROM users WHERE id = $1', [userId]);
  await sendPush(u.rows[0]?.fcm_token, { title, body, data: { orderId: orderId ?? '' } });
}

export async function notifyAdmins(title, body, orderId = null) {
  const { rows } = await query(`SELECT id FROM users WHERE role = 'ADMIN' AND is_active`);
  await Promise.all(rows.map((r) => notifyUser(r.id, title, body, orderId)));
}

/** Arxa planda işləsin – əsas sorğunu ləngitməsin */
export function fireAndForget(p) {
  Promise.resolve(p).catch((e) => console.error('[notify]', e));
}
