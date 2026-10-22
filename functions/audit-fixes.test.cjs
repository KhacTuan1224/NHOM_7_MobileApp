const {before, after, beforeEach, test} = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const admin = require('firebase-admin');
const crypto = require('node:crypto');
const {createCampaignLifecycle} = require('./campaign-lifecycle');
const {createOrderReconciliation} = require('./order-reconciliation');
const {createCartMigration} = require('./cart-migration');
if (!/^(localhost|127\.0\.0\.1):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST || '')) throw Error('Local emulator required; never production');
const appInstance = admin.initializeApp({projectId: 'demo-rescuefarm'}, 'audit-fixes');
const db = appInstance.firestore(), ts = ms => admin.firestore.Timestamp.fromMillis(ms);
let server, base;
const token = 'a'.repeat(64), hash = crypto.createHash('sha256').update(token).digest('hex');
before(async () => {
  const authenticate = async (req, roles) => {
    const uid = (req.get('Authorization') || '').replace(/^Bearer /, ''), user = uid ? await db.doc('users/' + uid).get() : null;
    if (!user?.exists || user.get('status') !== 'ACTIVE' || !roles.includes(user.get('role'))) throw Object.assign(Error('Forbidden'), {status: 403});
    return {uid, role: user.get('role')};
  };
  const lifecycle = createCampaignLifecycle({db, admin, authenticate});
  const app = express(); app.use(express.json());
  app.use(createOrderReconciliation({db, admin, authenticate, campaignLifecycle: lifecycle}).router);
  app.use(createCartMigration({db, admin, authenticate}).router);
  app.use((error, req, res, next) => res.status(error.status || 500).json({error: error.message}));
  server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  base = 'http://127.0.0.1:' + server.address().port;
});
after(async () => { if (server) await new Promise(resolve => server.close(resolve)); await appInstance.delete(); });
beforeEach(async () => {
  await fetch('http://' + process.env.FIRESTORE_EMULATOR_HOST + '/emulator/v1/projects/demo-rescuefarm/databases/(default)/documents', {method: 'DELETE'});
  const batch = db.batch();
  for (const [uid, role] of Object.entries({admin: 'ADMIN', seller: 'SELLER', customer: 'CUSTOMER', other: 'CUSTOMER'}))
    batch.set(db.doc('users/' + uid), {role, status: 'ACTIVE', sellerStatus: 'APPROVED'});
  batch.set(db.doc('products/p'), {sellerId: 'seller', unit: 'kg', status: 'ACTIVE'});
  batch.set(db.doc('productBatches/b'), {id: 'b', productId: 'p', initialQuantity: 10, availableQuantity: 7, reservedQuantity: 3,
    soldQuantity: 0, status: 'EXPIRED', expiryDate: ts(Date.now() - 10000), inventoryVersion: 1});
  batch.set(db.doc('orders/order'), {id: 'order', orderCode: 'RF-ORDER', ownerId: 'customer', ownerType: 'CUSTOMER', sellerId: 'seller',
    status: 'CONFIRMED', paymentStatus: 'PAID', paymentMethod: 'BANK_TRANSFER', totalAmount: 90, fulfillmentType: 'PICKUP',
    inventoryFinalized: false, campaignId: '', campaignReservedQuantity: 0, batchReservations: {b: 3}, expiryReviewRequired: true});
  batch.set(db.doc('payments/order'), {status: 'PAID', amount: 90, verificationProvider: 'SEPAY', referenceCode: 'ORIGINAL-SEPAY-TX'});
  await batch.commit();
});
async function post(path, body, uid = 'admin') {
  const r = await fetch(base + path, {method: 'POST', headers: {'Content-Type': 'application/json', ...(uid ? {Authorization: 'Bearer ' + uid} : {})}, body: JSON.stringify(body)});
  return {status: r.status, body: await r.json()};
}
const resolve = () => ({orderId: 'order', requestId: 'refund-1', mode: 'REFUND_CANCEL', reason: 'Đã đối chiếu bằng chứng', evidenceUrl: 'https://example.test/proof',
  refundAmount: 90, refundReference: 'EXTERNAL-REFUND-1', confirmRefunded: true});
