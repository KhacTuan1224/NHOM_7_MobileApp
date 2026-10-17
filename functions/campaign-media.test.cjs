const {test}=require('node:test');const assert=require('node:assert/strict');
const {createCampaignMedia,equalSecret}=require('./campaign-media');
const {MemoryFirestore,Timestamp,admin}=require('./test-support/memory-firestore.cjs');
function fixture() {
  let clock=Date.now();const removed=[];
  const db=new MemoryFirestore({'users/seller':{role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'},
    'users/other':{role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'},
    'products/p':{sellerId:'seller',status:'ACTIVE',unit:'kg'},
    'productBatches/b':{productId:'p',status:'AVAILABLE',availableQuantity:100,expiryDate:new Timestamp(clock+10*86400000)}});
  const media=createCampaignMedia({db,admin,authenticate:async()=>({uid:'seller'}),env:{MEDIA_WORKER_SECRET:'s'.repeat(32),CLOUDINARY_SIGNING_URL:'https://worker.example',CLOUDINARY_CLOUD_NAME:'cloud'},
    now:()=>clock,destroy:async id=>{removed.push(id);}});
  const form={id:'campaign',submit:false,title:'Campaign title',description:'Story',rescueReason:'OVER_SUPPLY',rescueMode:'FIXED_POINT',batchTargets:{b:10},targetQuantity:10,
    startDate:clock-1000,endDate:clock+5*86400000,latitude:10,longitude:106,locationName:'Farm',imageUrls:[],imagePublicIds:[]};
  return {db,media,form,removed,advance:ms=>{clock+=ms;},caller:{uid:'seller'}};
}
async function upload(f,slot=0) {
  const asset=await f.media.authorize(f.caller,{resourceId:'campaign',slot});
  const url='https://res.cloudinary.com/cloud/image/upload/v1/'+asset.publicId+'.jpg';
  await f.media.uploaded({leaseId:asset.leaseId,publicId:asset.publicId,secureUrl:url});return {...asset,url};
}
test('each campaign upload uses a different immutable public ID, even in the same slot',async()=>{
  const f=fixture(),a=await upload(f),b=await upload(f);assert.notEqual(a.publicId,b.publicId);assert.notEqual(a.leaseId,b.leaseId);
  assert.equal(f.db.documents.get('campaignMediaDrafts/campaign').ownerUid,'seller');
});
for(const status of ['PENDING_APPROVAL','ACTIVE','COMPLETED','STOPPED','EXPIRED'])test('cannot authorize changes for '+status+' Campaign',async()=>{
  const f=fixture();f.db.documents.set('campaigns/campaign',{sellerId:'seller',status});
  await assert.rejects(()=>f.media.authorize(f.caller,{resourceId:'campaign',slot:0}),e=>e.status===403);
  assert.equal([...f.db.documents.keys()].filter(k=>k.startsWith('campaignMediaUploads/')).length,0);
});
test('draft ownership, approved Seller and slot bounds are checked by the server',async()=>{
  const f=fixture();await upload(f);
  await assert.rejects(()=>f.media.authorize({uid:'other'},{resourceId:'campaign',slot:0}),e=>e.status===403);
  for(const slot of [-1,6,1.5,'0'])await assert.rejects(()=>f.media.authorize(f.caller,{resourceId:'campaign',slot}),e=>e.status===400);
  f.db.documents.get('users/seller').sellerStatus='REJECTED';await assert.rejects(()=>upload(f),e=>e.status===403);
});
test('cancelled/abandoned uploads are cleaned after TTL without touching a published reference',async()=>{
  const f=fixture(),orphan=await upload(f);f.advance(86400001);
  assert.equal((await f.media.reconcile()).deleted,1);assert.deepEqual(f.removed,[orphan.publicId]);
  const kept=await upload(f);f.db.documents.set('campaigns/campaign',{sellerId:'seller',status:'ACTIVE',imagePublicIds:[kept.publicId],imageUrls:[kept.url]});
  f.advance(86400001);assert.equal((await f.media.reconcile()).deleted,0);assert.equal(f.removed.length,1);
});
test('campaign save commits media and pending moderation event in the same transaction',async()=>{
  const f=fixture(),asset=await upload(f);
  const body={...f.form,submit:true,imageUrls:[asset.url],imagePublicIds:[asset.publicId]};await f.media.save(f.caller,body);
  assert.equal(f.db.documents.get('campaigns/campaign').status,'PENDING_APPROVAL');
  assert.equal(f.db.documents.get('campaignMediaUploads/'+asset.leaseId).status,'COMMITTED');
  const events=[...f.db.documents.entries()].filter(([key])=>key.startsWith('notificationOutbox/'));
  assert.equal(events.length,1);assert.equal(events[0][1].resourceId,'campaign');
  await assert.rejects(()=>upload(f),e=>e.status===403);
});
test('new image references cannot bypass ownership, lease state or cleanup claim',async()=>{
  const f=fixture(),asset=await upload(f);
  for(const status of ['AUTHORIZED','DELETING','DELETED']) {
    f.db.documents.get('campaignMediaUploads/'+asset.leaseId).status=status;
    await assert.rejects(()=>f.media.save(f.caller,{...f.form,imageUrls:[asset.url],imagePublicIds:[asset.publicId]}),e=>e.status===409);
  }
  assert.equal(f.db.documents.has('campaigns/campaign'),false);
  await assert.rejects(()=>f.media.save(f.caller,{...f.form,imageUrls:['https://evil.example/image.jpg'],imagePublicIds:['arbitrary']}),e=>e.status===409);
});
test('saving replacement detaches old lease; cleanup cannot delete a committed current image',async()=>{
  const f=fixture(),a=await upload(f);await f.media.save(f.caller,{...f.form,imageUrls:[a.url],imagePublicIds:[a.publicId]});
  const b=await upload(f);await f.media.save(f.caller,{...f.form,imageUrls:[b.url],imagePublicIds:[b.publicId]});
  assert.equal(f.db.documents.get('campaignMediaUploads/'+a.leaseId).status,'DETACHED');
  f.advance(86400001);assert.equal((await f.media.reconcile()).deleted,1);assert.deepEqual(f.removed,[a.publicId]);
  assert.equal(f.db.documents.get('campaignMediaUploads/'+b.leaseId).status,'COMMITTED');
});
test('save validates supply and rejects partial writes when source is invalid',async()=>{
  const f=fixture(),asset=await upload(f);f.db.documents.get('productBatches/b').availableQuantity=1;
  await assert.rejects(()=>f.media.save(f.caller,{...f.form,submit:true,imageUrls:[asset.url],imagePublicIds:[asset.publicId]}),e=>e.status===409);
  assert.equal(f.db.documents.has('campaigns/campaign'),false);assert.equal(f.db.documents.get('campaignMediaUploads/'+asset.leaseId).status,'UPLOADED');
});
test('cleanup failure stays retryable and cannot allow a later save to claim the deleting asset',async()=>{
  const f=fixture(),asset=await upload(f);f.advance(86400001);
  const failed=createCampaignMedia({db:f.db,admin,authenticate:async()=>f.caller,now:()=>Date.now()+2*86400000,destroy:async()=>{throw Error('network');}});
  assert.equal((await failed.reconcile()).errors,1);
  assert.equal(f.db.documents.get('campaignMediaUploads/'+asset.leaseId).status,'DELETING');
  await assert.rejects(()=>f.media.save(f.caller,{...f.form,imageUrls:[asset.url],imagePublicIds:[asset.publicId]}),e=>e.status===409);
});
test('Worker secret is required and compared without accepting a partial value',()=>{
  assert.equal(equalSecret('s'.repeat(32),'s'.repeat(32)),true);assert.equal(equalSecret('s','s'),false);assert.equal(equalSecret('x'.repeat(32),'s'.repeat(32)),false);
});

test('approval rejects uncommitted, external and wrong-cloud image references',async()=>{
  const {assertApprovalMedia}=require('./campaign-media-policy');const f=fixture(),asset=await upload(f);
  const c={sellerId:'seller',imageUrls:[asset.url],imagePublicIds:[asset.publicId]};
  await assert.rejects(()=>f.db.runTransaction(tx=>assertApprovalMedia(tx,f.db,'campaign',c,'cloud')),e=>e.status===409);
  await f.media.save(f.caller,{...f.form,imageUrls:[asset.url],imagePublicIds:[asset.publicId]});
  await f.db.runTransaction(tx=>assertApprovalMedia(tx,f.db,'campaign',c,'cloud'));
  await assert.rejects(()=>f.db.runTransaction(tx=>assertApprovalMedia(tx,f.db,'campaign',c,'wrong-cloud')),e=>e.status===409);
});
test('advertised volume threshold has the same unit price in authoritative checkout',()=>{
  const {price}=require('./commerce');const now=Date.now();
  const product={originalPrice:100000,rescuePrice:80000};const promo={active:true,type:'VOLUME',value:0,quantityDiscountTiers:{'1.5':12.5},startDate:new Timestamp(now-1000),endDate:new Timestamp(now+1000)};
  assert.equal(price(product,promo,1.5,now).unit,70000);assert.equal(price(product,promo,1,now).unit,80000);
  assert.equal(price(product,promo,1.5,now+1001).unit,80000);
});

test('Campaign approval during an in-flight upload prevents finalizing the new image',async()=>{
  const f=fixture(),asset=await f.media.authorize(f.caller,{resourceId:'campaign',slot:0});
  const old='rescuefarm/campaigns/seller/campaign/image-0';
  f.db.documents.set('campaigns/campaign',{sellerId:'seller',status:'ACTIVE',imagePublicIds:[old],imageUrls:['https://res.cloudinary.com/cloud/image/upload/v1/'+old+'.jpg']});
  await assert.rejects(()=>f.media.uploaded({leaseId:asset.leaseId,publicId:asset.publicId,secureUrl:'https://res.cloudinary.com/cloud/image/upload/v1/'+asset.publicId+'.jpg'}),e=>e.status===403);
  assert.equal(f.db.documents.get('campaignMediaUploads/'+asset.leaseId).status,'AUTHORIZED');
  f.advance(86400001);await f.media.reconcile();assert.deepEqual(f.removed,[asset.publicId]);
  assert.deepEqual(f.db.documents.get('campaigns/campaign').imagePublicIds,[old]);
});

test('legacy Campaign references freeze a shared Product image before generic upload/delete can overwrite it',async()=>{
  const f=fixture(),publicId='rescuefarm/products/seller/product/image-0';
  f.db.documents.set('campaigns/legacy',{sellerId:'seller',status:'ACTIVE',imageUrls:['https://res.cloudinary.com/cloud/image/upload/v1/'+publicId+'.jpg']});
  await f.media.migrateReferences();
  assert.equal((await f.media.assetGuard({uid:'seller',role:'SELLER'},{publicId})).protected,true);
  assert.equal((await f.media.assetGuard({uid:'seller',role:'SELLER'},{publicId:'rescuefarm/products/seller/product/image-1'})).protected,false);
  await assert.rejects(()=>f.media.assetGuard({uid:'other',role:'SELLER'},{publicId}),e=>e.status===403);
  assert.equal(f.db.documents.has('systemMigrations/campaignAssetReferencesV1'),true);
});
