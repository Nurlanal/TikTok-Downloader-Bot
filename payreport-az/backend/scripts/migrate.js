'use strict';
/** database/schema.sql faylını konfiqurasiya edilmiş bazaya tətbiq edir. */
const fs = require('node:fs');
const path = require('node:path');
const mysql = require('mysql2/promise');
const config = require('../src/config');

(async () => {
  const schemaPath = path.join(__dirname, '..', '..', 'database', 'schema.sql');
  const sql = fs
    .readFileSync(schemaPath, 'utf8')
    // Baza adı .env-dən gəlir
    .replace(/CREATE DATABASE IF NOT EXISTS payreport/g, `CREATE DATABASE IF NOT EXISTS \`${config.db.database}\``)
    .replace(/USE payreport;/g, `USE \`${config.db.database}\`;`);
  const conn = await mysql.createConnection({
    host: config.db.host,
    port: config.db.port,
    user: config.db.user,
    password: config.db.password,
    multipleStatements: true,
  });
  await conn.query(sql);
  await conn.end();
  console.log(`Sxem tətbiq edildi: ${config.db.database}`);
})().catch((e) => {
  console.error('Miqrasiya xətası:', e.message);
  process.exit(1);
});
