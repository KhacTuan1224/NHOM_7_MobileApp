import {test} from 'node:test';import assert from 'node:assert/strict';
import worker,{resolveAsset,imageMime,readCampaignImage,uploadCampaign} from '../src/index.js';
const png=new Uint8Array([137,80,78,71,13,10,26,10,0,0,0,0]);
const image=(bytes=png,mime='image/png')=>new Request('https://worker/upload-campaign?resourceId=campaign&slot=0',{method:'POST',headers:{'Content-Type':mime},body:bytes});

test('generated payment QR uses immutable owner-scoped IDs, including sellers preparing their profile', async () => {
  const version='12345678-1234-1234-1234-123456789abc';
  const asset=await resolveAsset({assetType:'paymentQr',resourceId:'seller',slot:0,version},'seller',
    {role:'SELLER',sellerStatus:'PENDING_APPROVAL'});
  assert.equal(asset.publicId,`rescuefarm/sellers/seller/payment-qr-${version}`);
  assert.equal(asset.immutable,true);
  assert.equal(await resolveAsset({assetType:'paymentQr',resourceId:'other',slot:0,version},'seller',{role:'SELLER'}),null);
  assert.equal(await resolveAsset({assetType:'paymentQr',resourceId:'seller',slot:0,version:'invalid'},'seller',{role:'SELLER'}),null);
});
test('signed routes cannot produce Campaign overwrite or ordinary delete permissions',async()=>{
  const profile={role:'SELLER',sellerStatus:'APPROVED'};
  assert.equal(await resolveAsset({assetType:'campaign',resourceId:'campaign',slot:0},'seller',profile),null);
  assert.equal(await resolveAsset({assetType:'delete',publicId:'rescuefarm/campaigns/seller/campaign/image-0'},'seller',profile),null);
});
test('Worker enforces actual bytes, type and size without trusting client MIME or file length',async()=>{
  assert.equal(imageMime(png),'image/png');assert.equal((await readCampaignImage(image())).mime,'image/png');
  await assert.rejects(()=>readCampaignImage(image(png,'image/jpeg')));
  await assert.rejects(()=>readCampaignImage(image(new TextEncoder().encode('<svg/>'),'image/png')));
  await assert.rejects(()=>readCampaignImage(image(new Uint8Array(10*1024*1024+1))));
});
test('Campaign proxy alone signs immutable IDs and does not expose an upload signature',async()=>{
  const original=globalThis.fetch,calls=[];
  const publicId='rescuefarm/campaigns/seller/campaign/image-0-12345678-1234-1234-1234-123456789abc';
  globalThis.fetch=async(url,options)=>{
    calls.push(String(url));
    if(String(url).endsWith('/authorize'))return Response.json({leaseId:'lease',publicId});
    if(String(url).includes('api.cloudinary.com')) {
      assert.equal(options.body.get('overwrite'),'false');assert.equal(options.body.get('public_id'),publicId);
      assert.equal(options.body.get('file').size,png.length);return Response.json({public_id:publicId,secure_url:'https://res.cloudinary.com/c/image/upload/v1/'+publicId+'.png'});
    }
    assert.equal(options.headers['X-Media-Worker-Secret'],'s'.repeat(32));return Response.json({ok:true});
  };
  try {
    const req=image();const result=await uploadCampaign(req,new URL(req.url),{uid:'seller',token:'token'},
      {MEDIA_BACKEND_URL:'https://backend',MEDIA_WORKER_SECRET:'s'.repeat(32),CLOUDINARY_CLOUD_NAME:'c',CLOUDINARY_API_KEY:'key',CLOUDINARY_API_SECRET:'secret'});
    assert.equal(result.public_id,publicId);assert.equal(result.signature,undefined);assert.equal(calls.length,3);
  } finally {globalThis.fetch=original;}
});
test('source permission denial prevents any Cloudinary upload',async()=>{
  const original=globalThis.fetch;let calls=0;globalThis.fetch=async()=>{calls++;return Response.json({error:'Campaign is ACTIVE'},{status:403});};
  try{const req=image();await assert.rejects(()=>uploadCampaign(req,new URL(req.url),{uid:'seller',token:'token'},
    {MEDIA_BACKEND_URL:'https://backend',MEDIA_WORKER_SECRET:'s'.repeat(32)}));assert.equal(calls,1);}finally{globalThis.fetch=original;}
});
test('internal cleanup is denied without the shared secret; legacy/static IDs are not accepted',async()=>{
  const request=body=>new Request('https://worker/internal/delete-campaign',{method:'POST',headers:{'Content-Type':'application/json','X-Media-Worker-Secret':'s'.repeat(32)},body:JSON.stringify(body)});
  assert.equal((await worker.fetch(request({publicId:'rescuefarm/campaigns/u/c/image-0'}),{MEDIA_WORKER_SECRET:'s'.repeat(32)})).status,400);
  assert.equal((await worker.fetch(request({publicId:'anything'}),{MEDIA_WORKER_SECRET:'x'.repeat(32)})).status,403);
});

test('authenticated legacy shared-asset signer versions protected IDs and refuses their deletion',async()=>{
  const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
  const jwk=await crypto.subtle.exportKey('jwk',pair.publicKey);jwk.kid='worker-test-key';
  const encode=obj=>Buffer.from(JSON.stringify(obj)).toString('base64url');const seconds=Math.floor(Date.now()/1000);
  const input=encode({alg:'RS256',kid:jwk.kid})+'.'+encode({sub:'seller',aud:'test',iss:'https://securetoken.google.com/test',iat:seconds,exp:seconds+3600});
  const signature=Buffer.from(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(input))).toString('base64url');
  const headers={Authorization:'Bearer '+input+'.'+signature,'Content-Type':'application/json'};
  const env={FIREBASE_PROJECT_ID:'test',MEDIA_BACKEND_URL:'https://backend',MEDIA_WORKER_SECRET:'s'.repeat(32),CLOUDINARY_API_KEY:'key',CLOUDINARY_API_SECRET:'secret'};
  const original=globalThis.fetch;let destroyCalls=0;
  globalThis.fetch=async url=>{
    if(String(url).includes('/jwk/'))return Response.json({keys:[jwk]});
    if(String(url).includes('firestore.googleapis.com'))return Response.json({fields:{role:{stringValue:'SELLER'},status:{stringValue:'ACTIVE'},sellerStatus:{stringValue:'APPROVED'}}});
    if(String(url).endsWith('/media/asset-guard'))return Response.json({protected:true});
    destroyCalls++;throw Error('Protected asset must never reach Cloudinary destroy');
  };
  try {
    const signed=await worker.fetch(new Request('https://worker/sign-upload',{method:'POST',headers,body:JSON.stringify({assetType:'product',resourceId:'product',slot:0})}),env);
    assert.equal(signed.status,200);const payload=await signed.json();assert.equal(payload.overwrite,'false');
    assert.match(payload.public_id,/^rescuefarm\/products\/seller\/product\/image-0-[a-f0-9-]{36}$/);
    const removed=await worker.fetch(new Request('https://worker/delete',{method:'POST',headers,body:JSON.stringify({assetType:'delete',publicId:'rescuefarm/products/seller/product/image-0'})}),env);
    assert.equal(removed.status,403);assert.equal(destroyCalls,0);
  }finally{globalThis.fetch=original;}
});
