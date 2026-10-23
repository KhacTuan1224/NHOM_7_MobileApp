import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment, assertFails} from '@firebase/rules-unit-testing';
import {doc, getDoc, onSnapshot} from 'firebase/firestore';
const require = createRequire(new URL('../../functions/package.json', import.meta.url));
const admin = require('firebase-admin');
const {createRealtimeApi} = require('./realtime');
const {createNotificationsApi} = require('./notifications-api');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(condition) {
  const deadline = Date.now() + 10000;
  while (!condition() && Date.now() < deadline) await pause(25);
  assert.ok(condition(), 'Expected a Firestore push update within 10 seconds');
}
function call(router, path, body) {
  const route = router.stack.find(layer => layer.route?.path === path).route;
  return new Promise((resolve, reject) => route.stack[0].handle({body, ip: 'test', get: () => 'Bearer emulator'},
    {set() { return this; }, json: resolve}, reject));
}
test('Console/server edits push a metadata snapshot to two devices; authorized report API shows the new result', async () => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8088', 'Emulator only');
  const env = await initializeTestEnvironment({projectId: 'demo-rescuefarm', firestore: {host: '127.0.0.1', port: 8088,
    rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8')}});
  const app = admin.initializeApp({projectId: 'demo-rescuefarm'}, 'realtime-test');
  const db = app.firestore(), caller = {uid: 'customer-a', role: 'CUSTOMER'};
  const realtime = createRealtimeApi({db, admin, authenticate: async () => caller, debounceMs: 10});
  const reports = createNotificationsApi({db, admin, authenticate: async () => caller});
  const listeners = [];
  try {
    await env.clearFirestore();
    await db.doc('users/customer-a').set({role: 'CUSTOMER', status: 'ACTIVE'});
    await db.doc('reports/report').set({reporterId: 'customer-a', status: 'OPEN', reason: 'Spam', description: 'Demo report'});
    const lease = await call(realtime.router, '/realtime/watch', {scope: 'reports'});
    const devices = [env.unauthenticatedContext().firestore(), env.authenticatedContext('customer-a').firestore()];
    const revisions = ['', '']; let last;
    for (let i=0; i<devices.length; i++) listeners.push(onSnapshot(doc(devices[i], 'realtimeSubscriptions/' + lease.subscriptionId), value => {
      if (value.exists()) { revisions[i] = value.get('revision'); last = value.data(); }
    }));
    await until(() => revisions.every(Boolean)); await pause(150);
    const before = [...revisions];
    await db.doc('reports/report').update({status: 'REJECTED', resolutionNote: 'PRIVATE MODERATION NOTE'});
    await until(() => revisions.every((value, index) => value !== before[index]));
    assert.deepEqual(Object.keys(last).sort(), ['revision', 'unavailable', 'updatedAt']);
    assert.equal(JSON.stringify(last).includes('PRIVATE'), false);
    await assertFails(getDoc(doc(devices[1], 'reports/report')));
    const projection = await call(reports.router, '/reports/mine', {reportId: 'report'});
    assert.equal(projection.reports[0].status, 'REJECTED');
    assert.equal(JSON.stringify(projection).includes('PRIVATE'), false);
    for (const stop of listeners) stop(); listeners.length = 0;
    await call(realtime.router, '/realtime/close', lease);
    assert.deepEqual(realtime.diagnostics(), {sessions: 0, watches: 0});
  } finally {
    for (const stop of listeners) stop(); realtime.close(); await env.cleanup(); await app.delete();
  }
});
