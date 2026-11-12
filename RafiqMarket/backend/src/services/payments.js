import { query } from '../db.js';
import { conflict, notFound } from '../lib/errors.js';
import * as epoint from './epoint.js';
import { applyPaymentResult } from './orders.js';

/** Müştəri üçün ödəniş sessiyası başladır (sifariş PENDING_PAYMENT olmalıdır) */
export async function startPayment(user, orderId) {
  const { rows } = await query('SELECT * FROM orders WHERE id = $1 AND customer_id = $2', [orderId, user.id]);
  const o = rows[0];
  if (!o) throw notFound('Sifariş tapılmadı');
  if (o.payment_method !== 'ONLINE' || o.status !== 'PENDING_PAYMENT') {
    throw conflict('Bu sifariş üçün ödəniş tələb olunmur.', 'PAYMENT_NOT_REQUIRED');
  }
  if (!epoint.isConfigured()) throw conflict('Onlayn ödəniş hazırda aktiv deyil.', 'ONLINE_PAYMENT_UNAVAILABLE');
  // Hər cəhd üçün unikal provayder sifariş ID-si
  const providerOrderId = `${o.order_number}-${Date.now().toString(36)}`;
  const { redirectUrl, transaction } = await epoint.createPayment({
    providerOrderId, amount: o.total, description: `Rafiq Market ${o.order_number}`,
  });
  await query(
    `INSERT INTO payments (order_id, provider, provider_order_id, transaction_id, amount, redirect_url)
     VALUES ($1,'epoint',$2,$3,$4,$5)`, [o.id, providerOrderId, transaction || null, o.total, redirectUrl]);
  return { redirectUrl };
}

/**
 * Provayder nəticəsini tətbiq edir. Məbləğ uyğunluğu yoxlanılır.
 * Ödəniş uğuru YALNIZ burada – imzası yoxlanılmış callback və ya serverin
 * provayderə etdiyi status sorğusu əsasında qeydə alınır.
 */
export async function applyProviderResult(result) {
  const providerOrderId = result.order_id;
  const { rows } = await query('SELECT * FROM payments WHERE provider_order_id = $1', [providerOrderId]);
  const p = rows[0];
  if (!p) return { ok: false, reason: 'unknown order' };
  const success = result.status === 'success';
  const amountOk = result.amount === undefined || Math.abs(Number(result.amount) - Number(p.amount)) < 0.005;
  const finalSuccess = success && amountOk;
  const isFinal = ['success', 'failed', 'error', 'returned'].includes(result.status);
  if (!isFinal) return { ok: true, pending: true };
  await query(
    `UPDATE payments SET status=$2, transaction_id=COALESCE($3, transaction_id), provider_message=$4, card_mask=$5, updated_at=now()
     WHERE id=$1`,
    [p.id, finalSuccess ? 'PAID' : 'FAILED', result.transaction || null,
      amountOk ? (result.message || null) : 'Məbləğ uyğun gəlmir', result.card_mask || null]);
  await applyPaymentResult(p.order_id, finalSuccess);
  return { ok: true };
}

/** Callback gəlmədikdə serverin özü provayderdən statusu soruşur */
export async function reconcile(orderId) {
  const { rows } = await query(
    `SELECT provider_order_id FROM payments WHERE order_id=$1 AND status='PENDING' ORDER BY id DESC`, [orderId]);
  for (const r of rows) {
    try {
      const st = await epoint.getStatus(r.provider_order_id);
      if (st && st.status) await applyProviderResult({ ...st, order_id: r.provider_order_id });
    } catch (e) {
      console.warn('[payments] status yoxlanılmadı:', e.message);
    }
  }
}

/** Vaxtı keçmiş ödənişlər: əvvəlcə provayderdən yoxla, sonra ləğv et */
export async function expireStale(minutes) {
  const { rows } = await query(
    `SELECT id FROM orders WHERE status='PENDING_PAYMENT' AND created_at < now() - make_interval(mins => $1)`, [minutes]);
  for (const r of rows) {
    if (epoint.isConfigured()) await reconcile(r.id);
    const cur = await query('SELECT status FROM orders WHERE id=$1', [r.id]);
    if (cur.rows[0]?.status === 'PENDING_PAYMENT') await applyPaymentResult(r.id, false);
  }
}
