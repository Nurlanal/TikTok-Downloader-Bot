'use strict';
const { ImapFlow } = require('imapflow');
const { simpleParser } = require('mailparser');
const db = require('../db');
const config = require('../config');
const { decryptSecret } = require('../crypto');
const { extractParts } = require('../extract');
const payments = require('../services/payments');

let running = null;

function matchesFilter(value, filter) {
  if (!filter || !String(filter).trim()) return true;
  const v = String(value || '').toLowerCase();
  return String(filter)
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean)
    .some((token) => v.includes(token));
}

async function getAccount() {
  const rows = await db.query('SELECT * FROM mail_accounts ORDER BY id LIMIT 1');
  return rows[0] || null;
}

function createClient(account, password) {
  return new ImapFlow({
    host: account.host,
    port: account.port,
    secure: !!account.secure,
    auth: { user: account.email, pass: password },
    logger: false,
    emitLogs: false,
    tls: { rejectUnauthorized: true },
    socketTimeout: 60000,
  });
}

/** Bağlantını yoxlayır: qoşulur, poçt qutusunu yalnız-oxuma rejimində açır və bağlanır. */
async function testConnection({ email, password, host = 'imap.mail.ru', port = 993, secure = true, mailbox = 'INBOX' }, clientFactory = createClient) {
  const client = clientFactory({ email, host, port, secure }, password);
  const started = Date.now();
  try {
    await client.connect();
    const status = await client.status(mailbox, { messages: true, unseen: true, uidNext: true });
    return { ok: true, messages: status.messages, unseen: status.unseen, latencyMs: Date.now() - started };
  } catch (e) {
    return { ok: false, error: humanizeImapError(e) };
  } finally {
    try {
      await client.logout();
    } catch (_) {
      /* bağlantı artıq bağlıdır */
    }
  }
}

function humanizeImapError(e) {
  const msg = String((e && (e.responseText || e.message)) || e);
  if (e && e.authenticationFailed) {
    return 'Autentifikasiya uğursuz oldu. Mail.ru üçün "Xarici tətbiqlər üçün parol" (app password) istifadə edin.';
  }
  if (/ENOTFOUND|EAI_AGAIN/.test(msg)) return 'Server tapılmadı (DNS). İnternet bağlantısını yoxlayın.';
  if (/ETIMEDOUT|timeout/i.test(msg)) return 'Bağlantı vaxtı bitdi.';
  if (/ECONNREFUSED/.test(msg)) return 'Server bağlantını rədd etdi.';
  return msg.slice(0, 500);
}

/**
 * Bir məktubu emal edir. Eyni məktub (UID və ya Message-ID) ikinci dəfə hesablanmır.
 * Orijinal məktub dəyişdirilmir: poçt qutusu yalnız-oxuma rejimində açılır, BODY.PEEK istifadə olunur.
 */
