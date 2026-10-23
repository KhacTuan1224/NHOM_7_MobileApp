const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createReadCache,createQuotaGuard,isQuotaError}=require('./read-cache');
const {createQuotaMiddleware}=require('./read-cache');
test('concurrent reads coalesce and cache expires without extending on every hit',async()=>{
  let now=0,reads=0;const cache=createReadCache({ttlMs:100,now:()=>now});
  const load=async()=>{reads++;return 'value';};
  await Promise.all(Array.from({length:20},()=>cache.get('doc',load)));assert.equal(reads,1);
  now=90;await cache.get('doc',load);assert.equal(reads,1);
  now=101;await cache.get('doc',load);assert.equal(reads,2);
});
test('HTTP gate sends 503 and Retry-After without running the query, while health remains accessible',()=>{
  const guard=createQuotaGuard({now:()=>1000});guard.trip();let next=0;
  const middleware=createQuotaMiddleware(guard,()=>true);
  const res={set(k,v){this.header=[k,v];return this;},status(v){this.statusCode=v;return this;},json(v){this.body=v;return this;}};
  middleware({path:'/campaigns/cards'},res,()=>next++);
  assert.equal(next,0);assert.equal(res.statusCode,503);assert.deepEqual(res.header,['Retry-After','300']);
  assert.equal(res.body.code,'FIRESTORE_QUOTA_EXCEEDED');
  middleware({path:'/health'},res,()=>next++);assert.equal(next,1);
});
test('failed reads never poison cache and bounded entries evict',async()=>{
  const cache=createReadCache({maxEntries:1});let reads=0;
  await assert.rejects(()=>cache.get('a',async()=>{throw Error('quota');}));
  await cache.get('a',async()=>++reads);await cache.get('b',async()=>++reads);
  await cache.get('a',async()=>++reads);assert.equal(reads,3);
});
test('quota cooldown absorbs repeated errors and allows recovery after delay',()=>{
  let now=1000;const guard=createQuotaGuard({now:()=>now,cooldownMs:300000});
  assert.equal(guard.remainingSeconds(),0);assert.equal(guard.trip(),true);
  now+=2000;assert.equal(guard.trip(),false);assert.equal(guard.remainingSeconds(),298);
  now+=298000;assert.equal(guard.remainingSeconds(),0);
  assert.ok(isQuotaError({code:8}));assert.ok(isQuotaError({cause:{message:'8 RESOURCE_EXHAUSTED: Quota exceeded'}}));
  assert.equal(isQuotaError({code:7,message:'Permission denied'}),false);
});
