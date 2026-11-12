import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import crypto from 'node:crypto';
import { config } from '../config.js';
import { query, tx } from '../db.js';
import { signAccessToken, authenticate } from '../auth.js';
import { ah, badRequest, unauthorized, ApiError } from '../lib/errors.js';
import { normalizePhone, randomToken, sha256 } from '../lib/util.js';
import { parse, z } from '../lib/validate.js';
import { sendSms } from '../services/sms.js';

const r = Router();
const limiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 30, standardHeaders: true, legacyHeaders: false });

const otpHash = (phone, code) => sha256(`${config.otpPepper}:${phone}:${code}`);

export function serializeUser(u) {
  return { id: u.id, phone: u.phone, name: u.name, role: u.role, isActive: u.is_active };
}

async function issueTokens(user) {
  const refresh = randomToken();
  await query(
    `INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1,$2, now() + make_interval(days => $3))`,
    [user.id, sha256(refresh), config.refreshTokenTtlDays]);
  return { accessToken: signAccessToken(user), refreshToken: refresh, expiresIn: config.accessTokenTtlSec, user: serializeUser(user) };
}

r.post('/otp/request', limiter, ah(async (req, res) => {
  const { phone: raw } = parse(z.object({ phone: z.string() }), req.body);
  const phone = normalizePhone(raw);
  const recent = await query(`SELECT count(*)::int AS n FROM otp_codes WHERE phone=$1 AND created_at > now() - interval '1 hour'`, [phone]);
  if (recent.rows[0].n >= config.otpMaxPerHour) throw new ApiError(429, 'TOO_MANY', 'Çox sayda cəhd. Bir saat sonra yenidən yoxlayın.');
  const last = await query(`SELECT created_at FROM otp_codes WHERE phone=$1 ORDER BY created_at DESC LIMIT 1`, [phone]);
  if (last.rows[0] && Date.now() - new Date(last.rows[0].created_at).getTime() < 60_000) {
    throw new ApiError(429, 'WAIT', 'Yeni kod üçün 1 dəqiqə gözləyin.');
  }
  const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
  await query(`INSERT INTO otp_codes (phone, code_hash, expires_at) VALUES ($1,$2, now() + make_interval(secs => $3))`,
    [phone, otpHash(phone, code), config.otpTtlSec]);
  await sendSms(phone, `Rafiq Market təsdiq kodu: ${code}. Kodu heç kimə deməyin.`);
  const exists = await query('SELECT 1 FROM users WHERE phone=$1', [phone]);
  res.json({ phone, isNewUser: !exists.rows[0], expiresIn: config.otpTtlSec });
}));

r.post('/otp/verify', limiter, ah(async (req, res) => {
  const body = parse(z.object({ phone: z.string(), code: z.string().regex(/^\d{6}$/, '6 rəqəmli kod'), name: z.string().trim().max(120).optional() }), req.body);
  const phone = normalizePhone(body.phone);
  const user = await tx(async (c) => {
    const { rows } = await c.query(
      `SELECT * FROM otp_codes WHERE phone=$1 AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE`, [phone]);
    const otp = rows[0];
    if (!otp || new Date(otp.expires_at) < new Date()) throw badRequest('Kodun vaxtı bitib. Yeni kod istəyin.', 'OTP_EXPIRED');
    if (otp.attempts >= config.otpMaxAttempts) throw badRequest('Cəhd limiti bitdi. Yeni kod istəyin.', 'OTP_LOCKED');
    const ok = crypto.timingSafeEqual(Buffer.from(otp.code_hash), Buffer.from(otpHash(phone, body.code)));
    if (!ok) {
      await c.query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id=$1', [otp.id]);
      return null;
    }
    await c.query('UPDATE otp_codes SET consumed_at = now() WHERE id=$1', [otp.id]);
    // Yeni istifadəçi HƏMİŞƏ CUSTOMER rolu alır. Kuryer/Admin rolunu yalnız admin təyin edir.
    const ins = await c.query(
      `INSERT INTO users (phone, name, role) VALUES ($1,$2,'CUSTOMER')
       ON CONFLICT (phone) DO UPDATE SET name = CASE WHEN users.name = '' THEN EXCLUDED.name ELSE users.name END, updated_at = now()
       RETURNING *`, [phone, body.name || '']);
    return ins.rows[0];
  });
  if (!user) throw badRequest('Kod yanlışdır.', 'OTP_INVALID');
  if (!user.is_active) throw new ApiError(403, 'FORBIDDEN', 'Hesabınız deaktiv edilib. Market ilə əlaqə saxlayın.');
  res.json(await issueTokens(user));
}));

r.post('/refresh', ah(async (req, res) => {
  const { refreshToken } = parse(z.object({ refreshToken: z.string().min(20) }), req.body);
  const user = await tx(async (c) => {
    const { rows } = await c.query(
      `UPDATE refresh_tokens SET revoked_at = now()
       WHERE token_hash=$1 AND revoked_at IS NULL AND expires_at > now() RETURNING user_id`, [sha256(refreshToken)]);
    if (!rows[0]) throw unauthorized('Sessiyanın vaxtı bitib. Yenidən daxil olun.');
    const u = await c.query('SELECT * FROM users WHERE id=$1', [rows[0].user_id]);
    return u.rows[0];
  });
  if (!user?.is_active) throw new ApiError(403, 'FORBIDDEN', 'Hesabınız deaktiv edilib.');
  res.json(await issueTokens(user));
}));

r.post('/logout', authenticate, ah(async (req, res) => {
  const { refreshToken } = parse(z.object({ refreshToken: z.string().optional() }), req.body || {});
  if (refreshToken) await query('UPDATE refresh_tokens SET revoked_at=now() WHERE token_hash=$1 AND user_id=$2', [sha256(refreshToken), req.user.id]);
  await query('UPDATE users SET fcm_token = NULL WHERE id=$1', [req.user.id]);
  res.json({ ok: true });
}));

export default r;
