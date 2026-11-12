import crypto from 'node:crypto';
import { config } from '../config.js';

/**
 * Epoint (epoint.az) – Azərbaycanda işləyən onlayn kart ödəniş provayderi.
 * Protokol (rəsmi sənəd: https://epoint.az/az/api-docs / developer.epoint.az):
 *   data      = base64(JSON)
 *   signature = base64( sha1_raw( private_key + data + private_key ) )
 * Sorğular POST application/x-www-form-urlencoded: data=...&signature=...
 *
 * QEYD: Kart məlumatları heç vaxt bizim serverə gəlmir – müştəri kartı Epoint-in
 * öz təhlükəsiz səhifəsində daxil edir. Private key yalnız serverdədir (APK-da deyil).
 */

export const isConfigured = () => !!(config.epoint.publicKey && config.epoint.privateKey);

export function sign(data, privateKey = config.epoint.privateKey) {
  return crypto.createHash('sha1').update(privateKey + data + privateKey).digest('base64');
}

export function encode(obj) {
  return Buffer.from(JSON.stringify(obj), 'utf8').toString('base64');
}

/** Callback imzasını sabit-zamanlı müqayisə ilə yoxlayır */
export function verify(data, signature, privateKey = config.epoint.privateKey) {
  if (typeof data !== 'string' || typeof signature !== 'string') return false;
  const expected = Buffer.from(sign(data, privateKey));
  const got = Buffer.from(signature);
  return expected.length === got.length && crypto.timingSafeEqual(expected, got);
}

export function decode(data) {
  return JSON.parse(Buffer.from(data, 'base64').toString('utf8'));
}

async function call(path, payload) {
  const data = encode({ public_key: config.epoint.publicKey, ...payload });
  const body = new URLSearchParams({ data, signature: sign(data) });
  const res = await fetch(`${config.epoint.apiBase}/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
    signal: AbortSignal.timeout(20000),
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { throw new Error(`Epoint cavabı oxunmadı: ${res.status} ${text.slice(0, 200)}`); }
  return json;
}

/** Ödəniş səhifəsi yaradır, redirect_url qaytarır */
export async function createPayment({ providerOrderId, amount, description }) {
  const r = await call('request', {
    amount: Number(amount),
    currency: 'AZN',
    language: 'az',
    order_id: providerOrderId,
    description,
    success_redirect_url: `${config.publicBaseUrl}/api/payments/return?result=success&o=${encodeURIComponent(providerOrderId)}`,
    error_redirect_url: `${config.publicBaseUrl}/api/payments/return?result=error&o=${encodeURIComponent(providerOrderId)}`,
    result_url: `${config.publicBaseUrl}/api/payments/epoint/callback`,
  });
  if (r.status !== 'success' || !r.redirect_url) {
    throw new Error(`Epoint ödəniş yaradılmadı: ${r.message || JSON.stringify(r)}`);
  }
  return { redirectUrl: r.redirect_url, transaction: r.transaction };
}

/** Provayderdən ödəniş statusunu soruşur (callback çatmadıqda üzləşdirmə üçün) */
export async function getStatus(providerOrderId) {
  return call('get-status', { order_id: providerOrderId });
}
