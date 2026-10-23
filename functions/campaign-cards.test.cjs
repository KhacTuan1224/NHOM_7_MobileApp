const {test}=require('node:test');
const assert=require('node:assert/strict');
const {MemoryFirestore}=require('./test-support/memory-firestore.cjs');
const {createCampaignCardsApi}=require('./campaign-cards');
const at=n=>({toMillis:()=>n});
async function request(seed,ids=['c']){
  const api=createCampaignCardsApi({db:new MemoryFirestore(seed),now:()=>1000});
  const handler=api.router.stack[0].route.stack[0].handle;
  return new Promise((resolve,reject)=>handler({body:{campaignIds:ids}},{json:resolve},reject));
}
function fixture(){return {
  'campaigns/c':{sellerId:'s',status:'ACTIVE',startDate:at(1),endDate:at(2000),createdAt:at(50),moderatedAt:at(100),locationName:'Đà Lạt',batchTargets:{b:10}},
  'users/s':{role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED',fullName:'PRIVATE NAME',avatarUrl:'https://private/avatar',email:'PRIVATE EMAIL'},
  'sellerPublicProfiles/s':{shopName:'Góc Nông Sản',shopAvatarUrl:'https://public/avatar',bankAccountNumber:'PRIVATE BANK'},
  'sellerApplications/s':{sellerId:'s',status:'APPROVED',proofImageUrl:'https://private/proof'},
  'productBatches/b':{productId:'p'},'products/p':{sellerId:'s',status:'ACTIVE',name:'Cà rốt',unit:'kg',rescuePrice:18000}
};}
test('campaign cards expose only public shop identity, real date and linked product preview',async()=>{
  const result=await request(fixture());assert.equal(result.cards.c.shopName,'Góc Nông Sản');
  assert.equal(result.cards.c.avatarUrl,'https://public/avatar');assert.equal(result.cards.c.verified,true);
  assert.equal(result.cards.c.postedAt,100);assert.equal(result.cards.c.products[0].name,'Cà rốt');
  assert.equal(JSON.stringify(result).includes('PRIVATE'),false);assert.equal(JSON.stringify(result).includes('/proof'),false);
});
test('missing public avatar never falls back to personal profile; missing approval or evidence stays gray',async()=>{
  const seed=fixture();delete seed['sellerPublicProfiles/s'];
  assert.equal((await request(seed)).cards.c.avatarUrl,'');assert.equal((await request(seed)).cards.c.shopName,'');
  for(const status of ['PENDING','REJECTED']){const s=fixture();s['sellerApplications/s'].status=status;assert.equal((await request(s)).cards.c.verified,false);}
  const s=fixture();s['sellerApplications/s'].proofImageUrl='';assert.equal((await request(s)).cards.c.verified,false);
  s['users/s'].status='BLOCKED';assert.equal((await request(s)).cards.c.avatarUrl,'');
});
test('private, expired and future campaigns do not expose author or product data',async()=>{
  for(const patch of [{status:'DRAFT'},{endDate:at(500)},{startDate:at(1500)}]){
    const s=fixture();Object.assign(s['campaigns/c'],patch);assert.deepEqual(Object.keys((await request(s)).cards),[]);
  }
});
test('campaign cards reject invalid IDs and oversized batches',async()=>{
  for(const ids of [null,['a/b'],[''],Array(13).fill('c')])await assert.rejects(()=>request(fixture(),ids),e=>e.status===400);
});
test('ten concurrent card requests read each shared document once and refresh after TTL',async()=>{
  const seed=fixture();seed['campaigns/c2']={...seed['campaigns/c']};
  const db=new MemoryFirestore(seed);let reads=0,clock=1000;const snapshot=db.snapshot.bind(db);
  db.snapshot=path=>{reads++;return snapshot(path);};
  const api=createCampaignCardsApi({db,now:()=>clock});const handler=api.router.stack[0].route.stack[0].handle;
  const send=()=>new Promise((resolve,reject)=>handler({body:{campaignIds:['c','c2']}},{json:resolve},reject));
  await Promise.all(Array.from({length:10},send));assert.equal(reads,7);
  await send();assert.equal(reads,7);
  clock+=30001;await send();assert.equal(reads,9); // Both expired campaigns are rechecked; private docs are not fetched.
});
