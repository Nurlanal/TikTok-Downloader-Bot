'use strict';
const express = require('express');
const rateLimit = require('express-rate-limit');
const db = require('../db');
const config = require('../config');
const { hashPassword, verifyPassword, newToken, sha256, encryptSecret, decryptSecret } = require('../crypto');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const payments = require('../services/payments');
const worker = require('../imap/worker');
const { HttpError } = payments;

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const ipOf = (req) => req.ip;
const VERSION = require('../../package.json').version;

// ---------------------------------------------------------------- Sağlamlıq
router.get('/health', wrap(async (req, res) => {
  let dbOk = false;
  try {
    await db.query('SELECT 1');
    dbOk = true;
  } catch (_) {
    dbOk = false;
  }
  res.status(dbOk ? 200 : 503).json({ status: dbOk ? 'ok' : 'degraded', db: dbOk, version: VERSION, time: new Date().toISOString() });
}));

// ---------------------------------------------------------------- Auth
const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 10, standardHeaders: 'draft-7', legacyHeaders: false,
  message: { error: 'Çox sayda uğursuz cəhd. 15 dəqiqə sonra yenidən cəhd edin.' }, skipSuccessfulRequests: true });

router.post('/auth/login', loginLimiter, wrap(async (req, res) => {
  const { username, password, deviceName } = req.body || {};
  if (!username || !password) throw new HttpError(400, 'İstifadəçi adı və şifrə tələb olunur');
  const rows = await db.query('SELECT * FROM users WHERE username = ? AND is_active = 1', [String(username)]);
  const user = rows[0];
  if (!user || !verifyPassword(String(password), user.password_hash)) {
    await db.query("INSERT INTO audit_log (action, actor, details, ip) VALUES ('LOGIN_FAILED', ?, NULL, ?)", [String(username).slice(0, 64), ipOf(req)]);
    throw new HttpError(401, 'İstifadəçi adı və ya şifrə yanlışdır');
  }
  const token = newToken();
  const expiresAt = new Date(Date.now() + config.tokenTtlDays * 24 * 60 * 60 * 1000);
  await db.query('INSERT INTO api_tokens (user_id, token_hash, device_name, expires_at) VALUES (?,?,?,?)', [
    user.id, sha256(token), deviceName ? String(deviceName).slice(0, 128) : null, expiresAt,
  ]);
  await db.query("INSERT INTO audit_log (action, actor, details, ip) VALUES ('LOGIN', ?, ?, ?)", [
    user.username, JSON.stringify({ deviceName: deviceName || null }), ipOf(req),
  ]);
  res.json({ token, expiresAt: expiresAt.toISOString(), user: { id: user.id, username: user.username, role: user.role } });
}));

router.use(requireAuth);

router.post('/auth/logout', wrap(async (req, res) => {
  await db.query('UPDATE api_tokens SET revoked = 1 WHERE id = ?', [req.user.token_id]);
  res.json({ ok: true });
}));

router.get('/auth/me', (req, res) => res.json({ id: req.user.id, username: req.user.username, role: req.user.role }));

router.post('/auth/change-password', requireAdmin, wrap(async (req, res) => {
  const { currentPassword, newPassword } = req.body || {};
  if (!newPassword || String(newPassword).length < 10) throw new HttpError(400, 'Yeni şifrə ən azı 10 simvol olmalıdır');
  const [u] = await db.query('SELECT * FROM users WHERE id = ?', [req.user.id]);
  if (!verifyPassword(String(currentPassword || ''), u.password_hash)) throw new HttpError(401, 'Cari şifrə yanlışdır');
  await db.query('UPDATE users SET password_hash = ? WHERE id = ?', [hashPassword(String(newPassword)), u.id]);
  await db.query('UPDATE api_tokens SET revoked = 1 WHERE user_id = ? AND id <> ?', [u.id, req.user.token_id]);
  await db.query("INSERT INTO audit_log (action, actor, ip) VALUES ('PASSWORD_CHANGE', ?, ?)", [u.username, ipOf(req)]);
  res.json({ ok: true });
}));

// ---------------------------------------------------------------- Ödənişlər
const STATUSES = ['CONFIRMED', 'PENDING', 'CANCELLED'];

router.get('/payments', wrap(async (req, res) => {
  const { status, phone, from, to, q, updated_since: updatedSince, after_id: afterId, limit, offset } = req.query;
  if (status && !STATUSES.includes(status)) throw new HttpError(400, 'Status düzgün deyil');
  const items = await payments.list({ status, phone, from, to, q, updatedSince, afterId, limit, offset });
  res.json({ items, serverTime: new Date().toISOString() });
}));

router.get('/payments/:id(\\d+)', wrap(async (req, res) => {
  const row = await payments.getById(Number(req.params.id));
  if (!row) throw new HttpError(404, 'Ödəniş tapılmadı');
  res.json(payments.serialize(row));
}));

