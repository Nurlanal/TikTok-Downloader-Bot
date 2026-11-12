import http from 'node:http';
import { config } from './config.js';
import { createApp } from './app.js';
import { attachRealtime } from './realtime.js';
import { expireStale } from './services/payments.js';

const app = createApp();
const server = http.createServer(app);
attachRealtime(server);

server.listen(config.port, () => {
  console.log(`Rafiq Market API: http://0.0.0.0:${config.port} (${config.env})`);
});

// Ödənişi tamamlanmamış sifarişlərin stokunu qaytarmaq üçün dövri yoxlama
setInterval(() => expireStale(config.pendingPaymentTimeoutMin).catch((e) => console.error('[expire]', e)), 60_000);
