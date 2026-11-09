# PayReport AZ — REST API sənədləri

Baza ünvanı: `https://<sizin-domen>/api`
Format: JSON (UTF-8). Bütün vaxtlar ISO-8601 UTC formatındadır (`2026-11-09T10:22:00.000Z`).
Məbləğlər **qəpiklə** (`amountMinor`) ötürülür: `5 AZN = 500`.

## Autentifikasiya

`/health` və `/auth/login` istisna olmaqla bütün sorğular Bearer token tələb edir:

```
Authorization: Bearer <token>
```

Tokenlər 30 gün etibarlıdır (`TOKEN_TTL_DAYS`). Bazada yalnız SHA-256 heşi saxlanılır.
Giriş cəhdləri 15 dəqiqədə 10 uğursuz cəhdlə məhdudlaşdırılır, ümumi API limiti dəqiqədə 300 sorğudur.

Rollar: `admin` (tam hüquq), `viewer` (yalnız oxuma). ✱ ilə işarələnmiş endpointlər admin tələb edir.

### Xəta formatı

```json
{ "error": "Telefon nömrəsi düzgün deyil" }
```

| Kod | Mənası |
|-----|--------|
| 400 | Yanlış məlumat |
| 401 | Token yoxdur / etibarsızdır |
| 403 | Admin hüququ lazımdır |
| 404 | Tapılmadı |
| 409 | Konflikt (məs. əməliyyat ID artıq var) |
| 429 | Çox sayda sorğu |

---

## Sağlamlıq

### `GET /health`
```json
{ "status": "ok", "db": true, "version": "1.0.0", "time": "2026-11-09T10:00:00.000Z" }
```

## Auth

### `POST /auth/login`
```json
{ "username": "admin", "password": "••••••••••", "deviceName": "Samsung SM-A546" }
```
Cavab:
```json
{ "token": "x2Q…", "expiresAt": "2026-12-09T10:00:00.000Z", "user": { "id": 1, "username": "admin", "role": "admin" } }
```

### `POST /auth/logout`
Cari tokeni ləğv edir.

### `GET /auth/me`

### `POST /auth/change-password` ✱
```json
{ "currentPassword": "…", "newPassword": "ən azı 10 simvol" }
```
Digər bütün tokenlər ləğv olunur.

---

## Ödənişlər

### Ödəniş obyekti
```json
{
  "id": 42,
  "dedupeKey": "TXN:TRX123456",
  "phone": "+994557776655",
  "phoneFormatted": "055 777 66 55",
  "amountMinor": 500,
  "currency": "AZN",
  "paidAt": "2026-11-09T10:22:00.000Z",
  "status": "CONFIRMED",
  "source": "TEXT",
  "confidence": 95,
  "txnId": "TRX123456",
  "emailMessageId": "<abc@bank.az>",
  "emailSubject": "Ödəniş bildirişi",
  "emailFrom": "bildiris@bank.az",
  "rawText": "055 777 66 55 — 5 AZN",
  "note": null,
  "cancelReason": null,
  "createdBy": "mail-worker",
  "createdAt": "…",
  "updatedAt": "…"
}
```

- `status`: `CONFIRMED` (mədaxilə daxildir), `PENDING` (yoxlama gözləyir), `CANCELLED` (ləğv edilib, silinmir)
- `source`: `TEXT` (email mətni), `PDF`, `IMAGE` (OCR), `MANUAL` (əl ilə)
- `dedupeKey`: təkrarların qarşısını alan unikal açar — `TXN:<əməliyyat ID>`, `MSG:<Message-ID>#<sıra>`, `MAN:<uuid>`, `SCAN:<istinad>#<sıra>`

### `GET /payments`
Query parametrləri:

| Parametr | Təsvir |
|----------|--------|
| `status` | `CONFIRMED` / `PENDING` / `CANCELLED` |
| `phone` | Tam nömrə (istənilən formatda) və ya qismən rəqəmlər |
| `from`, `to` | ISO tarix aralığı (`paid_at`) |
| `q` | Əməliyyat ID, qeyd, mövzu üzrə axtarış |
| `limit`, `offset` | Səhifələmə (maks. 1000) |
| `updated_since`, `after_id` | Mobil sinxronizasiya üçün keyset səhifələmə: `(updated_at, id)` üzrə artan sıra |

Cavab: `{ "items": [Ödəniş…], "serverTime": "…" }`

### `GET /payments/{id}`

### `POST /payments` ✱ — əl ilə ödəniş
```json
{ "phone": "055 777 66 55", "amount": "5.00", "paidAt": "2026-11-09T10:00:00Z", "txnId": "opsional", "note": "Nağd" }
```
`amount` əvəzinə `amountMinor` (tam ədəd) də göndərilə bilər. Cavab: `201` + Ödəniş.

### `PATCH /payments/{id}` ✱ — redaktə
Göndərilən sahələr dəyişdirilir: `phone`, `amount`/`amountMinor`, `paidAt`, `txnId`, `note`.
Hər dəyişiklik audit jurnalına `{sahə: {from, to}}` kimi yazılır.