async function processMessage(account, uidValidity, msg) {
  const exists = await db.query('SELECT id FROM emails WHERE account_id = ? AND uid_validity = ? AND uid = ?', [
    account.id,
    uidValidity,
    msg.uid,
  ]);
  if (exists.length) return { result: 'DUPLICATE', created: 0, pending: 0, duplicates: 1 };

  const mail = await simpleParser(msg.source);
  const messageId = mail.messageId || `<uid-${uidValidity}-${msg.uid}@${account.email}>`;
  const from = mail.from ? mail.from.text : '';
  const subject = mail.subject || '';
  const receivedAt = mail.date || msg.internalDate || new Date();

  const insertEmail = (q, result, created, error = null) =>
    q(
      `INSERT INTO emails (account_id, uid_validity, uid, message_id, from_addr, subject, received_at, result, payments_created, error)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [account.id, uidValidity, msg.uid, messageId.slice(0, 500), from.slice(0, 500), subject.slice(0, 1000), receivedAt, result, created, error]
    );

  const dupMsg = await db.query('SELECT id FROM emails WHERE account_id = ? AND message_id = ? LIMIT 1', [account.id, messageId]);
  if (dupMsg.length) {
    await insertEmail(db.query, 'DUPLICATE', 0);
    return { result: 'DUPLICATE', created: 0, pending: 0, duplicates: 1 };
  }

  if (!matchesFilter(from, account.sender_filter) || !matchesFilter(subject, account.subject_filter)) {
    await insertEmail(db.query, 'IGNORED', 0);
    return { result: 'IGNORED', created: 0, pending: 0, duplicates: 0 };
  }

  const parts = await extractParts(mail);
  const errors = parts.filter((p) => p.kind === 'ERROR').map((p) => `${p.name}: ${p.error}`);
  const items = payments.collectFromParts(parts);
  const rawText = parts
    .filter((p) => p.kind !== 'ERROR')
    .map((p) => (p.kind === 'TEXT' ? p.text : `--- ${p.kind}: ${p.name} ---\n${p.text}`))
    .join('\n');

  return db.transaction(async (q) => {
    const resultType = !items.length ? 'IGNORED' : items.every((i) => i.status === 'PENDING') ? 'PENDING' : 'PAYMENT';
    const emailRes = await insertEmail(q, errors.length && !items.length ? 'ERROR' : resultType, 0, errors.join('; ').slice(0, 1000) || null);
    const stats = await payments.ingestParsed(q, {
      items,
      dedupeBase: `MSG:${messageId}`,
      fallbackDate: receivedAt,
      email: { id: emailRes.insertId, messageId, subject, from },
      actor: 'mail-worker',
      rawText,
    });
    await q('UPDATE emails SET payments_created = ? WHERE id = ?', [stats.created + stats.pending, emailRes.insertId]);
    return { result: resultType, ...stats };
  });
}

/** IMAP sinxronizasiyası. Eyni anda yalnız bir icra. */
async function runSync({ trigger = 'AUTO', clientFactory = createClient } = {}) {
  if (running) return { skipped: true, message: 'Sinxronizasiya artıq icra olunur' };
  running = (async () => {
    const account = await getAccount();
    if (!account) return { skipped: true, message: 'Mail hesabı konfiqurasiya edilməyib' };
    if (!account.enabled) return { skipped: true, message: 'Mail hesabı deaktivdir' };

    const run = await db.query('INSERT INTO sync_runs (account_id, trigger_type) VALUES (?, ?)', [account.id, trigger]);
    const runId = run.insertId;
    const totals = { fetched: 0, created: 0, pending: 0, duplicates: 0, errors: 0 };
    let client;
    try {
      const password = decryptSecret(account.password_enc);
      client = clientFactory(account, password);
      await client.connect();
      const lock = await client.getMailboxLock(account.mailbox || 'INBOX', { readOnly: true });
      try {
        const uidValidity = Number(client.mailbox.uidValidity);
        let lastUid = Number(account.last_uid) || 0;
        if (account.uid_validity === null || Number(account.uid_validity) !== uidValidity) {
          lastUid = 0; // UIDVALIDITY dəyişib: UID-lər yenidən başlayır, Message-ID ilə təkrarlar bloklanır
        }

        let uids;
        if (lastUid > 0) {
          uids = (await client.search({ uid: `${lastUid + 1}:*` }, { uid: true })) || [];
        } else {
          const since = new Date(Date.now() - (account.initial_days || 30) * 24 * 60 * 60 * 1000);
          uids = (await client.search({ since }, { uid: true })) || [];
        }
        uids = uids.filter((u) => u > lastUid).sort((a, b) => a - b).slice(0, config.maxMessagesPerRun);

        for (const uid of uids) {
          const msg = await client.fetchOne(String(uid), { uid: true, source: true, internalDate: true }, { uid: true });
          if (!msg || !msg.source) continue;
          totals.fetched++;
          try {
            const r = await processMessage(account, uidValidity, msg);
            totals.created += r.created || 0;
            totals.pending += r.pending || 0;
            totals.duplicates += r.duplicates || 0;
          } catch (e) {
            totals.errors++;
            console.error(`[mail-worker] UID ${uid} emal edilmədi:`, e.message);
            await db
              .query(
                `INSERT IGNORE INTO emails (account_id, uid_validity, uid, result, error) VALUES (?,?,?,'ERROR',?)`,
                [account.id, uidValidity, uid, String(e.message).slice(0, 1000)]
              )
              .catch(() => {});
          }
          lastUid = Math.max(lastUid, uid);
          await db.query('UPDATE mail_accounts SET last_uid = ?, uid_validity = ? WHERE id = ?', [lastUid, uidValidity, account.id]);
        }
        await db.query('UPDATE mail_accounts SET uid_validity = ? WHERE id = ?', [uidValidity, account.id]);
      } finally {
        lock.release();
      }
      await client.logout();

      const message = `${totals.fetched} məktub oxundu, ${totals.created} təsdiqlənmiş, ${totals.pending} gözləyən, ${totals.duplicates} təkrar`;
      await db.query(
        "UPDATE mail_accounts SET last_sync_at = CURRENT_TIMESTAMP(3), last_status = 'OK', last_error = NULL WHERE id = ?",
        [account.id]
      );
      await db.query(
        "UPDATE sync_runs SET finished_at = CURRENT_TIMESTAMP(3), status = 'SUCCESS', fetched=?, created=?, pending=?, duplicates=?, errors=?, message=? WHERE id = ?",
        [totals.fetched, totals.created, totals.pending, totals.duplicates, totals.errors, message, runId]
      );
      return { runId, status: 'SUCCESS', ...totals, message };
    } catch (e) {
      const message = humanizeImapError(e);
      if (client) {
        try {
          await client.logout();
        } catch (_) {
          /* ignore */
        }
      }
      await db.query(
        "UPDATE mail_accounts SET last_sync_at = CURRENT_TIMESTAMP(3), last_status = 'ERROR', last_error = ? WHERE id = ?",
        [message.slice(0, 1000), account.id]
      );
      await db.query(
        "UPDATE sync_runs SET finished_at = CURRENT_TIMESTAMP(3), status = 'FAILED', fetched=?, created=?, pending=?, duplicates=?, errors=?, message=? WHERE id = ?",
        [totals.fetched, totals.created, totals.pending, totals.duplicates, totals.errors + 1, message.slice(0, 1000), runId]
      );
      return { runId, status: 'FAILED', ...totals, message };
    }
  })();
  try {
    return await running;
  } finally {
    running = null;
  }
}

function isRunning() {
  return !!running;
}

let timer = null;
function startScheduler() {
  if (!config.syncEnabled || timer) return;
  const ms = Math.max(1, config.syncIntervalMinutes) * 60 * 1000;
  const tick = () =>
    runSync({ trigger: 'AUTO' }).catch((e) => console.error('[mail-worker] sinxronizasiya xətası:', e.message));
  timer = setInterval(tick, ms);
  setTimeout(tick, 5000);
  console.log(`[mail-worker] Avtomatik sinxronizasiya hər ${config.syncIntervalMinutes} dəqiqədən bir`);
}

function stopScheduler() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = { runSync, processMessage, testConnection, getAccount, isRunning, startScheduler, stopScheduler, matchesFilter };
