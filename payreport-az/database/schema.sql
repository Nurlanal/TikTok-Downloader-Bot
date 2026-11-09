-- =====================================================================
-- PayReport AZ — MySQL 8 / MariaDB 10.6+ verilənlər bazası sxemi
-- Bütün vaxtlar UTC olaraq saxlanılır. Bakı vaxtı = UTC+4.
-- Məbləğlər qəpiklə (amount_minor) saxlanılır: 5 AZN = 500.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS payreport
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE payreport;

-- İdarəçilər (API istifadəçiləri)
CREATE TABLE IF NOT EXISTS users (
  id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(64)  NOT NULL UNIQUE,
  password_hash  VARCHAR(255) NOT NULL,           -- scrypt$N$r$p$salt$hash
  role           ENUM('admin','viewer') NOT NULL DEFAULT 'admin',
  is_active      TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

-- API tokenləri (yalnız SHA-256 heşi saxlanılır)
CREATE TABLE IF NOT EXISTS api_tokens (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id       INT UNSIGNED NOT NULL,
  token_hash    CHAR(64) NOT NULL UNIQUE,
  device_name   VARCHAR(128) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_used_at  DATETIME(3) NULL,
  expires_at    DATETIME(3) NOT NULL,
  revoked       TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Mail.ru hesabı (şifrə AES-256-GCM ilə şifrələnir, açar .env-dədir)
CREATE TABLE IF NOT EXISTS mail_accounts (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  password_enc    TEXT NOT NULL,
  host            VARCHAR(255) NOT NULL DEFAULT 'imap.mail.ru',
  port            SMALLINT UNSIGNED NOT NULL DEFAULT 993,
  secure          TINYINT(1) NOT NULL DEFAULT 1,
  mailbox         VARCHAR(255) NOT NULL DEFAULT 'INBOX',
  sender_filter   VARCHAR(1024) NULL,             -- vergüllə ayrılmış ünvanlar/domenlər
  subject_filter  VARCHAR(1024) NULL,             -- vergüllə ayrılmış açar sözlər
  initial_days    SMALLINT UNSIGNED NOT NULL DEFAULT 30,
  enabled         TINYINT(1) NOT NULL DEFAULT 1,
  uid_validity    BIGINT UNSIGNED NULL,
  last_uid        BIGINT UNSIGNED NOT NULL DEFAULT 0,
  last_sync_at    DATETIME(3) NULL,
  last_status     ENUM('NEVER','OK','ERROR') NOT NULL DEFAULT 'NEVER',
  last_error      VARCHAR(1024) NULL,
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

-- Emal olunmuş məktublar (eyni məktub ikinci dəfə hesablanmır)
CREATE TABLE IF NOT EXISTS emails (
  id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  account_id        INT UNSIGNED NOT NULL,
  uid_validity      BIGINT UNSIGNED NOT NULL,
  uid               BIGINT UNSIGNED NOT NULL,
  message_id        VARCHAR(512) NULL,
  from_addr         VARCHAR(512) NULL,
  subject           VARCHAR(1024) NULL,
  received_at       DATETIME(3) NULL,
  processed_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  result            ENUM('PAYMENT','PENDING','IGNORED','DUPLICATE','ERROR') NOT NULL,
  payments_created  INT UNSIGNED NOT NULL DEFAULT 0,
  error             VARCHAR(1024) NULL,
  UNIQUE KEY uq_email_uid (account_id, uid_validity, uid),
  KEY idx_email_message_id (message_id(191)),
  CONSTRAINT fk_emails_account FOREIGN KEY (account_id) REFERENCES mail_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Ödənişlər
CREATE TABLE IF NOT EXISTS payments (
  id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  dedupe_key        VARCHAR(600) NOT NULL,         -- TXN:<id> və ya MSG:<message-id>#<n> və ya MAN:<uuid>
  phone             VARCHAR(16) NULL,              -- +994XXXXXXXXX
  amount_minor      BIGINT NULL,                   -- qəpik
  currency          CHAR(3) NOT NULL DEFAULT 'AZN',
  paid_at           DATETIME(3) NOT NULL,          -- UTC
  status            ENUM('CONFIRMED','PENDING','CANCELLED') NOT NULL,
  source            ENUM('TEXT','PDF','IMAGE','MANUAL') NOT NULL,
  confidence        TINYINT UNSIGNED NOT NULL DEFAULT 0,
  txn_id            VARCHAR(64) NULL,
  email_id          BIGINT UNSIGNED NULL,
  email_message_id  VARCHAR(512) NULL,
  email_subject     VARCHAR(1024) NULL,
  email_from        VARCHAR(512) NULL,
  raw_text          TEXT NULL,
  note              VARCHAR(1024) NULL,
  cancel_reason     VARCHAR(1024) NULL,
  created_by        VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_payment_dedupe (dedupe_key(191)),
  KEY idx_payment_phone (phone),
  KEY idx_payment_paid_at (paid_at),
  KEY idx_payment_status (status, paid_at),
  KEY idx_payment_updated (updated_at, id),
  KEY idx_payment_txn (txn_id),
  CONSTRAINT fk_payments_email FOREIGN KEY (email_id) REFERENCES emails(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Audit tarixçəsi (heç vaxt silinmir)
CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  payment_id  BIGINT UNSIGNED NULL,
  action      VARCHAR(32) NOT NULL,              -- CREATE, UPDATE, CONFIRM, CANCEL, MAIL_CONFIG, LOGIN ...
  actor       VARCHAR(64) NOT NULL,
  details     JSON NULL,
  ip          VARCHAR(64) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_audit_payment (payment_id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB;

-- Sinxronizasiya tarixçəsi
CREATE TABLE IF NOT EXISTS sync_runs (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  account_id   INT UNSIGNED NULL,
  trigger_type ENUM('AUTO','MANUAL') NOT NULL,
  started_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at  DATETIME(3) NULL,
  status       ENUM('RUNNING','SUCCESS','FAILED') NOT NULL DEFAULT 'RUNNING',
  fetched      INT UNSIGNED NOT NULL DEFAULT 0,
  created      INT UNSIGNED NOT NULL DEFAULT 0,
  pending      INT UNSIGNED NOT NULL DEFAULT 0,
  duplicates   INT UNSIGNED NOT NULL DEFAULT 0,
  errors       INT UNSIGNED NOT NULL DEFAULT 0,
  message      VARCHAR(1024) NULL,
  KEY idx_sync_started (started_at)
) ENGINE=InnoDB;
