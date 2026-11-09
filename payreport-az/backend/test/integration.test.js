'use strict';
/**
 * Real MySQL/MariaDB üzərində inteqrasiya testi. IMAP serveri saxta (fake) klientlə əvəz olunur,
 * qalan hər şey (parser, PDF, baza, REST API, audit, təkrar yoxlaması) realdır.
 *
 * İşə salmaq:
 *   TEST_DB=1 DB_NAME=payreport_test DB_USER=payreport DB_PASSWORD=... node --test test/integration.test.js
 */
const test = require('node:test');
const assert = require('node:assert/strict');

if (!process.env.TEST_DB) {
  test('inteqrasiya testi (TEST_DB=1 tələb olunur)', { skip: true }, () => {});
  return;
}

process.env.CREDENTIALS_KEY = process.env.CREDENTIALS_KEY || Buffer.alloc(32, 7).toString('base64');
process.env.SYNC_ENABLED = '0';

const db = require('../src/db');
const { createApp } = require('../src/app');
const worker = require('../src/imap/worker');
const { hashPassword } = require('../src/crypto');
const { toBaku } = require('../src/time');

// ------------------------------------------------------------------ köməkçilər
function buildPdf(lines) {
  const content = ['BT', '/F1 12 Tf', '14 TL', '50 780 Td']
    .concat(lines.map((l) => `(${l.replace(/([()\\])/g, '\\$1')}) Tj T*`))
    .concat(['ET'])
    .join('\n');
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let out = '%PDF-1.4\n';
  const offsets = [];
  objs.forEach((o, i) => {
    offsets.push(Buffer.byteLength(out));
    out += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = Buffer.byteLength(out);
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`;
  for (const off of offsets) out += `${String(off).padStart(10, '0')} 00000 n \n`;
  out += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}

function rfc822({ id, from = 'bildiris@bank.az', subject = 'Ödəniş bildirişi', text, html, pdf, date = new Date() }) {
  const headers = [
    `From: ${from}`,
    'To: hesab@mail.ru',
    `Subject: =?UTF-8?B?${Buffer.from(subject).toString('base64')}?=`,
    `Message-ID: <${id}@bank.az>`,
    `Date: ${date.toUTCString()}`,
    'MIME-Version: 1.0',
  ];
  if (pdf) {
    const b = 'BOUNDARY42';
    return Buffer.from(
      [
        ...headers,
        `Content-Type: multipart/mixed; boundary="${b}"`,
        '',
        `--${b}`,
        'Content-Type: text/plain; charset=utf-8',
        'Content-Transfer-Encoding: base64',
        '',
        Buffer.from(text || 'Qəbz əlavədədir').toString('base64'),
        `--${b}`,
        'Content-Type: application/pdf; name="qebz.pdf"',
        'Content-Disposition: attachment; filename="qebz.pdf"',
        'Content-Transfer-Encoding: base64',
        '',
        pdf.toString('base64'),
        `--${b}--`,
        '',
      ].join('\r\n')
    );
  }
  const body = html || text;
  return Buffer.from(
    [...headers, `Content-Type: text/${html ? 'html' : 'plain'}; charset=utf-8`, 'Content-Transfer-Encoding: base64', '', Buffer.from(body).toString('base64'), ''].join('\r\n')
  );
}

function fakeClientFactory(messages, uidValidity = 777) {
  return () => ({
    mailbox: { uidValidity },
    async connect() {},
    async getMailboxLock(name, opts) {
      assert.equal(opts.readOnly, true, 'poçt qutusu yalnız-oxuma rejimində açılmalıdır');
      return { release() {} };
    },
    async search(q) {
      if (q.uid) {
        const from = parseInt(String(q.uid).split(':')[0], 10);
        return messages.map((m) => m.uid).filter((u) => u >= from);
      }
      return messages.map((m) => m.uid);
    },
    async fetchOne(uid) {
      const m = messages.find((x) => String(x.uid) === String(uid));
      return m ? { uid: m.uid, source: m.source, internalDate: new Date() } : false;
    },
    async logout() {},
  });
}

let server;
let base;
let token;

async function api(method, path, body, auth = true) {
  const res = await fetch(`${base}/api${path}`, {
    method,
    headers: { 'content-type': 'application/json', ...(auth && token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, body: json };
}

test.before(async () => {
  for (const t of ['audit_log', 'payments', 'emails', 'sync_runs', 'api_tokens', 'mail_accounts', 'users']) {
    await db.query(`DELETE FROM ${t}`);
  }
  await db.query('INSERT INTO users (username, password_hash, role) VALUES (?,?,?)', ['admin', hashPassword('SuperGizli123'), 'admin']);
  server = createApp().listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  server.close();
  await db.close();
});

// ------------------------------------------------------------------ testlər
test('autentifikasiya', async () => {
  assert.equal((await api('GET', '/payments', null, false)).status, 401);
  assert.equal((await api('POST', '/auth/login', { username: 'admin', password: 'yanlis' }, false)).status, 401);
  const ok = await api('POST', '/auth/login', { username: 'admin', password: 'SuperGizli123', deviceName: 'test' }, false);
  assert.equal(ok.status, 200);
  assert.ok(ok.body.token.length > 30);
  token = ok.body.token;
  const [row] = await db.query('SELECT token_hash FROM api_tokens LIMIT 1');
  assert.notEqual(row.token_hash, token, 'token açıq mətn kimi saxlanılmamalıdır');
});

test('mail konfiqurasiyası şifrələnmiş saxlanılır', async () => {
  const res = await api('PUT', '/mail/config', { email: 'hesab@mail.ru', password: 'app-parol-123', senderFilter: '', initialDays: 30 });
  assert.equal(res.status, 200);
  assert.equal(res.body.email, 'hesab@mail.ru');
  assert.equal(res.body.password, undefined);
  const [acc] = await db.query('SELECT password_enc FROM mail_accounts LIMIT 1');
  assert.ok(acc.password_enc.startsWith('v1:'));
  assert.ok(!acc.password_enc.includes('app-parol-123'));
});

const b = toBaku(new Date());
const todayDmy = `${String(b.d).padStart(2, '0')}.${String(b.m).padStart(2, '0')}.${b.y}`;

const messages = [
  { uid: 1, source: rfc822({ id: 'm1', text: '055 777 66 55 — 5 AZN' }) },
  { uid: 2, source: rfc822({ id: 'm2', text: '055 777 66 55 — 5 AZN' }) },
  { uid: 3, source: rfc822({ id: 'm3', text: '055 777 66 55 — 5 AZN' }) },
  {
    uid: 4,
    source: rfc822({
      id: 'm4',
      html: `<p>Hörmətli müştəri!</p><p>+994 50 123 45 67 nömrəsindən <b>12,50 AZN</b> ödəniş daxil oldu.</p><p>Əməliyyat ID: TRX123456<br>Tarix: ${todayDmy} 09:22</p>`,
    }),
  },
  {
    uid: 5,
    source: rfc822({
      id: 'm5',
      text: 'Ödəniş qəbzi əlavə olunub.',
      pdf: buildPdf(['ODENIS QEBZI', 'Telefon: 070 555 44 33', 'Mebleg: 20.00 AZN', 'Komissiya: 0.50 AZN', 'Qebz No: 884422']),
    }),
  },
  { uid: 6, source: rfc822({ id: 'm1', text: '055 777 66 55 — 5 AZN' }) }, // eyni Message-ID yenidən gəlib
  { uid: 7, source: rfc822({ id: 'm7', text: 'Salam, həftəlik xəbərlər bülleteni.' }) },
  { uid: 8, source: rfc822({ id: 'm8', text: 'Ödəniş qəbul edildi: 15 AZN' }) },
  { uid: 9, source: rfc822({ id: 'm9', text: 'Təkrar bildiriş. Əməliyyat ID: TRX123456. 050 123 45 67 — 12,50 AZN' }) },
];

test('IMAP sinxronizasiyası: mətn, HTML, PDF, təkrarlar', async () => {
  const r = await worker.runSync({ trigger: 'MANUAL', clientFactory: fakeClientFactory(messages) });
  assert.equal(r.status, 'SUCCESS', r.message);
  assert.equal(r.fetched, 9);
  assert.equal(r.created, 5); // 3x5 AZN + TRX123456 + PDF
  assert.equal(r.pending, 1); // yalnız məbləğ
  assert.equal(r.duplicates, 2); // m1 təkrarı + TRX123456 təkrarı

  const pdfPay = await db.query("SELECT * FROM payments WHERE source = 'PDF'");
  assert.equal(pdfPay.length, 1);
  assert.equal(pdfPay[0].phone, '+994705554433');
  assert.equal(Number(pdfPay[0].amount_minor), 2000);
  assert.equal(pdfPay[0].txn_id, '884422');

  const emails = await db.query('SELECT result, COUNT(*) c FROM emails GROUP BY result');
  const byRes = Object.fromEntries(emails.map((e) => [e.result, Number(e.c)]));
  assert.equal(byRes.DUPLICATE, 1);
  assert.equal(byRes.IGNORED, 1);
});

test('təkrar sinxronizasiya heç nəyi ikinci dəfə hesablamır', async () => {
  const again = await worker.runSync({ trigger: 'AUTO', clientFactory: fakeClientFactory(messages) });
  assert.equal(again.fetched, 0);
  // UID kursorunu sıfırlayıb bütün məktubları yenidən oxuyuruq
  await db.query('UPDATE mail_accounts SET last_uid = 0');
  const full = await worker.runSync({ trigger: 'AUTO', clientFactory: fakeClientFactory(messages) });
  assert.equal(full.fetched, 9);
  assert.equal(full.created, 0);
  assert.equal(full.pending, 0);
  // UIDVALIDITY dəyişsə belə Message-ID ilə bloklanır
  const reset = await worker.runSync({ trigger: 'AUTO', clientFactory: fakeClientFactory(messages, 999) });
  assert.equal(reset.created, 0);
  const [{ c }] = await db.query('SELECT COUNT(*) c FROM payments');
  assert.equal(Number(c), 6);
});

test('dashboard göstəriciləri yalnız təsdiqlənmişləri sayır', async () => {
  const s = await api('GET', '/stats/summary');
  assert.equal(s.status, 200);
  assert.equal(s.body.totalMinor, 1500 + 1250 + 2000);
  assert.equal(s.body.totalCount, 5);
  assert.equal(s.body.pendingCount, 1);
  assert.equal(s.body.todayMinor, 4750);
});

test('telefon nömrəsinə görə hesabat', async () => {
  const r = await api('GET', `/reports/phone/${encodeURIComponent('055 777 66 55')}`);
  assert.equal(r.status, 200);
  assert.equal(r.body.count, 3);
  assert.equal(r.body.totalMinor, 1500);
  assert.equal(r.body.phoneFormatted, '055 777 66 55');
  assert.equal(r.body.monthly.length, 1);
});

let manualId;
test('admin: əlavə, redaktə, ləğv və audit', async () => {
  const c = await api('POST', '/payments', { phone: '+994 51 444 55 66', amount: '30', note: 'Nağd' });
  assert.equal(c.status, 201);
  manualId = c.body.id;
  assert.equal(c.body.source, 'MANUAL');

  const e = await api('PATCH', `/payments/${manualId}`, { amount: '35.50', txnId: 'man-001' });
  assert.equal(e.status, 200);
  assert.equal(e.body.amountMinor, 3550);
  assert.equal(e.body.txnId, 'MAN-001');

  const dupTxn = await api('PATCH', `/payments/${manualId}`, { txnId: 'TRX123456' });
  assert.equal(dupTxn.status, 409);

  let s = await api('GET', '/stats/summary');
  assert.equal(s.body.totalMinor, 4750 + 3550);

  const x = await api('POST', `/payments/${manualId}/cancel`, { reason: 'Səhv daxil edilib' });
  assert.equal(x.body.status, 'CANCELLED');
  s = await api('GET', '/stats/summary');
  assert.equal(s.body.totalMinor, 4750);

  const [{ c: still }] = await db.query('SELECT COUNT(*) c FROM payments WHERE id = ?', [manualId]);
  assert.equal(Number(still), 1, 'ləğv edilən ödəniş silinməməlidir');

  const audit = await api('GET', `/audit?payment_id=${manualId}`);
  assert.deepEqual(audit.body.items.map((a) => a.action).sort(), ['CANCEL', 'CREATE', 'UPDATE']);
});

test('gözləyən ödənişin təsdiqlənməsi', async () => {
  const pend = await api('GET', '/payments?status=PENDING');
  assert.equal(pend.body.items.length, 1);
  const id = pend.body.items[0].id;
  assert.equal((await api('POST', `/payments/${id}/confirm`)).status, 400); // telefon yoxdur
  await api('PATCH', `/payments/${id}`, { phone: '099 888 77 66' });
  const ok = await api('POST', `/payments/${id}/confirm`);
  assert.equal(ok.body.status, 'CONFIRMED');
  const s = await api('GET', '/stats/summary');
  assert.equal(s.body.totalMinor, 4750 + 1500);
  assert.equal(s.body.pendingCount, 0);
});

test('mobil OCR mətni gözləyənlərə düşür', async () => {
  const r = await api('POST', '/payments/ingest-text', { text: 'Telefon: 077 222 33 44\nMəbləğ: 7 AZN', source: 'IMAGE', reference: 'img-1' });
  assert.equal(r.status, 201);
  assert.equal(r.body.payments[0].status, 'PENDING');
  const again = await api('POST', '/payments/ingest-text', { text: 'Telefon: 077 222 33 44\nMəbləğ: 7 AZN', source: 'IMAGE', reference: 'img-1' });
  assert.equal(again.body.created, 0);
});

test('aylıq hesabat', async () => {
  const r = await api('GET', `/reports/monthly?year=${b.y}&month=${b.m}`);
  assert.equal(r.status, 200);
  assert.equal(r.body.totalMinor, 6250);
  assert.equal(r.body.count, 6);
  assert.equal(r.body.topPayers[0].phoneFormatted, '070 555 44 33');
  assert.ok(r.body.daily.length >= 28);
});

test('mobil sinxronizasiya üçün keyset pagination', async () => {
  const all = [];
  let since = '1970-01-01T00:00:00.000Z';
  let afterId = 0;
  for (;;) {
    const page = await api('GET', `/payments?updated_since=${encodeURIComponent(since)}&after_id=${afterId}&limit=2`);
    if (!page.body.items.length) break;
    all.push(...page.body.items);
    const last = page.body.items[page.body.items.length - 1];
    since = last.updatedAt;
    afterId = last.id;
  }
  const [{ c }] = await db.query('SELECT COUNT(*) c FROM payments');
  assert.equal(all.length, Number(c));
  assert.equal(new Set(all.map((p) => p.id)).size, all.length);
});

test('sinxronizasiya tarixçəsi və mail statusu', async () => {
  const runs = await api('GET', '/sync/runs');
  assert.ok(runs.body.items.length >= 4);
  const st = await api('GET', '/mail/status');
  assert.equal(st.body.account.email, 'hesab@mail.ru');
  assert.equal(st.body.account.lastStatus, 'OK');
});

test('sabit "Terminal ID" fərqli ödənişləri birləşdirmir', async () => {
  const before = await api('GET', `/reports/phone/${encodeURIComponent('070 794 93 92')}`);
  const base = before.status === 200 ? before.body.totalMinor : 0;
  const msgs = [
    { uid: 101, source: rfc822({ id: 't1', text: 'Terminal ID: 55501\n070 794 93 92 — 1000 AZN' }) },
    { uid: 102, source: rfc822({ id: 't2', text: 'Terminal ID: 55501\n070 794 93 92 — 2500 AZN' }) },
    { uid: 103, source: rfc822({ id: 't3', text: 'Terminal ID: 55501\n070 794 93 92 — 2500 AZN' }) },
    { uid: 104, source: rfc822({ id: 't4', text: 'Terminal ID: 55501\n070 794 93 92 — 10000 AZN' }) },
  ];
  await db.query('UPDATE mail_accounts SET last_uid = 100, uid_validity = 777');
  const r = await worker.runSync({ trigger: 'MANUAL', clientFactory: fakeClientFactory(msgs) });
  assert.equal(r.status, 'SUCCESS', r.message);
  const rep = await api('GET', `/reports/phone/${encodeURIComponent('070 794 93 92')}`);
  // t2 və t3 eyni ID + eyni nömrə + eyni məbləğdir — həqiqi təkrar kimi bir dəfə sayılır
  assert.equal(rep.body.totalMinor - base, (1000 + 2500 + 10000) * 100);
  assert.equal(r.duplicates, 1);
});
