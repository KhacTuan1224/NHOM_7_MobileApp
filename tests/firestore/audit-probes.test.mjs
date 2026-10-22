import {before, after, beforeEach, test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment, assertFails, assertSucceeds} from '@firebase/rules-unit-testing';
import {doc, setDoc, updateDoc, getDoc, writeBatch, deleteField, serverTimestamp, Timestamp} from 'firebase/firestore';

// Diagnostics only: explicitly refuses any endpoint outside the local emulator.
const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^(127\.0\.0\.1|localhost):\d+$/.test(host || '')) throw Error('Local Firestore Emulator required; production is forbidden');
let env;
const past = Timestamp.fromMillis(Date.now() - 86400000);
before(async () => {
  env = await initializeTestEnvironment({projectId: 'demo-rescuefarm', firestore: {
    host: host.split(':')[0], port: Number(host.split(':')[1]),
    rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8')
  }});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, 'users/admin'), {role: 'ADMIN', status: 'ACTIVE'}),
      setDoc(doc(db, 'campaigns/c'), {status: 'ACTIVE'}),
      setDoc(doc(db, 'campaigns/c/comments/comment'), {id: 'comment', campaignId: 'c', userId: 'customer',
        status: 'ACTIVE', content: 'Test comment', createdAt: past, updatedAt: past}),
      setDoc(doc(db, 'categories/fruit'), {name: 'Trái cây', imageUrl: 'https://example.com/fruit.png', active: true, displayOrder: 1})
    ]);
  });
});
const moderation = () => ({moderatedBy: 'admin', moderatedAt: serverTimestamp(), updatedAt: serverTimestamp(), moderationReason: 'Audit moderation'});
test('audit: hide payload without previous status remains rejected', async () => {
  const db = env.authenticatedContext('admin').firestore();
  await assertFails(updateDoc(doc(db, 'campaigns/c/comments/comment'), {...moderation(), status: 'HIDDEN'}));
});
test('audit: actual app comment hide payload including previous status and audit commits and restores', async () => {
  const db = env.authenticatedContext('admin').firestore(), batch = writeBatch(db);
  batch.update(doc(db, 'campaigns/c/comments/comment'), {...moderation(), status: 'HIDDEN', moderationPreviousStatus: 'ACTIVE'});
  batch.set(doc(db, 'adminAuditLogs/hide'), {adminId: 'admin', resourceType: 'COMMENTS', resourceId: 'comment',
    resourcePath: 'campaigns/c/comments/comment', fromState: 'ACTIVE', toState: 'HIDDEN', reason: 'Audit moderation', createdAt: serverTimestamp()});
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(db, 'campaigns/c/comments/comment'))).get('status'), 'HIDDEN');
  await assertSucceeds(updateDoc(doc(db, 'campaigns/c/comments/comment'), {...moderation(), status: 'ACTIVE', moderationPreviousStatus: deleteField()}));
});
test('audit: category deactivation accepts empty reason by policy', async () => {
  const db = env.authenticatedContext('admin').firestore();
  await assertSucceeds(updateDoc(doc(db, 'categories/fruit'), {...moderation(), active: false, moderationReason: ''}));
  assert.equal((await getDoc(doc(db, 'categories/fruit'))).get('active'), false);
});
