import { WebSocketServer } from 'ws';
import { userFromToken } from './auth.js';
import { query } from './db.js';

/**
 * Real vaxt kanalları:
 *  - user:<id>        – istifadəçinin öz bildirişləri və sifariş yenilənmələri
 *  - role:ADMIN       – bütün adminlər
 *  - order:<id>       – sifarişin canlı izlənməsi (yalnız icazəli istifadəçilər)
 */
const channels = new Map(); // channel -> Set<ws>

function join(ws, ch) {
  if (!channels.has(ch)) channels.set(ch, new Set());
  channels.get(ch).add(ws);
  ws.channels.add(ch);
}
function leave(ws, ch) {
  channels.get(ch)?.delete(ws);
  ws.channels.delete(ch);
}

export function publish(channel, type, data) {
  const set = channels.get(channel);
  if (!set) return;
  const msg = JSON.stringify({ type, data });
  for (const ws of set) if (ws.readyState === 1) ws.send(msg);
}

export async function canViewOrder(user, orderId) {
  const { rows } = await query('SELECT customer_id, courier_id FROM orders WHERE id = $1', [orderId]);
  const o = rows[0];
  if (!o) return false;
  if (user.role === 'ADMIN') return true;
  if (user.role === 'CUSTOMER') return o.customer_id === user.id;
  if (user.role === 'COURIER') return o.courier_id === user.id;
  return false;
}

export function attachRealtime(server) {
  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 });

  wss.on('connection', async (ws, req) => {
    ws.channels = new Set();
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });

    // Token: "Sec-WebSocket-Protocol" yerinə Authorization başlığı və ya ?token=
    const url = new URL(req.url, 'http://x');
    const auth = req.headers.authorization?.replace(/^Bearer /, '') || url.searchParams.get('token');
    try {
      ws.user = await userFromToken(auth || '');
    } catch {
      ws.close(4401, 'unauthorized');
      return;
    }
    join(ws, `user:${ws.user.id}`);
    if (ws.user.role === 'ADMIN') join(ws, 'role:ADMIN');
    ws.send(JSON.stringify({ type: 'hello', data: { userId: ws.user.id, role: ws.user.role } }));

    ws.on('message', async (raw) => {
      let msg;
      try { msg = JSON.parse(raw.toString()); } catch { return; }
      if (msg.type === 'subscribe_order' && Number.isInteger(msg.orderId)) {
        if (await canViewOrder(ws.user, msg.orderId)) join(ws, `order:${msg.orderId}`);
        else ws.send(JSON.stringify({ type: 'error', data: { message: 'İcazə yoxdur' } }));
      } else if (msg.type === 'unsubscribe_order' && Number.isInteger(msg.orderId)) {
        leave(ws, `order:${msg.orderId}`);
      } else if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong' }));
      }
    });

    ws.on('close', () => { for (const ch of [...ws.channels]) leave(ws, ch); });
  });

  const interval = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) { ws.terminate(); continue; }
      ws.isAlive = false;
      ws.ping();
    }
  }, 30000);
  wss.on('close', () => clearInterval(interval));
  return wss;
}
