const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
const {createNotificationWorker, enqueueNotification} = require('./notification-outbox');
test('Firestore quota stops notification scans immediately without more queries or retry writes',async()=>{
  let reads=0,writes=0;
  const db={
    collection(){
      reads++;
      return {doc(){return {get:async()=>{throw Object.assign(Error('Quota exceeded'),{code:8});}};}};
    },
    runTransaction(){writes++;throw Error('Must not write during quota failure');}
  };
  const worker=createNotificationWorker({db,admin});
  await assert.rejects(()=>worker.runOnce(),error=>error.code===8);
  assert.equal(reads,1);assert.equal(writes,0);
});
function fixture(seed = {}) {
  let clock = Date.now() + 1000, failures = 0, sends = 0;
  const db = new MemoryFirestore({'users/customer': {role: 'CUSTOMER', status: 'ACTIVE'},
    'users/customer/devices/device': {token: 'token'}, ...seed});
  const worker = createNotificationWorker({db, admin, now: () => clock, messaging: () => ({sendEachForMulticast: async ({tokens}) => {
    sends++; if (failures-- > 0) throw Error('Temporary FCM outage');
    return {successCount: tokens.length, responses: tokens.map(() => ({success: true}))};
  }})});
  return {db, worker, later: () => { clock += 7200000; }, fail: () => { failures = 1; }, sends: () => sends};
}
async function event(f, id = 'event', extra = {}) {
  await f.db.runTransaction(async tx => enqueueNotification(tx, f.db, admin, id,
    {kind: 'DIRECT', resourceType: 'ORDER', resourceId: 'order', recipientUid: 'customer', decision: 'CONFIRMED', ...extra}));
}

test('retired rescue reminders with no offer are skipped before expansion and delivery', async () => {
  const f = fixture();
  await event(f, 'old-reminder', {resourceType: 'RESCUE_OFFER', resourceId: ''});
  const outbox = (await f.db.collection('notificationOutbox').get()).docs[0];
  await f.worker.expand(outbox);
  assert.equal((await f.db.collection('notificationDeliveries').get()).size, 0);
  f.db.documents.set('notificationDeliveries/pending', {id: 'pending', userId: 'customer',
    resourceType: 'RESCUE_OFFER', referenceId: '', status: 'PENDING'});
  await f.worker.deliver(f.db.collection('notificationDeliveries').doc('pending'));
  assert.equal(f.db.snapshot('notificationDeliveries/pending').get('status'), 'SKIPPED');
  assert.equal((await f.db.collection('notifications').get()).size, 0);
  assert.equal(f.sends(), 0);
});

test('nearby rescue push rechecks opt-in at expansion and again before delivery', async () => {
  const future = Date.now() + 86400000;
  const seed = {'rescueNearbyPreferences/customer': {enabled: true, latitude: 10.8, longitude: 106.7, radiusKm: 5, expiresAt: new Timestamp(future)},
    'rescueOffers/flash': {status: 'OPEN', sellerId: 'seller', latitude: 10.8, longitude: 106.7, availableUnits: 1, closesAt: future},
    'users/seller': {status: 'ACTIVE', sellerStatus: 'APPROVED'}};
  const f = fixture(seed);
  await event(f, 'nearby', {kind: 'RESCUE_NEARBY', resourceType: 'RESCUE_OFFER', resourceId: 'flash'});
  await f.worker.expand(await f.db.collection('notificationOutbox').doc('nearby').get());
  const delivery = (await f.db.collection('notificationDeliveries').get()).docs[0];
  assert.equal(delivery.get('type'), 'SYSTEM');
  await f.db.collection('rescueNearbyPreferences').doc('customer').delete();
  await f.worker.deliver(delivery.ref);
  assert.equal(f.sends(), 0); assert.equal((await delivery.ref.get()).get('status'), 'SKIPPED');
  await event(f, 'nearby2', {kind: 'RESCUE_NEARBY', resourceType: 'RESCUE_OFFER', resourceId: 'flash'});
  await f.worker.expand(await f.db.collection('notificationOutbox').doc('nearby2').get());
  assert.equal((await f.db.collection('notificationOutbox').doc('nearby2').get()).get('recipientCount'), 0);
});
test('FCM failure after inbox creation retries durably without duplicating the inbox', async () => {
  const f = fixture(); await event(f); f.fail(); await f.worker.runOnce();
  let rows = await f.db.collection('notificationDeliveries').get();
  assert.equal(rows.docs[0].get('inboxCreated'), true); assert.equal(rows.docs[0].get('pushDelivered'), false);
  assert.equal(rows.docs[0].get('retryCount'), 1); assert.equal((await f.db.collection('notifications').get()).size, 1);
  f.later(); await f.worker.runOnce(); await f.worker.runOnce();
  rows = await f.db.collection('notificationDeliveries').get();
  assert.equal(rows.docs[0].get('pushDelivered'), true); assert.equal(f.sends(), 2);
  assert.equal((await f.db.collection('notifications').get()).size, 1);
});
test('existing transactional order inbox is enriched, preserves read state and receives push', async () => {
  const f = fixture({'notifications/order_CONFIRMED': {userId: 'customer', isRead: true}});
  await event(f, 'event', {inboxId: 'order_CONFIRMED'}); await f.worker.runOnce();
  assert.equal((await f.db.collection('notifications').get()).size, 1);
  assert.equal(f.db.snapshot('notifications/order_CONFIRMED').get('isRead'), true);
  assert.equal(f.db.snapshot('notifications/order_CONFIRMED').get('resourceType'), 'ORDER'); assert.equal(f.sends(), 1);
});
test('offline recipient without device retains inbox and sends when a device later registers', async () => {
  const f = fixture(); f.db.documents.delete('users/customer/devices/device'); await event(f); await f.worker.runOnce();
  assert.equal((await f.db.collection('notifications').get()).size, 1); assert.equal(f.sends(), 0);
  f.db.documents.set('users/customer/devices/device', {token: 'new-token'}); f.later(); await f.worker.runOnce();
  assert.equal(f.sends(), 1);
});
test('parallel workers claim a delivery once through a lease', async () => {
  const f = fixture(); await event(f); const doc = (await f.db.collection('notificationOutbox').get()).docs[0];
  await f.worker.expand(doc); const delivery = (await f.db.collection('notificationDeliveries').get()).docs[0];
  await Promise.all([f.worker.deliver(delivery.ref), f.worker.deliver(delivery.ref)]); assert.equal(f.sends(), 1);
});

