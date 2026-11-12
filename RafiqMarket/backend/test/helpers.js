process.env.NODE_ENV = 'test';
process.env.DATABASE_URL ||= 'postgres://rafiq:rafiq@localhost:5432/rafiq_test';
process.env.TZ = 'Asia/Baku';

import fs from 'node:fs';
import http from 'node:http';

const { pool, query } = await import('../src/db.js');
const { createApp } = await import('../src/app.js');
const { attachRealtime } = await import('../src/realtime.js');
const { devOutbox } = await import('../src/services/sms.js');

export { pool, query, devOutbox };

export async function resetDb() {
  await pool.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
  await pool.query(fs.readFileSync(new URL('../db/schema.sql', import.meta.url), 'utf8'));
  // Testlər üçün 24 saat açıq
  await pool.query(`UPDATE settings SET open_time='00:00', close_time='00:00'`);
}

export async function startServer() {
  const server = http.createServer(createApp());
  const wss = attachRealtime(server);
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { server, wss, base, close: () => new Promise((r) => { for (const c of wss.clients) c.terminate(); wss.close(); server.closeAllConnections(); server.close(r); }) };
}

export function api(base) {
  return async (method, path, body, token) => {
    const res = await fetch(base + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let json; try { json = JSON.parse(text); } catch { json = text; }
    return { status: res.status, body: json };
  };
}

/** OTP ilə tam giriş axını */
export async function login(call, phone, name = 'Test') {
  const r1 = await call('POST', '/api/auth/otp/request', { phone });
  if (r1.status !== 200) throw new Error(JSON.stringify(r1));
  const sms = devOutbox.filter((m) => m.phone === r1.body.phone).at(-1);
  const code = /(\d{6})/.exec(sms.text)[1];
  const r2 = await call('POST', '/api/auth/otp/verify', { phone, code, name });
  if (r2.status !== 200) throw new Error(JSON.stringify(r2));
  return r2.body;
}

// Kiçik kvadrat zona (test üçün ixtiyari koordinatlar)
export const ZONE = [
  { lat: 40.000, lng: 49.000 }, { lat: 40.000, lng: 49.010 },
  { lat: 40.010, lng: 49.010 }, { lat: 40.010, lng: 49.000 },
];
export const INSIDE = { lat: 40.005, lng: 49.005 };
export const OUTSIDE = { lat: 40.050, lng: 49.050 };
