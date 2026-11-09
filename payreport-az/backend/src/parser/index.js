'use strict';

/**
 * PayReport AZ — ödəniş bildirişi parseri.
 *
 * Bu modul Android tətbiqindəki `PaymentParser.kt` ilə eyni alqoritmi
 * reallaşdırır. Hər iki tərəf `shared/parser-fixtures.json` ilə test olunur,
 * ona görə də qaydaları dəyişəndə hər iki faylı birlikdə yeniləyin.
 */

const AUTO_CONFIRM_THRESHOLD = 85;
const MAX_AMOUNT_MINOR = 100000 * 100; // 100 000 AZN-dən böyük məbləğ şübhəlidir

const PHONE_PREFIXES = '10|12|50|51|55|60|70|77|99';
const PHONE_RE = new RegExp(
  `(?<![\\d+])(?:\\+?994[\\s-]?\\(?|\\(?0)(${PHONE_PREFIXES})\\)?[\\s-]?(\\d{3})[\\s-]?(\\d{2})[\\s-]?(\\d{2})(?!\\d)`,
  'g'
);

const NUMBER = '(\\d{1,3}(?:[ ,]\\d{3})+(?:\\.\\d{1,2})?|\\d{1,3}(?: \\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)';
const AMOUNT_CURRENCY_RE = new RegExp(`(?<![\\d.,:])${NUMBER}\\s*azn(?![\\p{L}])`, 'giu');
const AMOUNT_PREFIX_RE = /(?<![\p{L}])azn\s*(\d+(?:[.,]\d{1,2})?)(?![\d])/giu;
const AMOUNT_LABEL_RE = /(?<![\p{L}])(?:məbləğ|mebleg|məbləq|meblegi|məbləği|amount|summa|cəmi|cemi)\s*[:=\-]?\s*(\d+(?:[.,]\d{1,2})?)(?![\d.,]|\s*azn)/giu;

const LABEL_KEYWORDS = /(məbləğ|mebleg|məbləq|amount|summa|cəmi|cemi|ödəniş|odenis|ödənildi|payment)/iu;
const EXCLUDE_KEYWORDS = /(balans|balance|qalıq|qaliq|komissiya|commission|fee|limit|bonus|cashback|keşbek|endirim|discount|borc)/iu;

