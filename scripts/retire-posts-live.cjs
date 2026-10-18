// Explicit, bounded retirement of the audited Post module. No key/token/document values are logged.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const admin=require('../functions/node_modules/firebase-admin');
const {GoogleAuth}=require('../functions/node_modules/google-auth-library');
const {v1:{FirestoreAdminClient}}=require('../functions/node_modules/@google-cloud/firestore');
const {buildPlan}=require('./post-retirement-plan.cjs');
const root=path.resolve(__dirname,'..'),keyFile=path.join(root,'serviceAccountKey.json');
const credentials=JSON.parse(fs.readFileSync(keyFile,'utf8')),project=credentials.project_id;
if(project!=='nhom7-rescuefarm'||process.env.FIRESTORE_EMULATOR_HOST)throw Error('Unexpected live project/environment');
const hash=s=>crypto.createHash('sha256').update(s.replace(/\r\n/g,'\n').trim()).digest('hex');
const canonical=v=>Array.isArray(v)?v.map(canonical):v&&typeof v==='object'
  ?Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])])):v;
const stable=v=>JSON.stringify(canonical(v));
const json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const write=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2));
function encode(v){
 if(v===null||typeof v!=='object')return v;
 if(v instanceof admin.firestore.Timestamp)return {$type:'timestamp',seconds:v.seconds,nanoseconds:v.nanoseconds};
 if(v instanceof admin.firestore.GeoPoint)return {$type:'geopoint',latitude:v.latitude,longitude:v.longitude};
 if(v instanceof admin.firestore.DocumentReference)return {$type:'reference',path:v.path};
 if(Buffer.isBuffer(v))return {$type:'bytes',base64:v.toString('base64')};
 if(Array.isArray(v))return v.map(encode);
 return Object.fromEntries(Object.entries(v).map(([k,value])=>[k,encode(value)]));
}
async function snapshot(db){
 const records=[];
 async function scan(collection){
  const refs=await collection.listDocuments();
  for(let i=0;i<refs.length;i+=40){
   const docs=await db.getAll(...refs.slice(i,i+40));
   await Promise.all(docs.map(async d=>{
    if(d.exists)records.push({path:d.ref.path,updateTime:encode(d.updateTime),data:encode(d.data())});
    for(const child of await d.ref.listCollections())await scan(child);
   }));
  }
 }
 for(const c of await db.listCollections())await scan(c);
 return {project,createdAt:new Date().toISOString(),records:records.sort((a,b)=>a.path.localeCompare(b.path))};
}
async function main(){
 const [mode,arg]=process.argv.slice(2);if(!['prepare','apply','verify','retire-indexes'].includes(mode)||!arg)throw Error('Use prepare <snapshot> or apply/verify/retire-indexes <prepared-directory>');
 const auth=new GoogleAuth({keyFilename:keyFile,scopes:['https://www.googleapis.com/auth/cloud-platform','https://www.googleapis.com/auth/firebase']});
 const http=await auth.getClient();
 const request=async(method,name,data)=>(await http.request({method,url:'https://firebaserules.googleapis.com/v1/'+name,data,timeout:30000})).data;
 const releaseName=`projects/${project}/releases/cloud.firestore`;
 const source=fs.readFileSync(path.join(root,'firestore.rules'),'utf8');
 const indexClient=new FirestoreAdminClient({keyFilename:keyFile,projectId:project});
 try {
 if(mode==='prepare'){
  const original=json(path.resolve(arg)),plan=buildPlan(original);
  if(original.project!==project||plan.protectedLinks.length)throw Error('Project mismatch or protected Post reference found');
  const directory=path.join(root,'build','post-retirement','live-'+new Date().toISOString().replace(/[:.]/g,'-'));
  fs.mkdirSync(directory,{recursive:true});write(path.join(directory,'original-snapshot.json'),original);write(path.join(directory,'plan.json'),plan);
  const release=await request('GET',releaseName),rules=await request('GET',release.rulesetName);
  const old=rules.source.files.find(f=>f.name==='firestore.rules')||rules.source.files[0];
  const head=cp.execFileSync('git',['show','HEAD:firestore.rules'],{cwd:root,encoding:'utf8'});
  const [indexes]=await indexClient.listIndexes({parent:`projects/${project}/databases/(default)/collectionGroups/-`});
  const metadata={project,preparedAt:new Date().toISOString(),release,oldRulesHash:hash(old.content),headRulesHash:hash(head),newRulesHash:hash(source),indexes};
  write(path.join(directory,'metadata-before.json'),metadata);fs.writeFileSync(path.join(directory,'rules-before.rules'),old.content);
  if(metadata.oldRulesHash!==metadata.headRulesHash)throw Error('Live rules differ from repository HEAD; review preserved live source before publishing');
  console.log(JSON.stringify({preparedDirectory:directory,deleteCounts:plan.counts,protectedLinks:plan.protectedLinks.length,liveRulesMatchHead:true,
   retiredIndexes:indexes.filter(i=>i.name.includes('/collectionGroups/posts/')).length}));
  return;
 }
 const directory=path.resolve(arg);
 if(!directory.startsWith(path.join(root,'build','post-retirement')+path.sep))throw Error('Prepared directory outside task backup root');
 const before=json(path.join(directory,'metadata-before.json')),original=json(path.join(directory,'original-snapshot.json')),plan=json(path.join(directory,'plan.json'));
 if(mode==='retire-indexes'){
  const applied=json(path.join(directory,'applied.json'));
  if(applied.project!==project)throw Error('Unexpected applied project');
  const names=before.indexes.filter(i=>i.name.startsWith(`projects/${project}/databases/(default)/collectionGroups/posts/indexes/`)).map(i=>i.name);
  for(const name of names){
   try{await http.request({method:'DELETE',url:'https://firestore.googleapis.com/v1/'+name,timeout:30000});}
   catch(error){if(error.response?.status!==404)throw error;}
  }
  write(path.join(directory,'indexes-retired.json'),{at:new Date().toISOString(),names});
  console.log(JSON.stringify({retiredPostIndexes:names.length}));return;
 }
 if(mode==='verify'){
  const applied=json(path.join(directory,'applied.json'));
  if(applied.project!==project)throw Error('Unexpected applied project');
  admin.initializeApp({credential:admin.credential.cert(credentials)});
  const fresh=json(path.join(directory,'pre-delete-snapshot.json')),after=await snapshot(admin.firestore());
  write(path.join(directory,'after-snapshot.json'),after);
  const afterMap=new Map(after.records.map(r=>[r.path,r]));
  const expected=new Set(applied.deletedPaths);
  const maintenanceRoots=new Set(['notificationWorkerState']);
  const unchangedScope=fresh.records.filter(r=>!expected.has(r.path)&&!maintenanceRoots.has(r.path.split('/')[0]));
  const changed=unchangedScope.filter(r=>stable(afterMap.get(r.path)?.data)!==stable(r.data)).map(r=>r.path);
  const response=await http.request({url:`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/collectionGroups/posts/indexes`,timeout:30000});
  const remainingIndexes=(response.data.indexes||[]).filter(i=>i.name.includes('/collectionGroups/posts/'));
  const released=await request('GET',releaseName),verified=await request('GET',released.rulesetName);
  const file=verified.source.files.find(f=>f.name==='firestore.rules')||verified.source.files[0];
  const remaining=buildPlan(after);
  const result={project,deleted:applied.deletedPaths.length,deletedIndexes:before.indexes.filter(i=>i.name.includes('/collectionGroups/posts/')).length-remainingIndexes.length,
   remainingPostIndexes:remainingIndexes.length,remainingPostDocuments:after.records.filter(r=>/^(posts|postViewReceipts)\//.test(r.path)).length,
   remainingPostMetadata:remaining.deletePaths.length,protectedRecordsChecked:unchangedScope.length,changedProtectedPaths:changed,
   releasedRulesMatch:hash(file.content)===hash(source),completedAt:new Date().toISOString(),backupDirectory:directory};
  write(path.join(directory,'verification.json'),result);console.log(JSON.stringify(result));
  if(changed.length||result.remainingPostDocuments||result.remainingPostMetadata||result.remainingPostIndexes||!result.releasedRulesMatch)throw Error('Verification needs review');
  return;
 }
 if(before.project!==project||before.newRulesHash!==hash(source)||plan.protectedLinks.length)throw Error('Prepared source/plan changed');
 if(fs.existsSync(path.join(directory,'applied.json')))throw Error('Already applied; inspect verification instead');
 const log=fs.readFileSync(path.join(root,'build/post-retirement/firestore-tests.log'),'utf8');
 if(!/# fail 0/.test(log)||!/Script exited successfully/.test(log))throw Error('Firestore tests must pass before live apply');
 const release=await request('GET',releaseName);
 if(release.rulesetName!==before.release.rulesetName)throw Error('Live rules release changed after preparation');
 admin.initializeApp({credential:admin.credential.cert(credentials)});const db=admin.firestore();
 const fresh=await snapshot(db),currentPlan=buildPlan(fresh);
 write(path.join(directory,'pre-delete-snapshot.json'),fresh);
 const expected=plan.deletePaths.map(p=>p.path).sort(),actual=currentPlan.deletePaths.map(p=>p.path).sort();
 if(stable(expected)!==stable(actual)||currentPlan.protectedLinks.length)throw Error('Deletion scope or references changed; re-audit required');
 const originalMap=new Map(original.records.map(r=>[r.path,r]));
 for(const p of plan.posts){
  const live=fresh.records.find(r=>r.path===p.path);
  if(!live||stable(live.data)!==stable(originalMap.get(p.path).data))throw Error('Post content changed after audit; refusing deletion');
 }
 // Compile source on Google's Rules service, publish it, then verify the exact released source.
 const created=await request('POST',`projects/${project}/rulesets`,{source:{files:[{name:'firestore.rules',content:source}]}});
 write(path.join(directory,'rules-created.json'),{rulesetName:created.name});
 await request('PATCH',releaseName,{release:{name:releaseName,rulesetName:created.name},updateMask:'rulesetName'});
 const released=await request('GET',releaseName),verified=await request('GET',released.rulesetName);
 const file=verified.source.files.find(f=>f.name==='firestore.rules')||verified.source.files[0];
 if(released.rulesetName!==created.name||hash(file.content)!==hash(source))throw Error('Published rules verification failed; data untouched');
 write(path.join(directory,'rules-published.json'),{rulesetName:created.name,hash:hash(file.content),at:new Date().toISOString()});
 const freshMap=new Map(fresh.records.map(r=>[r.path,r]));
 if(expected.length>100)throw Error('Deletion unexpectedly large');
 await db.runTransaction(async tx=>{
  const refs=expected.map(p=>db.doc(p)),docs=await tx.getAll(...refs);
  for(const doc of docs){
   const previous=freshMap.get(doc.ref.path);
   if(!doc.exists||stable(encode(doc.data()))!==stable(previous.data)
      ||stable(encode(doc.updateTime))!==stable(previous.updateTime))throw Error('A deletion target changed; transaction aborted');
  }
  for(const ref of refs)tx.delete(ref);
 });
 write(path.join(directory,'applied.json'),{project,at:new Date().toISOString(),deletedPaths:expected,counts:plan.counts});
 const deletedIndexes=[];
 for(const index of before.indexes){
  if(!index.name.startsWith(`projects/${project}/databases/(default)/collectionGroups/posts/indexes/`))continue;
  await indexClient.deleteIndex({name:index.name});deletedIndexes.push(index.name);
 }
 const after=await snapshot(db);write(path.join(directory,'after-snapshot.json'),after);
 const afterMap=new Map(after.records.map(r=>[r.path,r]));
 const protectedRoots=new Set(['users','orders','payments','shipments','products','productBatches','campaigns','categories','banners','reviews','sellerApplications','sellerPublicProfiles','sellerPaymentProfiles','bankTransferReferences','sepayWebhookEvents','sepayBankTransactions','customerCarts','checkoutReceipts','guestOrderCodes','guestOrderGrants','campaignAssetReferences','campaignMediaDrafts','campaignMediaUploads']);
 const changed=[];
 for(const r of fresh.records)if(protectedRoots.has(r.path.split('/')[0])&&stable(afterMap.get(r.path)?.data)!==stable(r.data))changed.push(r.path);
 const remaining=buildPlan(after);
 const verification={project,deleted:expected.length,deletedIndexes:deletedIndexes.length,remainingPostDocuments:after.records.filter(r=>r.path.startsWith('posts/')||r.path.startsWith('postViewReceipts/')).length,
   remainingPostMetadata:remaining.deletePaths.length,protectedRecordsChecked:fresh.records.filter(r=>protectedRoots.has(r.path.split('/')[0])).length,changedProtectedPaths:changed,
   completedAt:new Date().toISOString(),backupDirectory:directory};
 write(path.join(directory,'verification.json'),verification);console.log(JSON.stringify(verification));
 if(changed.length||verification.remainingPostDocuments||verification.remainingPostMetadata)throw Error('Post-delete verification needs review; see private report');
 } finally {await indexClient.close();if(admin.apps.length)await admin.app().delete();}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