function parseAmountMinor(body) {
  if (body.amountMinor !== undefined) return Number(body.amountMinor);
  if (body.amount !== undefined) {
    const n = Number(String(body.amount).replace(',', '.'));
    return Number.isFinite(n) ? Math.round(n * 100) : NaN;
  }
  return undefined;
}

router.post('/payments', requireAdmin, wrap(async (req, res) => {
  const b = req.body || {};
  const created = await payments.createManual(
    { phone: b.phone, amountMinor: parseAmountMinor(b), paidAt: b.paidAt, txnId: b.txnId, note: b.note, status: b.status },
    req.user.username,
    ipOf(req)
  );
  res.status(201).json(created);
}));

router.post('/payments/ingest-text', requireAdmin, wrap(async (req, res) => {
  const b = req.body || {};
  const result = await payments.ingestText({ text: b.text, source: b.source, reference: b.reference }, req.user.username, ipOf(req));
  res.status(result.created ? 201 : 200).json(result);
}));

router.patch('/payments/:id(\\d+)', requireAdmin, wrap(async (req, res) => {
  const b = req.body || {};
  const patch = {};
  for (const k of ['phone', 'paidAt', 'txnId', 'note']) if (k in b) patch[k] = b[k];
  const amt = parseAmountMinor(b);
  if (amt !== undefined) patch.amountMinor = amt;
  res.json(await payments.update(Number(req.params.id), patch, req.user.username, ipOf(req)));
}));

router.post('/payments/:id(\\d+)/confirm', requireAdmin, wrap(async (req, res) => {
  res.json(await payments.confirm(Number(req.params.id), req.user.username, ipOf(req)));
}));

router.post('/payments/:id(\\d+)/cancel', requireAdmin, wrap(async (req, res) => {
  res.json(await payments.cancel(Number(req.params.id), (req.body || {}).reason, req.user.username, ipOf(req)));
}));

// ---------------------------------------------------------------- Statistika və hesabatlar
router.get('/stats/summary', wrap(async (req, res) => res.json(await payments.summary())));

router.get('/reports/phone/:phone', wrap(async (req, res) => res.json(await payments.phoneReport(req.params.phone))));

router.get('/reports/monthly', wrap(async (req, res) => {
  res.json(await payments.monthlyReport(req.query.year, req.query.month));
}));

// ---------------------------------------------------------------- Mail konfiqurasiyası
function publicAccount(a) {
  if (!a) return { configured: false };
  return {
    configured: true,
    email: a.email,
    host: a.host,
    port: a.port,
    secure: !!a.secure,
    mailbox: a.mailbox,
    senderFilter: a.sender_filter,
    subjectFilter: a.subject_filter,
    initialDays: a.initial_days,
    enabled: !!a.enabled,
    lastUid: Number(a.last_uid),
    lastSyncAt: a.last_sync_at ? new Date(a.last_sync_at).toISOString() : null,
    lastStatus: a.last_status,
    lastError: a.last_error,
    passwordSet: !!a.password_enc,
  };
}

router.get('/mail/config', requireAdmin, wrap(async (req, res) => res.json(publicAccount(await worker.getAccount()))));

router.put('/mail/config', requireAdmin, wrap(async (req, res) => {
  const b = req.body || {};
  const existing = await worker.getAccount();
  const email = String(b.email || (existing && existing.email) || '').trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new HttpError(400, 'Email ünvanı düzgün deyil');
  if (!b.password && !existing) throw new HttpError(400, 'Mail şifrəsi tələb olunur');
  const fields = {
    email,
    host: String(b.host || (existing && existing.host) || 'imap.mail.ru').trim(),
    port: parseInt(b.port || (existing && existing.port) || 993, 10),
    secure: b.secure === undefined ? (existing ? existing.secure : 1) : b.secure ? 1 : 0,
    mailbox: String(b.mailbox || (existing && existing.mailbox) || 'INBOX'),
    sender_filter: b.senderFilter !== undefined ? String(b.senderFilter || '').slice(0, 1000) || null : existing ? existing.sender_filter : null,
    subject_filter: b.subjectFilter !== undefined ? String(b.subjectFilter || '').slice(0, 1000) || null : existing ? existing.subject_filter : null,
    initial_days: Math.min(Math.max(parseInt(b.initialDays || (existing && existing.initial_days) || 30, 10), 1), 3650),
    enabled: b.enabled === undefined ? (existing ? existing.enabled : 1) : b.enabled ? 1 : 0,
  };
  if (b.password) fields.password_enc = encryptSecret(String(b.password).replace(/\s/g, ''));
  if (existing) {
    // Hesab dəyişirsə UID kursorunu sıfırlayırıq (Message-ID ilə təkrarlar yenə bloklanır)
    if (existing.email !== email || existing.host !== fields.host || existing.mailbox !== fields.mailbox) {
      fields.last_uid = 0;
      fields.uid_validity = null;
    }
    const cols = Object.keys(fields);
    await db.query(`UPDATE mail_accounts SET ${cols.map((c) => `${c} = ?`).join(', ')} WHERE id = ?`, [...cols.map((c) => fields[c]), existing.id]);
  } else {
    const cols = Object.keys(fields);
    await db.query(`INSERT INTO mail_accounts (${cols.join(',')}) VALUES (${cols.map(() => '?').join(',')})`, cols.map((c) => fields[c]));
  }
  const after = await worker.getAccount();
  const details = { ...publicAccount(after), passwordChanged: !!b.password };
  await db.query("INSERT INTO audit_log (action, actor, details, ip) VALUES ('MAIL_CONFIG', ?, ?, ?)", [req.user.username, JSON.stringify(details), ipOf(req)]);
  res.json(publicAccount(after));
}));

