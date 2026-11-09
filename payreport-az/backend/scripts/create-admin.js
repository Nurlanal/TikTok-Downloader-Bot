'use strict';
/**
 * Admin istifadəçisi yaradır və ya şifrəsini yeniləyir.
 *   node scripts/create-admin.js <istifadəçi> <şifrə>
 * Şifrə ən azı 10 simvol olmalıdır.
 */
const db = require('../src/db');
const { hashPassword } = require('../src/crypto');

(async () => {
  const [username, password, role = 'admin'] = process.argv.slice(2);
  if (!username || !password) {
    console.error('İstifadə: node scripts/create-admin.js <istifadəçi> <şifrə> [admin|viewer]');
    process.exit(1);
  }
  if (password.length < 10) {
    console.error('Şifrə ən azı 10 simvol olmalıdır');
    process.exit(1);
  }
  await db.query(
    `INSERT INTO users (username, password_hash, role) VALUES (?,?,?)
       ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role), is_active = 1`,
    [username, hashPassword(password), role === 'viewer' ? 'viewer' : 'admin']
  );
  console.log(`İstifadəçi hazırdır: ${username} (${role})`);
  await db.close();
})().catch(async (e) => {
  console.error('Xəta:', e.message);
  await db.close();
  process.exit(1);
});
