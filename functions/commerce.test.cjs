const {before, after, beforeEach, test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const express = require('express');
const admin = require('firebase-admin');
const {createCommerce, price} = require('./commerce');
const {createCampaignLifecycle} = require('./campaign-lifecycle');
const {createNotificationWorker} = require('./notification-outbox');
const {createNotificationsApi} = require('./notifications-api');
const rulesRequire = require('node:module').createRequire(require('node:path').resolve(__dirname, '../tests/firestore/package.json'));
const {initializeTestEnvironment, assertFails} = rulesRequire('@firebase/rules-unit-testing');
const {doc, runTransaction, serverTimestamp} = rulesRequire('firebase/firestore');

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^(127\.0\.0\.1|localhost):\d+$/.test(emulator || '')) {
  throw new Error('These tests only run against a local Firestore Emulator, never production.');
}
admin.initializeApp({projectId: 'demo-rescuefarm'});
const db = admin.firestore();
const authenticate = async (req, roles) => {
  const uid = (req.get('Authorization') || '').replace(/^Bearer /, '');
  const user = uid ? await db.collection('users').doc(uid).get() : null;
  if (!user || !user.exists || user.get('status') !== 'ACTIVE' || !roles.includes(user.get('role')))
    throw Object.assign(new Error('Forbidden'), {status: 403});
  return {uid, role: user.get('role')};
};
const campaignLifecycle = createCampaignLifecycle({db, admin, authenticate});
const commerce = createCommerce({db, admin, authenticate, campaignLifecycle});
let server, base, rulesEnv;
const future = () => admin.firestore.Timestamp.fromMillis(Date.now() + 7 * 86400000);
const past = () => admin.firestore.Timestamp.fromMillis(Date.now() - 86400000);
const guest = () => ({guestId: `guest_${crypto.randomUUID()}`, guestToken: crypto.randomBytes(32).toString('hex')});
const request = (overrides = {}) => ({requestId: crypto.randomUUID(), receiverName: 'Khách kiểm thử',
  receiverPhone: '0912345678', receiverAddress: '10 đường A', latitude: 10.8, longitude: 106.7,
  fulfillmentType: 'PICKUP', paymentMethod: 'CASH_ON_SITE', note: '',
  items: [{productId: 'product-a', batchId: 'batch-a', sellerId: 'seller', quantity: 3}], ...overrides});

before(async () => {
  rulesEnv = await initializeTestEnvironment({projectId: 'demo-rescuefarm', firestore: {
    host: emulator.split(':')[0], port: Number(emulator.split(':')[1]),
    rules: require('node:fs').readFileSync(require('node:path').resolve(__dirname, '../firestore.rules'), 'utf8')
  }});
  const app = express(); app.use(express.json()); app.use(campaignLifecycle.router); app.use(commerce.router);
  app.use(createNotificationsApi({db, admin, authenticate}).router);
  app.use((error, req, res, next) => res.status(error.status || 500).json({error: error.message}));
  server = await new Promise(resolve => { const value = app.listen(0, '127.0.0.1', () => resolve(value)); });
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (server) await new Promise(resolve => server.close(resolve)); if (rulesEnv) await rulesEnv.cleanup(); await admin.app().delete(); });
beforeEach(async () => {
  const reset = await fetch(`http://${emulator}/emulator/v1/projects/demo-rescuefarm/databases/(default)/documents`, {method: 'DELETE'});
  assert.equal(reset.status, 200);
  const batch = db.batch();
  const documents = {
    'users/customer-a': {role: 'CUSTOMER', status: 'ACTIVE'},
    'users/customer-b': {role: 'CUSTOMER', status: 'ACTIVE'},
    'users/blocked': {role: 'CUSTOMER', status: 'BLOCKED'},
    'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'},
    'users/admin': {role: 'ADMIN', status: 'ACTIVE'},
    'sellerPublicProfiles/seller': {latitude: 10.8, longitude: 106.7},
    'sellerPaymentProfiles/seller': {bankCode: 'VCB', bankAccountName: 'Test Seller', bankAccountNumber: '123456', qrUrl: ''},
    'appConfig/shipping': {baseFeeVnd: 15000, perKmFeeVnd: 1000, freeDistanceKm: 2},
    'products/product-a': {sellerId: 'seller', status: 'ACTIVE', name: 'Rau cải', unit: 'kg', originalPrice: 40000, rescuePrice: 30000, imageUrls: ['https://example.com/image.jpg']},
    'productBatches/batch-a': {id: 'batch-a', productId: 'product-a', activeCampaignId: '', harvestDate: past(), expiryDate: future(),
      initialQuantity: 10, availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0, inventoryVersion: 1, status: 'AVAILABLE', createdAt: past(), updatedAt: past()},
    'customerCarts/customer-a': {items: [
      {id: 'product-a::batch-a', productId: 'product-a', batchId: 'batch-a', quantity: 4, selected: true},
      {id: 'keep', productId: 'other', batchId: 'other', quantity: 2, selected: false}]},
    'posts/published': {status: 'PUBLISHED', viewCount: 0},
    'posts/hidden': {status: 'HIDDEN', viewCount: 0}
  };
  for (const [path, data] of Object.entries(documents)) batch.set(db.doc(path), data);
  await batch.commit();
});

