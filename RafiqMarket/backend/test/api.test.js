import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { api, login, pool, query, resetDb, startServer, ZONE, INSIDE, OUTSIDE } from './helpers.js';

let srv, call, admin, customer, customer2, courier;
let productId, product2Id;

const orderBody = (items, extra = {}) => ({
  items, street: 'Mərkəzi küçə', house: '12', addressNote: 'Qapı yaşıl', contactPhone: '+994501112233',
  paymentMethod: 'CASH', ...INSIDE, ...extra,
});

before(async () => {
  await resetDb();
  srv = await startServer();
  call = api(srv.base);
  // İlk admin – server skripti ilə (create-admin) yaradılır
  await query(`INSERT INTO users (phone, name, role) VALUES ('+994501000001','Rafiq','ADMIN')`);
  admin = await login(call, '+994501000001');
  customer = await login(call, '0551000002', 'Aysel');
  customer2 = await login(call, '+994 70 100 00 03', 'Murad');
});

after(async () => { await srv.close(); await pool.end(); });

test('yeni istifadəçi avtomatik CUSTOMER olur, admin rolu serverdən gəlir', async () => {
  assert.equal(customer.user.role, 'CUSTOMER');
  assert.equal(customer.user.phone, '+994551000002');
  assert.equal(admin.user.role, 'ADMIN');
  const me = await call('GET', '/api/me', null, customer.accessToken);
  assert.equal(me.body.role, 'CUSTOMER');
});

test('yanlış OTP kodu rədd edilir və cəhdlər məhduddur', async () => {
  await call('POST', '/api/auth/otp/request', { phone: '+994509999999' });
  for (let i = 0; i < 5; i++) {
    const r = await call('POST', '/api/auth/otp/verify', { phone: '+994509999999', code: '000000' });
    assert.equal(r.status, 400);
  }
  const r = await call('POST', '/api/auth/otp/verify', { phone: '+994509999999', code: '000000' });
  assert.equal(r.body.code, 'OTP_LOCKED');
});

test('rol icazələri serverdə yoxlanılır', async () => {
  assert.equal((await call('GET', '/api/admin/stats', null, customer.accessToken)).status, 403);
  assert.equal((await call('GET', '/api/courier/orders', null, customer.accessToken)).status, 403);
  assert.equal((await call('GET', '/api/admin/stats')).status, 401);
  assert.equal((await call('GET', '/api/admin/stats', null, admin.accessToken)).status, 200);
});

test('admin kuryer əlavə edir, kuryer eyni girişlə daxil olur və kuryer rolunu alır', async () => {
  const r = await call('POST', '/api/admin/couriers', { phone: '+994501000004', name: 'Elvin' }, admin.accessToken);
  assert.equal(r.status, 201);
  courier = await login(call, '+994501000004');
  assert.equal(courier.user.role, 'COURIER');
  assert.equal((await call('GET', '/api/admin/orders', null, courier.accessToken)).status, 403);
});

test('admin məhsul və kateqoriya yaradır', async () => {
  const c = await call('POST', '/api/admin/categories', { name: 'Süd məhsulları', icon: '🥛' }, admin.accessToken);
  assert.equal(c.status, 201);
  const p = await call('POST', '/api/admin/products', { name: 'Süd 1L', categoryId: c.body.id, price: 1.8, unit: 'ədəd', stock: 5, barcode: '4760000000011', isNew: true }, admin.accessToken);
  assert.equal(p.status, 201, JSON.stringify(p.body));
  productId = p.body.id;
  const p2 = await call('POST', '/api/admin/products', { name: 'Çörək', price: '0.70', oldPrice: '0.90', stock: 100 }, admin.accessToken);
  product2Id = p2.body.id;
  const dup = await call('POST', '/api/admin/products', { name: 'X', price: 1, stock: 1, barcode: '4760000000011' }, admin.accessToken);
  assert.equal(dup.body.code, 'DUPLICATE_BARCODE');
  const home = await call('GET', '/api/catalog/home', null, customer.accessToken);
  assert.equal(home.body.newProducts.length, 1);
  assert.equal(home.body.discounted.length, 1);
  const search = await call('GET', '/api/catalog/products?q=s%C3%BCd', null, customer.accessToken);
  assert.equal(search.body.length, 1);
});

test('zona təyin edilməyibsə sifariş qəbul edilmir', async () => {
  const r = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }]), customer.accessToken);
  assert.equal(r.body.code, 'ZONE_NOT_CONFIGURED');
});

