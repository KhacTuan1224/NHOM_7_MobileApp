const {before, after, beforeEach, test} = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const admin = require('firebase-admin');
const {createRescuePrograms} = require('./rescue-programs');
const emulator = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^(localhost|127\.0\.0\.1):\d+$/.test(emulator || '')) throw Error('Local Firestore emulator required; never run against production.');
admin.initializeApp({projectId: 'demo-rescuefarm'});
const db = admin.firestore();
let now, server, base;
const ts = n => admin.firestore.Timestamp.fromMillis(n);
const authenticate = async (req, roles) => {
  const uid = (req.get('Authorization') || '').replace(/^Bearer /, '');
  const user = uid ? await db.doc('users/' + uid).get() : null;
  if (!user?.exists || user.get('status') !== 'ACTIVE' || !roles.includes(user.get('role')))
    throw Object.assign(Error('Forbidden'), {status: 403});
  return {uid, role: user.get('role')};
};
const api = createRescuePrograms({db, admin, authenticate, now: () => now});
before(async () => {
  const app = express(); app.use(express.json()); app.use(api.router);
  app.use((e, req, res, next) => res.status(e.status || 500).json({error: e.message}));
  await new Promise(resolve => { server = app.listen(0, '127.0.0.1', resolve); });
  base = 'http://127.0.0.1:' + server.address().port + '/rescue/';
});
after(async () => { await new Promise(resolve => server.close(resolve)); await admin.app().delete(); });
beforeEach(async () => {
  now = Date.now();
  assert.equal((await fetch(`http://${emulator}/emulator/v1/projects/demo-rescuefarm/databases/(default)/documents`, {method: 'DELETE'})).status, 200);
  const fixtures = {
    'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED', shopName: 'Vườn nhà'},
    'users/seller2': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'},
    'users/a': {role: 'CUSTOMER', status: 'ACTIVE', fullName: 'Khách A'},
    'users/b': {role: 'CUSTOMER', status: 'ACTIVE', fullName: 'Khách B'},
    'users/admin': {role: 'ADMIN', status: 'ACTIVE'},
    'products/p': {sellerId: 'seller', status: 'ACTIVE', name: 'Rau cải', unit: 'kg', originalPrice: 100000},
    'productBatches/batch': {id: 'batch', productId: 'p', status: 'AVAILABLE', harvestDate: ts(now - 1000), expiryDate: ts(now + 3 * 86400000),
      initialQuantity: 10, availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0, inventoryVersion: 1, activeCampaignId: ''}
  };
  const batch = db.batch(); for (const [path, value] of Object.entries(fixtures)) batch.set(db.doc(path), value); await batch.commit();
});
async function post(path, body, uid = 'seller') {
  const response = await fetch(base + path, {method: 'POST', headers: {'Content-Type': 'application/json',
    ...(uid ? {Authorization: 'Bearer ' + uid} : {})}, body: JSON.stringify(body)});
  return {status: response.status, body: await response.json()};
}
function input(overrides = {}) {
  return {requestId: 'offer', type: 'MYSTERY_BOX', title: 'Hộp rau xanh', description: 'Rau củ còn ăn tốt, nhận trong ngày',
    categoryLabel: 'Rau củ', contentsDisclosure: 'Có thể có đậu, không chọn từng loại', unitLabel: 'hộp',
    totalUnits: 10, unitPrice: 20000, originalUnitPrice: 100000, closesAt: now + 3600000,
    pickupStartAt: now + 3600000, pickupEndAt: now + 7200000, pickupAddress: '10 đường A, TP HCM', latitude: 10.8, longitude: 106.7,
    components: [{batchId: 'batch', quantityPerUnit: 1}], ...overrides};
}
async function create(overrides) { const response = await post('create', input(overrides)); assert.equal(response.status, 200, JSON.stringify(response.body)); return response; }
async function inventory() { return (await db.doc('productBatches/batch').get()).data(); }
test('batch picker pages past 30 products and 100 batches without losing or duplicating tomato stock', async () => {
  const write = db.batch(), baseBatch = await inventory();
  for (let i = 0; i < 31; i++) {
    const productId = 'z' + String(i).padStart(2, '0');
    write.set(db.doc('products/' + productId), {sellerId: 'seller', status: 'ACTIVE',
      name: i === 30 ? 'Cà chua chín Lâm Đồng' : 'Nông sản ' + i, unit: 'kg'});
    for (let j = 0; j < (i === 0 ? 101 : 1); j++) {
      const id = productId + '_b' + String(j).padStart(3, '0');
      write.set(db.doc('productBatches/' + id), {...baseBatch, id, productId});
    }
  }
  await write.commit();
  assert.equal((await post('batches', {})).body.items.some(b => b.productName === 'Cà chua chín Lâm Đồng'), false);
  const items = []; let cursor = '', pages = 0, more = true;
  while (more) {
    const response = await post('batches', {includeUnavailable: true, cursor});
    assert.equal(response.status, 200, JSON.stringify(response.body));
    items.push(...response.body.items); cursor = response.body.cursor; more = response.body.hasMore;
    assert.ok(++pages <= 5, 'pagination must terminate');
  }
  assert.equal(items.length, 132);
  assert.equal(new Set(items.map(b => b.batchId)).size, 132);
  assert.equal(items.find(b => b.productName === 'Cà chua chín Lâm Đồng').selectable, true);
});

