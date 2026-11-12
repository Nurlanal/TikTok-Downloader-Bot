// Android DTO müqavilə testləri üçün real server cavablarını JSON fayllarına yazır.
// İstifadə: node test/capture-fixtures.mjs <çıxış qovluğu>
import fs from 'node:fs';
import path from 'node:path';
import { WebSocket } from 'ws';
const { api, login, pool, query, resetDb, startServer, ZONE, INSIDE } = await import('./helpers.js');

const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const save = (name, body) => fs.writeFileSync(path.join(out, name + '.json'), JSON.stringify(body, null, 2));

await resetDb();
const srv = await startServer();
const call = api(srv.base);
await query(`INSERT INTO users (phone, name, role) VALUES ('+994501000001','Rafiq','ADMIN')`);
const admin = await login(call, '+994501000001');
const customer = await login(call, '+994551000002', 'Aysel');
save('auth', customer);
await call('POST', '/api/admin/couriers', { phone: '+994501000004', name: 'Elvin' }, admin.accessToken);
const courier = await login(call, '+994501000004');
const cat = (await call('POST', '/api/admin/categories', { name: 'Süd', icon: '🥛' }, admin.accessToken)).body;
await call('POST', '/api/admin/banners', { title: 'Endirim', categoryId: cat.id }, admin.accessToken);
const p = (await call('POST', '/api/admin/products', { name: 'Süd 1L', categoryId: cat.id, price: 1.8, oldPrice: 2.2, stock: 50, isNew: true, barcode: '123' }, admin.accessToken)).body;
await call('PUT', '/api/admin/settings', { deliveryFee: '1.50', minOrderAmount: '5', freeDeliveryThreshold: '30', openTime: '00:00', closeTime: '00:00', ordersEnabled: true }, admin.accessToken);
save('settings', (await call('PUT', '/api/admin/settings/zone', { polygon: ZONE }, admin.accessToken)).body);
save('home', (await call('GET', '/api/catalog/home', null, customer.accessToken)).body);
save('products', (await call('GET', '/api/catalog/products?q=s', null, customer.accessToken)).body);
save('quote', (await call('POST', '/api/customer/orders/quote', { items: [{ productId: p.id, quantity: 5 }] }, customer.accessToken)).body);
save('zone', (await call('GET', '/api/customer/delivery-zone', null, customer.accessToken)).body);
save('zone_check', (await call('POST', '/api/customer/delivery-zone/check', INSIDE, customer.accessToken)).body);
const order = (await call('POST', '/api/customer/orders', { items: [{ productId: p.id, quantity: 5 }], street: 'Mərkəz', house: '1', contactPhone: '0501112233', paymentMethod: 'CASH', ...INSIDE }, customer.accessToken)).body;
save('order_created', order);

const ws = new WebSocket(srv.base.replace('http', 'ws') + '/ws?token=' + customer.accessToken);
const events = [];
ws.on('message', (m) => events.push(JSON.parse(m.toString())));
await new Promise((r) => ws.on('open', r));
ws.send(JSON.stringify({ type: 'subscribe_order', orderId: order.id }));
await new Promise((r) => setTimeout(r, 100));

await call('POST', `/api/admin/orders/${order.id}/status`, { status: 'ACCEPTED' }, admin.accessToken);
await call('POST', `/api/admin/orders/${order.id}/assign`, { courierId: courier.user.id }, admin.accessToken);
await call('POST', `/api/courier/orders/${order.id}/accept`, {}, courier.accessToken);
await call('POST', `/api/courier/orders/${order.id}/status`, { status: 'PICKED_UP' }, courier.accessToken);
await call('POST', '/api/courier/location', { lat: 40.004, lng: 49.004, accuracy: 5, heading: 12.5, speed: 3 }, courier.accessToken);
await new Promise((r) => setTimeout(r, 150));
save('tracking', (await call('GET', `/api/customer/orders/${order.id}/tracking`, null, customer.accessToken)).body);
save('order_detail', (await call('GET', `/api/customer/orders/${order.id}`, null, customer.accessToken)).body);
save('my_orders', (await call('GET', '/api/customer/orders', null, customer.accessToken)).body);
save('courier_orders', (await call('GET', '/api/courier/orders?tab=active', null, courier.accessToken)).body);
save('courier_stats', (await call('GET', '/api/courier/stats', null, courier.accessToken)).body);
save('admin_stats', (await call('GET', '/api/admin/stats', null, admin.accessToken)).body);
save('admin_counts', (await call('GET', '/api/admin/orders/counts', null, admin.accessToken)).body);
save('admin_orders', (await call('GET', '/api/admin/orders', null, admin.accessToken)).body);
save('admin_couriers', (await call('GET', '/api/admin/couriers', null, admin.accessToken)).body);
save('admin_customers', (await call('GET', '/api/admin/customers', null, admin.accessToken)).body);
save('admin_user', (await call('GET', `/api/admin/users/${customer.user.id}`, null, admin.accessToken)).body);
save('admin_categories', (await call('GET', '/api/admin/categories', null, admin.accessToken)).body);
save('admin_products', (await call('GET', '/api/admin/products', null, admin.accessToken)).body);
save('notifications', (await call('GET', '/api/me/notifications', null, customer.accessToken)).body);
save('error', (await call('POST', '/api/customer/orders', { items: [{ productId: p.id, quantity: 999 }], street: 'Mərkəz', house: '1', contactPhone: '0501112233', paymentMethod: 'CASH', ...INSIDE }, customer.accessToken)).body);
save('ws_events', events);
ws.close();
await srv.close();
await pool.end();
console.log('fixtures:', fs.readdirSync(out).length);