test('Xıdırlı zonasından kənar ünvan rədd edilir', async () => {
  const z = await call('PUT', '/api/admin/settings/zone', { polygon: ZONE }, admin.accessToken);
  assert.equal(z.status, 200);
  const r = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }], OUTSIDE), customer.accessToken);
  assert.equal(r.status, 400);
  assert.equal(r.body.message, 'Rafiq Market hazırda yalnız Xıdırlı kəndi daxilində çatdırılma həyata keçirir.');
  const chk = await call('POST', '/api/customer/delivery-zone/check', INSIDE, customer.accessToken);
  assert.equal(chk.body.inside, true);
});

test('minimum məbləğ və server qiyməti tətbiq edilir', async () => {
  const r = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 1 }]), customer.accessToken);
  assert.equal(r.body.code, 'BELOW_MINIMUM');
  const q = await call('POST', '/api/customer/orders/quote', { items: [{ productId: product2Id, quantity: 10 }] }, customer.accessToken);
  assert.equal(q.body.subtotal, '7.00');
  assert.equal(q.body.deliveryFee, '1.50');
  assert.equal(q.body.total, '8.50');
});

test('paralel sifarişlərdə stok artıq satılmır', async () => {
  // Stok 5: 5 müştəri sorğusu eyni anda 2 ədəd + çörək istəyir -> yalnız 2 uğurlu
  const reqs = Array.from({ length: 5 }, (_, i) => call('POST', '/api/customer/orders',
    orderBody([{ productId, quantity: 2 }, { productId: product2Id, quantity: 8 }]), (i % 2 ? customer2 : customer).accessToken));
  const res = await Promise.all(reqs);
  const ok = res.filter((r) => r.status === 201);
  const fail = res.filter((r) => r.status === 409);
  assert.equal(ok.length, 2, JSON.stringify(res.map((r) => r.body.code || r.status)));
  assert.equal(fail.length, 3);
  assert.ok(fail.every((r) => r.body.code === 'OUT_OF_STOCK'));
  const { rows } = await query('SELECT stock FROM products WHERE id=$1', [productId]);
  assert.equal(rows[0].stock, 1);
  const { rows: r2 } = await query('SELECT stock FROM products WHERE id=$1', [product2Id]);
  assert.equal(r2[0].stock, 100 - 16); // uğursuz sifarişlərin çörəyi geri qaytarılıb (rollback)
});

test('tam sifariş axını: admin → kuryer → GPS → nağd → çatdırıldı', async () => {
  const created = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }]), customer.accessToken);
  assert.equal(created.status, 201);
  const id = created.body.id;
  assert.match(created.body.orderNumber, /^RM-\d+$/);
  assert.equal(created.body.status, 'PLACED');

  // Müştəri WebSocket ilə real vaxt yenilənmələrini dinləyir
  const ws = new WebSocket(srv.base.replace('http', 'ws') + '/ws?token=' + customer.accessToken);
  const events = [];
  ws.on('message', (m) => events.push(JSON.parse(m.toString())));
  await new Promise((r) => ws.on('open', r));
  ws.send(JSON.stringify({ type: 'subscribe_order', orderId: id }));
  await new Promise((r) => setTimeout(r, 100));

  // Başqa müştəri bu sifarişi görə bilməz
  assert.equal((await call('GET', `/api/customer/orders/${id}`, null, customer2.accessToken)).status, 404);
  assert.equal((await call('GET', `/api/customer/orders/${id}/tracking`, null, customer2.accessToken)).status, 404);

  // Kuryer ACCEPTED-dən əvvəl status dəyişə bilməz; admin addımları atlaya bilməz
  assert.equal((await call('POST', `/api/admin/orders/${id}/status`, { status: 'PREPARING' }, admin.accessToken)).body.code, 'BAD_TRANSITION');
  assert.equal((await call('POST', `/api/admin/orders/${id}/status`, { status: 'ACCEPTED' }, admin.accessToken)).status, 200);
  assert.equal((await call('POST', `/api/admin/orders/${id}/status`, { status: 'PREPARING' }, admin.accessToken)).status, 200);
  const as = await call('POST', `/api/admin/orders/${id}/assign`, { courierId: courier.user.id }, admin.accessToken);
  assert.equal(as.body.status, 'COURIER_ASSIGNED');

  // GPS hələ qəbul edilmir (çatdırılma başlamayıb)
  assert.equal((await call('POST', '/api/courier/location', { lat: 40.001, lng: 49.001 }, courier.accessToken)).body.code, 'NO_ACTIVE_DELIVERY');

  const newTab = await call('GET', '/api/courier/orders?tab=new', null, courier.accessToken);
  assert.equal(newTab.body.length, 1);
  assert.equal((await call('POST', `/api/courier/orders/${id}/status`, { status: 'PICKED_UP' }, courier.accessToken)).body.code, 'NOT_ACCEPTED');
  assert.equal((await call('POST', `/api/courier/orders/${id}/accept`, {}, courier.accessToken)).status, 200);
  assert.equal((await call('POST', `/api/courier/orders/${id}/status`, { status: 'PICKED_UP' }, courier.accessToken)).status, 200);
  assert.equal((await call('POST', `/api/courier/orders/${id}/status`, { status: 'ON_THE_WAY' }, courier.accessToken)).status, 200);

  const loc = await call('POST', '/api/courier/location', { lat: 40.002, lng: 49.003, heading: 90 }, courier.accessToken);
  assert.equal(loc.status, 200);
  const tr = await call('GET', `/api/customer/orders/${id}/tracking`, null, customer.accessToken);
  assert.equal(tr.body.active, true);
  assert.equal(tr.body.location.lat, 40.002);
  assert.equal(tr.body.courier.name, 'Elvin');
  await new Promise((r) => setTimeout(r, 100));
  assert.ok(events.some((e) => e.type === 'courier.location' && e.data.orderId === id));
  assert.ok(events.some((e) => e.type === 'order.updated' && e.data.status === 'ON_THE_WAY'));

  assert.equal((await call('POST', `/api/courier/orders/${id}/status`, { status: 'ARRIVED' }, courier.accessToken)).status, 200);
  assert.equal((await call('POST', `/api/courier/orders/${id}/status`, { status: 'DELIVERED' }, courier.accessToken)).body.code, 'CASH_NOT_CONFIRMED');
  assert.equal((await call('POST', `/api/courier/orders/${id}/confirm-cash`, {}, courier.accessToken)).body.paymentStatus, 'PAID');
  const done = await call('POST', `/api/courier/orders/${id}/status`, { status: 'DELIVERED' }, courier.accessToken);
  assert.equal(done.body.status, 'DELIVERED');

  // Çatdırıldıqdan sonra izləmə dayanır və GPS qəbul edilmir
  const tr2 = await call('GET', `/api/customer/orders/${id}/tracking`, null, customer.accessToken);
  assert.equal(tr2.body.active, false);
  assert.equal(tr2.body.location, null);
  assert.equal((await call('POST', '/api/courier/location', { lat: 40.002, lng: 49.003 }, courier.accessToken)).status, 409);

  const stats = await call('GET', '/api/courier/stats', null, courier.accessToken);
  assert.equal(stats.body.deliveredToday, 1);
  const adminStats = await call('GET', '/api/admin/stats', null, admin.accessToken);
  assert.equal(adminStats.body.today.completed, 1);
  assert.equal(adminStats.body.topProducts[0].name, 'Çörək');
  const notifs = await call('GET', '/api/me/notifications', null, customer.accessToken);
  assert.ok(notifs.body.some((n) => n.title === 'Sifarişiniz çatdırıldı'));
  ws.close();
});