async function post(path, data, uid = '') {
  const result = await fetch(base + path, {method: 'POST', headers: {'Content-Type': 'application/json',
    ...(uid ? {Authorization: `Bearer ${uid}`} : {})}, body: JSON.stringify(data)});
  return {status: result.status, body: await result.json()};
}

test('Customer checkout reserves stock, writes snapshots/payment and removes only bought cart quantities', async () => {
  const data = request(); const result = await post('/checkoutCustomer', {...data, totalAmount: 1, ownerId: 'customer-b'}, 'customer-a');
  assert.equal(result.status, 200); assert.equal(result.body.order.ownerId, 'customer-a');
  assert.equal(result.body.order.totalAmount, 90000);
  const stock = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(stock.availableQuantity, 7); assert.equal(stock.reservedQuantity, 3); assert.equal(stock.soldQuantity, 0);
  const line = (await db.doc(`orders/${data.requestId}/items/batch-a`).get()).data();
  assert.equal(line.productNameSnapshot, 'Rau cải'); assert.equal(line.finalPriceSnapshot, 30000);
  assert.equal((await db.doc(`payments/${data.requestId}`).get()).get('status'), 'UNPAID');
  assert.equal((await db.doc(`shipments/${data.requestId}`).get()).exists, false);
  const cart = (await db.doc('customerCarts/customer-a').get()).get('items');
  assert.equal(cart[0].quantity, 1); assert.equal(cart[1].id, 'keep');
  assert.equal((await db.doc(`notifications/${data.requestId}_PENDING`).get()).exists, true);
});

test('same request replay does not reserve, consume the cart or create notifications twice', async () => {
  const data = request();
  assert.equal((await post('/checkoutCustomer', data, 'customer-a')).status, 200);
  const again = await post('/checkoutCustomer', data, 'customer-a');
  assert.equal(again.status, 200); assert.equal(again.body.replay, true);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'), 3);
  assert.equal((await db.doc('customerCarts/customer-a').get()).get('items')[0].quantity, 1);
  assert.equal((await post('/checkoutCustomer', {...data, receiverPhone: '0987654321'}, 'customer-a')).status, 409);
  assert.equal((await post('/checkoutCustomer', data, 'customer-b')).status, 409);
});

test('concurrent shoppers cannot reserve the same remaining stock', async () => {
  const a = request({items: [{productId: 'product-a', batchId: 'batch-a', sellerId: 'seller', quantity: 7}]});
  await db.doc('customerCarts/customer-a').delete();
  const results = await Promise.all([post('/checkoutCustomer', a, 'customer-a'), post('/checkoutCustomer', {...a, requestId: crypto.randomUUID()}, 'customer-b')]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'), 7);
  assert.equal((await db.collection('orders').get()).size, 1);
});

for (const kind of ['expired', 'archived', 'no-stock', 'unapproved-seller']) {
  test(`checkout rejects ${kind} and rolls back every write`, async () => {
    if (kind === 'expired') await db.doc('productBatches/batch-a').update({expiryDate: past()});
    if (kind === 'archived') await db.doc('productBatches/batch-a').update({status: 'ARCHIVED'});
    if (kind === 'no-stock') await db.doc('productBatches/batch-a').update({availableQuantity: 1, soldQuantity: 9});
    if (kind === 'unapproved-seller') await db.doc('users/seller').update({sellerStatus: 'PENDING_APPROVAL'});
    assert.equal((await post('/checkoutCustomer', request(), 'customer-a')).status, 409);
    assert.equal((await db.collection('orders').get()).size, 0);
    assert.equal((await db.collection('payments').get()).size, 0);
    assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'), 0);
  });
}

test('missing promotions and expired promotions fall back to rescue price', async () => {
  const data = request();
  assert.equal((await post('/quoteCustomerCheckout', data, 'customer-a')).body.subtotal, 90000);
  await db.doc('promotions/product-a').set({active: true, type: 'PERCENT', value: 90, startDate: past(), endDate: past()});
  assert.equal((await post('/checkoutCustomer', data, 'customer-a')).body.order.subtotal, 90000);
});

test('fractional quantities share rounded line subtotals between quote and checkout', async () => {
  await db.doc('products/product-a').update({rescuePrice: 27001});
  const batch = (await db.doc('productBatches/batch-a').get()).data();
  await db.doc('productBatches/batch-b').set({...batch, id: 'batch-b'});
  const data = request({items: ['batch-a', 'batch-b'].map(batchId => ({productId: 'product-a', batchId, sellerId: 'seller', quantity: 1.5}))});
  await db.doc('customerCarts/customer-a').delete();
  const quote = await post('/quoteCustomerCheckout', data, 'customer-a'); assert.equal(quote.body.subtotal, 81004);
  const result = await post('/checkoutCustomer', {...data, expectedSubtotal: quote.body.subtotal, expectedShippingFee: 0}, 'customer-a');
  assert.equal(result.status, 200); assert.equal(result.body.order.subtotal, 81004);
});

test('a changed quote aborts without an order, payment or reservation', async () => {
  const result = await post('/checkoutCustomer', request({expectedSubtotal: 1}), 'customer-a');
  assert.equal(result.status, 409); assert.match(result.body.error, /QUOTE_CHANGED/);
  assert.equal((await db.collection('orders').get()).size, 0);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('availableQuantity'), 10);
});

