'use strict';
const crypto = require('node:crypto');
const config = require('./config');

/** Mail şifrəsini AES-256-GCM ilə şifrələyir: v1:<iv>:<tag>:<ciphertext> (base64) */
function encryptSecret(plain) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', config.credentialsKey(), iv);
  const enc = Buffer.concat([cipher.update(String(plain), 'utf8'), cipher.final()]);
  return ['v1', iv.toString('base64'), cipher.getAuthTag().toString('base64'), enc.toString('base64')].join(':');
}

function decryptSecret(payload) {
  const [v, iv, tag, data] = String(payload).split(':');
  if (v !== 'v1') throw new Error('Naməlum şifrələmə formatı');
  const decipher = crypto.createDecipheriv('aes-256-gcm', config.credentialsKey(), Buffer.from(iv, 'base64'));
  decipher.setAuthTag(Buffer.from(tag, 'base64'));
  return Buffer.concat([decipher.update(Buffer.from(data, 'base64')), decipher.final()]).toString('utf8');
}

const SCRYPT = { N: 16384, r: 8, p: 1, keylen: 64 };

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, SCRYPT.keylen, { N: SCRYPT.N, r: SCRYPT.r, p: SCRYPT.p });
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString('base64')}$${hash.toString('base64')}`;
}

function verifyPassword(password, stored) {
  const parts = String(stored).split('$');
  if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
  const [, N, r, p, salt, hash] = parts;
  const expected = Buffer.from(hash, 'base64');
  const actual = crypto.scryptSync(String(password), Buffer.from(salt, 'base64'), expected.length, {
    N: +N,
    r: +r,
    p: +p,
  });
  return crypto.timingSafeEqual(expected, actual);
}

function newToken() {
  return crypto.randomBytes(32).toString('base64url');
}

function sha256(s) {
  return crypto.createHash('sha256').update(String(s)).digest('hex');
}

module.exports = { encryptSecret, decryptSecret, hashPassword, verifyPassword, newToken, sha256 };
