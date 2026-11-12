import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';

process.env.EPOINT_PUBLIC_KEY = 'i000000001';
process.env.EPOINT_PRIVATE_KEY = 'test-private-key';
const { api, login, pool, query, resetDb, startServer, ZONE, INSIDE } = await import('./helpers.js');
const epoint = await import('../src/services/epoint.js');

let srv, call, customer, admin, productId;

before(async () => {
  await resetDb();
  srv = await startServer();
  call = api(srv.base);
  await query(`INSERT INTO users (phone, name, role) VALUES ('+994501000001','Rafiq','ADMIN')`);
  await query(`UPDATE settings SET delivery_zone=$1::jsonb`, [JSON.stringify(ZONE)]);
  admin = await login(call, '+994501000001');
  customer = await login(call, '+994551000002');
  const p = await call('POST', '/api/admin/products', { name: 'Ayran', price: 2, stock: 100 }, admin.accessToken);
  productId = p.body.id;
});
after(async () => { await srv.close(); await pool.end(); });

async function onlineOrder() {
  const r = await call('POST', '/api/customer/orders', {
    items: [{ productId, quantity: 5 }], street: 'Küçə', house: '1', contactPhone: '0501112233', paymentMethod: 'ONLINE', ...INSIDE,
  }, customer.accessToken);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.status, 'PENDING_PAYMENT');
  // Provayderə real sorğu göndərmədən ödəniş qeydini simulyasiya edirik (yalnız testdə)
  const pid = `${r.body.orderNumber}-t`;
  await query(`INSERT INTO payments (order_id, provider, provider_order_id, amount) VALUES ($1,'epoint',$2,$3)`, [r.body.id, pid, r.body.total]);
  return { order: r.body, pid };
}

function callback(payload, sig) {
  const data = epoint.encode(payload);
  return fetch(srv.base + '/api/payments/epoint/callback', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ data, signature: sig ?? epoint.sign(data) }),
  });
}

test('imza formatı: base64(sha1(private+data+private))', () => {
  assert.equal(epoint.sign('abc', 'k'), 'tbVgIzQpR27v2Yj1vCS6kM4b2x8=');
});

test('PENDING_PAYMENT sifariş admin siyahısında görünmür', async () => {
  const { order } = await onlineOrder();
  const list = await call('GET', '/api/admin/orders', null, admin.accessToken);
  assert.ok(!list.body.some((o) => o.id === order.id));
});

test('saxta imzalı callback rədd edilir, sifariş ödənilmiş sayılmır', async () => {
  const { order, pid } = await onlineOrder();
  const r = await callback({ order_id: pid, status: 'success', amount: order.total }, 'AAAA');
  assert.equal(r.status, 400);
  const o = await call('GET', `/api/customer/orders/${order.id}`, null, customer.accessToken);
  assert.equal(o.body.status, 'PENDING_PAYMENT');
});

test('məbləği uyğun gəlməyən callback uğursuz sayılır', async () => {
  const { order, pid } = await onlineOrder();
  await callback({ order_id: pid, status: 'success', amount: '0.01' });
  const o = await call('GET', `/api/customer/orders/${order.id}`, null, customer.accessToken);
  assert.equal(o.body.status, 'CANCELLED');
  assert.equal(o.body.paymentStatus, 'FAILED');
});

test('düzgün imzalı uğurlu callback sifarişi marketə göndərir', async () => {
  const { order, pid } = await onlineOrder();
  const r = await callback({ order_id: pid, status: 'success', amount: order.total, transaction: 'te000123' });
  assert.equal(r.status, 200);
  const o = await call('GET', `/api/customer/orders/${order.id}`, null, customer.accessToken);
  assert.equal(o.body.status, 'PLACED');
  assert.equal(o.body.paymentStatus, 'PAID');
  // Təkrar callback idempotentdir
  await callback({ order_id: pid, status: 'failed' });
  assert.equal((await call('GET', `/api/customer/orders/${order.id}`, null, customer.accessToken)).body.status, 'PLACED');
  const notifs = await call('GET', '/api/me/notifications', null, admin.accessToken);
  assert.ok(notifs.body.some((n) => n.title === 'Yeni sifariş daxil oldu'));
});

test('uğursuz ödəniş stoku qaytarır və admini xəbərdar edir', async () => {
  const before = (await query('SELECT stock FROM products WHERE id=$1', [productId])).rows[0].stock;
  const { pid } = await onlineOrder();
  await callback({ order_id: pid, status: 'failed', message: 'Insufficient funds' });
  const after = (await query('SELECT stock FROM products WHERE id=$1', [productId])).rows[0].stock;
  assert.equal(after, before);
  const notifs = await call('GET', '/api/me/notifications', null, admin.accessToken);
  assert.ok(notifs.body.some((n) => n.title === 'Ödəniş uğursuz oldu'));
});
