-- Rafiq Market – PostgreSQL sxemi
-- İdempotentdir: "npm run migrate" bu faylı təkrar icra edə bilər.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$ BEGIN
  CREATE TYPE user_role AS ENUM ('CUSTOMER', 'COURIER', 'ADMIN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE order_status AS ENUM (
    'PENDING_PAYMENT',   -- onlayn ödəniş gözlənilir (marketə hələ görünmür)
    'PLACED',            -- 1. Sifariş göndərildi
    'ACCEPTED',          -- 2. Market sifarişi qəbul etdi
    'PREPARING',         -- 3. Məhsullar hazırlanır
    'COURIER_ASSIGNED',  -- 4. Kuryer təyin edildi
    'PICKED_UP',         -- 5. Kuryer sifarişi götürdü
    'ON_THE_WAY',        -- 6. Sifariş yoldadır
    'ARRIVED',           -- 7. Kuryer ünvana çatdı
    'DELIVERED',         -- 8. Sifariş təhvil verildi
    'CANCELLED'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE payment_method AS ENUM ('CASH', 'ONLINE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE payment_status AS ENUM ('PENDING', 'PAID', 'FAILED', 'REFUND_REQUIRED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  phone         VARCHAR(20) NOT NULL UNIQUE,
  name          VARCHAR(120) NOT NULL DEFAULT '',
  role          user_role NOT NULL DEFAULT 'CUSTOMER',
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  fcm_token     TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS users_role_idx ON users(role);

CREATE TABLE IF NOT EXISTS otp_codes (
  id          BIGSERIAL PRIMARY KEY,
  phone       VARCHAR(20) NOT NULL,
  code_hash   CHAR(64) NOT NULL,
  attempts    INT NOT NULL DEFAULT 0,
  expires_at  TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS otp_phone_idx ON otp_codes(phone, created_at DESC);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  CHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS categories (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  icon        VARCHAR(16) NOT NULL DEFAULT '🛒',
  color       VARCHAR(9)  NOT NULL DEFAULT '#2E7D32',
  sort_order  INT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS products (
  id             BIGSERIAL PRIMARY KEY,
  category_id    BIGINT REFERENCES categories(id) ON DELETE SET NULL,
  name           VARCHAR(160) NOT NULL,
  description    TEXT NOT NULL DEFAULT '',
  price          NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  old_price      NUMERIC(10,2) CHECK (old_price IS NULL OR old_price >= 0),
  unit           VARCHAR(20) NOT NULL DEFAULT 'ədəd',
  stock          INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
  low_stock_threshold INT NOT NULL DEFAULT 5,
  barcode        VARCHAR(64),
  image_url      TEXT,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  is_new         BOOLEAN NOT NULL DEFAULT FALSE,
  sold_count     INT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS products_category_idx ON products(category_id);
CREATE UNIQUE INDEX IF NOT EXISTS products_barcode_uq ON products(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX IF NOT EXISTS products_name_idx ON products(lower(name));

CREATE TABLE IF NOT EXISTS banners (
  id          BIGSERIAL PRIMARY KEY,
  title       VARCHAR(120) NOT NULL,
  subtitle    VARCHAR(200) NOT NULL DEFAULT '',
  image_url   TEXT,
  color       VARCHAR(9) NOT NULL DEFAULT '#1B5E20',
  category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tək sətirli parametrlər cədvəli
CREATE TABLE IF NOT EXISTS settings (
  id                     SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  delivery_fee           NUMERIC(10,2) NOT NULL DEFAULT 1.50,
  min_order_amount       NUMERIC(10,2) NOT NULL DEFAULT 5.00,
  free_delivery_threshold NUMERIC(10,2),          -- NULL = pulsuz çatdırılma yoxdur
  open_time              TIME NOT NULL DEFAULT '09:00',
  close_time             TIME NOT NULL DEFAULT '22:00',
  orders_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
  -- Admin tərəfindən xəritədə çəkilən çatdırılma zonası: [{"lat":..,"lng":..}, ...]
  -- Əvvəlcədən doldurulmur. Boşdursa sifariş qəbul edilmir.
  delivery_zone          JSONB,
  zone_name              VARCHAR(80) NOT NULL DEFAULT 'Xıdırlı kəndi',
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO settings (id) VALUES (1) ON CONFLICT DO NOTHING;

CREATE SEQUENCE IF NOT EXISTS order_number_seq START 1001;

CREATE TABLE IF NOT EXISTS orders (
  id                 BIGSERIAL PRIMARY KEY,
  order_number       VARCHAR(20) NOT NULL UNIQUE,
  customer_id        BIGINT NOT NULL REFERENCES users(id),
  courier_id         BIGINT REFERENCES users(id),
  status             order_status NOT NULL,
  payment_method     payment_method NOT NULL,
  payment_status     payment_status NOT NULL DEFAULT 'PENDING',
  subtotal           NUMERIC(10,2) NOT NULL,
  delivery_fee       NUMERIC(10,2) NOT NULL,
  total              NUMERIC(10,2) NOT NULL,
  -- Ünvan
  address_street     VARCHAR(160) NOT NULL,
  address_house      VARCHAR(40)  NOT NULL,
  address_note       VARCHAR(300) NOT NULL DEFAULT '',
  contact_phone      VARCHAR(20)  NOT NULL,
  contact_name       VARCHAR(120) NOT NULL DEFAULT '',
  lat                DOUBLE PRECISION NOT NULL,
  lng                DOUBLE PRECISION NOT NULL,
  customer_note      VARCHAR(300) NOT NULL DEFAULT '',
  cancel_reason      VARCHAR(300),
  courier_accepted_at TIMESTAMPTZ,
  cash_confirmed_at  TIMESTAMPTZ,
  delivered_at       TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS orders_customer_idx ON orders(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS orders_courier_idx ON orders(courier_id, status);
CREATE INDEX IF NOT EXISTS orders_status_idx ON orders(status, created_at DESC);

CREATE TABLE IF NOT EXISTS order_items (
  id           BIGSERIAL PRIMARY KEY,
  order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id   BIGINT REFERENCES products(id) ON DELETE SET NULL,
  product_name VARCHAR(160) NOT NULL,
  unit         VARCHAR(20) NOT NULL,
  unit_price   NUMERIC(10,2) NOT NULL,
  quantity     INT NOT NULL CHECK (quantity > 0),
  line_total   NUMERIC(10,2) NOT NULL
);
CREATE INDEX IF NOT EXISTS order_items_order_idx ON order_items(order_id);

CREATE TABLE IF NOT EXISTS order_status_history (
  id          BIGSERIAL PRIMARY KEY,
  order_id    BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  status      order_status NOT NULL,
  changed_by  BIGINT REFERENCES users(id),
  note        VARCHAR(300),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS osh_order_idx ON order_status_history(order_id, created_at);

-- Ödəniş əməliyyatları. Kart məlumatları HEÇ VAXT saxlanılmır –
-- yalnız provayderin əməliyyat identifikatoru və maskalanmış kart (provayder verirsə).
CREATE TABLE IF NOT EXISTS payments (
  id                BIGSERIAL PRIMARY KEY,
  order_id          BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  provider          VARCHAR(30) NOT NULL,
  provider_order_id VARCHAR(64) NOT NULL UNIQUE,
  transaction_id    VARCHAR(128),
  amount            NUMERIC(10,2) NOT NULL,
  status            payment_status NOT NULL DEFAULT 'PENDING',
  redirect_url      TEXT,
  provider_message  TEXT,
  card_mask         VARCHAR(32),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS payments_order_idx ON payments(order_id);

-- Yalnız hər kuryerin SON mövqeyi saxlanılır (məxfilik: hərəkət tarixçəsi toplanmır).
CREATE TABLE IF NOT EXISTS courier_locations (
  courier_id  BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  lat         DOUBLE PRECISION NOT NULL,
  lng         DOUBLE PRECISION NOT NULL,
  accuracy    REAL,
  heading     REAL,
  speed       REAL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notifications (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       VARCHAR(120) NOT NULL,
  body        VARCHAR(400) NOT NULL,
  order_id    BIGINT REFERENCES orders(id) ON DELETE CASCADE,
  is_read     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications(user_id, created_at DESC);
