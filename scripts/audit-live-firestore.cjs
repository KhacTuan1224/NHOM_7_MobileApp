// Local, read-only Firestore snapshot. Never prints credentials or document values.
const fs = require('node:fs');
const path = require('node:path');
const admin = require('../functions/node_modules/firebase-admin');
const root = path.resolve(__dirname, '..');
const key = JSON.parse(fs.readFileSync(path.join(root, 'serviceAccountKey.json'), 'utf8'));
if (key.project_id !== 'nhom7-rescuefarm') throw new Error('Unexpected project');
if (process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Emulator configured; refusing live audit');
admin.initializeApp({credential: admin.credential.cert(key)});
const db = admin.firestore();
function encode(v) {
  if (v === null || typeof v !== 'object') return v;
  if (v instanceof admin.firestore.Timestamp) return {$type:'timestamp',seconds:v.seconds,nanoseconds:v.nanoseconds};
  if (v instanceof admin.firestore.GeoPoint) return {$type:'geopoint',latitude:v.latitude,longitude:v.longitude};
  if (v instanceof admin.firestore.DocumentReference) return {$type:'reference',path:v.path};
  if (Buffer.isBuffer(v)) return {$type:'bytes',base64:v.toString('base64')};
  if (Array.isArray(v)) return v.map(encode);
  return Object.fromEntries(Object.entries(v).map(([k,value])=>[k,encode(value)]));
}
async function main() {
  const directory = path.join(root,'build','database-cleanup',new Date().toISOString().replace(/[:.]/g,'-'));
  fs.mkdirSync(directory,{recursive:true});
  const records=[];
  async function scan(collection) {
    const refs=await collection.listDocuments();
    for(let i=0;i<refs.length;i+=50) {
      const snapshots=await db.getAll(...refs.slice(i,i+50));
      await Promise.all(snapshots.map(async snapshot => {
        if(snapshot.exists) records.push({path:snapshot.ref.path,updateTime:encode(snapshot.updateTime),data:encode(snapshot.data())});
        for(const child of await snapshot.ref.listCollections()) await scan(child);
      }));
    }
  }
  for(const collection of await db.listCollections()) {
    const start=records.length;
    await scan(collection);
    console.log(collection.id+': '+(records.length-start)+' documents including descendants');
  }
  const snapshot={project:key.project_id,createdAt:new Date().toISOString(),records};
  fs.writeFileSync(path.join(directory,'snapshot.json'),JSON.stringify(snapshot,null,2));
  const schemas={};
  for(const record of records){const collection=record.path.split('/').at(-2);schemas[collection]??=new Set();Object.keys(record.data).forEach(k=>schemas[collection].add(k));}
  console.log(JSON.stringify({snapshotDirectory:directory,total:records.length,schemas:Object.fromEntries(Object.entries(schemas).map(([k,v])=>[k,[...v].sort()]))},null,2));
}
main().catch(e=>{console.error(e.message);process.exitCode=1;}).finally(()=>admin.app().delete());
