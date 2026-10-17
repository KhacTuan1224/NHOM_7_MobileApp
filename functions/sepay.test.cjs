const {test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
const {createCommerce} = require('./commerce');
const {createCampaignLifecycle} = require('./campaign-lifecycle');
const {createSePayApi, paymentDetails, merchants} = require('./sepay');

const secret = 'test-only-webhook-secret-12345678901234567890';
const account = '0123456789';
const bank = {bankCode: 'ICB', bankAccountName: 'NGUYEN AN', bankAccountNumber: account};
const merchant = {enabled: true, bankCode: 'ICB', accountNumber: account, webhookSecret: secret, authMode: 'hmac'};
function environment(entry = merchant) { return {SEPAY_MERCHANTS_JSON: JSON.stringify({seller: entry})}; }
function request(payload, overrides = {}) {
  const timestamp = String(Math.floor(Date.now() / 1000));
  const rawBody = Buffer.from(JSON.stringify(payload, null, 2));
  const signature = 'sha256=' + crypto.createHmac('sha256', secret).update(timestamp + '.').update(rawBody).digest('hex');
  const headers = {'x-sepay-timestamp': timestamp, 'x-sepay-signature': signature, authorization: 'Apikey ' + secret, ...overrides};
  return {params: {sellerId: 'seller'}, body: payload, rawBody, get: name => headers[name.toLowerCase()]};
}
async function fixture(ownerType = 'CUSTOMER', env = environment()) {
  const db = new MemoryFirestore({
    'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'},
    'users/buyer': {role: 'CUSTOMER', status: 'ACTIVE'},
    'sellerPaymentProfiles/seller': bank,
    'products/product': {sellerId: 'seller', status: 'ACTIVE', name: 'Cà rốt', unit: 'kg', originalPrice: 30000, rescuePrice: 20000},
    'productBatches/batch': {id: 'batch', productId: 'product', activeCampaignId: '', status: 'AVAILABLE',
      initialQuantity: 10, availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      expiryDate: new Timestamp(Date.now() + 86400000), inventoryVersion: 1}
  });
  const lifecycle = createCampaignLifecycle({db, admin, authenticate: async () => ({uid: 'buyer', role: 'CUSTOMER'})});
  const commerce = createCommerce({db, admin, authenticate: async () => ({uid: 'buyer', role: 'CUSTOMER'}), campaignLifecycle: lifecycle, env});
  const id = crypto.randomUUID();
  await commerce.checkout({requestId: id, receiverName: 'Người nhận', receiverPhone: '0912345678',
    fulfillmentType: 'PICKUP', paymentMethod: 'BANK_TRANSFER', latitude: 0, longitude: 0, note: '',
    items: [{productId: 'product', batchId: 'batch', sellerId: 'seller', quantity: 1}]},
  {ownerType, ownerId: 'buyer', tokenHash: ownerType === 'GUEST' ? 'guest-token-hash' : ''});
  const payment = db.snapshot('payments/' + id).data();
  const payload = {id: 92704, gateway: 'VietinBank', accountNumber: account, code: payment.transferContent,
    content: payment.transferContent + ' thanh toan cà rốt', transferType: 'in', transferAmount: 20000,
    transactionDate: new Date(Date.now() + 7 * 3600000).toISOString().slice(0, 19).replace('T', ' '),
    referenceCode: 'VTB-TRANSACTION-123'};
  return {db, id, payment, payload, lifecycle, commerce, api: createSePayApi({db, admin, env})};
}

test('checkout creates a unique short SEVQR reference bound to its seller and committed payment', async () => {
  const f = await fixture();
  assert.match(f.payment.transferContent, /^SEVQR[A-F0-9]{20}$/);
  assert.equal(f.payment.transferContent.length, 25);
  assert.equal(f.payment.verificationProvider, 'SEPAY');
  assert.equal(f.db.snapshot('bankTransferReferences/' + f.payment.transferContent).get('orderId'), f.id);
  assert.equal(f.db.snapshot('bankTransferReferences/' + f.payment.transferContent).get('sellerId'), 'seller');
});

test('customer and guest get mapped SEVQR content even before seller enables automatic verification', async () => {
  for (const ownerType of ['CUSTOMER', 'GUEST']) {
    const f = await fixture(ownerType, {});
    assert.equal(f.payment.verificationProvider, 'MANUAL');
    assert.match(f.payment.transferContent, /^SEVQR[A-F0-9]{20}$/);
    assert.equal(f.db.snapshot('bankTransferReferences/' + f.payment.transferContent).get('orderId'), f.id);
    assert.equal(f.db.snapshot('bankTransferReferences/' + f.payment.transferContent).get('sellerId'), 'seller');
  }
});

