// Retire ONLY product promotions, saved promotion wallets, volume-discount claims
// and notifications for those promotions. Orders, payments and inventory are never written.
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const ROOTS = new Set(['promotions', 'customerPromotionWallets', 'platformDiscountClaims']);
const NOTIFICATIONS = new Set(['notifications', 'notificationOutbox', 'notificationDeliveries']);
function permitted(documentPath, data = {}) {
  const parts = documentPath.split('/');
  if (parts.length % 2 || parts.some(x => !x)) return false;
  if (ROOTS.has(parts[0])) return true;
  return parts.length === 2 && NOTIFICATIONS.has(parts[0])
    && [data.type, data.kind, data.resourceType].includes('PROMOTION');
}
function fingerprint(records) {
  return crypto.createHash('sha256').update(JSON.stringify(records.map(r =>
    [r.path, r.updateTime]).sort((a,b) => a[0].localeCompare(b[0])))).digest('hex');
}
async function run() {
  const admin = require('../functions/node_modules/firebase-admin');
  const root = path.resolve(__dirname, '..');
  if (process.env.FIRESTORE_EMULATOR_HOST) throw Error('Live retirement refuses an emulator override');
  const key = JSON.parse(fs.readFileSync(path.join(root, 'serviceAccountKey.json'), 'utf8'));
  if (key.project_id !== 'nhom7-rescuefarm') throw Error('Unexpected Firebase project');
  admin.initializeApp({credential: admin.credential.cert(key)});
  const db = admin.firestore();
  function encode(value) {
    if (value === null || typeof value !== 'object') return value;
    if (value instanceof admin.firestore.Timestamp) return {$timestamp:[value.seconds,value.nanoseconds]};
    if (value instanceof admin.firestore.GeoPoint) return {$geopoint:[value.latitude,value.longitude]};
    if (value instanceof admin.firestore.DocumentReference) return {$reference:value.path};
    if (Buffer.isBuffer(value)) return {$bytes:value.toString('base64')};
    if (Array.isArray(value)) return value.map(encode);
    return Object.fromEntries(Object.entries(value).map(([k,v]) => [k,encode(v)]));
  }
  async function scan() {
    const records = new Map();
    async function walk(ref) {
      // listDocuments also returns missing parents with surviving subcollections.
      for (const document of await ref.listDocuments()) {
        const snapshot = await document.get();
        if (snapshot.exists) records.set(document.path, {
          path:document.path,updateTime:encode(snapshot.updateTime),data:encode(snapshot.data())
        });
        for (const child of await document.listCollections()) await walk(child);
      }
    }
    for (const name of ROOTS) await walk(db.collection(name));
    for (const name of NOTIFICATIONS) for (const field of ['type','kind','resourceType']) {
      const page = await db.collection(name).where(field,'==','PROMOTION').get();
      for (const doc of page.docs) records.set(doc.ref.path, {
        path:doc.ref.path,updateTime:encode(doc.updateTime),data:encode(doc.data())
      });
    }
    const list = [...records.values()].sort((a,b) => a.path.localeCompare(b.path));
    for (const record of list) if (!permitted(record.path, record.data)) throw Error('Scope violation: '+record.path);
    return list;
  }
  try {
    const applyIndex = process.argv.indexOf('--apply');
    if (applyIndex < 0) {
      const records = await scan();
      const directory = path.join(root,'build','discount-retirement',new Date().toISOString().replace(/[:.]/g,'-'));
      fs.mkdirSync(directory,{recursive:true});
      const backup = {project:key.project_id,createdAt:new Date().toISOString(),fingerprint:fingerprint(records),records};
      fs.writeFileSync(path.join(directory,'backup.json'),JSON.stringify(backup,null,2));
      const summary = {project:key.project_id,total:records.length,counts:records.reduce((acc,r) => {
        const name=r.path.split('/')[0];acc[name]=(acc[name]||0)+1;return acc;
      },{}),directory};
      fs.writeFileSync(path.join(directory,'summary.json'),JSON.stringify(summary,null,2));
      console.log(JSON.stringify(summary,null,2));
      return;
    }
    const directory = path.resolve(process.argv[applyIndex+1] || '');
    if (!directory.startsWith(path.join(root,'build','discount-retirement')+path.sep)) throw Error('Backup must be in build/discount-retirement');
    const backup = JSON.parse(fs.readFileSync(path.join(directory,'backup.json'),'utf8'));
    if (backup.project !== key.project_id || backup.fingerprint !== fingerprint(backup.records)) throw Error('Invalid backup');
    if (backup.fingerprint !== fingerprint(await scan())) throw Error('Data changed since backup; take another snapshot first');
    for (const row of backup.records) if (!permitted(row.path,row.data)) throw Error('Scope violation');
    // updateTime preconditions prevent deleting a document modified after the backup.
    // Child documents are deleted first; each successful batch can be restored from backup.
    const sorted = [...backup.records].sort((a,b) => b.path.split('/').length-a.path.split('/').length);
    let deleted=0;
    for (let offset=0;offset<sorted.length;offset+=400) {
      const batch=db.batch();
      for (const row of sorted.slice(offset,offset+400)) batch.delete(db.doc(row.path), {
        lastUpdateTime:new admin.firestore.Timestamp(...row.updateTime.$timestamp)
      });
      await batch.commit(); deleted+=Math.min(400,sorted.length-offset);
      fs.writeFileSync(path.join(directory,'progress.json'),JSON.stringify({deleted,total:sorted.length}));
    }
    const remaining = await scan();
    if (remaining.length) throw Error('Retired data was recreated by an old backend/client. Deploy the new backend and Rules before retrying.');
    const result={project:key.project_id,deleted,remaining:0,completedAt:new Date().toISOString()};
    fs.writeFileSync(path.join(directory,'applied.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result,null,2));
  } finally { await db.terminate(); await admin.app().delete(); }
}
module.exports={permitted,fingerprint};
if(require.main===module) run().catch(error=>{console.error(error.message);process.exitCode=1;});
