const {test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const {createRealtimeApi} = require('./realtime');
const {createReadCache} = require('./read-cache');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
class LiveDb {
  constructor(documents = {}) { this.documents = new Map(Object.entries(documents)); this.listeners = new Set(); this.removed = 0; }
  collection(path) { return new Target(this, path); }
  emit(path, error, snapshot = {}) {
    for (const listener of [...this.listeners]) if (listener.path === path) {
      if (error) listener.error(error); else listener.next(snapshot);
    }
  }
}
class Target {
  constructor(db, path, filters = []) { Object.assign(this, {db, path, filters}); }
  doc(id) { return new Target(this.db, this.path + '/' + id, this.filters); }
  where(...filter) { return new Target(this.db, this.path, [...this.filters, filter]); }
  async get() { const data = this.db.documents.get(this.path); return {exists: !!data, get: key => data?.[key]}; }
  async set(data) { this.db.documents.set(this.path, data); }
  async update(data) { if (!this.db.documents.has(this.path)) throw Error('missing'); this.db.documents.set(this.path, {...this.db.documents.get(this.path), ...data}); }
  async delete() { this.db.documents.delete(this.path); }
  onSnapshot(next, error) {
    const listener = {path: this.path, filters: this.filters, next, error}; this.db.listeners.add(listener);
    queueMicrotask(() => { if (this.db.listeners.has(listener)) next({}); });
    return () => { this.db.listeners.delete(listener); this.db.removed++; };
  }
}
function fixture(caller = {uid: 'seller', role: 'SELLER'}, options = {}) {
  const db = new LiveDb({'users/seller': {status: 'ACTIVE', role: 'SELLER'}, ...(options.documents || {})});
  const api = createRealtimeApi({db, admin: {firestore: {FieldValue: {serverTimestamp: () => 'timestamp'}}},
    authenticate: async () => caller, debounceMs: 5, ...options});
  const call = (path, body = {}, signed = true) => new Promise((resolve, reject) => {
    const route = api.router.stack.find(layer => layer.route?.path === '/realtime/' + path).route;
    route.stack[0].handle({body, ip: 'test-client', get: key => key === 'authorization' && signed ? 'Bearer test' : ''},
      {set() { return this; }, json: resolve}, reject);
  });
  return {db, api, call};
}
test('fixed scopes reject arbitrary paths, unauthenticated private scopes and wrong roles', async t => {
  const f = fixture(); t.after(() => f.api.close());
  await assert.rejects(() => f.call('watch', {scope: 'admin_dashboard'}), error => error.status === 403);
  await assert.rejects(() => f.call('watch', {scope: 'reports'}, false), error => error.status === 401);
  await assert.rejects(() => f.call('watch', {scope: 'users', path: 'users/admin'}), error => error.status === 400);
  assert.deepEqual(f.api.diagnostics(), {sessions: 0, watches: 0});
  const adminPlan = await f.api.plan({scope: 'admin_dashboard'}, {uid: 'admin', role: 'ADMIN'});
  for (const collection of ['categories', 'banners', 'reviews', 'productBatches'])
    assert.ok(adminPlan.some(spec => spec.collection === collection));
});
test('Seller and reporter query identity comes from verified caller, ignoring client identity', async t => {
  const f = fixture(); t.after(() => f.api.close());
  const plan = await f.api.plan({scope: 'seller_statistics', uid: 'other', sellerId: 'other'}, {uid: 'seller', role: 'SELLER'});
  assert.ok(plan.some(spec => spec.collection === 'orders' && spec.field === 'sellerId' && spec.value === 'seller'));
  assert.equal(plan.some(spec => spec.value === 'other'), false);
  assert.deepEqual(await f.api.plan({scope: 'reports', reporterId: 'other'}, {uid: 'customer', role: 'CUSTOMER'}),
    [{collection: 'reports', field: 'reporterId', value: 'customer'}]);
});
test('sessions share source listeners and last close/expiry removes every registration', async t => {
  let now = 1000;
  const f = fixture(undefined, {now: () => now, leaseMs: 100}); t.after(() => f.api.close());
  const first = await f.call('watch', {scope: 'seller_statistics'});
  const second = await f.call('watch', {scope: 'seller_statistics'});
  assert.deepEqual(f.api.diagnostics(), {sessions: 2, watches: 4});
  await f.call('close', first, false);
  assert.equal(f.db.listeners.size, 4);
  now += 101; f.api.sweep();
  assert.deepEqual(f.api.diagnostics(), {sessions: 0, watches: 0});
  assert.equal(f.db.listeners.size, 0);
  await assert.rejects(() => f.call('keepalive', second, false), error => error.status === 410);
});
test('source changes push only a revision and invalidate cached API projections immediately', async t => {
  const f = fixture(); t.after(() => f.api.close());
  const lease = await f.call('watch', {scope: 'seller_statistics'});
  await pause(20);
  const path = 'realtimeSubscriptions/' + lease.subscriptionId;
  const previous = f.db.documents.get(path).revision;
  const cache = createReadCache(); await cache.get('statistics', async () => 'old');
  f.db.emit('orders'); f.db.emit('orders');
  assert.equal(await cache.get('statistics', async () => 'new'), 'new');
  await pause(20);
  const signal = f.db.documents.get(path);
  assert.notEqual(signal.revision, previous);
  assert.deepEqual(Object.keys(signal).sort(), ['revision', 'unavailable', 'updatedAt']);
  assert.equal(JSON.stringify(signal).includes('seller'), false);
});
test('listener errors cannot be cleared by a different successful source; reconnect releases the session', async t => {
  const f = fixture(); t.after(() => f.api.close());
  const lease = await f.call('watch', {scope: 'seller_statistics'});
  f.db.emit('orders', Error('permission')); f.db.emit('products'); await pause(20);
  assert.equal(f.db.documents.get('realtimeSubscriptions/' + lease.subscriptionId).unavailable, true);
  await assert.rejects(() => f.call('keepalive', lease, false), error => error.status === 503);
  assert.equal(f.db.listeners.size, 0);
});
test('Guest watches require an order capability and recheck expiring lookup grants', async t => {
  let now = 1000; const token = 'a'.repeat(64), digest = sha(token);
  const f = fixture(null, {now: () => now, documents: {['guestOrderGrants/' + digest]: {orderId: 'order', expiresAt: {toMillis: () => 1050}}}});
  t.after(() => f.api.close());
  await assert.rejects(() => f.call('watch', {scope: 'guest_order', orderId: 'different', accessToken: token}, false), error => error.status === 403);
  const lease = await f.call('watch', {scope: 'guest_order', orderId: 'order', accessToken: token}, false);
  assert.equal(f.db.listeners.size, 4);
  now = 1051;
  await assert.rejects(() => f.call('keepalive', lease, false), error => error.status === 403);
  assert.equal(f.db.listeners.size, 0);
});
test('Guest checkout receipt capability watches the order, payment, shipment and item changes', async t => {
  const token = 'b'.repeat(64);
  const f = fixture(null, {documents: {'checkoutReceipts/order': {ownerType: 'GUEST', tokenHash: sha(token)}}}); t.after(() => f.api.close());
  const lease = await f.call('watch', {scope: 'guest_order', orderId: 'order', accessToken: token}, false);
  assert.deepEqual([...f.db.listeners].map(listener => listener.path).sort(), ['orders/order', 'orders/order/items', 'payments/order', 'shipments/order']);
  await assert.rejects(() => f.call('close', {...lease, leaseToken: 'forged'}, false), error => error.status === 410);
  assert.equal(f.db.listeners.size, 4);
});
test('concurrent watch creation respects session capacity after asynchronous authorization', async t => {
  const f = fixture(undefined, {maxSessions: 1}); t.after(() => f.api.close());
  const results = await Promise.allSettled([f.call('watch', {scope: 'seller_statistics'}), f.call('watch', {scope: 'seller_statistics'})]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal(results.find(result => result.status === 'rejected').reason.status, 429);
});
test('blocked accounts cannot keep a private subscription alive', async t => {
  const f = fixture(); t.after(() => f.api.close());
  const lease = await f.call('watch', {scope: 'seller_statistics'});
  f.db.documents.set('users/seller', {role: 'SELLER', status: 'BLOCKED'});
  await assert.rejects(() => f.call('keepalive', lease, false), error => error.status === 403);
  assert.equal(f.db.listeners.size, 0);
});
test('Seller statistics tracks nested order items and removes listeners when orders disappear', async t => {
  const f = fixture(); t.after(() => f.api.close());
  const lease = await f.call('watch', {scope: 'seller_statistics'}); await pause(15);
  f.db.emit('orders', null, {docs: [{id: 'own-order'}]}); await pause(15);
  assert.ok([...f.db.listeners].some(listener => listener.path === 'orders/own-order/items'));
  const before = f.db.documents.get('realtimeSubscriptions/' + lease.subscriptionId).revision;
  f.db.emit('orders/own-order/items'); await pause(15);
  assert.notEqual(f.db.documents.get('realtimeSubscriptions/' + lease.subscriptionId).revision, before);
  f.db.emit('orders', null, {docs: []});
  assert.equal([...f.db.listeners].some(listener => listener.path === 'orders/own-order/items'), false);
  await f.call('close', lease, false); assert.equal(f.db.listeners.size, 0);
});
test('checkout watches current product, batch, shipping and private payment configuration as metadata only', async t => {
  const f = fixture(null, {documents: {'products/p': {sellerId: 'seller'}}}); t.after(() => f.api.close());
  await f.call('watch', {scope: 'checkout', lines: [{productId: 'p', batchId: 'b'}]}, false);
  assert.ok([...f.db.listeners].some(listener => listener.path === 'sellerPaymentProfiles/seller'));
  await assert.rejects(() => f.call('watch', {scope: 'checkout', lines: [{productId: '../private', batchId: 'b'}]}, false), error => error.status === 400);
});