test('Guest DELIVERY/COD creates a shipment; Guest PICKUP transfer snapshots the bank and stays PENDING', async () => {
  const g = guest(), delivery = request({...g, fulfillmentType: 'DELIVERY', paymentMethod: 'COD'});
  const result = await post('/checkoutGuest', delivery); assert.equal(result.status, 200);
  assert.equal(result.body.order.ownerType, 'GUEST'); assert.equal(result.body.order.shippingFee, 15000);
  assert.equal((await db.doc(`shipments/${delivery.requestId}`).get()).exists, true);
  const pickup = request({...g, paymentMethod: 'BANK_TRANSFER'});
  const second = await post('/checkoutGuest', pickup); assert.equal(second.status, 200);
  const payment = (await db.doc(`payments/${pickup.requestId}`).get()).data();
  assert.equal(payment.status, 'PENDING'); assert.equal(payment.bankCode, 'VCB'); assert.equal(payment.amount, 90000);
  assert.equal((await db.doc(`shipments/${pickup.requestId}`).get()).exists, false);
});

test('Guest ON_SITE requires a fresh active mobile point and reserves campaign without counting rescued', async () => {
  await db.doc('productBatches/batch-a').update({activeCampaignId: 'mobile'});
  await db.doc('campaigns/mobile').set({sellerId: 'seller', status: 'ACTIVE', startDate: past(), endDate: future(),
    rescueMode: 'MOBILE_POINT', locationSharingEnabled: true, currentLatitude: 10.8, currentLongitude: 106.7,
    locationUpdatedAt: past(), targetQuantity: 10, reservedQuantity: 0, rescuedQuantity: 0, batchTargets: {'batch-a': 10}});
  const data = request({...guest(), fulfillmentType: 'ON_SITE_RESCUE'});
  assert.equal((await post('/checkoutGuest', data)).status, 409);
  await db.doc('campaigns/mobile').update({locationUpdatedAt: admin.firestore.Timestamp.now()});
  const result = await post('/checkoutGuest', data); assert.equal(result.status, 200);
  assert.equal((await db.doc('campaigns/mobile').get()).get('reservedQuantity'), 3);
  assert.equal((await db.doc('campaigns/mobile').get()).get('rescuedQuantity'), 0);
  assert.equal((await db.doc(`shipments/${data.requestId}`).get()).exists, false);
});

test('Guest replay requires the original device secret and binds all order inputs', async () => {
  const data = request({...guest()}); assert.equal((await post('/checkoutGuest', data)).status, 200);
  assert.equal((await post('/checkoutGuest', data)).body.replay, true);
  assert.equal((await post('/checkoutGuest', {...data, guestToken: crypto.randomBytes(32).toString('hex')})).status, 409);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'), 3);
});

