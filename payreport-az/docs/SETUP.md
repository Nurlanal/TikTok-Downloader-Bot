# PayReport AZ — Konfiqurasiya təlimatı

Tətbiq iki rejimdə işləyir:

| Rejim | Kimə uyğundur | Nə lazımdır |
|-------|---------------|-------------|
| **Cihazda (birbaşa)** | Bir telefon, server yoxdur | Yalnız APK + Mail.ru tətbiq parolu |
| **Server** | Bir neçə cihaz, 24/7 avtomatik oxuma | Linux server, MySQL, domen + HTTPS |

---

## 1. Mail.ru tətbiq parolunun yaradılması (hər iki rejim üçün)

Mail.ru xarici proqramların əsas şifrə ilə qoşulmasına icazə vermir. Ayrıca parol yaradın:

1. <https://account.mail.ru> → **Təhlükəsizlik** (Безопасность / Security).
2. İki mərhələli doğrulamanı aktiv edin (tələb olunarsa).
3. **Xarici tətbiqlər üçün parollar** (Пароли для внешних приложений) → **Əlavə et**.
4. Ad yazın (məs. `PayReport AZ`), giriş növü: **IMAP / tam giriş**.
5. Yaradılan parolu kopyalayın — onu PayReport AZ-a daxil edəcəksiniz.

Mail.ru ayarlarında **Poçt proqramları → IMAP** girişinin aktiv olduğunu yoxlayın.

IMAP parametrləri (avtomatik doldurulur):

| Parametr | Dəyər |
|----------|-------|
| Server | `imap.mail.ru` |
| Port | `993` |
| Şifrələmə | SSL/TLS |

> Sistem məktubları **yalnız oxuyur**: qovluq read-only açılır, `BODY.PEEK` istifadə olunur — məktublar dəyişdirilmir, silinmir, hətta "oxunmuş" kimi işarələnmir.

---

## 2. Android tətbiqi — birbaşa rejim

1. `release/` qovluğundan APK-nı telefona köçürün:
   - `PayReport-AZ-1.0.3-arm64-v8a.apk` — 2017-dən sonrakı demək olar bütün telefonlar
   - `PayReport-AZ-1.0.3-armeabi-v7a.apk` — köhnə 32-bit telefonlar
2. Quraşdırarkən "Naməlum mənbələrdən quraşdırma"ya icazə verin.
3. **Ayarlar → İş rejimi → Cihazda**.
4. **Mail.ru hesabı**: email və tətbiq parolunu daxil edin → **Bağlantını yoxla** → **Saxla**.
5. İstəyə görə **Əlavə ayarlar**:
   - *Göndərən filtri*: yalnız bu ünvanlardan/domenlərdən gələn məktublar oxunsun (məs. `bank.az, noreply@pay.az`).
   - *Mövzu filtri*: mövzuda bu sözlər olsun (məs. `ödəniş`).
   - *İlk sinxronizasiya*: ilk dəfə neçə günlük məktub oxunsun (standart 30).
6. **Ayarlar → Təhlükəsizlik**: Admin PIN təyin edin.
7. Paneldə **Yenilə** düyməsi ilə ilk sinxronizasiyanı başladın.

Fon sinxronizasiyası WorkManager ilə ən az 15 dəqiqədən bir işləyir (Android məhdudiyyəti).

### Cihazda təhlükəsizlik
- Mail şifrəsi, API token və PIN heşi **Android Keystore** açarı (AES-256-GCM) ilə şifrələnir.
- Verilənlər bazası **SQLCipher** (AES-256) ilə şifrələnir; açar təsadüfi yaradılır və Keystore ilə qorunur.
- Bulud ehtiyat nüsxəsi söndürülüb (məlumat başqa cihaza köçmür).
- Şəbəkə: yalnız HTTPS/TLS; açıq mətnli HTTP qadağandır.

---

## 3. Backend server (server rejimi)

