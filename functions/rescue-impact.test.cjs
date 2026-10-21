const test = require('node:test');
const assert = require('node:assert/strict');
const {impactWindow, summarizeImpact} = require('./rescue-impact');
const now = Date.parse('2026-10-21T01:00:00+07:00');

test('period uses Vietnam midnight and rejects unsupported periods', () => {
  assert.equal(impactWindow(now, 7).startAt, Date.parse('2026-10-15T00:00:00+07:00'));
  assert.equal(impactWindow(now, 30).nextDayAt, Date.parse('2026-10-22T00:00:00+07:00'));
  assert.throws(() => impactWindow(now, 8), {status: 400});
});
test('only actual confirmed receipts in range count, with zero-filled days and correct cumulative totals', () => {
  const start = impactWindow(now, 7).startAt;
  const rows = [
    {status: 'COMPLETED', receivedAt: {toMillis: () => start}, offerType: 'FLASH', units: 100},
    {status: 'COMPLETED', receivedAt: now - 2 * 3600000, offerType: 'MYSTERY_BOX'},
    {status: 'COMPLETED', receivedAt: now, offerType: 'FLASH'},
    {status: 'READY', receivedAt: now, offerType: 'FLASH'},
    {status: 'CANCELLED', receivedAt: now, offerType: 'FLASH'},
    {status: 'COMPLETED', receivedAt: start - 1, offerType: 'FLASH'},
    {status: 'COMPLETED', receivedAt: now + 1, offerType: 'FLASH'},
    {status: 'COMPLETED', offerType: 'FLASH'}
  ];
  const result = summarizeImpact(rows, now, 7);
  assert.equal(result.completedPickups, 3); // Receipts, not units claimed.
  assert.equal(result.daily.length, 7);
  assert.deepEqual(result.daily.map(day => day.completedPickups), [1, 0, 0, 0, 0, 1, 1]);
  assert.deepEqual(result.daily.map(day => day.cumulativePickups), [1, 1, 1, 1, 1, 2, 3]);
  assert.deepEqual(result.byType, {FLASH: 2, MYSTERY_BOX: 1, OTHER: 0});
});
test('empty periods do not fabricate activity; missing legacy methods are explicitly unclassified', () => {
  const empty = summarizeImpact([], now, 90);
  assert.equal(empty.daily.length, 90);
  assert.equal(empty.completedPickups, 0);
  assert.ok(empty.daily.every(day => day.cumulativePickups === 0));
  assert.equal(summarizeImpact([{status: 'COMPLETED', receivedAt: now}], now, 7).byType.OTHER, 1);
});
