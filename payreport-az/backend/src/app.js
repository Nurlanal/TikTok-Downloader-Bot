'use strict';
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const config = require('./config');
const routes = require('./routes');

function createApp() {
  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', 1);
  app.use(helmet());
  app.use(express.json({ limit: '2mb' }));
  app.use('/api', rateLimit({ windowMs: 60 * 1000, limit: 300, standardHeaders: 'draft-7', legacyHeaders: false }));
  app.use('/api', routes);
  app.use((req, res) => res.status(404).json({ error: 'Tapılmadı' }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    const status = err.status || err.statusCode || 500;
    if (status >= 500) console.error('[api]', err);
    res.status(status).json({ error: status >= 500 ? 'Server xətası' : err.message });
  });
  return app;
}

module.exports = { createApp };
