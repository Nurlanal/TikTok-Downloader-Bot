# Rafiq Market

A market ordering and delivery system for Xıdırlı village, inside **a single Android APK (`RafiqMarket.apk`)**.
Customers, couriers and admins install the same APK. After login the server checks the user's role and opens the matching interface.
There is no website, no web admin panel, and no separate courier app.

```
RafiqMarket/
├── android/                  Android Studio project (Kotlin, Jetpack Compose, Material 3)
│   ├── app/                  THE ONLY application module → RafiqMarket.apk
│   ├── core/                 network (Retrofit), WebSocket, Room (cart), session, theme, shared components
│   └── feature/
│       ├── auth/             phone + SMS login
│       ├── customer/         customer interface
│       ├── courier/          courier interface + GPS foreground service
│       └── admin/            admin panel
├── backend/                  Node.js REST API + WebSocket + PostgreSQL
│   ├── db/schema.sql         database schema
│   ├── src/                  server code
│   ├── test/                 integration tests (real PostgreSQL)
│   └── deploy/               Caddy (HTTPS) and systemd examples
└── README.md
```

---

## 1. Features that need real service keys

These features **do not work without keys**, and the system does not pretend they do:

| Feature | Service | Where the key goes | Behaviour without a key |
|---|---|---|---|
| SMS login code | **Twilio** (official REST API; supports Azerbaijani numbers) | `backend/.env` → `TWILIO_*` | Production: the server refuses to start. Development (`SMS_PROVIDER=console`): the code is printed only to the server log |
| Online card payment | **Epoint** (epoint.az, Azerbaijan) | `backend/.env` → `EPOINT_PUBLIC_KEY`, `EPOINT_PRIVATE_KEY` | An `ONLINE` order is **rejected** (`ONLINE_PAYMENT_UNAVAILABLE`); only cash works |
| Push notifications | **Firebase Cloud Messaging** | `android/app/google-services.json` + `backend/.env` → `FIREBASE_SERVICE_ACCOUNT_FILE` | No push. In-app notifications over WebSocket keep working |
| Maps (address picker, live tracking, zone drawing) | **Google Maps SDK for Android** | `android/local.properties` → `MAPS_API_KEY` | A "Map is not active" warning appears in place of the map. Zone checking on the server still works |

> The Epoint private key, Twilio token and Firebase service account live **only on the server** and are never put in the APK.
> The Google Maps key has to be in the APK by Google's own design: in Cloud Console, restrict it to the package name `az.rafiqmarket.app` and your release SHA-1.

**Note on Epoint:** the integration uses the documented protocol: `data = base64(JSON)` and `signature = base64(sha1(private_key + data + private_key))`, with endpoints `/api/1/request` and `/api/1/get-status` and a `result_url` callback. Before going live, check the field names against the official documentation in your Epoint merchant account and run one real test payment.

---

## 2. Server setup (Ubuntu 22.04/24.04)

```bash
# 1) Packages
sudo apt install -y postgresql caddy
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs

# 2) Database
sudo -u postgres psql -c "CREATE USER rafiq WITH PASSWORD 'STRONG_PASSWORD';"
sudo -u postgres psql -c "CREATE DATABASE rafiq OWNER rafiq;"

# 3) Code
sudo mkdir -p /opt/rafiq-market && sudo cp -r backend /opt/rafiq-market/
cd /opt/rafiq-market/backend
npm ci --omit=dev            # or: npm install --omit=dev
cp .env.example .env         # fill in every value (see section 1)
#   JWT_SECRET and OTP_PEPPER:  openssl rand -hex 48

# 4) Apply the schema
npm run migrate

# 5) Create the FIRST admin (the market owner's number)
npm run create-admin -- +994501234567 "Rafiq"

# 6) (Optional) sample categories – no products or prices are created
npm run seed-catalog

# 7) Service + HTTPS
sudo useradd -r -s /usr/sbin/nologin rafiq && sudo chown -R rafiq /opt/rafiq-market/backend/uploads
sudo cp deploy/rafiq-market.service /etc/systemd/system/ && sudo systemctl enable --now rafiq-market
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile   # change the domain name
sudo systemctl reload caddy
```

The domain's DNS A record must point to the server. Caddy obtains the HTTPS certificate automatically.
In Epoint's merchant account, set the `result_url` to: `https://<domain>/api/payments/epoint/callback`.

### Tests
```bash
cd backend
createdb rafiq_test   # a test database is needed
npm test              # 20 integration tests: auth, roles, zone, stock race, order flow, GPS, Epoint signature
```

---

## 3. Building the APK (Android Studio or command line)

Requirements: Android Studio (Ladybug or newer) or JDK 17+ and the Android SDK (platform 35).

