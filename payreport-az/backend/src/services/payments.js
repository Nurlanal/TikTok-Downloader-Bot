'use strict';
const crypto = require('node:crypto');
const db = require('../db');
const { formatPhone, normalizePhoneInput, parsePayments } = require('../parser');
const { bakuLocalToDate, periods, DAY_MS, bakuDayStart } = require('../time');

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function toIso(d) {
  return d ? new Date(d).toISOString() : null;
}

function serialize(row) {
  if (!row) return null;
  return {
    id: Number(row.id),
    dedupeKey: row.dedupe_key,
    phone: row.phone,
    phoneFormatted: formatPhone(row.phone),
    amountMinor: row.amount_minor === null ? null : Number(row.amount_minor),
    currency: row.currency,
    paidAt: toIso(row.paid_at),
    status: row.status,
    source: row.source,
    confidence: Number(row.confidence),
    txnId: row.txn_id,
    emailMessageId: row.email_message_id,
    emailSubject: row.email_subject,
    emailFrom: row.email_from,
    rawText: row.raw_text,
    note: row.note,
    cancelReason: row.cancel_reason,
    createdBy: row.created_by,
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
  };
}

async function audit(q, { paymentId = null, action, actor, details = null, ip = null }) {
  await q('INSERT INTO audit_log (payment_id, action, actor, details, ip) VALUES (?,?,?,?,?)', [
    paymentId,
    action,
    actor,
    details === null ? null : JSON.stringify(details),
    ip,
  ]);
}

async function getById(id, q = db.query) {
  const rows = await q('SELECT * FROM payments WHERE id = ?', [id]);
  return rows[0] || null;
}

/**
 * Parser nəticələrini bazaya yazır. Təkrarlar dedupe_key ilə bloklanır:
 *   - əməliyyat ID varsa: TXN:<id>|<telefon>|<məbləğ> (məktubdakı sabit "Terminal ID" kimi
 *     nömrələr fərqli ödənişləri birləşdirməsin deyə telefon və məbləğ də açara daxildir)
 *   - yoxdursa: <dedupeBase>#<sıra> (məs. MSG:<message-id>#0)
 * @returns {{created:number, pending:number, duplicates:number, ids:number[]}}
 */