test('HMAC webhook atomically confirms order and money without finalizing goods; both parties get Vietnamese notifications', async () => {
  const f = await fixture();
  assert.deepEqual(await f.api.receive(request(f.payload)), {success: true});
  const order = f.db.snapshot('orders/' + f.id).data(), payment = f.db.snapshot('payments/' + f.id).data();
  assert.equal(order.status, 'CONFIRMED'); assert.equal(order.paymentStatus, 'PAID');
  assert.equal(order.inventoryFinalized, false);
  assert.equal(payment.verificationSource, 'SEPAY_WEBHOOK'); assert.equal(payment.referenceCode, f.payload.referenceCode);
  assert.equal(f.db.snapshot('productBatches/batch').get('reservedQuantity'), 1);
  assert.equal(f.db.snapshot('productBatches/batch').get('soldQuantity'), 0);
  const notices = (await f.db.collection('notifications').get()).docs.filter(doc => doc.id.includes('SEPAY_PAID'));
  assert.deepEqual(notices.map(doc => doc.get('userId')).sort(), ['buyer', 'seller']);
  assert.ok(notices.every(doc => doc.get('body').includes('xác minh')));
});

test('simultaneous retries and replay do not confirm or notify twice', async () => {
  const f = await fixture();
  await Promise.all([f.api.receive(request(f.payload)), f.api.receive(request(f.payload))]);
  const paidAt = f.db.snapshot('payments/' + f.id).get('paidAt').toMillis();
  await f.api.receive(request(f.payload));
  assert.equal((await f.db.collection('sepayWebhookEvents').get()).size, 1);
  assert.equal((await f.db.collection('sepayBankTransactions').get()).size, 1);
  assert.equal(f.db.snapshot('payments/' + f.id).get('paidAt').toMillis(), paidAt);
  assert.equal((await f.db.collection('notificationOutbox').get()).docs.filter(doc => doc.id.startsWith('sepay_')).length, 2);
  await assert.rejects(() => f.api.receive(request({...f.payload, transferAmount: 1})), error => error.status === 409);
});

test('same bank transaction cannot be reused under another SePay event ID', async () => {
  const f = await fixture(); await f.api.receive(request(f.payload));
  await f.api.receive(request({...f.payload, id: f.payload.id + 1}));
  const events = (await f.db.collection('sepayWebhookEvents').get()).docs;
  assert.ok(events.some(doc => doc.get('reason') === 'BANK_TRANSACTION_ALREADY_USED'));
});

test('one bank transaction cannot pay two different orders even with different event IDs and codes', async () => {
  const f = await fixture();
  const secondId = crypto.randomUUID();
  await f.commerce.checkout({requestId: secondId, receiverName: 'Người nhận', receiverPhone: '0912345678',
    fulfillmentType: 'PICKUP', paymentMethod: 'BANK_TRANSFER', latitude: 0, longitude: 0, note: '',
    items: [{productId: 'product', batchId: 'batch', sellerId: 'seller', quantity: 1}]},
  {ownerType: 'CUSTOMER', ownerId: 'buyer', tokenHash: ''});
  await f.api.receive(request(f.payload));
  const code = f.db.snapshot('payments/' + secondId).get('transferContent');
  await f.api.receive(request({...f.payload, id: f.payload.id + 1, code, content: code}));
  assert.equal(f.db.snapshot('payments/' + secondId).get('status'), 'PENDING');
  assert.equal((await f.db.collection('sepayBankTransactions').get()).size, 1);
});

for (const [name, patch, reason] of [
  ['underpayment', {transferAmount: 19999}, 'AMOUNT_MISMATCH'],
  ['overpayment', {transferAmount: 20001}, 'AMOUNT_MISMATCH'],
  ['another account', {accountNumber: '99999999'}, 'ACCOUNT_MISMATCH'],
  ['another bank', {gateway: 'MBBank'}, 'ACCOUNT_MISMATCH'],
  ['no code', {code: null, content: 'ordinary transfer'}, 'MISSING_OR_AMBIGUOUS_CODE'],
  ['unknown code', {code: null, content: 'SEVQR00000000000000000000'}, 'UNKNOWN_PAYMENT_CODE'],
  ['conflicting code', {code: 'wrong-code'}, 'MISSING_OR_AMBIGUOUS_CODE'],
  ['ambiguous code', {code: null, content: 'SEVQR00000000000000000000 SEVQR11111111111111111111'}, 'MISSING_OR_AMBIGUOUS_CODE'],
  ['missing bank reference', {referenceCode: ''}, 'MISSING_BANK_REFERENCE'],
  ['old transaction', {transactionDate: '2020-01-01 00:00:00'}, 'TRANSACTION_BEFORE_ORDER'],
  ['invalid date', {transactionDate: 'invalid'}, 'INVALID_TRANSACTION_TIME']
]) test(name + ' is retained for reconciliation without paying the order', async () => {
  const f = await fixture(); await f.api.receive(request({...f.payload, ...patch}));
  assert.equal(f.db.snapshot('payments/' + f.id).get('status'), 'PENDING');
  const events = (await f.db.collection('sepayWebhookEvents').get()).docs;
  assert.equal(events[0].get('status'), 'REVIEW'); assert.equal(events[0].get('reason'), reason);
});