test('decision stream is resumable, keeps Seller reasons and hides internal report notes', async () => {
  const at = new Timestamp(Date.now() - 1000);
  const f = fixture({'users/seller': {role: 'SELLER', status: 'ACTIVE'}, 'sellerApplications/seller': {sellerId: 'seller'},
    'reports/report': {reporterId: 'customer', resolutionNote: 'INTERNAL SECRET'},
    'adminAuditLogs/a': {resourceType: 'SELLER_APPLICATIONS', resourceId: 'seller', fromState: 'PENDING', toState: 'REJECTED', reason: 'Thiếu giấy tờ', createdAt: at},
    'adminAuditLogs/b': {resourceType: 'REPORTS', resourceId: 'report', fromState: 'PENDING', toState: 'RESOLVED', reason: 'INTERNAL SECRET', createdAt: at}});
  await f.worker.auditDecisions(); await f.worker.auditDecisions();
  const docs = (await f.db.collection('notificationOutbox').get()).docs;
  assert.equal(docs.length, 2); assert.match(f.db.snapshot('notificationOutbox/audit_a').get('publicMessage'), /Thiếu giấy tờ/);
  assert.equal(f.db.snapshot('notificationOutbox/audit_b').get('publicMessage').includes('SECRET'), false);
});

test('audit cursor preserves nanosecond ordering even when document IDs reverse within a millisecond', async () => {
  const base = Math.floor(Date.now() / 1000) * 1000;
  const f = fixture({'reports/r': {reporterId: 'customer'}, 'adminAuditLogs/z': {resourceType: 'REPORTS', resourceId: 'r',
    fromState: 'PENDING', toState: 'RESOLVED', reason: 'Internal', createdAt: new Timestamp(base, 100)}});
  await f.worker.auditDecisions();
  f.db.documents.set('adminAuditLogs/a', {resourceType: 'REPORTS', resourceId: 'r', fromState: 'PENDING', toState: 'REJECTED',
    reason: 'Internal', createdAt: new Timestamp(base, 200)});
  await f.worker.auditDecisions(); assert.equal((await f.db.collection('notificationOutbox').get()).size, 2);
});
test('partial multicast retry sends only tokens not already accepted by FCM', async () => {
  const f = fixture({'users/customer/devices/other': {token: 'other'}}); const batches = [];
  const worker = createNotificationWorker({db: f.db, admin, now: () => Date.now() + 7200000,
    messaging: () => ({sendEachForMulticast: async ({tokens}) => {
      batches.push(tokens); return {responses: tokens.map((token, i) => batches.length === 1 && i === 1
        ? {success: false, error: {code: 'messaging/server-unavailable'}} : {success: true})};
    }})});
  await event(f); await worker.expand((await f.db.collection('notificationOutbox').get()).docs[0]);
  const delivery = (await f.db.collection('notificationDeliveries').get()).docs[0];
  await worker.deliver(delivery.ref);
  f.db.documents.set(delivery.ref.path, {...f.db.snapshot(delivery.ref.path).data(), nextAttemptAt: new Timestamp(0)});
  await worker.deliver(delivery.ref); assert.equal(batches[0].length, 2); assert.equal(batches[1].length, 1);
  assert.equal(f.db.snapshot(delivery.ref.path).get('pushDelivered'), true);
});

test('report enforcement never forwards the internal note to the target owner', async () => {
  const f = fixture({'products/p': {sellerId: 'seller'}, 'adminAuditLogs/enforcement': {resourceType: 'PRODUCTS', resourceId: 'p',
    fromState: 'ACTIVE', toState: 'HIDDEN', reportId: 'r', reason: 'INTERNAL SECRET', createdAt: new Timestamp(Date.now() - 1)}});
  await f.worker.auditDecisions();
  assert.equal(f.db.snapshot('notificationOutbox/audit_enforcement').get('publicMessage').includes('SECRET'), false);
});

test('legacy promotions no longer create notification deliveries or backfill events',async()=>{
  const f=fixture({'promotions/p':{active:true,updatedAt:new Timestamp(Date.now()),startDate:new Timestamp(0),endDate:new Timestamp(Date.now()+86400000)}});
  await event(f,'retired',{kind:'PROMOTION',resourceType:'PROMOTION',resourceId:'p'});
  await f.worker.runOnce();
  assert.equal(f.sends(),0);
  assert.equal((await f.db.collection('notificationDeliveries').get()).size,0);
  assert.equal((await f.db.collection('notificationOutbox').get()).size,1);
});
