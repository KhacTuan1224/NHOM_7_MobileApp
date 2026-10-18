const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const assert=require('node:assert/strict');
const beforeDir=path.resolve(process.argv[2]),afterDir=path.resolve(process.argv[3]);
const read=(dir,file)=>JSON.parse(fs.readFileSync(path.join(dir,file),'utf8'));
const before=read(beforeDir,'pre-delete-snapshot.json'),after=read(afterDir,'snapshot.json');
const plan=read(beforeDir,'applied-plan.json'),receipt=read(beforeDir,'applied.json');
assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(beforeDir,'pre-delete-snapshot.json'))).digest('hex'),receipt.backupSha256);
assert.equal(before.project,after.project);
const oldMap=new Map(before.records.map(r=>[r.path,r.data])),newMap=new Map(after.records.map(r=>[r.path,r.data]));
const canonical=v=>v&&typeof v==='object'?(Array.isArray(v)?v.map(canonical):Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])]))):v;
const same=(a,b,label)=>assert.deepEqual(canonical(a),canonical(b),label);
const actions=new Map(plan.actions.map(a=>[a.path,a]));
for(const a of plan.actions){
  if(a.op==='delete'){assert(!newMap.has(a.path),'Deleted path still exists: '+a.path);continue;}
  assert(newMap.has(a.path),'Missing migrated document: '+a.path);
  const actual=newMap.get(a.path),fields=a.op==='create'?a.data:a.fields;
  for(const[k,v]of Object.entries(fields))if(v?.$type==='serverTimestamp')assert.equal(actual[k]?.$type,'timestamp');else same(actual[k],v,a.path+':'+k);
  if(a.op==='update')for(const[k,v]of Object.entries(oldMap.get(a.path)))if(!(k in fields))same(actual[k],v,'Unexpected field change: '+a.path+':'+k);
}
const protectedCollections=new Set(['users','orders','payments','shipments','productBatches','campaigns','sellerApplications','sellerPaymentProfiles','sellerPublicProfiles','appConfig','bankTransferReferences','reviews','posts','adminAuditLogs']);
let unchanged=0;
for(const[p,d]of oldMap)if(!actions.has(p)&&protectedCollections.has(p.split('/')[0])){assert(newMap.has(p),'Protected record missing: '+p);same(newMap.get(p),d,'Protected record changed: '+p);unchanged++;}
const deletedIds=new Set(plan.actions.filter(a=>a.op==='delete'&&a.path.split('/').length===2).map(a=>a.path.split('/')[1]));
const operational=new Set(['categories','products','productBatches','campaigns','banners','posts','promotions','orders','payments','shipments','checkoutReceipts','guestOrderCodes','guestOrderGrants','bankTransferReferences','favorites','reviews','customerCarts','campaignViewReceipts','postViewReceipts','notificationOutbox','notificationDeliveries','notifications','campaignMediaDrafts','campaignMediaUploads']);
function references(v){if(typeof v==='string')return deletedIds.has(v)?[v]:[];if(!v||typeof v!=='object')return[];return Object.entries(v).flatMap(([k,x])=>[...(deletedIds.has(k)?[k]:[]),...references(x)]);}
for(const[p,d]of newMap)if(operational.has(p.split('/')[0]))assert.equal(references(d).length,0,'Operational reference to deleted record: '+p);
const roots=c=>after.records.filter(r=>r.path.startsWith(c+'/')&&r.path.split('/').length===2);
const orderRows=roots('orders'),reserved={},sold={},campaignReserved={},campaignSold={};
for(const r of orderRows){const id=r.path.split('/')[1],d=r.data;
  const payment=newMap.get('payments/'+id);assert(payment,'Missing payment');assert.equal(payment.status,d.paymentStatus,'Order/payment status mismatch');
  if(d.fulfillmentType==='DELIVERY')assert(newMap.has('shipments/'+id),'Missing shipment');
  if(d.campaignId)assert(newMap.has('campaigns/'+d.campaignId),'Missing campaign');
  const items=after.records.filter(i=>i.path.startsWith(r.path+'/items/'));assert(items.length,'Missing order items');
  for(const item of items){assert(newMap.has('products/'+item.data.productId),'Missing product');assert(newMap.has('productBatches/'+item.data.batchId),'Missing batch');}
  for(const[bid,q]of Object.entries(d.batchReservations||{})){
    assert(newMap.has('productBatches/'+bid),'Missing reserved batch');
    if(!d.inventoryFinalized)reserved[bid]=(reserved[bid]||0)+q;
    else if(d.status==='DELIVERED')sold[bid]=(sold[bid]||0)+q;
  }
  if(d.campaignId){const target=d.inventoryFinalized?(d.status==='DELIVERED'?campaignSold:null):campaignReserved;
    if(target)target[d.campaignId]=(target[d.campaignId]||0)+(d.campaignReservedQuantity||0);}
}
const near=(a,b,label)=>assert(Math.abs(a-b)<0.000001,label+': '+a+' != '+b);
for(const r of roots('productBatches')){const id=r.path.split('/')[1],d=r.data;
  assert(newMap.has('products/'+d.productId));
  near(d.initialQuantity,d.availableQuantity+d.reservedQuantity+d.soldQuantity,'Batch conservation');
  near(d.reservedQuantity,reserved[id]||0,'Reserved quantity');near(d.soldQuantity,sold[id]||0,'Sold quantity');
  if(d.activeCampaignId)assert(newMap.has('campaigns/'+d.activeCampaignId));
}
for(const r of roots('campaigns')){const id=r.path.split('/')[1];near(r.data.reservedQuantity,campaignReserved[id]||0,'Campaign reservations');near(r.data.rescuedQuantity,campaignSold[id]||0,'Campaign rescued');}
for(const r of roots('products'))assert(newMap.has('categories/'+r.data.categoryId));
const result={verifiedAt:new Date().toISOString(),project:after.project,unchangedProtectedDocuments:unchanged,users:roots('users').length,userDocumentsIncludingChildren:after.records.filter(r=>r.path.startsWith('users/')).length,orders:orderRows.length,products:roots('products').length,batches:roots('productBatches').length,campaigns:roots('campaigns').length,categories:roots('categories').length,deletedDocuments:plan.actions.filter(a=>a.op==='delete').length,updatedDocuments:plan.actions.filter(a=>a.op==='update').length,createdDocuments:plan.actions.filter(a=>a.op==='create').length,integrity:'PASS'};
fs.writeFileSync(path.join(beforeDir,'verification.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
