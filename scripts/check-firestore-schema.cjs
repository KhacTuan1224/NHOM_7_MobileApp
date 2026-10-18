// Checks the schema inventory and indexes; --live runs read-only query probes.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),schema=require('../firestore.schema.json');
const directory=path.resolve(process.argv[2]||'');
if(!directory.startsWith(path.join(root,'build','schema-audit')+path.sep))throw Error('Provide a schema-audit directory');
const metadata=JSON.parse(fs.readFileSync(path.join(directory,'metadata.json'),'utf8'));
assert.equal(metadata.project,schema.projectId);
const roots=schema.collections.filter(c=>c.path.split('/').length===2).map(c=>c.path.split('/')[0]);
const groups=new Set(schema.collections.map(c=>c.path.split('/').at(-2)));
const sourceReferences={};
function walk(folder,extension){return fs.readdirSync(folder,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(folder,e.name),extension):e.name.endsWith(extension)?[path.join(folder,e.name)]:[]);}
const sources=[...fs.readdirSync(path.join(root,'functions')).filter(n=>n.endsWith('.js')).map(n=>path.join(root,'functions',n)),path.join(root,'functions/scripts/bootstrap-admin.js'),...walk(path.join(root,'app/src/main/java'),'.java')];
for(const file of sources){
  const text=fs.readFileSync(file,'utf8'),constants=Object.fromEntries([...text.matchAll(/\b([A-Z][A-Z_0-9]+)\s*=\s*"([A-Za-z][A-Za-z0-9]+)"/g)].map(m=>[m[1],m[2]]));
  for(const m of text.matchAll(/\.collection(?:Group)?\(\s*(?:['"]([A-Za-z][A-Za-z0-9]+)['"]\s*\)|([A-Z][A-Z_0-9]+)\s*\))/g)){
    const name=m[1]||constants[m[2]];if(!name)throw Error('Unresolved collection constant in '+file+': '+m[2]);
    assert(groups.has(name),'Collection absent from schema: '+name);
    (sourceReferences[name]??=new Set()).add(path.relative(root,file).replace(/\\/g,'/'));
  }
}
for(const group of groups)assert(sourceReferences[group], 'Schema entry has no production reference: '+group);
const unknownLive=metadata.collections.filter(c=>!roots.includes(c.name));
const notMaterialized=roots.filter(c=>!metadata.collections.some(l=>l.name===c));
const key=(group,scope,fields)=>group+'|'+scope+'|'+fields.filter(f=>f.fieldPath!=='__name__').map(f=>f.fieldPath+':'+(f.order||f.arrayConfig)).join(',');
const liveIndexes=new Map(metadata.indexes.map(i=>[key(i.name.split('/collectionGroups/')[1].split('/')[0],i.queryScope,i.fields),i]));
const wanted=JSON.parse(fs.readFileSync(path.join(root,'firestore.indexes.json'),'utf8'));
for(const i of wanted.indexes){const live=liveIndexes.get(key(i.collectionGroup,i.queryScope,i.fields));assert(live,'Missing index: '+i.collectionGroup+' '+JSON.stringify(i.fields));assert.equal(live.state,'READY');}
assert(metadata.commentIdField.indexConfig.indexes.some(i=>i.queryScope==='COLLECTION_GROUP'&&i.state==='READY'&&i.fields.some(f=>f.fieldPath==='id'&&f.order==='ASCENDING')),'comments.id collection-group index missing');
assert.equal(metadata.rules.localHash,metadata.rules.deployedHash,'Local/deployed rules differ');
const result={checkedAt:new Date().toISOString(),project:schema.projectId,rootCollections:roots.length,subcollectionPaths:schema.collections.length-roots.length,liveRootCollections:metadata.collections.length,notMaterialized,unknownLive,configuredCompositeIndexes:wanted.indexes.length,indexesReady:true,commentGroupIndexReady:true,rulesMatch:true,sourceReferences:Object.fromEntries(Object.entries(sourceReferences).sort().map(([n,refs])=>[n,[...refs].sort()])),documentsWritten:0};
async function main(){
  if(process.argv.includes('--live')){
    const admin=require('../functions/node_modules/firebase-admin');
    if(process.env.FIRESTORE_EMULATOR_HOST)throw Error('Emulator configured');
    const credentials=JSON.parse(fs.readFileSync(path.join(root,'serviceAccountKey.json'),'utf8'));
    assert.equal(credentials.project_id,schema.projectId);
    admin.initializeApp({credential:admin.credential.cert(credentials)});
    try{
      const db=admin.firestore(),checks=[];
      const add=(name,query)=>checks.push({name,query});
      for(const c of roots)add('collection:'+c,db.collection(c));
      for(const c of ['products','campaigns','orders'])add(c+':seller/createdAt',db.collection(c).where('sellerId','==','__schema_read_probe__').orderBy('createdAt','desc'));
      add('orders:customer',db.collection('orders').where('ownerId','==','__schema_read_probe__').where('ownerType','==','CUSTOMER'));
      add('orders:guest lookup',db.collection('orders').where('orderCode','==','__schema_read_probe__').where('receiverPhone','==','__schema_read_probe__'));
      add('orders:campaign/customer',db.collection('orders').where('campaignId','==','__schema_read_probe__').where('ownerType','==','CUSTOMER'));
      add('reviews:published',db.collection('reviews').where('productId','==','__schema_read_probe__').where('status','==','PUBLISHED'));
      add('reports:reporter/date',db.collection('reports').where('reporterId','==','__schema_read_probe__').orderBy('createdAt','desc'));
      add('campaigns:expiration',db.collection('campaigns').where('status','==','ACTIVE').where('endDate','<=',admin.firestore.Timestamp.now()));
      add('comments:moderation collection group',db.collectionGroup('comments').where('id','==','__schema_read_probe__'));
      for(const c of ['notificationOutbox','notificationDeliveries']){
        add(c+':pending/due',db.collection(c).where('status','==','PENDING').where('nextAttemptAt','<=',admin.firestore.Timestamp.now()));
        add(c+':pending/retries',db.collection(c).where('status','==','PENDING').where('retryCount','>=',5));
      }
      const completed=[];
      for(let i=0;i<checks.length;i+=10)await Promise.all(checks.slice(i,i+10).map(async check=>{await check.query.limit(1).get();completed.push(check.name);}));
      await db.collection('orders').where('status','==','DELIVERED').aggregate({total:admin.firestore.AggregateField.sum('totalAmount')}).get();
      completed.push('orders:delivered revenue aggregate');
      result.liveQueries={passed:completed.length,checks:completed.sort()};
    }finally{await admin.app().delete();}
  }
  fs.writeFileSync(path.join(directory,'schema-check.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify({...result,sourceReferences:undefined},null,2));
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
