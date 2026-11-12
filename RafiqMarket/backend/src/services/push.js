import fs from 'node:fs';
import { config } from '../config.js';

let messaging = null;
let initTried = false;

/** firebase-admin yalnız service account faylı mövcud olduqda aktivləşir. */
async function getMessaging() {
  if (initTried) return messaging;
  initTried = true;
  const file = config.firebaseServiceAccountFile;
  if (!file || !fs.existsSync(file)) {
    console.warn('[FCM] FIREBASE_SERVICE_ACCOUNT_FILE yoxdur – push bildirişləri DEAKTİVDİR (WebSocket bildirişləri işləyir).');
    return null;
  }
  const admin = (await import('firebase-admin')).default;
  const credential = admin.credential.cert(JSON.parse(fs.readFileSync(file, 'utf8')));
  admin.initializeApp({ credential });
  messaging = admin.messaging();
  console.log('[FCM] Firebase Cloud Messaging aktivdir');
  return messaging;
}

export async function sendPush(token, { title, body, data = {} }) {
  if (!token) return false;
  const m = await getMessaging();
  if (!m) return false;
  try {
    await m.send({
      token,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: { priority: 'high', notification: { channelId: 'orders', sound: 'default' } },
    });
    return true;
  } catch (e) {
    console.warn('[FCM] göndərilmədi:', e.code || e.message);
    return false;
  }
}
