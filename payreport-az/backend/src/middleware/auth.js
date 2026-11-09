'use strict';
const db = require('../db');
const { sha256 } = require('../crypto');

/** Bearer token autentifikasiyası. Token bazada yalnız SHA-256 heşi kimi saxlanılır. */
async function requireAuth(req, res, next) {
  try {
    const header = req.get('authorization') || '';
    const m = /^Bearer\s+([A-Za-z0-9_\-]{20,})$/.exec(header);
    if (!m) return res.status(401).json({ error: 'Avtorizasiya tələb olunur' });
    const rows = await db.query(
      `SELECT t.id AS token_id, u.id, u.username, u.role
         FROM api_tokens t JOIN users u ON u.id = t.user_id
        WHERE t.token_hash = ? AND t.revoked = 0 AND t.expires_at > CURRENT_TIMESTAMP(3) AND u.is_active = 1`,
      [sha256(m[1])]
    );
    if (!rows.length) return res.status(401).json({ error: 'Token etibarsızdır və ya vaxtı bitib' });
    req.user = rows[0];
    db.query('UPDATE api_tokens SET last_used_at = CURRENT_TIMESTAMP(3) WHERE id = ?', [rows[0].token_id]).catch(() => {});
    return next();
  } catch (e) {
    return next(e);
  }
}

function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== 'admin') return res.status(403).json({ error: 'Bu əməliyyat üçün admin hüququ lazımdır' });
  return next();
}

module.exports = { requireAuth, requireAdmin };
