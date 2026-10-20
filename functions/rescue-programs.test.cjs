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
    'users/a': {role: 'CUSTOMER', status: 'ACTIVE', fullName: 'Khách A', customerType: 'BUSINESS', companyName: 'Bếp xanh'},
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
test('creation atomically holds physical stock, redacts mystery contents and replays after cutoff', async () => {
  const request = input(); assert.equal((await post('create', request)).status, 200);
  assert.equal((await inventory()).availableQuantity, 0); assert.equal((await inventory()).reservedQuantity, 10);
  const catalog = await post('catalog', {}, ''); assert.equal(catalog.body.items.length, 1);
  assert.equal(catalog.body.items[0].components, undefined); assert.ok(catalog.body.items[0].contentsDisclosure);
  now += 3601000;
  assert.equal((await post('create', request)).body.replay, true);
  assert.equal((await post('create', {...request, title: 'Gói khác'})).status, 409);
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
test('unsuccessful group releases stock once without payment or refund records', async () => {
  await create({type: 'GROUP_BUY', minPeople: 2, minUnits: 5});
  assert.equal((await post('join', {offerId: 'offer', units: 6}, 'a')).body.claim.status, 'PLEDGED');
  now += 3600001; await api.settle('offer'); await api.settle('offer');
  assert.equal((await inventory()).availableQuantity, 10); assert.equal((await inventory()).reservedQuantity, 0);
  assert.equal((await post('mine', {}, 'a')).body.items[0].status, 'CANCELLED');
  assert.equal((await db.collection('payments').get()).size, 0);
});
test('successful group freezes pledge cancellation and releases only unclaimed stock at cutoff', async () => {
  await create({type: 'GROUP_BUY', minPeople: 2, minUnits: 5});
  const a = (await post('join', {offerId: 'offer', units: 3}, 'a')).body.claim;
  await post('join', {offerId: 'offer', units: 2}, 'b');
  assert.equal((await post('claims/action', {claimId: a.id, action: 'CANCEL'}, 'a')).status, 409);
  now += 3600001; await api.settle('offer');
  assert.equal((await inventory()).availableQuantity, 5); assert.equal((await inventory()).reservedQuantity, 5);
  now += 3600001; await api.settle('offer');
  assert.equal((await inventory()).availableQuantity, 10);
});
test('only a verified organization may receive donations and only admin can verify', async () => {
  await create({type: 'DONATION', unitPrice: 0});
  assert.equal((await post('join', {offerId: 'offer', units: 1}, 'b')).status, 403);
  assert.equal((await post('organizations/apply', {name: 'Bếp ăn xanh', registration: 'ORG-123', contact: '0912345678', evidence: 'Hồ sơ tổ chức: thông tin xác minh'}, 'b')).status, 200);
  assert.equal((await post('organizations/review', {organizationId: 'b', status: 'VERIFIED', reason: 'Đã xác minh'}, 'a')).status, 403);
  assert.equal((await post('organizations/review', {organizationId: 'b', status: 'VERIFIED', reason: 'Đã xác minh hồ sơ'}, 'admin')).status, 200);
  const claim = (await post('join', {offerId: 'offer', units: 1}, 'b')).body.claim;
  assert.equal(claim.totalAmount, 0); assert.equal(claim.paymentStatus, 'NOT_REQUIRED');
});
test('bulk quotes are private, role restricted, versioned and never trust a client price', async () => {
  await create({type: 'BULK'});
  assert.equal((await post('quotes/request', {offerId: 'offer', units: 2}, 'b')).status, 403);
  const quote = (await post('quotes/request', {offerId: 'offer', units: 2}, 'a')).body.quote;
  assert.equal((await post('quotes/list', {offerId: 'offer'}, 'b')).body.items.length, 0);
  const proposed = await post('quotes/propose', {quoteId: quote.id, unitPrice: 10000});
  assert.equal((await post('quotes/accept', {offerId: 'offer', units: 2, revision: 1}, 'a')).status, 409);
  const accepted = await post('quotes/accept', {offerId: 'offer', units: 2, revision: proposed.body.revision, unitPrice: 1}, 'a');
  assert.equal(accepted.body.claim.totalAmount, 20000);
  assert.equal((await post('quotes/propose', {quoteId: quote.id, unitPrice: 9999})).status, 403);
});
test('subscription reminders create no payment or unattended orders and pause stops reminders', async () => {
  await create({type: 'SUBSCRIPTION', planKey: 'weekly-veg'});
  const saved = await post('subscriptions/save', {offerId: 'offer', cadenceDays: 7}, 'a');
  assert.equal(saved.status, 200); await api.reconcile(); await api.reconcile();
  assert.equal((await db.collection('rescueClaims').get()).size, 0);
  const due = (await db.collection('notificationOutbox').get()).docs.filter(d => d.get('title') === 'Đến kỳ giải cứu định kỳ');
  assert.equal(due.length, 1);
  const body = {offerId: 'offer', units: 1, subscriptionId: saved.body.subscriptionId};
  assert.equal((await post('join', body, 'a')).status, 200);
  const subscriptions = (await post('subscriptions/list', {}, 'a')).body.items;
  assert.ok(subscriptions[0].nextDueAt > now); assert.equal(subscriptions[0].currentOffer, null);
  assert.equal((await post('subscriptions/pause', {subscriptionId: saved.body.subscriptionId, status: 'PAUSED'}, 'a')).status, 200);
});
test('following a subscription after joining its first offer starts the next cycle without suggesting duplicate pickup', async () => {
  await create({type: 'SUBSCRIPTION', planKey: 'weekly'});
  assert.equal((await post('join', {offerId: 'offer', units: 1}, 'a')).status, 200);
  assert.equal((await post('subscriptions/save', {offerId: 'offer', cadenceDays: 7}, 'a')).status, 200);
  const item = (await post('subscriptions/list', {}, 'a')).body.items[0];
  assert.ok(item.nextDueAt > now); assert.equal(item.currentOffer, null);
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