test('müştəri ləğvi stoku qaytarır; qəbuldan sonra ləğv edə bilmir', async () => {
  const before = (await query('SELECT stock FROM products WHERE id=$1', [product2Id])).rows[0].stock;
  const o = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }]), customer.accessToken);
  const c = await call('POST', `/api/customer/orders/${o.body.id}/cancel`, {}, customer.accessToken);
  assert.equal(c.body.status, 'CANCELLED');
  const after = (await query('SELECT stock FROM products WHERE id=$1', [product2Id])).rows[0].stock;
  assert.equal(after, before);
  const o2 = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }]), customer.accessToken);
  await call('POST', `/api/admin/orders/${o2.body.id}/status`, { status: 'ACCEPTED' }, admin.accessToken);
  assert.equal((await call('POST', `/api/customer/orders/${o2.body.id}/cancel`, {}, customer.accessToken)).body.code, 'CANNOT_CANCEL');
});

test('deaktiv edilmiş hesab dərhal bloklanır', async () => {
  await call('PATCH', `/api/admin/users/${customer2.user.id}/active`, { isActive: false }, admin.accessToken);
  assert.equal((await call('GET', '/api/me', null, customer2.accessToken)).status, 403);
  assert.equal((await call('POST', '/api/auth/refresh', { refreshToken: customer2.refreshToken })).status, 401);
});

test('refresh token rotasiyası: köhnə token təkrar işləmir', async () => {
  const r1 = await call('POST', '/api/auth/refresh', { refreshToken: customer.refreshToken });
  assert.equal(r1.status, 200);
  const r2 = await call('POST', '/api/auth/refresh', { refreshToken: customer.refreshToken });
  assert.equal(r2.status, 401);
  customer = r1.body;
});

test('onlayn ödəniş açarları olmadan ONLINE sifariş qəbul edilmir (saxta ödəniş yoxdur)', async () => {
  const r = await call('POST', '/api/customer/orders', orderBody([{ productId: product2Id, quantity: 10 }], { paymentMethod: 'ONLINE' }), customer.accessToken);
  assert.equal(r.body.code, 'ONLINE_PAYMENT_UNAVAILABLE');
});