test('reconciliation API enforces ownership/admin, full actual refund, atomic stock and replay while preserving SePay receipt', async () => {
  assert.equal((await post('/orders/reconciliation/get', {orderId: 'order'}, 'other')).status, 403);
  assert.equal((await post('/orders/reconciliation/get', {orderId: 'order'}, 'customer')).status, 200);
  assert.equal((await post('/orders/reconciliation/list', {}, 'seller')).body.items.length, 1);
  assert.equal((await post('/orders/reconciliation/resolve', resolve(), 'seller')).status, 403);
  assert.equal((await post('/orders/reconciliation/resolve', {...resolve(), confirmRefunded: false})).status, 400);
  assert.equal((await db.doc('productBatches/b').get()).get('reservedQuantity'), 3);
  const result = await post('/orders/reconciliation/resolve', resolve()); assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal((await post('/orders/reconciliation/resolve', resolve())).body.replay, true);
  assert.equal((await db.doc('payments/order').get()).get('status'), 'REFUNDED');
  assert.equal((await db.doc('payments/order').get()).get('referenceCode'), 'ORIGINAL-SEPAY-TX');
  assert.equal((await db.doc('payments/order').get()).get('verificationProvider'), 'SEPAY');
  assert.equal((await db.doc('productBatches/b').get()).get('availableQuantity'), 10);
  assert.equal((await db.collection('orderReconciliationEvents').get()).size, 1);
});
test('handover cannot be backdated; a real pre-expiry record plus buyer acknowledgment permits late receipt', async () => {
  await db.doc('orders/order').update({status: 'READY_FOR_PICKUP'});
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', action: 'HANDOVER', evidenceUrl: 'https://example.test/handover'}, 'seller')).status, 409);
  await db.doc('orders/order').update({status: 'READY_FOR_PICKUP', expiryReviewRequired: false});
  await db.doc('productBatches/b').update({status: 'RESERVED_PARTIAL', expiryDate: ts(Date.now() + 3600000)});
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', action: 'HANDOVER', evidenceUrl: 'https://example.test/handover'}, 'seller')).status, 200);
  const handedAt = (await db.doc('orders/order').get()).get('handoverRecordedAt').toMillis();
  await db.doc('productBatches/b').update({status: 'EXPIRED', expiryDate: ts(handedAt + 1)});
  await db.doc('orders/order').update({expiryReviewRequired: true});
  const command = {...resolve(), mode: 'LATE_RECEIPT'};
  assert.equal((await post('/orders/reconciliation/resolve', command)).status, 409);
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', action: 'ACK_RECEIVED'}, 'customer')).status, 200);
  const result = await post('/orders/reconciliation/resolve', command); assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal((await db.doc('orders/order').get()).get('status'), 'DELIVERED');
  assert.equal((await db.doc('productBatches/b').get()).get('soldQuantity'), 3);
});
test('guest evidence access accepts only the matching checkout token or unexpired grant', async () => {
  await db.doc('orders/order').update({ownerType: 'GUEST', ownerId: 'guest-owner'});
  await db.doc('checkoutReceipts/order').set({ownerType: 'GUEST', ownerId: 'guest-owner', tokenHash: hash});
  assert.equal((await post('/orders/reconciliation/get', {orderId: 'order', accessToken: 'b'.repeat(64)}, '')).status, 403);
  assert.equal((await post('/orders/reconciliation/get', {orderId: 'order', accessToken: token}, '')).body.role, 'GUEST');
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', accessToken: token, action: 'REQUEST'}, '')).status, 200);
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', accessToken: token, action: 'RETURNED', evidenceUrl: 'https://example.test/fake'}, '')).status, 400);
});
test('cart migration real Firestore transaction survives concurrent/replayed delivery and account switches', async () => {
  const body = {requestId: 'local-durable-job', targetUserId: 'customer', items: [{id: 'p::b', productId: 'p', batchId: 'b', sellerId: 'seller', quantity: 3, unitPrice: 30, selected: true}]};
  const results = await Promise.all([post('/cart/migrate-guest', body, 'customer'), post('/cart/migrate-guest', body, 'customer')]);
  assert.deepEqual(results.map(r => r.status), [200, 200]);
  assert.equal((await db.doc('customerCarts/customer').get()).get('items')[0].quantity, 3);
  assert.equal((await post('/cart/migrate-guest', body, 'customer')).body.replay, true);
  assert.equal((await post('/cart/migrate-guest', body, 'other')).status, 400);
  assert.equal((await post('/cart/migrate-guest', {...body, items: [{...body.items[0], quantity: 4}]}, 'customer')).status, 409);
});
test('paid shipping reconciliation requires actual return evidence before releasing inventory', async () => {
  await db.doc('orders/order').update({status: 'SHIPPING', fulfillmentType: 'DELIVERY'});
  await db.doc('shipments/order').set({status: 'SHIPPING'});
  assert.equal((await post('/orders/reconciliation/resolve', {...resolve(), confirmReturned: true})).status, 409);
  assert.equal((await post('/orders/reconciliation/evidence', {orderId: 'order', action: 'RETURNED', evidenceUrl: 'https://example.test/actual-return'}, 'seller')).status, 200);
  const result = await post('/orders/reconciliation/resolve', {...resolve(), confirmReturned: true});
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal((await db.doc('productBatches/b').get()).get('availableQuantity'), 10);
  assert.equal((await db.doc('shipments/order').get()).get('status'), 'CANCELLED');
});
test('pending transfer cancellation requires admin bank-statement evidence, never manually confirms incoming payment', async () => {
  await db.doc('orders/order').update({status: 'PENDING', paymentStatus: 'PENDING'});
  await db.doc('payments/order').update({status: 'PENDING'});
  const command = {...resolve(), mode: 'CANCEL_UNPAID', refundAmount: 0, confirmRefunded: false};
  assert.equal((await post('/orders/reconciliation/resolve', command)).status, 409);
  const result = await post('/orders/reconciliation/resolve', {...command, confirmNoTransfer: true});
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal((await db.doc('payments/order').get()).get('status'), 'FAILED');
  assert.equal((await db.doc('payments/order').get()).get('referenceCode'), 'ORIGINAL-SEPAY-TX');
});
