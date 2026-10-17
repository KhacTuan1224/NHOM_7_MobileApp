const {test} = require('node:test');
const assert = require('node:assert/strict');
const {campaignUnit, normalizeUnit, kilograms} = require('./quantity-units');
test('unit aliases preserve quantities and never turn bundles/bags into kilograms', () => {
  assert.equal(campaignUnit([{unit: ' KG '}, {unit: 'kilogram'}]), 'kg');
  assert.equal(normalizeUnit('BÓ'), 'bó');
  assert.throws(() => campaignUnit([{unit: 'kg'}, {unit: 'bó'}]), /cùng một đơn vị/);
  assert.throws(() => campaignUnit([{unit: ''}]), /cùng một đơn vị/);
});
test('kg dashboards include only verified kg campaigns', () => {
  assert.equal(kilograms({quantityUnit: 'kg', unitStatus: 'VERIFIED'}), true);
  for (const c of [{quantityUnit: 'kg'}, {quantityUnit: 'bó', unitStatus: 'VERIFIED'}, {quantityUnit: 'kg', unitStatus: 'REVIEW_REQUIRED'}]) assert.equal(kilograms(c), false);
});
