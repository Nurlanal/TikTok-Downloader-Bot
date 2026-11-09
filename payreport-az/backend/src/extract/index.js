'use strict';
const config = require('../config');

/** HTML məktubdan sadə mətn çıxarır (sətir strukturunu saxlayaraq). */
function htmlToText(html) {
  return String(html || '')
    .replace(/<(script|style|head)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|tr|li|h[1-6]|table|section)>/gi, '\n')
    .replace(/<\/(td|th)>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/[ \t]+/g, ' ')
    .replace(/\n\s*\n+/g, '\n')
    .trim();
}

/** PDF faylından mətn çıxarır. */
async function pdfToText(buffer) {
  // pdf-parse-ın index.js faylı debug rejimində test faylı oxuyur, ona görə lib-i birbaşa çağırırıq
  const pdfParse = require('pdf-parse/lib/pdf-parse.js');
  const res = await pdfParse(buffer);
  return res.text || '';
}

let ocrWorkerPromise = null;

/** Şəkil qəbzini OCR ilə tanıyır (OCR_ENABLED=1 olduqda). */
async function imageToText(buffer) {
  if (!config.ocrEnabled) return null;
  let Tesseract;
  try {
    Tesseract = require('tesseract.js');
  } catch (e) {
    throw new Error('tesseract.js quraşdırılmayıb (npm install tesseract.js)');
  }
  if (!ocrWorkerPromise) ocrWorkerPromise = Tesseract.createWorker(config.ocrLanguages);
  const worker = await ocrWorkerPromise;
  const { data } = await worker.recognize(buffer);
  return data.text || '';
}

/**
 * mailparser nəticəsindən analiz ediləcək hissələri qaytarır.
 * @returns {Promise<Array<{kind:'TEXT'|'PDF'|'IMAGE', text:string, name:string}>>}
 */
async function extractParts(mail) {
  const parts = [];
  const body = mail.text && mail.text.trim() ? mail.text : htmlToText(mail.html || '');
  if (body && body.trim()) parts.push({ kind: 'TEXT', text: body, name: 'body' });

  for (const att of mail.attachments || []) {
    const type = String(att.contentType || '').toLowerCase();
    const name = att.filename || 'attachment';
    try {
      if (type === 'application/pdf' || /\.pdf$/i.test(name)) {
        const text = await pdfToText(att.content);
        if (text.trim()) parts.push({ kind: 'PDF', text, name });
      } else if (type.startsWith('image/')) {
        const text = await imageToText(att.content);
        if (text && text.trim()) parts.push({ kind: 'IMAGE', text, name });
      }
    } catch (e) {
      parts.push({ kind: 'ERROR', text: '', name, error: e.message });
    }
  }
  return parts;
}

module.exports = { htmlToText, pdfToText, imageToText, extractParts };
