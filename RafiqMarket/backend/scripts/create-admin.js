// İstifadə: npm run create-admin -- +994501234567 "Rafiq"
import { pool } from '../src/db.js';
import { normalizePhone } from '../src/lib/util.js';

const [rawPhone, name = 'Admin'] = process.argv.slice(2);
if (!rawPhone) { console.error('İstifadə: npm run create-admin -- +99450XXXXXXX "Ad"'); process.exit(1); }
const phone = normalizePhone(rawPhone);
const { rows } = await pool.query(
  `INSERT INTO users (phone, name, role) VALUES ($1,$2,'ADMIN')
   ON CONFLICT (phone) DO UPDATE SET role='ADMIN', is_active=true, name=EXCLUDED.name RETURNING id, phone, role`, [phone, name]);
console.log('Admin hesabı hazırdır:', rows[0]);
await pool.end();
