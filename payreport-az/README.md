# PayReport AZ

Mail.ru poçtuna gələn ödəniş bildirişlərini avtomatik oxuyan, ödənişləri tanıyan və mədaxili hesablayan Android tətbiqi + backend.

```
055 777 66 55 — 5 AZN   ×3   →   3 əməliyyat · 15,00 AZN
```

| | |
|---|---|
| Paket | `az.payreport.app` |
| Dil / valyuta | Azərbaycan dili / AZN |
| Android | 8.0+ (minSdk 26, targetSdk 35) |
| Backend | Node.js 22 · Express · MySQL 8 / MariaDB |

## Qovluq strukturu

```
payreport-az/
├── android/                 Android Studio layihəsi (Kotlin, Jetpack Compose, MVVM)
│   └── app/src/main/java/az/payreport/app/
│       ├── core/parser/      Ödəniş tanıma (PaymentParser, PhoneUtils)
│       ├── core/security/    Android Keystore, PIN (SecureStore)
│       ├── data/db/          Room + SQLCipher (şifrələnmiş baza)
│       ├── data/mail/        Mail.ru IMAP (cihazda birbaşa rejim)
│       ├── data/extract/     ML Kit OCR, PDFBox, HTML
│       ├── data/remote/      Backend REST klienti (OkHttp)
│       ├── data/repo/        Admin əməliyyatları, sinxronizasiya
│       ├── domain/           Aylıq / nömrə hesabatları
│       ├── export/           Excel (.xlsx) və PDF ixracı
│       ├── work/             WorkManager fon sinxronizasiyası
│       └── ui/               Dashboard, Nömrələr, Hesabat, Admin, Ayarlar
├── backend/                 REST API + IMAP worker (Node.js)
├── database/schema.sql      MySQL sxemi
├── shared/parser-fixtures.json   Android və backend üçün ortaq parser testləri
├── docs/API.md              API sənədləri
├── docs/SETUP.md            Konfiqurasiya və quraşdırma təlimatı
└── release/                 İmzalanmış release APK-lar
```

## Arxitektura

```
                    ┌──────────── Cihazda (birbaşa) rejim ─────────────┐
  imap.mail.ru:993  │  JavaMail (read-only) → Mətn / PDFBox / ML Kit OCR │
  (SSL/TLS)  ───────┤  → PaymentParser → Room+SQLCipher → Compose UI     │
                    └───────────────────────────────────────────────────┘
                    ┌──────────────── Server rejimi ────────────────────┐
  imap.mail.ru:993  │  imapflow worker → parser → MySQL                  │
  ──────────────────┤        ↑ REST API (HTTPS, Bearer token)            │
                    │  Android ← keyset sync → Room (keş) → Compose UI   │
                    └───────────────────────────────────────────────────┘
```

- **Tanıma**: eyni sətirdə nömrə+məbləğ (95%), qəbz/PDF sahələri (85–95%), OCR (−15%). ≥85% avtomatik təsdiqlənir, qalanı **Gözləyən**ə düşür.
- **Təkrar qoruması**: `UIDVALIDITY:UID` + `Message-ID` (məktub səviyyəsi), `TXN:<əməliyyat ID>` (qlobal), məktub mətni ↔ əlavə müqayisəsi.
- **Hesablama**: yalnız `CONFIRMED` ödənişlər; Bakı vaxtı (UTC+4) ilə gün/həftə/ay.
- **Audit**: yaratma, redaktə, təsdiq, ləğv — hamısı jurnalda; ləğv edilən ödəniş silinmir.

## Təhlükəsizlik

| Təhlükə | Tədbir |
|---------|--------|
| Mail şifrəsi kodda | Kodda heç bir şifrə yoxdur; istifadəçi Ayarlarda daxil edir |
| Cihazda şifrə | Android Keystore AES-256-GCM (`SecureStore`) |
| Cihazda baza | SQLCipher AES-256, açar Keystore ilə qorunur |
| Serverdə mail şifrəsi | AES-256-GCM, açar `.env`-də (`CREDENTIALS_KEY`) |
| API girişi | Bearer token (yalnız SHA-256 heşi saxlanılır), scrypt şifrə heşi, rate-limit |
| Şəbəkə | Yalnız HTTPS/TLS (cleartext qadağandır), IMAP SSL/TLS + sertifikat yoxlaması |
| Admin əməliyyatları | PIN (PBKDF2), audit jurnalı |
| Ehtiyat nüsxə sızması | `allowBackup=false`, bulud/cihaz köçürməsi istisna |

## Sürətli başlanğıc

1. `release/PayReport-AZ-1.0.2-arm64-v8a.apk` faylını telefona quraşdırın.
2. Mail.ru-da **xarici tətbiq parolu** yaradın ([SETUP.md §1](docs/SETUP.md)).
3. Tətbiqdə **Ayarlar → Mail.ru hesabı** → email + parol → **Bağlantını yoxla** → **Saxla**.
4. Paneldə **Yenilə**.

Server rejimi üçün: [docs/SETUP.md §3](docs/SETUP.md).

## Testlər

```bash
cd backend && npm test                                  # parser (21 test)
TEST_DB=1 DB_NAME=payreport_test DB_PASSWORD=… npm test # + MySQL inteqrasiya (12 test)
cd android && ./gradlew testDebugUnitTest               # parser + hesabat + XLSX (9 test)
```