test('batch picker explains unavailable stock and never exposes another seller inventory', async () => {
  const batch = await inventory();
  await db.doc('products/private').set({sellerId: 'seller2', status: 'ACTIVE', name: 'Private'});
  await db.doc('productBatches/private').set({...batch, productId: 'private'});
  for (const [change, expected] of [
    [{activeCampaignId: 'campaign'}, /chiến dịch/],
    [{activeCampaignId: '', harvestDate: ts(now + 3600000)}, /thu hoạch/],
    [{harvestDate: ts(now - 1000), expiryDate: ts(now - 1)}, /hết hạn/],
    [{expiryDate: ts(now + 86400000), availableQuantity: 0}, /khả dụng/]
  ]) {
    await db.doc('productBatches/batch').update(change);
    const result = await post('batches', {includeUnavailable: true});
    assert.equal(result.status, 200);
    assert.equal(result.body.items.length, 1);
    assert.equal(result.body.items[0].selectable, false);
    assert.match(result.body.items[0].unavailableReason, expected);
  }
  await db.doc('products/p').update({status: 'INACTIVE'});
  assert.match((await post('batches', {includeUnavailable: true})).body.items[0].unavailableReason, /Đang bán/);
  assert.equal((await post('batches', {includeUnavailable: true, cursor: 'invalid'})).status, 400);
  assert.equal((await post('batches', {includeUnavailable: true}, 'a')).status, 403);
  assert.equal((await post('batches', {})).body.items.length, 0);
});

