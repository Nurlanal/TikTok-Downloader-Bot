import 'dotenv/config';

function required(name, fallback) {
  const v = process.env[name] ?? fallback;
  if (v === undefined || v === '') throw new Error(`Mühit dəyişəni tələb olunur: ${name}`);
  return v;
}

const env = process.env.NODE_ENV || 'development';

export const config = {
  env,
  isProd: env === 'production',
  port: Number(process.env.PORT || 8080),
  publicBaseUrl: (process.env.PUBLIC_BASE_URL || 'http://localhost:8080').replace(/\/$/, ''),
  databaseUrl: required('DATABASE_URL', env === 'test' ? 'postgres://rafiq:rafiq@localhost:5432/rafiq_test' : undefined),
  jwtSecret: required('JWT_SECRET', env === 'production' ? undefined : 'dev-only-secret-dev-only-secret-0000'),
  otpPepper: required('OTP_PEPPER', env === 'production' ? undefined : 'dev-only-pepper'),
  accessTokenTtlSec: 60 * 60,
  refreshTokenTtlDays: 60,
  otpTtlSec: 5 * 60,
  otpMaxAttempts: 5,
  otpMaxPerHour: 5,
  timezone: process.env.TZ || 'Asia/Baku',
  sms: {
    provider: process.env.SMS_PROVIDER || (env === 'production' ? 'twilio' : 'console'),
    twilioSid: process.env.TWILIO_ACCOUNT_SID || '',
    twilioToken: process.env.TWILIO_AUTH_TOKEN || '',
    twilioFrom: process.env.TWILIO_FROM || '',
  },
  epoint: {
    publicKey: process.env.EPOINT_PUBLIC_KEY || '',
    privateKey: process.env.EPOINT_PRIVATE_KEY || '',
    apiBase: (process.env.EPOINT_API_BASE || 'https://epoint.az/api/1').replace(/\/$/, ''),
  },
  firebaseServiceAccountFile: process.env.FIREBASE_SERVICE_ACCOUNT_FILE || '',
  uploadDir: process.env.UPLOAD_DIR || new URL('../uploads/', import.meta.url).pathname,
  pendingPaymentTimeoutMin: 30,
};

if (config.isProd) {
  if (config.jwtSecret.length < 32) throw new Error('JWT_SECRET ən azı 32 simvol olmalıdır');
  if (config.sms.provider === 'console') throw new Error('Production rejimində SMS_PROVIDER=console qadağandır');
  if (config.sms.provider === 'twilio' && !(config.sms.twilioSid && config.sms.twilioToken && config.sms.twilioFrom)) {
    throw new Error('SMS üçün TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN və TWILIO_FROM təyin edilməlidir');
  }
  if (!config.publicBaseUrl.startsWith('https://')) throw new Error('Production rejimində PUBLIC_BASE_URL https:// ilə başlamalıdır');
}