test('outgoing transfers and dashboard probes do not change payment status', async () => {
  const f = await fixture();
  await f.api.receive(request({...f.payload, id: 0}));
  assert.equal((await f.db.collection('sepayWebhookEvents').get()).size, 0);
  await f.api.receive(request({...f.payload, transferType: 'out'}));
  assert.equal(f.db.snapshot('payments/' + f.id).get('status'), 'PENDING');
});

test('cancelled order and modified payment binding cannot be paid by delayed webhook', async () => {
  for (const cancelled of [true, false]) {
    const f = await fixture();
    if (cancelled) await f.lifecycle.transitionOrder({uid: 'seller', role: 'SELLER'}, {orderId: f.id, targetStatus: 'CANCELLED'});
    else f.db.documents.set('payments/' + f.id, {...f.payment, bankAccountNumber: '99999999'});
    await f.api.receive(request(f.payload));
    assert.notEqual(f.db.snapshot('payments/' + f.id).get('status'), 'PAID');
  }
});

test('HMAC uses raw UTF8 bytes and rejects tampering, missing signatures and stale timestamps', async () => {
  const f = await fixture();
  const tampered = request(f.payload); tampered.rawBody = Buffer.from('tampered');
  for (const req of [tampered, request(f.payload, {'x-sepay-signature': ''}),
    request(f.payload, {'x-sepay-timestamp': '1000000000'})])
    await assert.rejects(() => f.api.receive(req), error => error.status === 401);
  assert.equal(f.db.snapshot('payments/' + f.id).get('status'), 'PENDING');
  await f.api.receive(request(f.payload));
  assert.equal(f.db.snapshot('payments/' + f.id).get('status'), 'PAID');
});

test('API-key mode is explicit and rejects wrong keys; unconfigured seller fails closed', async () => {
  const f = await fixture('GUEST', environment({...merchant, authMode: 'apikey'}));
  await assert.rejects(() => f.api.receive(request(f.payload, {authorization: 'Apikey wrong'})), error => error.status === 401);
  const unknown = request(f.payload); unknown.params.sellerId = 'other';
  await assert.rejects(() => f.api.receive(unknown), error => error.status === 503);
  await f.api.receive(request(f.payload));
  assert.equal(f.db.snapshot('orders/' + f.id).get('paymentStatus'), 'PAID');
});

test('manual confirmation cannot bypass SePay, even for the owning seller', async () => {
  const f = await fixture();
  await assert.rejects(() => f.lifecycle.confirmBankTransfer({uid: 'seller', role: 'SELLER'},
    {orderId: f.id, referenceCode: 'fake-transfer'}), /SePay/);
});

test('configuration requires strong per-merchant secrets and unique bank accounts; bank edits cannot redirect payment', async () => {
  const unconfigured = paymentDetails('seller', bank, {});
  assert.equal(unconfigured.verificationProvider, 'MANUAL');
  assert.match(unconfigured.transferContent, /^SEVQR[A-F0-9]{20}$/);
  assert.throws(() => merchants(environment({...merchant, webhookSecret: 'weak'})), error => error.status === 503);
  assert.throws(() => merchants({SEPAY_MERCHANTS_JSON: '{'}), error => error.status === 503);
  assert.throws(() => merchants({SEPAY_MERCHANTS_JSON: JSON.stringify({seller: merchant, other: merchant})}), /nhiều seller/);
  assert.throws(() => merchants({SEPAY_MERCHANTS_JSON: JSON.stringify({seller: merchant,
    other: {...merchant, bankCode: 'VietinBank', accountNumber: ' ' + account + ' '}})}), /nhiều seller/);
  assert.throws(() => paymentDetails('seller', {...bank, bankAccountNumber: '99999999'}, environment()), /không khớp/);
});