### `POST /payments/{id}/confirm` ✱
Gözləyən ödənişi təsdiqləyir. Telefon və məbləğ boşdursa `400`.

### `POST /payments/{id}/cancel` ✱
```json
{ "reason": "Səhv daxil edilib" }
```
Ödəniş silinmir — statusu `CANCELLED` olur, audit jurnalına əvvəlki vəziyyətin tam surəti yazılır.

### `POST /payments/ingest-text` ✱
Mobil tətbiqin ML Kit OCR və ya PDF ilə oxuduğu qəbz mətnini qəbul edir. Nəticə həmişə `PENDING` olur.
```json
{ "text": "Telefon: 077 222 33 44\nMəbləğ: 7 AZN", "source": "IMAGE", "reference": "content://…/receipt.jpg" }
```
Cavab: `{ "created": 1, "duplicates": 0, "payments": [Ödəniş…] }`. Eyni `reference` ikinci dəfə göndərilərsə təkrar sayılmır.

---

## Statistika və hesabatlar

Bütün hesablamalar yalnız `CONFIRMED` ödənişlər üzrə, Bakı vaxtı (UTC+4) ilə aparılır.

### `GET /stats/summary`
```json
{
  "todayMinor": 1500, "yesterdayMinor": 2000, "weekMinor": 9500, "monthMinor": 47500,
  "totalMinor": 250000, "totalCount": 500, "pendingCount": 3, "currency": "AZN"
}
```
Həftə bazar ertəsindən başlayır.

### `GET /reports/phone/{phone}`
```json
{
  "phone": "+994557776655", "phoneFormatted": "055 777 66 55",
  "count": 3, "totalMinor": 1500, "lastPaidAt": "…", "pendingCount": 0,
  "monthly": [{ "month": "2026-11", "count": 3, "totalMinor": 1500 }],
  "payments": [Ödəniş…]
}
```

### `GET /reports/monthly?year=2026&month=11`
```json
{
  "year": 2026, "month": 11, "totalMinor": 47500, "count": 9,
  "daily": [{ "date": "2026-11-01", "count": 0, "totalMinor": 0 }, …],
  "byPhone": [{ "phone": "+994…", "phoneFormatted": "0…", "count": 3, "totalMinor": 1500, "lastPaidAt": "…" }],
  "topPayers": [ … ilk 10 … ]
}
```

---

## Mail.ru konfiqurasiyası

### `GET /mail/config` ✱
Şifrə heç vaxt qaytarılmır (`passwordSet: true`).

### `PUT /mail/config` ✱
```json
{
  "email": "hesab@mail.ru",
  "password": "xarici-tətbiq-parolu",
  "host": "imap.mail.ru", "port": 993, "secure": true, "mailbox": "INBOX",
  "senderFilter": "bank.az, noreply@pay.az",
  "subjectFilter": "ödəniş",
  "initialDays": 30,
  "enabled": true
}
```
`password` yalnız dəyişdirmək istədikdə göndərilir; AES-256-GCM ilə şifrələnib saxlanılır.

### `POST /mail/test` ✱
Göndərilən (və ya saxlanmış) parametrlərlə IMAP bağlantısını yoxlayır, poçt qutusunu dəyişmir.
```json
{ "ok": true, "messages": 1240, "unseen": 3, "latencyMs": 812 }
```
```json
{ "ok": false, "error": "Autentifikasiya uğursuz oldu. Mail.ru üçün \"Xarici tətbiqlər üçün parol\" istifadə edin." }
```

### `GET /mail/status`
Hesab vəziyyəti, son sinxronizasiya və emal olunmuş məktubların statistikası.

## Sinxronizasiya

### `POST /sync/run` ✱
IMAP sinxronizasiyasını dərhal icra edir (eyni anda yalnız bir icra).
```json
{ "runId": 17, "status": "SUCCESS", "fetched": 4, "created": 3, "pending": 1, "duplicates": 0, "errors": 0,
  "message": "4 məktub oxundu, 3 təsdiqlənmiş, 1 gözləyən, 0 təkrar" }
```

### `GET /sync/runs?limit=50`
Sinxronizasiya tarixçəsi.

## Audit

### `GET /audit` ✱
| Parametr | Təsvir |
|----------|--------|
| `since_id` | Bu ID-dən sonrakı qeydlər (artan sıra) |
| `payment_id` | Yalnız bir ödənişin tarixçəsi |
| `limit` | maks. 1000 |

```json
{ "items": [{ "id": 9, "paymentId": 42, "action": "CANCEL", "actor": "admin", "details": { "from": "CONFIRMED", "reason": "…" }, "ip": "…", "createdAt": "…" }] }
```
Əməliyyatlar: `CREATE`, `UPDATE`, `CONFIRM`, `CANCEL`, `LOGIN`, `LOGIN_FAILED`, `MAIL_CONFIG`, `PASSWORD_CHANGE`.