const TXN_RE = /(?<![\p{L}\d])(?:əməliyyat(?:ın)?\s*(?:id|№|nömrəsi|nomresi|kodu|no)|emeliyyat\s*(?:id|№|no)|transaction\s*(?:id|no|number)|txn\s*id|trx\s*id|tranzaksiya\s*(?:id|№|no)?|qəbz\s*(?:№|nömrəsi|no)|qebz\s*(?:№|no)|çek\s*(?:№|no)|rrn|reference|ref\.?(?:\s*no)?|id)(?![\p{L}])\s*[:#№.]?\s*([A-Za-z0-9][A-Za-z0-9\-_/]{3,40})/giu;

const DATE_DMY_RE = /(?<!\d)(\d{1,2})[./](\d{1,2})[./](\d{4})(?:[\sT,]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?(?![\d])/g;
const DATE_ISO_RE = /(?<!\d)(\d{4})-(\d{2})-(\d{2})(?:[\sT]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?(?![\d])/g;
const TIME_RE = /(?<![\d:])(\d{1,2}):(\d{2})(?::(\d{2}))?(?![\d:])/g;

function normalizeText(text) {
  return String(text || '')
    .replace(/\r\n?/g, '\n')
    .replace(/[   \t]/g, ' ')
    .replace(/İ/g, 'I')
    .replace(/ı/g, 'i')
    .replace(/₼/g, ' AZN')
    .replace(/(?<![\p{L}])(?:manat|man\.)(?![\p{L}])/giu, 'AZN')
    .replace(/ {2,}/g, ' ');
}

function normalizePhone(prefix, a, b, c) {
  return `+994${prefix}${a}${b}${c}`;
}

/** İstifadəçinin daxil etdiyi istənilən formatı +994XXXXXXXXX formatına çevirir, alınmasa null. */
function normalizePhoneInput(input) {
  const digits = String(input || '').replace(/\D/g, '');
  let local = null;
  if (digits.length === 12 && digits.startsWith('994')) local = digits.slice(3);
  else if (digits.length === 10 && digits.startsWith('0')) local = digits.slice(1);
  else if (digits.length === 9) local = digits;
  if (!local) return null;
  if (!new RegExp(`^(${PHONE_PREFIXES})\\d{7}$`).test(local)) return null;
  return `+994${local}`;
}

/** +994557776655 -> 055 777 66 55 */
function formatPhone(phone) {
  if (!phone) return '';
  const m = /^\+994(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(phone);
  return m ? `0${m[1]} ${m[2]} ${m[3]} ${m[4]}` : phone;
}

function amountToMinor(raw) {
  let s = String(raw).replace(/\s/g, '');
  if (/^\d{1,3}(,\d{3})+(\.\d{1,2})?$/.test(s)) s = s.replace(/,/g, '');
  else s = s.replace(',', '.');
  const [whole, frac = ''] = s.split('.');
  const minor = parseInt(whole, 10) * 100 + parseInt((frac + '00').slice(0, 2), 10);
  return Number.isFinite(minor) ? minor : null;
}

function pad(n) {
  return String(n).padStart(2, '0');
}

function validDate(y, mo, d) {
  return y >= 2000 && y <= 2100 && mo >= 1 && mo <= 12 && d >= 1 && d <= 31;
}

function validTime(h, mi, s) {
  return h <= 23 && mi <= 59 && s <= 59;
}

/** Mətndə ilk tarixi (və saatı) tapır. {date:'YYYY-MM-DD', time:'HH:mm:ss'|null} */
function findDate(text) {
  const found = [];
  for (const m of text.matchAll(DATE_DMY_RE)) {
    const d = +m[1], mo = +m[2], y = +m[3];
    if (!validDate(y, mo, d)) continue;
    found.push({ index: m.index, date: `${y}-${pad(mo)}-${pad(d)}`, time: timeOf(m[4], m[5], m[6]) });
  }
  for (const m of text.matchAll(DATE_ISO_RE)) {
    const y = +m[1], mo = +m[2], d = +m[3];
    if (!validDate(y, mo, d)) continue;
    found.push({ index: m.index, date: `${y}-${pad(mo)}-${pad(d)}`, time: timeOf(m[4], m[5], m[6]) });
  }
  if (!found.length) return null;
  found.sort((a, b) => a.index - b.index);
  return found[0];
}

function timeOf(h, mi, s) {
  if (h === undefined || mi === undefined) return null;
  const hh = +h, mm = +mi, ss = s === undefined ? 0 : +s;
  if (!validTime(hh, mm, ss)) return null;
  return `${pad(hh)}:${pad(mm)}:${pad(ss)}`;
}

function findTime(text) {
  for (const m of text.matchAll(TIME_RE)) {
    const t = timeOf(m[1], m[2], m[3]);
    if (t) return t;
  }
  return null;
}

/** Tarixi və saatı birləşdirir: "YYYY-MM-DDTHH:mm:ss" və ya null. */
function resolveDateTime(scopeText, fullText) {
  const d = findDate(scopeText);
  if (!d) return null;
  const time = d.time || findTime(scopeText.replace(DATE_DMY_RE, ' ').replace(DATE_ISO_RE, ' ')) ||
    findTime(fullText.replace(DATE_DMY_RE, ' ').replace(DATE_ISO_RE, ' '));
  return `${d.date}T${time || '00:00:00'}`;
}

function maskDates(text) {
  return text
    .replace(DATE_DMY_RE, (m) => '#'.repeat(m.length))
    .replace(DATE_ISO_RE, (m) => '#'.repeat(m.length))
    .replace(TIME_RE, (m) => '#'.repeat(m.length));
}

function findPhones(text) {
  const out = [];
  for (const m of maskDates(text).matchAll(PHONE_RE)) {
    out.push({ index: m.index, length: m[0].length, phone: normalizePhone(m[1], m[2], m[3], m[4]) });
  }
  return out;
}

function maskPhones(text) {
  return maskDates(text).replace(PHONE_RE, (m) => '§'.repeat(m.length));
}

function findTxnIds(text) {
  const ids = [];
  for (const m of text.matchAll(TXN_RE)) {
    const v = m[1].replace(/[.\-_/]+$/, '').toUpperCase();
    if (!/\d/.test(v) || v.length < 4) continue;
    if (!ids.includes(v)) ids.push(v);
  }
  return ids;
}

/** Bir sətirdə məbləğləri tapır. Hər biri: {index, minor, excluded} */
function findAmounts(line) {
  const masked = maskPhones(line);
  // Bəndlərə bölürük: "5 AZN, balans: 100 AZN" -> balans bəndi istisna olunur
  const clauses = [];
  const splitter = /(, |; |\. |[;|!?])/g;
  let start = 0;
  let m;
  while ((m = splitter.exec(masked)) !== null) {
    clauses.push([start, m.index]);
    start = m.index + m[0].length;
  }
  clauses.push([start, masked.length]);
  const clauseOf = (idx) => {
    for (const [s, e] of clauses) if (idx >= s && idx < e) return masked.slice(s, e);
    return masked;
  };

  const found = [];
  const taken = [];
  const add = (index, length, raw) => {
    if (taken.some(([s, e]) => index < e && index + length > s)) return;
    taken.push([index, index + length]);
    const minor = amountToMinor(raw);
    if (minor === null || minor <= 0) return;
    found.push({ index, minor, excluded: EXCLUDE_KEYWORDS.test(clauseOf(index)) });
  };
  for (const a of masked.matchAll(AMOUNT_CURRENCY_RE)) add(a.index, a[0].length, a[1]);
  for (const a of masked.matchAll(AMOUNT_PREFIX_RE)) add(a.index, a[0].length, a[1]);
  for (const a of masked.matchAll(AMOUNT_LABEL_RE)) add(a.index, a[0].length, a[1]);
  found.sort((x, y) => x.index - y.index);
  return found;
}

function finalize(result, sourceType, base, bonuses) {
  let confidence = base + bonuses;
  if (sourceType === 'IMAGE') confidence -= 15;
  confidence = Math.max(0, Math.min(99, confidence));
  const valid = result.phone && result.amountMinor && result.amountMinor > 0 && result.amountMinor <= MAX_AMOUNT_MINOR;
  if (result.amountMinor > MAX_AMOUNT_MINOR) confidence = Math.min(confidence, 40);
  return {
    ...result,
    confidence,
    status: valid && confidence >= AUTO_CONFIRM_THRESHOLD ? 'CONFIRMED' : 'PENDING',
  };
}

/**
 * Mətndən ödənişləri çıxarır.
 * @param {string} text
 * @param {'TEXT'|'PDF'|'IMAGE'} sourceType
 * @returns {Array<{phone:string|null, amountMinor:number|null, txnId:string|null, paidAt:string|null, confidence:number, status:string, rawLine:string}>}
 */
function parsePayments(text, sourceType = 'TEXT') {
  const t = normalizeText(text);
  const lines = t.split('\n').map((l) => l.trim()).filter(Boolean);
  const docTxnIds = findTxnIds(t);
  const docDate = resolveDateTime(t, t);

  // 1-ci mərhələ: eyni sətirdə nömrə + məbləğ
  const pairs = [];
  for (const line of lines) {
    const phones = findPhones(line);
    if (phones.length !== 1) continue;
    const amounts = findAmounts(line).filter((a) => !a.excluded);
    if (!amounts.length) continue;
    const lineTxn = findTxnIds(line);
    pairs.push({
      phone: phones[0].phone,
      amountMinor: amounts[0].minor,
      txnId: lineTxn[0] || null,
      paidAt: resolveDateTime(line, line) || docDate,
      rawLine: line,
    });
  }
  if (pairs.length) {
    if (pairs.length === 1 && !pairs[0].txnId && docTxnIds.length === 1) pairs[0].txnId = docTxnIds[0];
    return pairs.map((p) => finalize(p, sourceType, 95, p.txnId ? 5 : 0));
  }

  // 2-ci mərhələ: bütün sənəd üzrə (qəbz, PDF, OCR)
  const phones = [...new Set(findPhones(t).map((p) => p.phone))];
  const candidates = [];
  for (const line of lines) {
    const labelled = LABEL_KEYWORDS.test(line);
    for (const a of findAmounts(line)) if (!a.excluded) candidates.push({ minor: a.minor, labelled });
  }
  const distinct = [...new Set(candidates.map((c) => c.minor))];
  const labelledDistinct = [...new Set(candidates.filter((c) => c.labelled).map((c) => c.minor))];

  let amountMinor = null;
  let ambiguous = false;
  let labelled = false;
  if (labelledDistinct.length === 1) {
    amountMinor = labelledDistinct[0];
    labelled = true;
  } else if (labelledDistinct.length === 0 && distinct.length === 1) {
    amountMinor = distinct[0];
  } else if (distinct.length > 0) {
    amountMinor = (labelledDistinct[0] ?? distinct[0]);
    ambiguous = true;
  }

  // Məbləğ olmadan qeyd yaradılmır (məs. imzadakı telefon nömrəsi)
  if (amountMinor === null) return [];

  const phone = phones[0] || null;
  let base;
  if (phones.length === 1 && !ambiguous) base = 85;
  else if (phone) base = 50;
  else base = 30;

  const txnId = docTxnIds.length === 1 ? docTxnIds[0] : null;
  let bonus = 0;
  if (base === 85) {
    if (labelled) bonus += 5;
    if (txnId) bonus += 5;
  }
  return [
    finalize(
      { phone, amountMinor, txnId, paidAt: docDate, rawLine: lines.slice(0, 12).join(' | ') },
      sourceType,
      base,
      bonus
    ),
  ];
}

module.exports = {
  parsePayments,
  normalizePhoneInput,
  formatPhone,
  amountToMinor,
  AUTO_CONFIRM_THRESHOLD,
};