### Tələblər
- Ubuntu 22.04/24.04 (və ya istənilən Linux)
- Node.js 20+ (22 tövsiyə olunur)
- MySQL 8.0+ və ya MariaDB 10.6+
- Domen və HTTPS sertifikatı (Let's Encrypt)

### 3.1. MySQL

```bash
sudo apt install mysql-server
sudo mysql <<'SQL'
CREATE DATABASE payreport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'payreport'@'127.0.0.1' IDENTIFIED BY 'GÜCLÜ_ŞİFRƏ';
GRANT ALL ON payreport.* TO 'payreport'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
```

### 3.2. Backend

```bash
sudo useradd -r -m -d /opt/payreport payreport
sudo -u payreport git clone <repo> /opt/payreport/src
cd /opt/payreport/src/payreport-az/backend
sudo -u payreport npm ci --omit=dev
sudo -u payreport cp .env.example .env
sudo -u payreport chmod 600 .env
npm run gen-key            # çıxan dəyəri .env-də CREDENTIALS_KEY-ə yazın
nano .env                  # DB_PASSWORD və s.
sudo -u payreport npm run migrate
sudo -u payreport npm run create-admin -- admin 'ən-azı-10-simvolluq-şifrə'
```

> `CREDENTIALS_KEY` mail şifrəsini şifrələyir. Onu itirsəniz, mail parolunu yenidən daxil etməlisiniz. Açarı `.env`-dən başqa yerdə saxlamayın və git-ə əlavə etməyin.

### 3.3. systemd xidməti

`/etc/systemd/system/payreport.service`:
```ini
[Unit]
Description=PayReport AZ API və mail worker
After=network-online.target mysql.service

[Service]
User=payreport
WorkingDirectory=/opt/payreport/src/payreport-az/backend
ExecStart=/usr/bin/node src/server.js
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/payreport
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now payreport
journalctl -u payreport -f
```

### 3.4. HTTPS (nginx + Let's Encrypt)

`/etc/nginx/sites-available/payreport`:
```nginx
server {
    server_name api.sizin-domen.az;
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
        client_max_body_size 4m;
    }
    location / { return 404; }
}
```
```bash
sudo ln -s /etc/nginx/sites-available/payreport /etc/nginx/sites-enabled/
sudo certbot --nginx -d api.sizin-domen.az
```
`.env`-də `HOST=127.0.0.1` və `TRUST_PROXY=1` olsun ki, API yalnız nginx üzərindən əlçatan olsun.

Alternativ: `TLS_CERT_FILE` və `TLS_KEY_FILE` təyin etsəniz, Node.js özü HTTPS ilə işləyir.

### 3.5. Yoxlama

```bash
curl https://api.sizin-domen.az/api/health
```

### 3.6. Tətbiqi serverə qoşmaq
1. **Ayarlar → İş rejimi → Server**.
2. Server ünvanı (`https://api.sizin-domen.az`), admin istifadəçi adı və şifrəsi → **Daxil ol**.
3. **Mail.ru hesabı (serverdə)**: email + tətbiq parolu → **Yoxla** → **Serverə saxla**.
4. Server hər `SYNC_INTERVAL_MINUTES` (standart 5) dəqiqədən bir poçtu oxuyur; tətbiq dəyişiklikləri fon rejimində çəkir.

### 3.7. Şəkil qəbzləri üçün server OCR (istəyə görə)
`.env`: `OCR_ENABLED=1`. İlk istifadədə `tesseract.js` dil fayllarını (`eng+aze`) CDN-dən yükləyir — serverin internetə çıxışı olmalıdır. Söndürülübsə, şəkil qəbzlərini tətbiqdə **Admin → Qəbz skan** ilə (ML Kit, cihazda) oxuya bilərsiniz.

### 3.8. Ehtiyat nüsxə
```bash
mysqldump --single-transaction payreport | gzip > payreport-$(date +%F).sql.gz
```
`.env` faylını (xüsusən `CREDENTIALS_KEY`) ayrıca təhlükəsiz yerdə saxlayın.

---

## 4. Ödəniş tanıma qaydaları

Dəstəklənən formatlar (mətn, PDF, OCR):

```
055 777 66 55 — 5 AZN
+994 50 123 45 67 nömrəsindən 12,50 AZN ödəniş daxil oldu. Əməliyyat ID: TRX123456
Telefon: (070) 555-44-33 / Məbləğ: 20.00 AZN / Qəbz №: 884422 / Tarix: 2026-11-08 09:15
994551234567 - 3.5 azn
050 222 33 44 → 8 ₼
```

- Telefon: `+994`, `994`, `0` prefiksli; operator kodları 10, 12, 50, 51, 55, 60, 70, 77, 99.
- Məbləğ: `AZN`, `azn`, `₼`, `manat`; `5`, `5.50`, `5,50`, `1 250,50`, `1,250.50`.
- Balans, komissiya, bonus, keşbek, endirim məbləğləri nəzərə alınmır.
- Əməliyyat ID: `Əməliyyat ID`, `Transaction ID`, `TRX ID`, `Qəbz №`, `Çek №`, `RRN`, `Ref`, `ID`.
- Tarix: `09.11.2026 14:22`, `2026-11-08 09:15:30`; yoxdursa məktubun tarixi götürülür.

**Avtomatik təsdiq**: dəqiqlik ≥ 85% olduqda. Əks halda (məs. OCR, bir neçə fərqli məbləğ, nömrə tapılmayıb) ödəniş **Gözləyən** bölməsinə düşür və mədaxilə daxil edilmir.

**Təkrar qoruması**:
1. Eyni məktub (UID / Message-ID) ikinci dəfə emal olunmur.
2. Eyni əməliyyat ID başqa məktubda gəlsə belə bir dəfə sayılır.
3. Eyni məktubun mətni və PDF əlavəsi eyni ödənişi göstərirsə, bir dəfə sayılır.
4. Fərqli məktublarla gələn eyni məbləğlər (məs. 3 × `055 777 66 55 — 5 AZN`) ayrı-ayrı ödəniş kimi sayılır → 15 AZN.

Qaydaları dəyişəndə həm `backend/src/parser/index.js`, həm də `android/.../PaymentParser.kt` faylını yeniləyin və `shared/parser-fixtures.json`-a nümunə əlavə edin — hər iki tərəfin testləri eyni faylı yoxlayır.

---

## 5. Mənbədən yığma (build)

### Backend testləri
```bash
cd backend
npm ci
npm test                                     # parser testləri
TEST_DB=1 DB_NAME=payreport_test DB_USER=… DB_PASSWORD=… npm test   # + MySQL inteqrasiya testləri
```

### Android
Tələblər: JDK 17+, Android SDK (platform 35, build-tools 35).
```bash
cd android
./gradlew testDebugUnitTest      # unit testlər
./gradlew assembleDebug          # debug APK
```
Release APK üçün `android/keystore.properties` yaradın (git-ə düşmür):
```properties
storeFile=/tam/yol/payreport-release.jks
storePassword=…
keyAlias=payreport
keyPassword=…
```
```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-arm64-v8a-release.apk və s.
```
> Tətbiqi yeniləmək üçün **eyni imza açarından** istifadə etməlisiniz. `.jks` faylını və şifrəsini təhlükəsiz yerdə saxlayın.
