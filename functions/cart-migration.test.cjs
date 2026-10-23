const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore, admin} = require('./test-support/memory-firestore.cjs');
const {createCartMigration} = require('./cart-migration');
const caller = {uid: 'customer', role: 'CUSTOMER'};
function setup() {
  const db = new MemoryFirestore({'users/customer': {status: 'ACTIVE', role: 'CUSTOMER'},
    'customerCarts/customer': {items: [{id: 'p::b', productId: 'p', batchId: 'b', sellerId: 's', quantity: 2, unitPrice: 10, selected: false}]}});
  return {db, service: createCartMigration({db, admin, authenticate: async () => caller})};
}
const request = () => ({targetUserId: 'customer', requestId: 'durable-job-1', items: [{id: 'p::b', productId: 'p', batchId: 'b', sellerId: 's', quantity: 3, unitPrice: 10, selected: true}]});
test('cart migration is atomic and concurrent/restarted retries add the snapshot exactly once', async () => {
  const {db, service} = setup(), body = request();
  const results = await Promise.all([service.migrate(caller, body), service.migrate(caller, body)]);
  assert.equal(results.filter(r => r.replay).length, 1);
  // Simulate lost response/process death before deleting the local durable snapshot.
  assert.equal((await service.migrate(caller, JSON.parse(JSON.stringify(body)))).replay, true);
  assert.equal(db.snapshot('customerCarts/customer').get('items')[0].quantity, 5);
  assert.equal((await db.collection('cartMigrationReceipts').get()).size, 1);
  // A fresh local outbox job may legitimately contain identical items later.
  await service.migrate(caller, {...body, requestId: 'fresh-job'});
  assert.equal(db.snapshot('customerCarts/customer').get('items')[0].quantity, 8);
});
test('cart migration rejects account switch, mutated replay, invalid quantity and ID collision without changing cart', async () => {
  const {db, service} = setup(), body = request(); await service.migrate(caller, body);
  await assert.rejects(service.migrate({...caller, uid: 'other'}, body));
  await assert.rejects(service.migrate(caller, {...body, items: [{...body.items[0], quantity: 8}]}), e => e.status === 409);
  await assert.rejects(service.migrate(caller, {...body, requestId: 'bad', items: [{...body.items[0], quantity: Infinity}]}));
  await assert.rejects(service.migrate(caller, {...body, requestId: 'collision', items: [{...body.items[0], productId: 'different'}]}));
  assert.equal(db.snapshot('customerCarts/customer').get('items')[0].quantity, 5);
});