test('Guest lookup requires exact code AND normalized phone and issues a scoped expiring capability', async () => {
  const data = request({...guest()}), created = await post('/checkoutGuest', data);
  const code = created.body.order.orderCode;
  assert.equal((await post('/lookupGuestOrder', {orderCode: code, receiverPhone: '0987654321'})).status, 404);
  const found = await post('/lookupGuestOrder', {orderCode: code.toLowerCase(), receiverPhone: '+84 912 345 678'});
  assert.equal(found.status, 200); assert.equal(found.body.order.items.length, 1);
  assert.equal(found.body.payment.method, 'CASH_ON_SITE');
  const details = {orderId: data.requestId, accessToken: found.body.accessToken};
  assert.equal((await post('/guestOrderDetails', details)).status, 200);
  assert.equal((await post('/guestOrderDetails', {...details, orderId: crypto.randomUUID()})).status, 403);
  await db.collection('guestOrderGrants').doc(crypto.createHash('sha256').update(details.accessToken).digest('hex')).update({expiresAt: past()});
  assert.equal((await post('/guestOrderDetails', details)).status, 403);
  assert.equal((await post('/guestOrderDetails', {orderId: data.requestId, accessToken: data.guestToken})).status, 200);
});

test('wrong/absent Guest capability, anonymous Customer checkout and blocked account are denied', async () => {
  const data = request({...guest()}); await post('/checkoutGuest', data);
  assert.equal((await post('/guestOrderDetails', {orderId: data.requestId})).status, 403);
  assert.equal((await post('/checkoutCustomer', request())).status, 403);
  assert.equal((await post('/checkoutCustomer', request(), 'blocked')).status, 403);
});

test('Guest lookup is persistently rate limited; Customer orders cannot be looked up as Guest', async () => {
  const data = request(), created = await post('/checkoutCustomer', data, 'customer-a');
  for (let i = 0; i < 10; i++) assert.equal((await post('/lookupGuestOrder', {orderCode: created.body.order.orderCode, receiverPhone: data.receiverPhone})).status, 404);
  assert.equal((await post('/lookupGuestOrder', {})).status, 429);
});

test('post views increment atomically once per device/day, including Guest; hidden posts stay unchanged', async () => {
  const viewerId = guest().guestId, data = {postId: 'published', viewerId};
  const results = await Promise.all([post('/recordPostView', data), post('/recordPostView', data)]);
  assert.equal(results.every(result => result.status === 200), true);
  assert.equal((await db.doc('posts/published').get()).get('viewCount'), 1);
  assert.equal((await post('/recordPostView', {postId: 'hidden', viewerId})).status, 404);
  assert.equal((await db.doc('posts/hidden').get()).get('viewCount'), 0);
});

test('invalid duplicate batches and incompatible payment cannot reach inventory writes', async () => {
  const data = request(), result = await post('/checkoutGuest', {...data, ...guest(), items: [...data.items, ...data.items]});
  assert.equal(result.status, 400);
  assert.equal((await post('/checkoutGuest', request({...guest(), paymentMethod: 'COD'}))).status, 400);
  assert.equal((await db.collection('orders').get()).size, 0);
});

async function sellerTransition(orderId, target) {
  const result = await post('/orders/transition', {orderId, targetStatus: target}, 'seller');
  assert.equal(result.status, 200, JSON.stringify(result.body));
}

test('Seller can cancel a backend-created Guest order through the trusted backend; Guest sees released stock/status', async () => {
  const data = request({...guest()}); assert.equal((await post('/checkoutGuest', data)).status, 200);
  await sellerTransition(data.requestId, 'CANCELLED');
  const result = await post('/guestOrderDetails', {orderId: data.requestId, accessToken: data.guestToken});
  assert.equal(result.body.order.status, 'CANCELLED'); assert.equal(result.body.payment.status, 'FAILED');
  const batch = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(batch.availableQuantity, 10); assert.equal(batch.reservedQuantity, 0); assert.equal(batch.everOrdered, true);
});

test('Seller can complete a backend-created Guest pickup through the trusted backend; Guest sees PAID/DELIVERED', async () => {
  const data = request({...guest()}); assert.equal((await post('/checkoutGuest', data)).status, 200);
  await sellerTransition(data.requestId, 'CONFIRMED'); await sellerTransition(data.requestId, 'PREPARING');
  await sellerTransition(data.requestId, 'READY_FOR_PICKUP'); await sellerTransition(data.requestId, 'DELIVERED');
  const result = await post('/guestOrderDetails', {orderId: data.requestId, accessToken: data.guestToken});
  assert.equal(result.body.order.status, 'DELIVERED'); assert.equal(result.body.payment.status, 'PAID');
  const batch = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(batch.availableQuantity, 7); assert.equal(batch.reservedQuantity, 0); assert.equal(batch.soldQuantity, 3);
});

