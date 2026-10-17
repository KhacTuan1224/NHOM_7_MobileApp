const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore} = require('./test-support/memory-firestore.cjs');
const {createSellerNamesApi} = require('./seller-names');

function request(seed, sellerIds) {
  const db = new MemoryFirestore(seed);
  const {router} = createSellerNamesApi({db});
  const handle = router.stack.find(layer => layer.route.path === '/sellers/names').route.stack[0].handle;
  return new Promise((resolve, reject) => handle({body: {sellerIds}}, {json: resolve}, reject));
}

test('cart names use fullName, not shopName or seller ID, and expose no private fields', async () => {
  const result = await request({'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED',
    fullName: '  Nguyễn Văn An  ', shopName: 'Vườn nhà', email: 'private@example.com', bankAccountNumber: 'secret'}},
  ['seller', 'seller']);
  assert.deepEqual({...result.sellerNames}, {seller: 'Nguyễn Văn An'});
  assert.deepEqual(Object.keys(result), ['ok', 'sellerNames']);
  assert.equal(JSON.stringify(result).includes('secret'), false);
  assert.equal(JSON.stringify(result).includes('private@example.com'), false);
});

test('unknown, inactive, unapproved, non-seller, and blank-name accounts have no public name', async () => {
  const active = {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED', fullName: 'Name'};
  const result = await request({
    'users/inactive': {...active, status: 'DISABLED'},
    'users/pending': {...active, sellerStatus: 'PENDING_APPROVAL'},
    'users/customer': {...active, role: 'CUSTOMER'},
    'users/blank': {...active, fullName: ' '}
  }, ['missing', 'inactive', 'pending', 'customer', 'blank']);
  assert.deepEqual({...result.sellerNames}, {});
});

test('rejects malformed and oversized batches', async () => {
  for (const ids of [null, {}, [''], ['users/seller'], [7], Array(51).fill('seller')])
    await assert.rejects(() => request({}, ids), error => error.status === 400);
  assert.deepEqual({...((await request({}, [])).sellerNames)}, {});
});
