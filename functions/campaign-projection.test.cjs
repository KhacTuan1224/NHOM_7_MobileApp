const {test} = require('node:test');
const assert = require('node:assert/strict');
const {projectCampaign} = require('./campaign-projection');
const {Timestamp} = require('./test-support/memory-firestore.cjs');
const day = 86400000, now = 100 * day, ts = n => new Timestamp(n);
const campaign = {status: 'ACTIVE', urgencyLevel: 'NORMAL', startDate: ts(now - day), endDate: ts(now + 10 * day),
  targetQuantity: 100, reservedQuantity: 10, rescuedQuantity: 10, batchTargets: {a: 100}};
test('unchanged documents cross urgency and expiry thresholds using the supplied clock', () => {
  const batches = [{id: 'a', status: 'RESERVED_PARTIAL', availableQuantity: 80, expiryDate: ts(now + 4 * day)}];
  assert.equal(projectCampaign(campaign, batches, now).urgencyLevel, 'HIGH');
  assert.equal(projectCampaign(campaign, batches, now + 2 * day).urgencyLevel, 'CRITICAL');
  assert.equal(projectCampaign(campaign, batches, now + 10 * day).active, false);
  assert.equal(campaign.urgencyLevel, 'NORMAL');
});
test('future, fully allocated, terminal and past campaigns are not publicly active', () => {
  for (const override of [{startDate: ts(now + 1)}, {reservedQuantity: 90}, {status: 'STOPPED'}, {endDate: ts(now)}])
    assert.equal(projectCampaign({...campaign, ...override}, [], now).active, false);
});
test('expired, archived, empty and unrelated batches do not determine urgency', () => {
  const batches = [{id: 'a', status: 'ARCHIVED', availableQuantity: 1, expiryDate: ts(now + day)},
    {id: 'other', status: 'AVAILABLE', availableQuantity: 1, expiryDate: ts(now + day)}];
  assert.equal(projectCampaign(campaign, batches, now).urgencyLevel, 'NORMAL');
  assert.equal(projectCampaign({...campaign, endDate: ts(now + day)}, [], now).urgencyLevel, 'CRITICAL');
});