test('retired rescue types cannot be created, discovered or joined', async () => {
  for (const type of ['BULK', 'DONATION', 'GROUP_BUY', 'SUBSCRIPTION']) {
    assert.equal((await post('create', input({type, unitPrice: type === 'DONATION' ? 0 : 20000}))).status, 400);
    await db.doc('rescueOffers/retired').set({...input(), id: 'retired', type, sellerId: 'seller', status: 'OPEN', availableUnits: 10});
    assert.equal((await post('catalog', {}, '')).body.items.length, 0);
    assert.equal((await post('detail', {offerId: 'retired'}, '')).status, 404);
    assert.equal((await post('join', {offerId: 'retired', units: 1}, 'a')).status, 404);
    assert.equal((await post('claims', {offerId: 'retired'}, 'a')).status, 404);
    await db.doc('rescueClaims/retired-claim').set({offerId: 'retired', customerId: 'a', sellerId: 'seller', status: 'READY'});
    assert.equal((await post('claims/action', {claimId: 'retired-claim', action: 'COMPLETE', confirmReceived: true, confirmPaid: true}, 'a')).status, 404);
  }
  for (const route of ['quotes/request', 'quotes/list', 'quotes/propose', 'quotes/accept',
    'organizations/apply', 'organizations/list', 'organizations/review',
    'subscriptions/save', 'subscriptions/list', 'subscriptions/pause']) {
    const response = await fetch(base + route, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: '{}'});
    assert.equal(response.status, 404);
  }
  assert.equal((await inventory()).reservedQuantity, 0);
});
test('retired recurring offers release reserved stock at cutoff without generating reminders', async () => {
  await db.doc('productBatches/batch').update({availableQuantity: 0, reservedQuantity: 10, status: 'RESERVED_PARTIAL'});
  await db.doc('rescueOffers/legacy').set({...input(), id: 'legacy', sellerId: 'seller',
    type: 'SUBSCRIPTION', status: 'OPEN', availableUnits: 8, claimedUnits: 2, completedUnits: 0,
    releasedUnits: 0, closesAt: now - 1, nextActionAt: ts(now - 1)});
  await db.doc('rescueClaims/legacy').set({offerId: 'legacy', customerId: 'a', sellerId: 'seller',
    units: 2, status: 'RESERVED'});
  await db.doc('rescueSubscriptions/legacy').set({customerId: 'a', sellerId: 'seller', status: 'ACTIVE',
    nextDueAt: now - 1, nextRunAt: ts(now - 1)});
  await api.reconcile();
  assert.equal((await inventory()).availableQuantity, 10);
  assert.equal((await inventory()).reservedQuantity, 0);
  assert.equal((await db.doc('rescueOffers/legacy').get()).get('status'), 'CANCELLED');
  assert.equal((await db.doc('rescueClaims/legacy').get()).get('status'), 'CANCELLED');
  assert.equal((await db.collection('notificationOutbox').get()).docs.some(d => !d.get('resourceId')), false);
  await api.reconcile();
  assert.equal((await inventory()).availableQuantity, 10);
});

