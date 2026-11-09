'use strict';
require('dotenv').config();

function required(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Mühit dəyişəni təyin edilməyib: ${name} (.env.example faylına baxın)`);
  return v;
}

function int(name, def) {
  const v = process.env[name];
  return v === undefined || v === '' ? def : parseInt(v, 10);
}

const config = {
  port: int('PORT', 8080),
  host: process.env.HOST || '0.0.0.0',
  trustProxy: process.env.TRUST_PROXY === '1',
  tls: {
    certFile: process.env.TLS_CERT_FILE || '',
    keyFile: process.env.TLS_KEY_FILE || '',
  },
  db: {
    host: process.env.DB_HOST || '127.0.0.1',
    port: int('DB_PORT', 3306),
    user: process.env.DB_USER || 'payreport',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'payreport',
  },
  // 32 baytlıq base64 açar: `npm run gen-key`
  credentialsKey: () => {
    const key = Buffer.from(required('CREDENTIALS_KEY'), 'base64');
    if (key.length !== 32) throw new Error('CREDENTIALS_KEY 32 bayt (base64) olmalıdır');
    return key;
  },
  tokenTtlDays: int('TOKEN_TTL_DAYS', 30),
  syncIntervalMinutes: int('SYNC_INTERVAL_MINUTES', 5),
  syncEnabled: process.env.SYNC_ENABLED !== '0',
  ocrEnabled: process.env.OCR_ENABLED === '1',
  ocrLanguages: process.env.OCR_LANGUAGES || 'eng+aze',
  maxMessagesPerRun: int('MAX_MESSAGES_PER_RUN', 2000),
};

module.exports = config;
