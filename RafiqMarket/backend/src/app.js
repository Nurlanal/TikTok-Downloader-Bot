import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { config } from './config.js';
import { authenticate, requireRole } from './auth.js';
import { ApiError } from './lib/errors.js';
import authRoutes from './routes/auth.js';
import meRoutes from './routes/me.js';
import catalogRoutes from './routes/catalog.js';
import orderRoutes from './routes/orders.js';
import courierRoutes from './routes/courier.js';
import adminRoutes from './routes/admin.js';
import paymentRoutes from './routes/payments.js';

export function createApp() {
  const app = express();
  app.set('trust proxy', 1); // HTTPS reverse proxy (Caddy/Nginx) arxasında
  app.disable('x-powered-by');
  app.use(helmet());

  // Production-da HTTPS məcburidir (proxy X-Forwarded-Proto göndərir)
  if (config.isProd) {
    app.use((req, res, next) => (req.secure ? next() : res.status(403).json({ code: 'HTTPS_REQUIRED', message: 'Yalnız HTTPS' })));
  }

  app.use('/api/payments', paymentRoutes); // callback urlencoded gəlir – JSON parserdən əvvəl
  app.use(express.json({ limit: '256kb' }));
  app.use('/api', rateLimit({ windowMs: 60_000, limit: 300, standardHeaders: true, legacyHeaders: false }));

  app.get('/api/health', (_req, res) => res.json({ ok: true }));
  app.use('/uploads', express.static(config.uploadDir, { maxAge: '7d', fallthrough: false }));

  app.use('/api/auth', authRoutes);
  // Aşağıdakıların hamısı autentifikasiya tələb edir
  app.use('/api/me', authenticate, meRoutes);
  app.use('/api/catalog', authenticate, catalogRoutes);
  app.use('/api/customer', authenticate, requireRole('CUSTOMER'), orderRoutes);
  app.use('/api/courier', authenticate, requireRole('COURIER'), courierRoutes);
  app.use('/api/admin', authenticate, requireRole('ADMIN'), adminRoutes);

  app.use((_req, res) => res.status(404).json({ code: 'NOT_FOUND', message: 'Tapılmadı' }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    if (err instanceof ApiError) return res.status(err.status).json({ code: err.code, message: err.message, details: err.details });
    if (err?.code === 'LIMIT_FILE_SIZE') return res.status(400).json({ code: 'FILE_TOO_LARGE', message: 'Şəkil 5 MB-dan böyük ola bilməz' });
    if (err?.type === 'entity.parse.failed') return res.status(400).json({ code: 'BAD_JSON', message: 'Yanlış JSON' });
    console.error(err);
    res.status(500).json({ code: 'INTERNAL', message: 'Server xətası. Bir az sonra yenidən cəhd edin.' });
  });
  return app;
}