async function ingestParsed(q, { items, dedupeBase, fallbackDate, email = {}, actor = 'system', rawText = null, ip = null }) {
  const stats = { created: 0, pending: 0, duplicates: 0, ids: [] };
  for (let i = 0; i < items.length; i++) {
    const it = items[i];
    const dedupeKey = it.txnId ? `TXN:${it.txnId}|${it.phone || ''}|${it.amountMinor || ''}` : `${dedupeBase}#${i}`;
    const paidAt = bakuLocalToDate(it.paidAt) || fallbackDate || new Date();
    const res = await q(
      `INSERT IGNORE INTO payments
        (dedupe_key, phone, amount_minor, currency, paid_at, status, source, confidence, txn_id,
         email_id, email_message_id, email_subject, email_from, raw_text, created_by)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [
        dedupeKey,
        it.phone,
        it.amountMinor,
        'AZN',
        paidAt,
        it.status,
        it.source,
        it.confidence,
        it.txnId,
        email.id || null,
        email.messageId || null,
        email.subject ? String(email.subject).slice(0, 1000) : null,
        email.from ? String(email.from).slice(0, 500) : null,
        (rawText || it.rawLine || '').slice(0, 60000),
        actor,
      ]
    );
    if (!res.affectedRows) {
      stats.duplicates++;
      continue;
    }
    stats.ids.push(res.insertId);
    if (it.status === 'PENDING') stats.pending++;
    else stats.created++;
    await audit(q, {
      paymentId: res.insertId,
      action: 'CREATE',
      actor,
      ip,
      details: { source: it.source, status: it.status, confidence: it.confidence, phone: it.phone, amountMinor: it.amountMinor, txnId: it.txnId },
    });
  }
  return stats;
}

/**
 * Bir məktubun bütün hissələrindən (mətn, PDF, şəkil) ödənişləri toplayır.
 * Əlavədəki ödəniş artıq məktub mətnində varsa (eyni nömrə + məbləğ), təkrar sayılmır.
 */
function collectFromParts(parts) {
  const out = [];
  const bodyKeys = new Set();
  for (const part of parts) {
    if (!['TEXT', 'PDF', 'IMAGE'].includes(part.kind)) continue;
    const parsed = parsePayments(part.text, part.kind);
    for (const p of parsed) {
      const key = `${p.phone}|${p.amountMinor}`;
      if (part.kind === 'TEXT') bodyKeys.add(key);
      else if (bodyKeys.has(key)) continue;
      out.push({ ...p, source: part.kind });
    }
  }
  return out;
}

async function createManual({ phone, amountMinor, paidAt, txnId, note, status }, actor, ip) {
  const normalized = normalizePhoneInput(phone);
  if (!normalized) throw new HttpError(400, 'Telefon nömrəsi düzgün deyil');
  if (!Number.isInteger(amountMinor) || amountMinor <= 0) throw new HttpError(400, 'Məbləğ düzgün deyil');
  const when = paidAt ? new Date(paidAt) : new Date();
  if (Number.isNaN(when.getTime())) throw new HttpError(400, 'Tarix düzgün deyil');
  const txn = txnId ? String(txnId).trim().toUpperCase() : null;
  const st = status === 'PENDING' ? 'PENDING' : 'CONFIRMED';
  return db.transaction(async (q) => {
    if (txn) {
      const ex = await q('SELECT id FROM payments WHERE txn_id = ? LIMIT 1', [txn]);
      if (ex.length) throw new HttpError(409, `Bu əməliyyat ID artıq mövcuddur (#${ex[0].id})`);
    }
    const res = await q(
      `INSERT INTO payments (dedupe_key, phone, amount_minor, currency, paid_at, status, source, confidence, txn_id, note, created_by)
       VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
      [txn ? `TXN:${txn}|${normalized}|${amountMinor}` : `MAN:${crypto.randomUUID()}`, normalized, amountMinor, 'AZN', when, st, 'MANUAL', 100, txn, note || null, actor]
    );
    await audit(q, {
      paymentId: res.insertId,
      action: 'CREATE',
      actor,
      ip,
      details: { source: 'MANUAL', phone: normalized, amountMinor, status: st, txnId: txn, note: note || null },
    });
    return serialize(await getById(res.insertId, q));
  });
}

/** Mobil tətbiqin ML Kit OCR / PDF ilə oxuduğu mətni qəbul edir. */
async function ingestText({ text, source, reference }, actor, ip) {
  if (!text || !String(text).trim()) throw new HttpError(400, 'Mətn boşdur');
  const kind = ['TEXT', 'PDF', 'IMAGE'].includes(source) ? source : 'TEXT';
  const parsed = parsePayments(text, kind).map((p) => ({
    ...p,
    source: kind,
    // Mobil skan həmişə admin tərəfindən yoxlanılır
    status: 'PENDING',
  }));
  if (!parsed.length) return { created: 0, payments: [] };
  const base = `SCAN:${reference ? String(reference).slice(0, 200) : crypto.randomUUID()}`;
  return db.transaction(async (q) => {
    const stats = await ingestParsed(q, { items: parsed, dedupeBase: base, fallbackDate: new Date(), actor, rawText: String(text), ip });
    const rows = stats.ids.length ? await q('SELECT * FROM payments WHERE id IN (?)', [stats.ids]) : [];
    return { created: stats.ids.length, duplicates: stats.duplicates, payments: rows.map(serialize) };
  });
}

const EDITABLE = {
  phone: 'phone',
  amountMinor: 'amount_minor',
  paidAt: 'paid_at',
  txnId: 'txn_id',
  note: 'note',
};

async function update(id, patch, actor, ip) {
  return db.transaction(async (q) => {
    const before = await getById(id, q);
    if (!before) throw new HttpError(404, 'Ödəniş tapılmadı');
    const sets = [];
    const params = [];
    const changes = {};
    for (const [key, col] of Object.entries(EDITABLE)) {
      if (!(key in patch)) continue;
      let v = patch[key];
      if (key === 'phone') {
        v = normalizePhoneInput(v);
        if (!v) throw new HttpError(400, 'Telefon nömrəsi düzgün deyil');
      } else if (key === 'amountMinor') {
        if (!Number.isInteger(v) || v <= 0) throw new HttpError(400, 'Məbləğ düzgün deyil');
      } else if (key === 'paidAt') {
        v = new Date(v);
        if (Number.isNaN(v.getTime())) throw new HttpError(400, 'Tarix düzgün deyil');
      } else if (key === 'txnId') {
        v = v ? String(v).trim().toUpperCase() : null;
        if (v) {
          const ex = await q('SELECT id FROM payments WHERE txn_id = ? AND id <> ? LIMIT 1', [v, id]);
          if (ex.length) throw new HttpError(409, `Bu əməliyyat ID artıq #${ex[0].id} ödənişində var`);
        }
      } else if (key === 'note') {
        v = v ? String(v).slice(0, 1000) : null;
      }
      sets.push(`${col} = ?`);
      params.push(v);
      changes[key] = { from: key === 'paidAt' ? toIso(before[col]) : before[col], to: key === 'paidAt' ? toIso(v) : v };
    }
    if (!sets.length) return serialize(before);
    await q(`UPDATE payments SET ${sets.join(', ')} WHERE id = ?`, [...params, id]);
    await audit(q, { paymentId: id, action: 'UPDATE', actor, ip, details: changes });
    return serialize(await getById(id, q));
  });
}

