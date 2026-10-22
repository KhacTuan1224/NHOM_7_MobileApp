const {before, after, beforeEach, test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const express = require('express');
const admin = require('firebase-admin');
const {createCommerce, price} = require('./commerce');
const {createCampaignLifecycle} = require('./campaign-lifecycle');
const {createBatchExpiry} = require('./batch-expiry');
const {createNotificationWorker} = require('./notification-outbox');
const {createNotificationsApi} = require('./notifications-api');
const {createSePayApi} = require('./sepay');
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
const testEnv = {SEPAY_MERCHANTS_JSON: JSON.stringify({seller: {enabled: true, bankCode: 'VCB', accountNumber: '123456', webhookSecret: 'emulator-only-webhook-secret-at-least-32-characters'}})};
const commerce = createCommerce({db, admin, authenticate, campaignLifecycle, env: testEnv});
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
  const app = express(); app.use(express.json({verify: (req, res, bytes) => { req.rawBody = Buffer.from(bytes); }}));
  app.use(createSePayApi({db, admin, env: testEnv}).router);
  app.use(campaignLifecycle.router); app.use(commerce.router);
  app.use(require('./rescue-programs').createRescuePrograms({db, admin, authenticate}).router);
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
  };
  for (const [path, data] of Object.entries(documents)) batch.set(db.doc(path), data);
  await batch.commit();
});

async function post(path, data, uid = '') {
  const result = await fetch(base + path, {method: 'POST', headers: {'Content-Type': 'application/json',
    ...(uid ? {Authorization: `Bearer ${uid}`} : {})}, body: JSON.stringify(data)});
  const body = await result.text();
  return {status: result.status, body: body.startsWith("{") ? JSON.parse(body) : {error: body}};
}

test('expanded rescue and normal checkout share one stock ledger without releasing each other reservations', async () => {
  const now = Date.now(), offer = {requestId: 'shared-stock', type: 'MYSTERY_BOX', title: 'Hộp rau',
    description: 'Rau cải giải cứu nhận tại cửa hàng', categoryLabel: 'Rau củ', contentsDisclosure: 'Một suất rau cải còn ăn tốt',
    totalUnits: 6, unitLabel: 'hộp', unitPrice: 10000, originalUnitPrice: 40000,
    closesAt: now + 3600000, pickupStartAt: now + 3600000, pickupEndAt: now + 7200000,
    pickupAddress: '10 đường A, TP HCM', latitude: 10.8, longitude: 106.7,
    components: [{batchId: 'batch-a', quantityPerUnit: 1}]};
  assert.equal((await post('/rescue/create', offer, 'seller')).status, 200);
  const order = request(); assert.equal((await post('/checkoutCustomer', order, 'customer-a')).status, 200);
  let stock = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(stock.availableQuantity, 1); assert.equal(stock.reservedQuantity, 9);
  assert.equal((await post('/rescue/close', {offerId: offer.requestId}, 'seller')).status, 200);
  stock = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(stock.availableQuantity, 7); assert.equal(stock.reservedQuantity, 3);
  assert.equal((await post('/orders/transition', {orderId: order.requestId, targetStatus: 'CANCELLED'}, 'seller')).status, 200);
  stock = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(stock.availableQuantity, 10); assert.equal(stock.reservedQuantity, 0);
});

test('Batch expiry real Firestore sweep preserves stock, cancels cash hold and records one private event', async () => {
  const data = request();
  assert.equal((await post('/checkoutCustomer', data, 'customer-a')).status, 200);
  await db.doc('productBatches/batch-a').update({expiryDate: past()});
  const worker = createBatchExpiry({db, admin, campaignLifecycle, pageSize: 1});
  for (let i = 0; i < 5; i++) await worker.reconcile();
  const stock = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(stock.status, 'EXPIRED'); assert.equal(stock.availableQuantity, 10);
  assert.equal(stock.reservedQuantity, 0); assert.equal(stock.soldQuantity, 0);
  const order = (await db.doc(`orders/${data.requestId}`).get()).data();
  assert.equal(order.status, 'CANCELLED'); assert.equal(order.cancellationReason, 'BATCH_EXPIRED');
  assert.equal((await db.collection('batchExpiryEvents').get()).size, 1);
  assert.equal((await db.doc('batchExpiryEvents/batch-a').get()).get('sellerId'), 'seller');
});

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