test('creation atomically holds physical stock, redacts mystery contents and replays after cutoff', async () => {
  const request = input(); assert.equal((await post('create', request)).status, 200);
  assert.equal((await inventory()).availableQuantity, 0); assert.equal((await inventory()).reservedQuantity, 10);
  const catalog = await post('catalog', {}, ''); assert.equal(catalog.body.items.length, 1);
  assert.equal(catalog.body.items[0].components, undefined); assert.ok(catalog.body.items[0].contentsDisclosure);
  now += 3601000;
  assert.equal((await post('create', request)).body.replay, true);
  assert.equal((await post('create', {...request, title: 'Gói khác'})).status, 409);
});
test('normal offer replay survives removal of retired method metadata without reserving twice', async () => {
  const request = input(); await create();
  await db.doc('rescueOffers/offer').update({fingerprint: 'older-normalization', planKey: '', minPeople: 1, minUnits: 1, groupSucceeded: false});
  assert.equal((await post('create', request)).body.replay, true);
  assert.equal((await post('create', {...request, unitPrice: 15000})).status, 409);
  assert.equal((await inventory()).reservedQuantity, 10);
});
test('concurrent offers cannot over-reserve same batch and nonowners cannot use stock', async () => {
  const results = await Promise.all([post('create', input({requestId: 'one', totalUnits: 6})), post('create', input({requestId: 'two', totalUnits: 6}))]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal((await inventory()).reservedQuantity, 6);
  assert.equal((await post('create', input({requestId: 'foreign'}), 'seller2')).status, 409);
});
test('parallel customers cannot oversell, replay does not create two claims', async () => {
  await create({totalUnits: 1});
  const results = await Promise.all([post('join', {offerId: 'offer', units: 1}, 'a'), post('join', {offerId: 'offer', units: 1}, 'b')]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  const uid = results[0].status === 200 ? 'a' : 'b';
  assert.equal((await post('join', {offerId: 'offer', units: 1}, uid)).body.replay, true);
  assert.equal((await inventory()).reservedQuantity, 1);
});
test('one micro-unit shortage cannot produce a negative available balance', async () => {
  await db.doc('productBatches/batch').update({availableQuantity: 0.999999, reservedQuantity: 9.000001, status: 'RESERVED_PARTIAL'});
  assert.equal((await post('create', input({totalUnits: 1}))).status, 409);
  assert.equal((await inventory()).availableQuantity, 0.999999);
  assert.equal((await db.doc('rescueOffers/offer').get()).exists, false);
});
test('multi-batch reservation rolls back every component if one batch is short', async () => {
  const batch = await inventory();
  await db.doc('productBatches/second').set({...batch, id: 'second', initialQuantity: 1, availableQuantity: 1});
  const response = await post('create', input({totalUnits: 2, components: [
    {batchId: 'batch', quantityPerUnit: 1}, {batchId: 'second', quantityPerUnit: 1}]}));
  assert.equal(response.status, 409);
  assert.equal((await inventory()).availableQuantity, 10);
  assert.equal((await inventory()).reservedQuantity, 0);
});
test('advertised original box value cannot exceed the sum of source product values', async () => {
  assert.equal((await post('create', input({originalUnitPrice: 200000}))).status, 400);
  assert.equal((await inventory()).reservedQuantity, 0);
});
test('receipt requires owning buyer, seller readiness, actual pickup time and explicit cash confirmation', async () => {
  await create(); const claim = (await post('join', {offerId: 'offer', units: 2}, 'a')).body.claim;
  assert.equal((await post('claims/action', {claimId: claim.id, action: 'COMPLETE', confirmReceived: true, confirmPaid: true}, 'a')).status, 409);
  now += 3600001;
  assert.equal((await post('claims/action', {claimId: claim.id, action: 'READY'}, 'seller2')).status, 403);
  assert.equal((await post('claims/action', {claimId: claim.id, action: 'READY'})).status, 200);
  assert.equal((await post('claims/action', {claimId: claim.id, action: 'COMPLETE', confirmReceived: true}, 'a')).status, 400);
  const receipt = {claimId: claim.id, action: 'COMPLETE', confirmReceived: true, confirmPaid: true};
  assert.equal((await post('claims/action', receipt, 'a')).status, 200);
  assert.equal((await post('claims/action', receipt, 'a')).body.replay, true);
  assert.equal((await inventory()).soldQuantity, 2);
  const receiptDetails = (await post('detail', {offerId: 'offer'}, 'a')).body;
  assert.equal(receiptDetails.receivedContents[0].productName, 'Rau cải');
  assert.equal((await post('impact', {sellerId: 'seller'}, '')).body.impact.quantityByUnit.kg, 2);
  await api.settle('offer'); assert.equal((await inventory()).availableQuantity, 8);
});
test('nearby opt-in is private, bounded, deduplicated and expires', async () => {
  await create({type: 'FLASH'});
  assert.equal((await post('nearby/preferences', {enabled: true, latitude: 10.8, longitude: 106.7, radiusKm: 10}, 'a')).status, 400);
  await post('nearby/preferences', {enabled: true, latitude: 10.8, longitude: 106.7, radiusKm: 5}, 'a');
  await api.reconcile(); now += 6 * 60000; await api.reconcile();
  assert.equal((await db.collection('notificationOutbox').get()).size, 1);
  now += 86400000; await api.reconcile(); assert.equal((await db.collection('rescueNearbyPreferences').get()).size, 0);
});
test('cancel before deadline returns units to offer; closing and expiring never release twice', async () => {
  await create(); const claim = (await post('join', {offerId: 'offer', units: 2}, 'a')).body.claim;
  assert.equal((await post('claims/action', {claimId: claim.id, action: 'CANCEL'}, 'a')).status, 200);
  assert.equal((await inventory()).reservedQuantity, 10);
  assert.equal((await post('close', {offerId: 'offer'})).status, 200);
  assert.equal((await post('close', {offerId: 'offer'})).body.replay, true);
  assert.equal((await inventory()).reservedQuantity, 0); assert.equal((await inventory()).availableQuantity, 10);
});
