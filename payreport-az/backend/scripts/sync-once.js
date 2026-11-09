'use strict';
/** Bir dəfəlik IMAP sinxronizasiyası (cron üçün və ya əl ilə yoxlama). */
const worker = require('../src/imap/worker');
const db = require('../src/db');

(async () => {
  const res = await worker.runSync({ trigger: 'MANUAL' });
  console.log(JSON.stringify(res, null, 2));
  await db.close();
  process.exit(res.status === 'FAILED' ? 2 : 0);
})().catch(async (e) => {
  console.error('Xəta:', e.message);
  await db.close();
  process.exit(1);
});