```bash
cd android
cp local.properties.example local.properties     # sdk.dir, RAFIQ_API_URL, MAPS_API_KEY
# Firebase: Firebase Console → Android app (package: az.rafiqmarket.app) → google-services.json → android/app/

# Release signing key (once only; keep it safe – you need it for updates)
keytool -genkeypair -v -keystore rafiqmarket-release.jks -alias rafiqmarket -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # fill in the passwords

./gradlew assembleRelease
# Result: android/app/build/outputs/apk/release/RafiqMarket.apk
```

- The release build **refuses to build** without an `https://` value for `RAFIQ_API_URL`.
- The debug build (`./gradlew assembleDebug` → `RafiqMarket-debug.apk`) allows HTTP for a local server. Its package name is `az.rafiqmarket.app.debug`.
- Contract tests between app and server: `./gradlew :core:testDebugUnitTest`

Install: send `RafiqMarket.apk` to the phone, allow "Install unknown apps", and install.
Use the same APK for customers, couriers and the admin.

---

## 4. First launch: market owner's steps

1. Log in with the admin number (SMS code). The **admin panel** opens.
2. **More → Delivery zone**: on the map, tap points along Xıdırlı village's border → **Save**.
   *The boundary is not pre-filled. Until it is drawn, orders are not accepted.*
3. **More → Delivery settings**: delivery fee, minimum order amount, free delivery threshold, hours, accept-orders switch.
4. **Products → Categories**, then **Product add**: photo, price, unit, stock, barcode (scan with the camera).
5. **Couriers → Add courier**: name + number. The courier installs the same APK and logs in with that number via SMS, and the **courier interface** opens automatically.
6. **More → Advertising banners** (optional).

---

## 5. Roles and security

- A newly registered user is always `CUSTOMER`. The `COURIER` and `ADMIN` roles can be assigned only by an admin (`/api/admin/*`).
- Every request: the JWT is verified, then the user is **read from the database** (role, active status). A role change or deactivation takes effect immediately.
- Routes: `/api/customer/*` → CUSTOMER only, `/api/courier/*` → COURIER only, `/api/admin/*` → ADMIN only (`requireRole`).
- OTP: 6 digits, 5 minutes, at most 5 attempts, at most 5 codes per number per hour, 60 seconds between codes. Codes are stored hashed.
- Refresh tokens rotate (a used token cannot be reused) and are stored hashed.
- Release builds allow only HTTPS (`network_security_config`). In production the server rejects HTTP requests.
- `allowBackup=false`: the session and cart do not go into backups.
- **Stock:** `UPDATE ... SET stock = stock - q WHERE stock >= q` inside a transaction, so parallel orders cannot oversell (verified by a test).
- **Prices** are always taken from the server. Prices sent by the app are ignored.
- **Payment:** card details never reach our server. Success is recorded only from a **signature-verified** Epoint callback or from the server's own status query. The amount is checked too.
- **GPS:** only the courier's **last** position is stored (no movement history). It is accepted only while there is an active delivery. Customers see only their own order's courier.

## 6. Order stages

`PLACED` Order sent → `ACCEPTED` Market accepted the order → `PREPARING` Items being prepared → `COURIER_ASSIGNED` Courier assigned → `PICKED_UP` Courier picked up the order → `ON_THE_WAY` Order on the way → `ARRIVED` Courier arrived at the address → `DELIVERED` Order delivered
(+ `PENDING_PAYMENT` for online payment, `CANCELLED`). Every transition is checked on the server by role and current status.
For cash payment, the order cannot be closed until the courier confirms receiving the money.

## 7. Live GPS (Android requirements)

- Location sharing starts only after the courier picks up the order. A **privacy disclosure** is shown before the permission request.
- `ForegroundService` (`foregroundServiceType="location"`) plus a permanent notification. Only "while in use" permission is requested. **`ACCESS_BACKGROUND_LOCATION` is not used.**
- When the order is delivered or cancelled, the server rejects new positions and the service stops itself.
- If the courier's connection drops, the customer sees the last known position and the time of the last update.

## 8. Main API endpoints

| | |
|---|---|
| `POST /api/auth/otp/request`, `/otp/verify`, `/refresh`, `/logout` | Login |
| `GET /api/me`, `PUT /api/me/fcm-token`, `GET /api/me/notifications` | Profile |
| `GET /api/catalog/home`, `/products` | Catalog |
| `POST /api/customer/orders`, `/orders/quote`, `GET /orders/:id/tracking`, `POST /orders/:id/pay` | Customer |
| `GET /api/courier/orders?tab=`, `POST /orders/:id/accept|reject|status|confirm-cash`, `POST /location` | Courier |
| `/api/admin/products|categories|banners|orders|couriers|customers|users|stats|settings|settings/zone|uploads` | Admin |
| `POST /api/payments/epoint/callback` | Epoint (signed) |
| `wss://…/ws` | Real time: `order.updated`, `courier.location`, `notification` |
