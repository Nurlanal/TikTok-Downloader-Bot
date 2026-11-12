import pg from 'pg';
import { config } from './config.js';

// NUMERIC -> string olaraq qalır (dəqiqlik üçün); BIGINT id-ləri rəqəmə çeviririk.
pg.types.setTypeParser(20, (v) => Number(v));

export const pool = new pg.Pool({ connectionString: config.databaseUrl, max: 20 });

export const query = (text, params) => pool.query(text, params);

/** Tranzaksiya köməkçisi. fn(client) uğursuz olarsa ROLLBACK edilir. */
export async function tx(fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
}
