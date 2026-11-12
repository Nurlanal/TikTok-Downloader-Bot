import { config } from '../config.js';

/**
 * SMS göndərmə. Twilio REST API (rəsmi) istifadə olunur – Azərbaycan nömrələrini dəstəkləyir.
 * Yerli provayder (məs. LSIM, ATL) istifadə etmək istəsəniz, sendSms funksiyasını həmin
 * provayderin rəsmi API-si ilə əvəz edin.
 */
/** Yalnız test/development: göndərilən SMS-lər (production-da istifadə olunmur) */
export const devOutbox = [];

export async function sendSms(phone, text) {
  const { provider } = config.sms;
  if (provider === 'console') {
    if (config.isProd) throw new Error('console SMS production-da qadağandır');
    devOutbox.push({ phone, text });
    if (config.env !== 'test') console.log(`[SMS:console] ${phone}: ${text}`);
    return;
  }
  if (provider === 'twilio') {
    const { twilioSid, twilioToken, twilioFrom } = config.sms;
    if (!twilioSid || !twilioToken || !twilioFrom) throw new Error('Twilio açarları təyin edilməyib');
    const body = new URLSearchParams({ To: phone, From: twilioFrom, Body: text });
    const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${twilioSid}/Messages.json`, {
      method: 'POST',
      headers: {
        Authorization: 'Basic ' + Buffer.from(`${twilioSid}:${twilioToken}`).toString('base64'),
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body,
    });
    if (!res.ok) throw new Error(`Twilio xətası: ${res.status} ${await res.text()}`);
    return;
  }
  throw new Error(`Naməlum SMS_PROVIDER: ${provider}`);
}
