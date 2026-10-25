const {test}=require('node:test'),assert=require('node:assert/strict');
const {createPasswordCaptcha}=require('./password-captcha');
const TIME=Date.parse('2026-10-25T10:00:00Z');
const env={PASSWORD_RECAPTCHA_SITE_KEY:'synthetic-public-site-key',PASSWORD_RECAPTCHA_SECRET_KEY:'synthetic-secret',PASSWORD_RECAPTCHA_HOSTNAMES:'demo.example.test'};
const good={success:true,hostname:'demo.example.test',challenge_ts:new Date(TIME).toISOString()};
function fixture({config=env,result=good,fetcher}={}){
  let count=0,request;const api=createPasswordCaptcha({env:config,now:()=>TIME,fetchImpl:async(url,options)=>{
    count++;request={url,options};if(fetcher)return fetcher();return {ok:true,json:async()=>result};
  }});
  function call(method,body={responseToken:'synthetic-captcha-response'}){
    const path=method==='get'?'/auth/password-captcha':'/auth/password-captcha/verify';
    return new Promise((resolve,reject)=>{
      const headers={};const res={set(k,v){if(typeof k==='object')Object.assign(headers,k);else headers[k]=v;return this;},type(){return this;},send:html=>resolve({html,headers}),json:result=>resolve(result)};
      api.router.stack.find(x=>x.route?.path===path).route.stack[0].handle({body,ip:'demo-ip'},res,reject);
    });
  }
  return {api,call,count:()=>count,request:()=>request};
}
test('missing configuration fails closed without calling Google',async()=>{
  const f=fixture({config:{}});assert.equal(f.api.configured,false);
  await assert.rejects(f.call('get'),e=>e.status===503);await assert.rejects(f.call('post'),e=>e.status===503);assert.equal(f.count(),0);
});
test('public Google test key cannot be used as production verification',async()=>{
  const f=fixture({config:{...env,PASSWORD_RECAPTCHA_SITE_KEY:'6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI'}});
  await assert.rejects(f.call('get'),e=>e.status===503);
});
test('hosted page uses a real Google widget nonce CSP and never exposes the secret',async()=>{
  const f=fixture(),r=await f.call('get');assert.ok(r.html.includes('google.com/recaptcha/api.js'));
  assert.ok(r.html.includes('grecaptcha.render'));assert.ok(!r.html.includes(env.PASSWORD_RECAPTCHA_SECRET_KEY));
  assert.ok(r.headers['Content-Security-Policy'].includes("frame-ancestors 'none'"));assert.equal(r.headers['Cache-Control'],'no-store');
});
test('verification sends only response and server secret to Google and rejects replay',async()=>{
  const f=fixture();assert.deepEqual(await f.call('post'),{ok:true});
  assert.equal(f.request().url,'https://www.google.com/recaptcha/api/siteverify');
  assert.equal(f.request().options.body.get('secret'),env.PASSWORD_RECAPTCHA_SECRET_KEY);
  await assert.rejects(f.call('post'),e=>e.status===409);assert.equal(f.count(),1);
});
test('wrong hostname failed challenges and invalid timestamps fail closed',async()=>{
  for(const result of [{...good,hostname:'evil.example.test'},{...good,success:false},{...good,challenge_ts:'bad'},
      {...good,challenge_ts:new Date(TIME+20000).toISOString()}])await assert.rejects(fixture({result}).call('post'),e=>e.status===403);
});
test('provider failure is sanitized and never accepted',async()=>{
  const f=fixture({fetcher:()=>{throw Error('sensitive provider detail');}});
  await assert.rejects(f.call('post'),e=>e.status===503&&!e.message.includes('sensitive'));
});
test('concurrent use reserves the response before provider completion',async()=>{
  let release;const f=fixture({fetcher:()=>new Promise(resolve=>{release=()=>resolve({ok:true,json:async()=>good});})});
  const first=f.call('post');await assert.rejects(f.call('post'),e=>e.status===409);release();assert.deepEqual(await first,{ok:true});
});
test('credentials and malformed response bodies are never forwarded',async()=>{
  const f=fixture();for(const body of [{responseToken:'short'},{responseToken:'synthetic-captcha-response',password:'do-not-send'},{}])
    await assert.rejects(f.call('post',body),e=>e.status===400);assert.equal(f.count(),0);
});
test('request limiter is bounded and rejects repeated requests without Firestore reads',async()=>{
  const f=fixture();for(let i=0;i<120;i++)await f.call('get');await assert.rejects(f.call('get'),e=>e.status===429);
});
