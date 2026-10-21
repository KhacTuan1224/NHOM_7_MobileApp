const DAY = 86400000;
const OFFSET = 7 * 3600000;
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : Number(value);

function impactWindow(now, days = 30) {
  if (![7, 30, 90].includes(days)) throw Object.assign(Error('Chọn kỳ 7, 30 hoặc 90 ngày.'), {status: 400});
  const today = Math.floor((now + OFFSET) / DAY) * DAY - OFFSET;
  return {days, startAt: today - (days - 1) * DAY, endAt: now, nextDayAt: today + DAY, timezone: 'Asia/Ho_Chi_Minh'};
}

/** Counts confirmed pickups, never sums quantities with different physical units. */
function summarizeImpact(claims, now, days = 30) {
  const window = impactWindow(now, days);
  const daily = Array.from({length: days}, (_, i) => ({
    date: new Date(window.startAt + i * DAY + OFFSET).toISOString().slice(0, 10), completedPickups: 0
  }));
  const byType = {FLASH: 0, MYSTERY_BOX: 0, OTHER: 0};
  let completedPickups = 0;
  for (const claim of claims) {
    const receivedAt = millis(claim.receivedAt);
    if (claim.status !== 'COMPLETED' || !Number.isFinite(receivedAt)
        || receivedAt < window.startAt || receivedAt > now) continue;
    daily[Math.floor((receivedAt - window.startAt) / DAY)].completedPickups++;
    const type = ['FLASH', 'MYSTERY_BOX'].includes(claim.offerType) ? claim.offerType : 'OTHER';
    byType[type]++;
    completedPickups++;
  }
  let cumulative = 0;
  for (const day of daily) { cumulative += day.completedPickups; day.cumulativePickups = cumulative; }
  return {...window, daily, byType, completedPickups};
}
module.exports = {impactWindow, summarizeImpact};
