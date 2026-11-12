import { Router } from 'express';
import express from 'express';
import { ah } from '../lib/errors.js';
import * as epoint from '../services/epoint.js';
import { applyProviderResult } from '../services/payments.js';

const r = Router();

/**
 * Epoint server-to-server callback (result_url).
 * İmza yoxlanılmadan heç bir nəticə qəbul edilmir.
 */
r.post('/epoint/callback', express.urlencoded({ extended: false, limit: '32kb' }), ah(async (req, res) => {
  const { data, signature } = req.body || {};
  if (!epoint.isConfigured() || !epoint.verify(data, signature)) {
    console.warn('[epoint] imzası yanlış callback rədd edildi');
    return res.status(400).json({ ok: false });
  }
  const result = epoint.decode(data);
  await applyProviderResult(result);
  res.json({ ok: true });
}));

/**
 * Ödəniş səhifəsindən sonra brauzer bura yönləndirilir və tətbiqə qaytarılır.
 * Bu səhifə ödənişi TƏSDİQLƏMİR – tətbiq serverdən statusu soruşur.
 */
r.get('/return', (req, res) => {
  const result = req.query.result === 'success' ? 'success' : 'error';
  const deepLink = `rafiqmarket://payment?result=${result}`;
  res.set('Content-Type', 'text/html; charset=utf-8').send(`<!doctype html><html lang="az"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Rafiq Market</title>
<meta http-equiv="refresh" content="0;url=${deepLink}"></head>
<body style="font-family:sans-serif;text-align:center;padding:40px">
<p>Ödəniş prosesi bitdi. Tətbiqə qayıdın.</p>
<p><a href="${deepLink}" style="background:#2E7D32;color:#fff;padding:12px 20px;border-radius:12px;text-decoration:none">Rafiq Market-ə qayıt</a></p>
</body></html>`);
});

export default r;
