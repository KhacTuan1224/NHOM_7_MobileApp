const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore, admin} = require('./test-support/memory-firestore.cjs');
const {createNotificationsApi} = require('./notifications-api');
function fixture(type, role, source = {}) {
  const collections = {ORDER: 'orders', CAMPAIGN: 'campaigns', SELLER_APPLICATION: 'sellerApplications', REPORT: 'reports', PRODUCT: 'products'};
  const db = new MemoryFirestore({'notifications/n': {userId: 'recipient', resourceType: type, referenceId: 'record', action: 'REVIEW'},
    [collections[type] + '/record']: source});
  const api = createNotificationsApi({db, admin, authenticate: async () => ({uid: 'recipient', role})});
  return {db, resolve: () => api.resolve({uid: 'recipient', role}, 'n'), api};
}
for (const [type, section] of [['CAMPAIGN', 'CAMPAIGNS'], ['SELLER_APPLICATION', 'SELLER_APPLICATIONS'], ['REPORT', 'REPORTS'], ['ORDER', 'ORDERS']]) {
  test('Admin notification opens the exact ' + type + ' resource', async () => {
    const f = fixture(type, 'ADMIN'); const result = await f.resolve();
    assert.equal(result.destination, 'adminDetailFragment'); assert.equal(result.args.section, section); assert.equal(result.args.itemId, 'record');
  });
}
test('Customer report opens only their own result; cannot use Admin routes', async () => {
  const f = fixture('REPORT', 'CUSTOMER', {reporterId: 'recipient'});
  assert.equal((await f.resolve()).destination, 'reportResultFragment');
  f.db.documents.set('reports/record', {reporterId: 'other'}); await assert.rejects(f.resolve, e => e.status === 403);
});
test('changed account and actor cannot reuse another recipient or private campaign link', async () => {
  const f = fixture('CAMPAIGN', 'CUSTOMER', {sellerId: 'seller', status: 'PENDING_APPROVAL'});
  await assert.rejects(f.resolve, e => e.status === 403);
  await assert.rejects(() => f.api.resolve({uid: 'other', role: 'ADMIN'}, 'n'), e => e.status === 403);
});
test('Seller receives own order route rather than an Admin route', async () => {
  const f = fixture('ORDER', 'SELLER', {sellerId: 'recipient', ownerId: 'buyer'});
  assert.equal((await f.resolve()).destination, 'orderDetailFragment');
});

function call(router, path, body) {
  const handle = router.stack.find(layer => layer.route && layer.route.path === path).route.stack[0].handle;
  return new Promise((resolve, reject) => handle({body}, {json: resolve}, reject));
}
test('public report API redacts internal notes and denies another account report/cursor', async () => {
  const f = fixture('REPORT', 'CUSTOMER', {reporterId: 'recipient', status: 'REJECTED', reason: 'Spam', description: 'Report', resolutionNote: 'INTERNAL SECRET'});
  const body = await call(f.api.router, '/reports/mine', {reportId: 'record'});
  assert.equal(body.reports.length, 1); assert.equal(JSON.stringify(body).includes('SECRET'), false);
  assert.match(body.reports[0].publicResult, /chưa đủ cơ sở/);
  f.db.documents.set('reports/other', {reporterId: 'other', resolutionNote: 'SECRET'});
  await assert.rejects(() => call(f.api.router, '/reports/mine', {reportId: 'other'}), e => e.status === 403);
  await assert.rejects(() => call(f.api.router, '/reports/mine', {cursor: 'other'}), e => e.status === 403);
});
test('opening resolution alone never marks a notification read', async () => {
  const f = fixture('REPORT', 'CUSTOMER', {reporterId: 'recipient'});
  await f.resolve(); assert.notEqual(f.db.snapshot('notifications/n').get('isRead'), true);
  await call(f.api.router, '/notifications/read', {notificationId: 'n'});
  assert.equal(f.db.snapshot('notifications/n').get('isRead'), true);
});
