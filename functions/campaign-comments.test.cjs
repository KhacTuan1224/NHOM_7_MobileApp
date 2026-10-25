const {test}=require('node:test'),assert=require('node:assert/strict');
const {createCampaignCommentsApi,imageAllowed,identity}=require('./campaign-comments');
const timestamp=n=>({toMillis:()=>n});
function fixture(){
  const rows=new Map([['users/customer',{role:'CUSTOMER',status:'ACTIVE',fullName:'Tên hồ sơ',avatarUrl:'https://example.test/avatar.png',bankAccountNumber:'PRIVATE'}],
    ['campaigns/campaign',{status:'ACTIVE',startDate:timestamp(1),endDate:timestamp(100000)}]]);let reads=0;
  class Ref{constructor(path){this.path=path;}collection(name){return new Ref(this.path+'/'+name);}doc(key){return new Ref(this.path+'/'+key);}
    async get(){reads++;const data=rows.get(this.path);return {exists:!!data,get:k=>data?.[k],data:()=>data};}}
  const db={collection:name=>new Ref(name),runTransaction:fn=>fn({get:ref=>ref.get(),set:(ref,value)=>rows.set(ref.path,value)})};
  const api=createCampaignCommentsApi({db,admin:{firestore:{FieldValue:{serverTimestamp:()=>timestamp(1000)}}},now:()=>1000,cloudName:'cloud',authenticate:async()=>({uid:'customer',role:'CUSTOMER'})});
  const call=(path,body)=>new Promise((resolve,reject)=>api.router.stack.find(x=>x.route?.path.endsWith('/'+path)).route.stack[0].handle({body},{json:resolve},reject));
  return {rows,call,reads:()=>reads};
}
test('comment image ownership, cloud, transport and immutable ID are enforced',()=>{
  const url='https://res.cloudinary.com/cloud/image/upload/v1/rescuefarm/campaign-comments/customer/comment/image-12345678-1234-1234-1234-123456789abc.png';
  assert.ok(imageAllowed(url,'customer','comment','cloud'));assert.ok(!imageAllowed(url,'other','comment','cloud'));
  assert.ok(!imageAllowed(url,'customer','other','cloud'));assert.ok(!imageAllowed(url.replace('https:','http:'),'customer','comment','cloud'));
  assert.ok(!imageAllowed(url+'?token=fake','customer','comment','cloud'));
});
test('anonymous identity never contains profile name or avatar',()=>{
  assert.deepEqual(identity({fullName:'PRIVATE',avatarUrl:'https://example.test/private'},true),{name:'Người dùng ẩn danh',avatarUrl:'',anonymous:true});
});
test('server snapshots real author, hides anonymous metadata and replay does not duplicate',async()=>{
  const f=fixture(),body={campaignId:'campaign',commentId:'one',content:'Bình luận thử',senderName:'FORGED',senderAvatarUrl:'https://evil.test'};
  await f.call('add',body);assert.equal(f.rows.get('campaigns/campaign/comments/one').senderName,'Tên hồ sơ');
  assert.equal((await f.call('add',body)).replay,true);
  await f.call('add',{...body,commentId:'two',anonymous:true});
  const saved=f.rows.get('campaigns/campaign/comments/two');assert.equal(saved.senderAvatarUrl,'');assert.equal(saved.senderName,'Người dùng ẩn danh');
  await assert.rejects(()=>f.call('add',{...body,content:'Thay nội dung'}),e=>e.status===409);
});
test('invalid text, too many photos, foreign photos and expired campaign are rejected',async()=>{
  const f=fixture(),body={campaignId:'campaign',commentId:'one',content:'OK'};
  await assert.rejects(()=>f.call('add',{...body,content:''}),e=>e.status===400);
  await assert.rejects(()=>f.call('add',{...body,imageUrls:['a','b','c','d']}),e=>e.status===400);
  await assert.rejects(()=>f.call('add',{...body,imageUrls:['https://example.test/image.png']}),e=>e.status===400);
  f.rows.set('campaigns/campaign',{status:'ACTIVE',endDate:timestamp(500)});
  await assert.rejects(()=>f.call('add',body),e=>e.status===409);
});
test('legacy authors are resolved as a cached batch, without leaking private fields',async()=>{
  const f=fixture();for(const key of ['a','b'])f.rows.set('campaigns/campaign/comments/'+key,{userId:'customer',status:'ACTIVE'});
  const b={campaignId:'campaign',commentIds:['a','b']},result=await f.call('presentation',b),reads=f.reads();
  assert.equal(reads,4);assert.equal(result.authors.a.name,'Tên hồ sơ');assert.ok(!JSON.stringify(result).includes('PRIVATE'));
  await f.call('presentation',b);assert.equal(f.reads(),reads);
  f.rows.set('campaigns/campaign/comments/hidden',{userId:'customer',status:'HIDDEN'});
  assert.equal((await f.call('presentation',{...b,commentIds:['hidden']})).authors.hidden,undefined);
});