test('retired Post endpoint is unavailable and cannot write any receipts', async () => {
  const response = await fetch(base + '/recordPostView', {method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({postId: 'retired', viewerId: guest().guestId})});
  assert.equal(response.status, 404);
  assert.equal((await db.collection('postViewReceipts').get()).size, 0);
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

for (const legacy of [false, true]) test(`real Firestore SePay webhook confirms ${legacy ? 'legacy pending' : 'new automatic'} payment once and notifies both parties`, async () => {
  const data = request({paymentMethod: 'BANK_TRANSFER'});
  assert.equal((await post('/checkoutCustomer', data, 'customer-a')).status, 200);
  const ref = db.doc('payments/' + data.requestId);
  if (legacy) await ref.update({verificationProvider: 'MANUAL'});
  const payment = (await ref.get()).data();
  const payload = {id: 92704, gateway: 'Vietcombank', transactionDate: new Date(Date.now() + 7 * 3600000).toISOString().slice(0,19).replace('T',' '),
    accountNumber: '123456', code: payment.transferContent, content: 'BANK-' + payment.transferContent,
    transferType: 'in', transferAmount: payment.amount, referenceCode: 'TEST-TRANSACTION-92704'};
  const raw = JSON.stringify(payload), timestamp = String(Math.floor(Date.now()/1000));
  const signature = 'sha256=' + crypto.createHmac('sha256', JSON.parse(testEnv.SEPAY_MERCHANTS_JSON).seller.webhookSecret)
    .update(timestamp + '.').update(raw).digest('hex');
  const send = (authenticated = true) => fetch(base + '/webhooks/sepay/seller', {method:'POST',
    headers:{'Content-Type':'application/json', ...(authenticated ? {'x-sepay-timestamp':timestamp,'x-sepay-signature':signature} : {})},body:raw});
  assert.equal((await send(false)).status,401);
  assert.equal((await ref.get()).get('status'),'PENDING');
  const results=await Promise.all([send(),send()]);assert(results.every(r=>r.status===200));
  const order=(await db.doc('orders/'+data.requestId).get()).data(), paid=(await ref.get()).data();
  assert.equal(order.status,'CONFIRMED');assert.equal(order.paymentStatus,'PAID');assert.equal(order.inventoryFinalized,false);
  assert.equal(paid.verificationProvider,'SEPAY');assert.equal(paid.verificationSource,'SEPAY_WEBHOOK');
  assert.equal(paid.transferContent,payment.transferContent);
  assert.equal((await db.collection('sepayBankTransactions').get()).size,1);
  for(const uid of ['seller','customer-a'])assert.equal((await db.doc(`notifications/${data.requestId}_SEPAY_PAID_${uid}`).get()).get('isRead'),false);
  const stock=(await db.doc('productBatches/batch-a').get()).data();assert.equal(stock.reservedQuantity,3);assert.equal(stock.soldQuantity,0);
});

test('Seller can cancel a backend-created Guest order through the trusted backend; Guest sees released stock/status', async () => {
  const data = request({...guest()}); assert.equal((await post('/checkoutGuest', data)).status, 200);
  await sellerTransition(data.requestId, 'CANCELLED');
  const result = await post('/guestOrderDetails', {orderId: data.requestId, accessToken: data.guestToken});
  assert.equal(result.body.order.status, 'CANCELLED'); assert.equal(result.body.payment.status, 'FAILED');
  const batch = (await db.doc('productBatches/batch-a').get()).data();
  assert.equal(batch.availableQuantity, 10); assert.equal(batch.reservedQuantity, 0); assert.equal(batch.everOrdered, true);
});

test('only Guest buyer can complete receipt after seller prepares pickup; Guest sees PAID/DELIVERED', async () => {
  const data = request({...guest()}); assert.equal((await post('/checkoutGuest', data)).status, 200);
  await sellerTransition(data.requestId, 'CONFIRMED'); await sellerTransition(data.requestId, 'PREPARING');
  await sellerTransition(data.requestId, 'READY_FOR_PICKUP');
  assert.equal((await post('/orders/transition', {orderId: data.requestId, targetStatus: 'DELIVERED'}, 'seller')).status, 403);
  assert.equal((await post('/orders/confirm-guest-receipt', {orderId: data.requestId, accessToken: data.guestToken})).status, 200);
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
async function multiSellerRequest(overrides = {}) {
  const batch = db.batch();
  batch.set(db.doc('users/seller-two'), {role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED',fullName:'Vườn thứ hai'});
  batch.set(db.doc('sellerPublicProfiles/seller-two'), {latitude:10.81,longitude:106.71,address:'Điểm nhận thứ hai'});
  batch.set(db.doc('products/product-two'), {sellerId:'seller-two',status:'ACTIVE',name:'Hộp trái cây',unit:'hộp',originalPrice:80000,rescuePrice:60000});
  batch.set(db.doc('productBatches/batch-two'), {productId:'product-two',status:'AVAILABLE',initialQuantity:10,availableQuantity:10,reservedQuantity:0,soldQuantity:0,inventoryVersion:1,harvestDate:past(),expiryDate:future()});
  batch.set(db.doc('customerCarts/customer-a'), {items:[
    {id:'product-a::batch-a',productId:'product-a',batchId:'batch-a',quantity:2,selected:true},
    {id:'product-two::batch-two',productId:'product-two',batchId:'batch-two',quantity:1,selected:true}]});
  await batch.commit();
  return request({items:[{productId:'product-a',batchId:'batch-a',sellerId:'seller',quantity:2},
    {productId:'product-two',batchId:'batch-two',sellerId:'seller-two',quantity:1}], ...overrides});
}

test('multi-seller checkout atomically creates separate orders at rescue prices',async()=>{
  const input = await multiSellerRequest();
  const quote=await post('/quoteCustomerCheckout',input,'customer-a');
  assert.equal(quote.status,200,JSON.stringify(quote.body));
  assert.equal(quote.body.subtotal,120000);assert.equal(quote.body.sellerDiscount,undefined);
  assert.equal(quote.body.platformDiscount,undefined);assert.equal(quote.body.total,120000);
  assert.equal(quote.body.availablePaymentMethods.includes('BANK_TRANSFER'),false);
  const result=await post('/checkoutCustomer',{...input,expectedSubtotal:120000,expectedShippingFee:0},'customer-a');
  assert.equal(result.status,200,JSON.stringify(result.body));assert.equal(result.body.orders.length,2);
  assert.ok(result.body.orders.every(order => order.pricingVersion === 3));
  assert.deepEqual(result.body.orders.map(o=>o.totalAmount),[60000,60000]);
  assert.ok(result.body.orders.every(o=>o.quantityDiscount===undefined));
  assert.equal((await db.doc('productBatches/batch-two').get()).get('reservedQuantity'),1);
  assert.deepEqual((await db.doc('customerCarts/customer-a').get()).get('items'),[]);
  for(const order of result.body.orders) {
    assert.equal((await db.doc('payments/'+order.id).get()).get('amount'),order.totalAmount);
    assert.equal(order.sellerReceivableAmount,60000);
    assert.equal((await db.doc('platformDiscountClaims/'+order.id).get()).exists,false);
    const items=await db.collection('orders/'+order.id+'/items').get();
    assert.ok(items.docs.every(d=>d.get('platformVolumeDiscount')===undefined));
  }
  const retry=await post('/checkoutCustomer',input,'customer-a');
  assert.equal(retry.status,200);assert.equal(retry.body.replay,true);
  assert.deepEqual(retry.body.orderIds,result.body.orderIds);
  assert.equal((await db.collection('orders').get()).size,2);
});

test('one unavailable seller or a stale price aborts the complete basket without partial reservations',async()=>{
  const input=await multiSellerRequest();
  await db.doc('productBatches/batch-two').update({availableQuantity:0,reservedQuantity:10});
  const result=await post('/checkoutCustomer',input,'customer-a');assert.equal(result.status,409);
  assert.equal((await db.collection('orders').get()).size,0);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'),0);
  await db.doc('productBatches/batch-two').update({availableQuantity:10,reservedQuantity:0});
  const stale=await post('/checkoutCustomer',{...input,expectedSubtotal:110000},'customer-a');
  assert.equal(stale.status,409);assert.match(stale.body.error,/QUOTE_CHANGED/);
  assert.equal((await db.collection('orders').get()).size,0);
});

test('Guest receives both orders, separate protected lookup codes and idempotent replay',async()=>{
  const input={...await multiSellerRequest(),...guest()};
  const result=await post('/checkoutGuest',input);assert.equal(result.status,200,JSON.stringify(result.body));
  assert.equal(result.body.orders.length,2);
  for(const order of result.body.orders) {
    const details=await post('/guestOrderDetails',{orderId:order.id,accessToken:input.guestToken});
    assert.equal(details.status,200);
    // Protected retrieval is checked through the existing lookup endpoint with phone verification.
    const lookup=await post('/lookupGuestOrder',{orderCode:order.orderCode,receiverPhone:input.receiverPhone});
    assert.equal(lookup.status,200,JSON.stringify(lookup.body));
    assert.equal(lookup.body.order.id,order.id);
  }
  const replay=await post('/checkoutGuest',input);assert.equal(replay.body.replay,true);
  assert.equal((await db.collection('orders').get()).size,2);
});

test('retired promotion data cannot change Customer or Guest checkout prices', async () => {
  const input = await multiSellerRequest();
  await db.doc('promotions/product-a').set({sellerId:'seller', productId:'product-a', active:true,
    type:'PERCENT', value:90, quantityDiscountTiers:{'1':90}, startDate:past(), endDate:future()});
  await db.doc('customerPromotionWallets/customer-a/items/product-a').set({customerId:'customer-a',sellerId:'seller',productId:'product-a'});
  for (const actor of ['customer-a', null]) {
    const path = actor ? '/quoteCustomerCheckout' : '/quoteGuestCheckout';
    const quote = await post(path, {...input, savedPromotionIds:['product-a'], quantityDiscount:99999}, actor);
    assert.equal(quote.status,200);
    assert.equal(quote.body.total,120000);
    assert.equal(quote.body.pricingVersion,3);
    assert.equal(quote.body.buyMorePolicy,undefined);
    assert.equal(quote.body.lines[0].finalUnitPrice,30000);
  }
  for (const endpoint of ['catalog','save','remove']) {
    for (const actor of [null,'customer-a','seller','admin'])
      assert.equal((await post('/promotions/'+endpoint,{productId:'product-a'},actor)).status,404);
  }
  const placed = await post('/checkoutCustomer',input,'customer-a');
  assert.equal(placed.status,200);
  assert.equal(placed.body.orders.reduce((sum,o)=>sum+o.totalAmount,0),120000);
});

test('changing rescue price after a quote requires reconfirmation without reserving stock', async () => {
  const data = request();
  const quote = (await post('/quoteCustomerCheckout',data,'customer-a')).body;
  await db.doc('products/product-a').update({rescuePrice:31000});
  const result = await post('/checkoutCustomer',{...data,expectedSubtotal:quote.subtotal,expectedShippingFee:quote.shippingFee},'customer-a');
  assert.equal(result.status,409); assert.match(result.body.error,/QUOTE_CHANGED/);
  assert.equal((await db.collection('orders').get()).size,0);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'),0);
});

test('stale discounted quotes require reconfirmation while existing legacy orders keep their committed price on retry',async()=>{
  const input=request({expectedQuantityDiscount:2700,expectedSubtotal:90000,expectedShippingFee:0});
  const stale=await post('/checkoutCustomer',input,'customer-a');
  assert.equal(stale.status,409);assert.match(stale.body.error,/QUOTE_CHANGED/);
  assert.equal((await db.collection('orders').get()).size,0);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'),0);
  const created=await post('/checkoutCustomer',{...input,expectedQuantityDiscount:0},'customer-a');
  assert.equal(created.status,200);
  // A pre-retirement order has authoritative price/payment snapshots.
  await db.doc('orders/'+input.requestId).update({totalAmount:87300,quantityDiscount:2700});
  await db.doc('payments/'+input.requestId).update({amount:87300});
  const replay=await post('/checkoutCustomer',input,'customer-a');
  assert.equal(replay.status,200);assert.equal(replay.body.replay,true);
  assert.equal(replay.body.order.totalAmount,87300);
  assert.equal((await db.doc('payments/'+input.requestId).get()).get('amount'),87300);
  assert.equal((await db.doc('productBatches/batch-a').get()).get('reservedQuantity'),3);
});
