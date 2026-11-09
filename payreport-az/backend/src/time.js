'use strict';
/** Azərbaycan (Bakı) vaxtı: UTC+4, yay vaxtı yoxdur (2016-dan). */
const BAKU_OFFSET_MS = 4 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** "YYYY-MM-DDTHH:mm:ss" (Bakı yerli) -> Date (UTC) */
function bakuLocalToDate(local) {
  if (!local) return null;
  const d = new Date(`${local}+04:00`);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Date -> Bakı yerli komponentləri */
function toBaku(date) {
  const d = new Date(date.getTime() + BAKU_OFFSET_MS);
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), dow: d.getUTCDay() };
}

/** Bakı vaxtı ilə günün başlanğıcı (UTC Date) */
function bakuDayStart(y, m, d) {
  return new Date(Date.UTC(y, m - 1, d) - BAKU_OFFSET_MS);
}

function bakuDateKey(date) {
  const b = toBaku(date);
  return `${b.y}-${String(b.m).padStart(2, '0')}-${String(b.d).padStart(2, '0')}`;
}

/** Dashboard üçün dövrlər (Bakı vaxtı ilə) */
function periods(now = new Date()) {
  const b = toBaku(now);
  const today = bakuDayStart(b.y, b.m, b.d);
  const tomorrow = new Date(today.getTime() + DAY_MS);
  const yesterday = new Date(today.getTime() - DAY_MS);
  const isoDow = b.dow === 0 ? 7 : b.dow; // Bazar ertəsi = 1
  const weekStart = new Date(today.getTime() - (isoDow - 1) * DAY_MS);
  const monthStart = bakuDayStart(b.y, b.m, 1);
  return { today, tomorrow, yesterday, weekStart, monthStart };
}

module.exports = { bakuLocalToDate, toBaku, bakuDayStart, bakuDateKey, periods, BAKU_OFFSET_MS, DAY_MS };
