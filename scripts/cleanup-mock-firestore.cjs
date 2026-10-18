// Live cleanup requires --apply and an explicit, local audit directory.
// All deletions, category migration and dependent cleanup commit atomically.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const admin=require('../functions/node_modules/firebase-admin');
const {buildPlan}=require('./mock-cleanup-plan.cjs');
const root=path.resolve(__dirname,'..');
const directory=path.resolve(process.argv[2]||'');
const allowed=path.join(root,'build','database-cleanup')+path.sep;
if(!directory.startsWith(allowed))throw Error('An audit directory under build/database-cleanup is required');
const snapshot=JSON.parse(fs.readFileSync(path.join(directory,'snapshot.json'),'utf8'));
if(snapshot.project!=='nhom7-rescuefarm')throw Error('Wrong project');
const seed=JSON.parse(execFileSync('python',['-B','-c',
  'import json; from datetime import datetime, timezone; from scripts.seed_firestore import build_documents; print(json.dumps(build_documents(datetime.now(timezone.utc)),default=str))'],{cwd:root,encoding:'utf8'}));
const planned=buildPlan(snapshot.records,seed);
fs.writeFileSync(path.join(directory,'plan.json'),JSON.stringify(planned,null,2));
console.log(JSON.stringify({summary:planned.summary,keptOrders:planned.keptOrders.length,deletedOrders:planned.deletedOrders.length,warnings:planned.warnings},null,2));
function encode(v){
  if(v===null||typeof v!=='object')return v;
  if(v instanceof admin.firestore.Timestamp)return {$type:'timestamp',seconds:v.seconds,nanoseconds:v.nanoseconds};
  if(v instanceof admin.firestore.GeoPoint)return {$type:'geopoint',latitude:v.latitude,longitude:v.longitude};
  if(v instanceof admin.firestore.DocumentReference)return {$type:'reference',path:v.path};
  if(Buffer.isBuffer(v))return {$type:'bytes',base64:v.toString('base64')};
  if(Array.isArray(v))return v.map(encode);
  return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,encode(x)]));
}
function decode(v,db){
  if(v===null||typeof v!=='object')return v;
  if(v.$type==='timestamp')return new admin.firestore.Timestamp(v.seconds,v.nanoseconds);
  if(v.$type==='serverTimestamp')return admin.firestore.FieldValue.serverTimestamp();
  if(v.$type==='geopoint')return new admin.firestore.GeoPoint(v.latitude,v.longitude);
  if(v.$type==='reference')return db.doc(v.path);
  if(v.$type==='bytes')return Buffer.from(v.base64,'base64');
  if(Array.isArray(v))return v.map(x=>decode(x,db));
  return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,decode(x,db)]));
}
async function apply(){
  if(fs.existsSync(path.join(directory,'applied.json')))throw Error('This audit has already been applied');
  if(process.env.FIRESTORE_EMULATOR_HOST)throw Error('Emulator configured');
  const key=JSON.parse(fs.readFileSync(path.join(root,'serviceAccountKey.json'),'utf8'));
  if(key.project_id!==snapshot.project)throw Error('Credential project mismatch');
  admin.initializeApp({credential:admin.credential.cert(key)});
  const db=admin.firestore();
  try{
    const collections=[...new Set(snapshot.records.map(r=>r.path.split('/').slice(0,-1).join('/')))];
    const signature=p=>JSON.stringify(p.actions.filter(a=>!a.path.startsWith('notification')).sort((a,b)=>a.path.localeCompare(b.path)));
    const result=await db.runTransaction(async tx=>{
      const fresh=[];
      // Query reads also cover documents created since the initial audit.
      for(let i=0;i<collections.length;i+=15){
        const pages=await Promise.all(collections.slice(i,i+15).map(c=>tx.get(db.collection(c))));
        for(const page of pages)for(const s of page.docs)fresh.push({path:s.ref.path,updateTime:encode(s.updateTime),data:encode(s.data())});
      }
      const current=buildPlan(fresh,seed);
      if(signature(current)!==signature(planned))throw Error('Data/cleanup scope changed since audit; no writes performed. Take a new audit.');
      if(current.actions.length>450)throw Error('Unexpectedly large cleanup; refusing multi-batch deletion');
      const before={project:snapshot.project,createdAt:new Date().toISOString(),records:fresh};
      const bytes=JSON.stringify(before,null,2);
      fs.writeFileSync(path.join(directory,'pre-delete-snapshot.json'),bytes);
      fs.writeFileSync(path.join(directory,'applied-plan.json'),JSON.stringify(current,null,2));
      for(const a of current.actions){
        if(a.path.startsWith('users/'))throw Error('Users are protected');
        const ref=db.doc(a.path);
        if(a.op==='delete')tx.delete(ref);
        else if(a.op==='create')tx.create(ref,decode(a.data,db));
        else if(a.op==='update')tx.update(ref,decode(a.fields,db));
        else throw Error('Unknown operation');
      }
      return {summary:current.summary,actions:current.actions.length,backupSha256:crypto.createHash('sha256').update(bytes).digest('hex')};
    },{maxAttempts:3});
    fs.writeFileSync(path.join(directory,'applied.json'),JSON.stringify({project:snapshot.project,committedAt:new Date().toISOString(),...result},null,2));
    console.log('COMMITTED '+JSON.stringify(result));
  }finally{await admin.app().delete();}
}
if(process.argv.includes('--apply'))apply().catch(e=>{console.error(e.message);process.exitCode=1;});
