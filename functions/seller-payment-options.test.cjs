const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore} = require('./test-support/memory-firestore.cjs');
const {createSellerPaymentOptionsApi, availablePaymentMethods} = require('./seller-payment-options');

const seller = {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'};
const bank = {bankCode: 'MB', bankAccountName: 'NGUYEN AN', bankAccountNumber: '123456789',
  qrUrl: 'https://res.cloudinary.com/private-qr.png'};
function call(db, body = {sellerId: 'seller', fulfillmentType: 'DELIVERY'}) {
  const {router} = createSellerPaymentOptionsApi({db});
  const handle = router.stack.find(layer => layer.route).route.stack[0].handle;
  return new Promise((resolve, reject) => handle({body}, {json: resolve}, reject));
}

test('customer and guest can see bank transfer capability from private payment configuration only', async () => {
  const db = new MemoryFirestore({'users/seller': seller,
    'sellerPublicProfiles/seller': {shopName: 'Store'}, 'sellerPaymentProfiles/seller': bank});
  const result = await call(db);
  assert.deepEqual(result, {ok: true, availablePaymentMethods: ['COD', 'BANK_TRANSFER']});
  for (const privateValue of Object.values(bank)) assert.equal(JSON.stringify(result).includes(privateValue), false);
});

test('availability is refreshed when the saved bank changes and does not require a stored QR image', async () => {
  const db = new MemoryFirestore({'users/seller': seller});
  assert.deepEqual((await call(db)).availablePaymentMethods, ['COD']);
  db.documents.set('sellerPaymentProfiles/seller', {...bank, qrUrl: ''});
  assert.deepEqual((await call(db)).availablePaymentMethods, ['COD', 'BANK_TRANSFER']);
  db.documents.set('sellerPaymentProfiles/seller', {...bank, bankAccountNumber: ' '});
  assert.deepEqual((await call(db)).availablePaymentMethods, ['COD']);
});

test('pickup and on-site allow cash and bank transfer; delivery allows COD and bank transfer', () => {
  for (const mode of ['PICKUP', 'ON_SITE_RESCUE'])
    assert.deepEqual(availablePaymentMethods(mode, bank), ['CASH_ON_SITE', 'BANK_TRANSFER']);
  assert.deepEqual(availablePaymentMethods('DELIVERY', bank), ['COD', 'BANK_TRANSFER']);
});

test('legacy public bank fields and caller-supplied bank data cannot enable transfers', async () => {
  const db = new MemoryFirestore({'users/seller': seller, 'sellerPublicProfiles/seller': bank});
  const result = await call(db, {sellerId: 'seller', fulfillmentType: 'DELIVERY', ...bank});
  assert.deepEqual(result.availablePaymentMethods, ['COD']);
});

test('invalid requests and unavailable sellers are rejected', async () => {
  const db = new MemoryFirestore({'users/seller': {...seller, sellerStatus: 'SUSPENDED'}});
  await assert.rejects(() => call(db), error => error.status === 409);
  await assert.rejects(() => call(new MemoryFirestore({})), error => error.status === 409);
  for (const body of [null, {}, {sellerId: '../seller', fulfillmentType: 'DELIVERY'},
    {sellerId: 'seller', fulfillmentType: 'delivery'}])
    await assert.rejects(() => call(db, body), error => error.status === 400);
});
