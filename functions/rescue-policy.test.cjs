const {test} = require('node:test');
const assert = require('node:assert/strict');
const P = require('./rescue-policy');
const now = 2000000000000;
const offer = overrides => ({requestId: 'offer-1', type: 'MYSTERY_BOX', title: 'Hộp rau xanh', description: 'Rau củ còn ăn tốt trong ngày',
  categoryLabel: 'Rau củ', contentsDisclosure: 'Có thể chứa đậu; không chọn từng loại', totalUnits: 5, unitLabel: 'hộp',
  unitPrice: 20000, originalUnitPrice: 100000, closesAt: now + 3600000, pickupStartAt: now + 3600000,
  pickupEndAt: now + 7200000, latitude: 10.8, longitude: 106.7, pickupAddress: '10 đường A, TP HCM',
  components: [{batchId: 'b', quantityPerUnit: 0.5}], ...overrides});
test('all three types validate with explicit stock and pickup windows', () => {
  for (const type of P.TYPES) assert.equal(P.validateOffer(offer({type, planKey: 'weekly',
    unitPrice: 20000}), now).type, type);
});
test('free offers, invalid windows, duplicate batches and fractions are rejected', () => {
  for (const bad of [{unitPrice: 0}, {pickupEndAt: now}, {latitude: 100},
    {totalUnits: 1.5}, {type: 'FLASH', closesAt: now + 3 * 3600000},
    {components: [{batchId: 'b', quantityPerUnit: 1}, {batchId: 'b', quantityPerUnit: 2}]},
    {components: [{batchId: 'b', quantityPerUnit: 0.0000001}]}]) assert.throws(() => P.validateOffer(offer(bad), now));
});
test('stock safety rejects expired, campaign-linked, foreign and inconsistent batches', () => {
  const batch = {harvestDate: now - 1, expiryDate: now + 10000, initialQuantity: 10, availableQuantity: 7,
    reservedQuantity: 2, soldQuantity: 1, status: 'RESERVED_PARTIAL'};
  const product = {sellerId: 's', status: 'ACTIVE'};
  P.assertBatch(batch, product, 's', now, now + 5000);
  for (const bad of [{expiryDate: now + 100}, {activeCampaignId: 'c'}, {reservedQuantity: 4}, {status: 'ARCHIVED'}])
    assert.throws(() => P.assertBatch({...batch, ...bad}, product, 's', now, now + 5000));
  assert.throws(() => P.assertBatch(batch, product, 'other', now, now + 5000));
});
test('claim states preserve receipt history after an offer expires', () => {
  assert.equal(P.claimState({status: 'OPEN'}, {status: 'RESERVED'}), 'RESERVED');
  assert.equal(P.claimState({status: 'EXPIRED'}, {status: 'RESERVED'}), 'CANCELLED');
  assert.equal(P.claimState({status: 'EXPIRED'}, {status: 'COMPLETED'}), 'COMPLETED');
});
test('nearby distance uses kilometers and handles date line', () => {
  assert.equal(P.distanceKm({latitude: 10, longitude: 106}, {latitude: 10, longitude: 106}), 0);
  assert.ok(P.distanceKm({latitude: 0, longitude: 179.99}, {latitude: 0, longitude: -179.99}) < 3);
});