async function pendingCampaign() {
  await db.doc('products/product-b').set({sellerId: 'seller', status: 'ACTIVE', name: 'B', unit: 'kg', originalPrice: 40000, rescuePrice: 30000});
  await db.doc('productBatches/batch-b').set({...(await db.doc('productBatches/batch-a').get()).data(), id: 'batch-b', productId: 'product-b', initialQuantity: 100, availableQuantity: 100});
  await db.doc('campaigns/allocation').set({id: 'allocation', sellerId: 'seller', status: 'PENDING_APPROVAL', title: 'Rescue',
    targetQuantity: 93, batchTargets: {'batch-a': 3, 'batch-b': 90}, reservedQuantity: 0, rescuedQuantity: 0,
    startDate: past(), endDate: future(), rescueMode: 'FIXED_POINT', rescueReason: 'OVER_SUPPLY'});
}

test('real Firestore transactions serialize competing checkouts against per-batch allocation', async () => {
  await pendingCampaign();
  assert.equal((await post('/campaigns/moderate', {campaignId: 'allocation', targetStatus: 'ACTIVE'}, 'admin')).status, 200);
  const results = await Promise.all([post('/checkoutGuest', request({...guest()})), post('/checkoutGuest', request({...guest()}))]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  let campaign = (await db.doc('campaigns/allocation').get()).data();
  assert.equal(campaign.reservedByBatch['batch-a'], 3);
  const winner = results.find(r => r.status === 200).body.order.id;
  await sellerTransition(winner, 'CANCELLED');
  campaign = (await db.doc('campaigns/allocation').get()).data();
  assert.equal(campaign.reservedByBatch['batch-a'], 0);
  assert.equal((await post('/checkoutGuest', request({...guest()}))).status, 200);
});

test('approval reads current stock and rejects atomically; inactive links are repaired at checkout', async () => {
  await pendingCampaign();
  await db.doc('productBatches/batch-a').update({availableQuantity: 1, soldQuantity: 9});
  assert.equal((await post('/campaigns/moderate', {campaignId: 'allocation', targetStatus: 'ACTIVE'}, 'admin')).status, 409);
  assert.equal((await db.doc('campaigns/allocation').get()).get('status'), 'PENDING_APPROVAL');
  assert.equal((await db.doc('productBatches/batch-b').get()).get('activeCampaignId'), '');
  await db.doc('productBatches/batch-a').update({availableQuantity: 10, soldQuantity: 0, activeCampaignId: 'allocation'});
  await db.doc('campaigns/allocation').update({status: 'REJECTED'});
  assert.equal((await post('/checkoutGuest', request({...guest()}))).status, 200);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('activeCampaignId'), null);
});

test('old client cannot transition an order directly after backend lifecycle cutover', async () => {
  const data = request({...guest()}); await post('/checkoutGuest', data);
  const client = rulesEnv.authenticatedContext('seller').firestore();
  await assertFails(runTransaction(client, async tx => {
    const ref = doc(client, `orders/${data.requestId}`); await tx.get(ref);
    tx.update(ref, {status: 'CONFIRMED', updatedAt: serverTimestamp()});
  }));
  await sellerTransition(data.requestId, 'CONFIRMED');
});

test('committed checkout delivers inbox/push without opening any Android screen; FCM failure retries once', async () => {
  await db.doc('users/customer-a/devices/test').set({token: 'test-token'});
  const data = request(); assert.equal((await post('/checkoutCustomer', data, 'customer-a')).status, 200);
  let sends = 0;
  const worker = createNotificationWorker({db, admin, now: () => Date.now() + 1000, messaging: () => ({sendEachForMulticast: async () => {
    sends++; if (sends === 1) throw Error('Simulated FCM outage'); return {responses: [{success: true}]};
  }})});
  await worker.runOnce();
  const customerInbox = await db.collection('notifications').where('userId', '==', 'customer-a').get();
  assert.equal(customerInbox.size, 1); assert.equal(customerInbox.docs[0].id, `${data.requestId}_PENDING`);
  assert.equal((await db.collection('notifications').where('userId', '==', 'seller').get()).size, 1);
  assert.equal((await db.collection('notifications').where('userId', '==', 'admin').get()).size, 1);
  const deliveries = await db.collection('notificationDeliveries').where('userId', '==', 'customer-a').get();
  await deliveries.docs[0].ref.update({nextAttemptAt: past()}); await worker.runOnce(); assert.equal(sends, 2);
  assert.equal((await db.collection('notifications').where('userId', '==', 'customer-a').get()).size, 1);
  const opened = await post('/notifications/resolve', {notificationId: customerInbox.docs[0].id}, 'customer-a');
  assert.equal(opened.status, 200); assert.equal(opened.body.destination, 'orderDetailFragment');
  assert.equal((await post('/notifications/resolve', {notificationId: customerInbox.docs[0].id}, 'customer-b')).status, 403);
});