async function confirm(id, actor, ip) {
  return db.transaction(async (q) => {
    const p = await getById(id, q);
    if (!p) throw new HttpError(404, 'Ödəniş tapılmadı');
    if (p.status === 'CANCELLED') throw new HttpError(409, 'Ləğv edilmiş ödəniş təsdiqlənə bilməz');
    if (!p.phone || !p.amount_minor) throw new HttpError(400, 'Təsdiqdən əvvəl telefon və məbləği daxil edin');
    if (p.status === 'CONFIRMED') return serialize(p);
    await q("UPDATE payments SET status = 'CONFIRMED' WHERE id = ?", [id]);
    await audit(q, { paymentId: id, action: 'CONFIRM', actor, ip, details: { from: p.status } });
    return serialize(await getById(id, q));
  });
}

async function cancel(id, reason, actor, ip) {
  return db.transaction(async (q) => {
    const p = await getById(id, q);
    if (!p) throw new HttpError(404, 'Ödəniş tapılmadı');
    if (p.status === 'CANCELLED') return serialize(p);
    const r = reason ? String(reason).slice(0, 1000) : null;
    await q("UPDATE payments SET status = 'CANCELLED', cancel_reason = ? WHERE id = ?", [r, id]);
    await audit(q, { paymentId: id, action: 'CANCEL', actor, ip, details: { from: p.status, reason: r, snapshot: serialize(p) } });
    return serialize(await getById(id, q));
  });
}

async function list({ status, phone, from, to, q: search, updatedSince, afterId, limit = 200, offset = 0 }) {
  const where = [];
  const params = [];
  if (status) {
    where.push('status = ?');
    params.push(status);
  }
  if (phone) {
    const n = normalizePhoneInput(phone);
    if (n) {
      where.push('phone = ?');
      params.push(n);
    } else {
      const digits = String(phone).replace(/\D/g, '').replace(/^0/, '');
      if (digits) {
        where.push('phone LIKE ?');
        params.push(`%${digits}%`);
      }
    }
  }
  if (from) {
    where.push('paid_at >= ?');
    params.push(new Date(from));
  }
  if (to) {
    where.push('paid_at < ?');
    params.push(new Date(to));
  }
  if (search) {
    where.push('(txn_id LIKE ? OR note LIKE ? OR email_subject LIKE ? OR phone LIKE ?)');
    const s = `%${search}%`;
    params.push(s, s, s, s);
  }
  let order = 'paid_at DESC, id DESC';
  if (updatedSince) {
    // Mobil sinxronizasiya üçün keyset pagination: (updated_at, id)
    const since = new Date(updatedSince);
    where.push('(updated_at > ? OR (updated_at = ? AND id > ?))');
    params.push(since, since, Number(afterId) || 0);
    order = 'updated_at ASC, id ASC';
  }
  const lim = Math.min(Math.max(parseInt(limit, 10) || 200, 1), 1000);
  const off = Math.max(parseInt(offset, 10) || 0, 0);
  const sql = `SELECT * FROM payments ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY ${order} LIMIT ? OFFSET ?`;
  const rows = await db.query(sql, [...params, lim, off]);
  return rows.map(serialize);
}

async function sumBetween(from, to) {
  const rows = await db.query(
    "SELECT COALESCE(SUM(amount_minor),0) AS total, COUNT(*) AS cnt FROM payments WHERE status='CONFIRMED' AND paid_at >= ? AND paid_at < ?",
    [from, to]
  );
  return { totalMinor: Number(rows[0].total), count: Number(rows[0].cnt) };
}