router.post('/mail/test', requireAdmin, wrap(async (req, res) => {
  const b = req.body || {};
  const existing = await worker.getAccount();
  const params = {
    email: b.email || (existing && existing.email),
    password: b.password ? String(b.password).replace(/\s/g, '') : existing ? decryptSecret(existing.password_enc) : null,
    host: b.host || (existing && existing.host) || 'imap.mail.ru',
    port: parseInt(b.port || (existing && existing.port) || 993, 10),
    secure: b.secure === undefined ? (existing ? !!existing.secure : true) : !!b.secure,
    mailbox: b.mailbox || (existing && existing.mailbox) || 'INBOX',
  };
  if (!params.email || !params.password) throw new HttpError(400, 'Email və şifrə tələb olunur');
  res.json(await worker.testConnection(params));
}));

router.get('/mail/status', wrap(async (req, res) => {
  const a = await worker.getAccount();
  const [lastRun] = await db.query('SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1');
  const [counts] = await db.query(
    "SELECT COUNT(*) AS total, SUM(result='PAYMENT') AS payment, SUM(result='PENDING') AS pending, SUM(result='IGNORED') AS ignored, SUM(result='ERROR') AS error FROM emails"
  );
  res.json({
    account: publicAccount(a),
    syncRunning: worker.isRunning(),
    syncIntervalMinutes: config.syncIntervalMinutes,
    ocrEnabled: config.ocrEnabled,
    lastRun: lastRun ? serializeRun(lastRun) : null,
    emails: {
      total: Number(counts.total || 0),
      payment: Number(counts.payment || 0),
      pending: Number(counts.pending || 0),
      ignored: Number(counts.ignored || 0),
      error: Number(counts.error || 0),
    },
  });
}));

// ---------------------------------------------------------------- Sinxronizasiya
function serializeRun(r) {
  return {
    id: Number(r.id),
    trigger: r.trigger_type,
    startedAt: new Date(r.started_at).toISOString(),
    finishedAt: r.finished_at ? new Date(r.finished_at).toISOString() : null,
    status: r.status,
    fetched: r.fetched,
    created: r.created,
    pending: r.pending,
    duplicates: r.duplicates,
    errors: r.errors,
    message: r.message,
  };
}

router.post('/sync/run', requireAdmin, wrap(async (req, res) => {
  const result = await worker.runSync({ trigger: 'MANUAL' });
  res.json(result);
}));

router.get('/sync/runs', wrap(async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit, 10) || 50, 500);
  const rows = await db.query('SELECT * FROM sync_runs ORDER BY id DESC LIMIT ?', [limit]);
  res.json({ items: rows.map(serializeRun) });
}));

// ---------------------------------------------------------------- Audit
router.get('/audit', requireAdmin, wrap(async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit, 10) || 200, 1000);
  const sinceId = parseInt(req.query.since_id, 10) || 0;
  const params = [];
  let where = 'WHERE id > ?';
  params.push(sinceId);
  if (req.query.payment_id) {
    where += ' AND payment_id = ?';
    params.push(Number(req.query.payment_id));
  }
  const rows = await db.query(`SELECT * FROM audit_log ${where} ORDER BY id ${sinceId ? 'ASC' : 'DESC'} LIMIT ?`, [...params, limit]);
  res.json({
    items: rows.map((r) => ({
      id: Number(r.id),
      paymentId: r.payment_id === null ? null : Number(r.payment_id),
      action: r.action,
      actor: r.actor,
      details: typeof r.details === 'string' ? safeJson(r.details) : r.details,
      ip: r.ip,
      createdAt: new Date(r.created_at).toISOString(),
    })),
  });
}));

function safeJson(s) {
  try {
    return JSON.parse(s);
  } catch (_) {
    return s;
  }
}

module.exports = router;
