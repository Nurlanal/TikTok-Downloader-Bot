import jwt from 'jsonwebtoken';
import { config } from './config.js';
import { query } from './db.js';
import { unauthorized, forbidden } from './lib/errors.js';

export function signAccessToken(user) {
  return jwt.sign({ sub: String(user.id), role: user.role }, config.jwtSecret, {
    expiresIn: config.accessTokenTtlSec,
    issuer: 'rafiq-market',
  });
}

/**
 * Tokeni yoxlayır və istifadəçini HƏR DƏFƏ bazadan oxuyur:
 * rol dəyişikliyi və ya deaktivasiya dərhal qüvvəyə minir.
 */
export async function userFromToken(token) {
  let payload;
  try {
    payload = jwt.verify(token, config.jwtSecret, { issuer: 'rafiq-market' });
  } catch {
    throw unauthorized('Sessiyanın vaxtı bitib. Yenidən daxil olun.');
  }
  const { rows } = await query('SELECT id, phone, name, role, is_active FROM users WHERE id = $1', [Number(payload.sub)]);
  const user = rows[0];
  if (!user) throw unauthorized('İstifadəçi tapılmadı');
  if (!user.is_active) throw forbidden('Hesabınız deaktiv edilib. Market ilə əlaqə saxlayın.');
  return user;
}

export async function authenticate(req, _res, next) {
  try {
    const h = req.headers.authorization || '';
    const m = /^Bearer (.+)$/.exec(h);
    if (!m) throw unauthorized();
    req.user = await userFromToken(m[1]);
    next();
  } catch (e) {
    next(e);
  }
}

/** Server tərəfində rol yoxlaması */
export const requireRole = (...roles) => (req, _res, next) => {
  if (!req.user) return next(unauthorized());
  if (!roles.includes(req.user.role)) return next(forbidden());
  next();
};
