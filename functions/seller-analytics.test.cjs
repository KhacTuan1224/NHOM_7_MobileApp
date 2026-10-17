const {test} = require('node:test');
const assert = require('node:assert/strict');
const {aggregateSellerStatistics, allPages} = require('./seller-analytics');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
test('statistics read all pages, independently of the 50-order UI page size', async () => {
  const seed = {};
  for (let i = 0; i < 451; i++) seed[`orders/order-${String(i).padStart(4, '0')}`] = {sellerId: 'seller', totalAmount: 1};
  seed['orders/other'] = {sellerId: 'other'};
  const db = new MemoryFirestore(seed);
  assert.equal((await allPages(db.collection('orders').where('sellerId', '==', 'seller'), admin)).length, 451);
});
test('date scope, shipping exclusion, mixed units, and rankings use actual delivered items', () => {
  const now = Date.now(), old = new Timestamp(now - 40 * 86400000), recent = new Timestamp(now - 1000);
  const orders = Array.from({length: 80}, (_, i) => ({id: 'o' + i, createdAt: old, deliveredAt: recent, status: 'DELIVERED',
    subtotal: 100, quantityDiscount: 5, shippingFee: 10, campaignId: i % 2 ? 'c-kg' : 'c-bo', campaignReservedQuantity: 2, batchReservations: {a: 2}}));
  orders.push({id: 'pending', createdAt: old, status: 'PENDING'});
  const items = new Map(orders.slice(0, 80).map((o, i) => [o.id, [{productId: 'p' + i % 2, productNameSnapshot: 'Product', batchId: 'a', unitSnapshot: i % 2 ? 'kg' : 'bó', quantity: 2}]]));
  const result = aggregateSellerStatistics({orders, campaigns: ['kg', 'bó'].map((unit, i) => ({id: i ? 'c-bo' : 'c-kg', quantityUnit: unit, unitStatus: 'VERIFIED', batchTargets: {a: 1000}, rescueReason: 'NEAR_EXPIRY', status: 'COMPLETED', completedAt: recent})), itemsByOrder: items, days: 7, now});
  assert.equal(result.revenue, 80 * 95); assert.equal(result.deliveredOrders, 80); assert.equal(result.orders, 0);
  assert.equal(result.pendingOrders, 1); assert.equal(result.completedCampaigns, 2);
  assert.deepEqual(result.rescuedByUnit, {'bó': 80, kg: 80});
  assert.equal(result.revenueTrend.reduce((sum, p) => sum + p.revenue, 0), result.revenue);
  assert.equal(result.approximateDates, false);
});
test('empty period and legacy date fallback are explicit', () => {
  const now = Date.now();
  const empty = aggregateSellerStatistics({orders: [], campaigns: [], itemsByOrder: new Map(), days: 30, now});
  assert.equal(empty.hasData, false); assert.equal(empty.revenue, 0);
  const legacy = aggregateSellerStatistics({orders: [{id: 'old', createdAt: new Timestamp(now - 1), status: 'DELIVERED', subtotal: 10}], campaigns: [], itemsByOrder: new Map(), days: 30, now});
  assert.equal(legacy.approximateDates, true); assert.equal(legacy.revenue, 10);
});

test('rescue rate averages campaign percentages without summing mixed-unit targets', () => {
  const now = Date.now(), begin = new Timestamp(now - 1000), end = new Timestamp(now + 1000);
  const campaigns = [{unitStatus: 'VERIFIED', quantityUnit: 'kg', id: 'a', status: 'ACTIVE', startDate: begin, endDate: end, targetQuantity: 10, rescuedQuantity: 5},
    {unitStatus: 'VERIFIED', quantityUnit: 'bó', id: 'b', status: 'COMPLETED', startDate: begin, endDate: end, targetQuantity: 1000, rescuedQuantity: 1000},
    {id: 'pending', status: 'PENDING_APPROVAL', startDate: begin, endDate: end, targetQuantity: 100, rescuedQuantity: 0}];
  const result = aggregateSellerStatistics({orders: [], campaigns, itemsByOrder: new Map(), days: 7, now});
  assert.equal(result.rescueRate, 75); assert.equal(result.rescueRateCampaigns, 2);
  assert.equal(result.hasData, true);
});