async function summary(now = new Date()) {
  const p = periods(now);
  const [today, yesterday, week, month, all, pending] = await Promise.all([
    sumBetween(p.today, p.tomorrow),
    sumBetween(p.yesterday, p.today),
    sumBetween(p.weekStart, p.tomorrow),
    sumBetween(p.monthStart, p.tomorrow),
    db.query("SELECT COALESCE(SUM(amount_minor),0) AS total, COUNT(*) AS cnt FROM payments WHERE status='CONFIRMED'"),
    db.query("SELECT COUNT(*) AS cnt FROM payments WHERE status='PENDING'"),
  ]);
  return {
    todayMinor: today.totalMinor,
    yesterdayMinor: yesterday.totalMinor,
    weekMinor: week.totalMinor,
    monthMinor: month.totalMinor,
    totalMinor: Number(all[0].total),
    totalCount: Number(all[0].cnt),
    pendingCount: Number(pending[0].cnt),
    currency: 'AZN',
  };
}

async function phoneReport(phoneInput) {
  const phone = normalizePhoneInput(phoneInput);
  if (!phone) throw new HttpError(400, 'Telefon nömrəsi düzgün deyil');
  const rows = await db.query('SELECT * FROM payments WHERE phone = ? ORDER BY paid_at DESC, id DESC', [phone]);
  const confirmed = rows.filter((r) => r.status === 'CONFIRMED');
  const monthly = await db.query(
    `SELECT DATE_FORMAT(DATE_ADD(paid_at, INTERVAL 4 HOUR), '%Y-%m') AS ym, COUNT(*) AS cnt, SUM(amount_minor) AS total
       FROM payments WHERE phone = ? AND status = 'CONFIRMED' GROUP BY ym ORDER BY ym DESC`,
    [phone]
  );
  return {
    phone,
    phoneFormatted: formatPhone(phone),
    count: confirmed.length,
    totalMinor: confirmed.reduce((s, r) => s + Number(r.amount_minor || 0), 0),
    lastPaidAt: confirmed[0] ? toIso(confirmed[0].paid_at) : null,
    pendingCount: rows.filter((r) => r.status === 'PENDING').length,
    monthly: monthly.map((m) => ({ month: m.ym, count: Number(m.cnt), totalMinor: Number(m.total) })),
    payments: rows.map(serialize),
  };
}

async function monthlyReport(year, month) {
  const y = parseInt(year, 10);
  const m = parseInt(month, 10);
  if (!(y >= 2000 && y <= 2100 && m >= 1 && m <= 12)) throw new HttpError(400, 'Ay və ya il düzgün deyil');
  const from = bakuDayStart(y, m, 1);
  const to = m === 12 ? bakuDayStart(y + 1, 1, 1) : bakuDayStart(y, m + 1, 1);
  const daily = await db.query(
    `SELECT DATE_FORMAT(DATE_ADD(paid_at, INTERVAL 4 HOUR), '%Y-%m-%d') AS day, COUNT(*) AS cnt, SUM(amount_minor) AS total
       FROM payments WHERE status='CONFIRMED' AND paid_at >= ? AND paid_at < ? GROUP BY day ORDER BY day`,
    [from, to]
  );
  const byPhone = await db.query(
    `SELECT phone, COUNT(*) AS cnt, SUM(amount_minor) AS total, MAX(paid_at) AS last_paid
       FROM payments WHERE status='CONFIRMED' AND paid_at >= ? AND paid_at < ? GROUP BY phone ORDER BY total DESC, cnt DESC`,
    [from, to]
  );
  const days = Math.round((to - from) / DAY_MS);
  const dailyMap = new Map(daily.map((d) => [d.day, d]));
  const dailyFull = [];
  for (let d = 1; d <= days; d++) {
    const key = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    const row = dailyMap.get(key);
    dailyFull.push({ date: key, count: row ? Number(row.cnt) : 0, totalMinor: row ? Number(row.total) : 0 });
  }
  const totalMinor = dailyFull.reduce((s, d) => s + d.totalMinor, 0);
  const count = dailyFull.reduce((s, d) => s + d.count, 0);
  const phones = byPhone.map((r) => ({
    phone: r.phone,
    phoneFormatted: formatPhone(r.phone),
    count: Number(r.cnt),
    totalMinor: Number(r.total),
    lastPaidAt: toIso(r.last_paid),
  }));
  return { year: y, month: m, totalMinor, count, daily: dailyFull, byPhone: phones, topPayers: phones.slice(0, 10) };
}

module.exports = {
  HttpError,
  serialize,
  audit,
  ingestParsed,
  collectFromParts,
  createManual,
  ingestText,
  update,
  confirm,
  cancel,
  list,
  summary,
  phoneReport,
  monthlyReport,
  getById,
};
