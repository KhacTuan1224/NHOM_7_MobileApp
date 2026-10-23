const {test, before, after, beforeEach} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const express = require('express');
const admin = require('firebase-admin');
const {createRescueFund, settings, dayKey} = require('./rescue-fund');
const {createSePayApi} = require('./sepay');
const emulator = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^(127\.0\.0\.1|localhost):\d+$/.test(emulator || '')) throw Error('Local demo Firestore Emulator required.');
const rulesRequire = require('node:module').createRequire(require('node:path').resolve(__dirname, '../tests/firestore/package.json'));
const {initializeTestEnvironment, assertFails} = rulesRequire('@firebase/rules-unit-testing');
const {doc, getDoc, setDoc} = rulesRequire('firebase/firestore');
const db = admin.initializeApp({projectId: 'demo-rescuefarm'}, 'fund-tests').firestore();
let clock, base, server, rules, sequence = 0;
const ts = n => admin.firestore.Timestamp.fromMillis(n), get = (c, id) => db.collection(c).doc(id).get();
const secret = 'test-only-fund-webhook-secret-32-characters';
const env = {RESCUE_FUND_BANK_JSON: JSON.stringify({bankCode: 'VCB', accountNumber: '123456789', accountName: 'TEST FUND', webhookSecret: secret}), CLOUDINARY_CLOUD_NAME: 'test-cloud'};
const authenticate = async (req, roles) => {
  const uid = (req.get('authorization') || '').replace(/^Bearer /, ''), s = uid ? await get('users', uid) : null;
  if (!s || !s.exists || s.get('status') !== 'ACTIVE' || !roles.includes(s.get('role'))) throw Object.assign(Error('Forbidden'), {status: 403});
  return {uid, role: s.get('role')};
};
const testAdmin = {firestore: {Timestamp: admin.firestore.Timestamp, FieldPath: admin.firestore.FieldPath,
  FieldValue: {serverTimestamp: () => ts(clock)}}};
const fund = createRescueFund({db, admin: testAdmin, authenticate, env, now: () => clock,
  fetchImpl: async () => new Response(null, {status: 200, headers: {'content-type': 'image/jpeg'}})});
const sharedMerchant = {enabled:true, bankCode:'VCB', accountNumber:'987654321', webhookSecret:secret, authMode:'hmac'};
const sharedEnv = {CLOUDINARY_CLOUD_NAME:'test-cloud', SEPAY_MERCHANTS_JSON:JSON.stringify({seller:sharedMerchant})};
const sharedFund = createRescueFund({db,admin:testAdmin,authenticate,env:sharedEnv,now:()=>clock});
const sharedOrder = createSePayApi({db,admin:testAdmin,env:sharedEnv,now:()=>clock});
const sharedApi = (path, body) => call('/shared/rescue-fund/' + path,body);
async function sharedWebhook(payload, authMode='hmac', invalid=false) {
  const raw=JSON.stringify(payload),timestamp=String(Math.floor(clock/1000));
  const signature='sha256='+crypto.createHmac('sha256',secret).update(timestamp+'.').update(raw).digest('hex');
  const headers={'content-type':'application/json',...(authMode==='apikey'?{authorization:'Apikey '+(invalid?'wrong':secret)}
    :{'x-sepay-timestamp':timestamp,'x-sepay-signature':invalid?'sha256'+'0'.repeat(64):signature})};
  const response=await fetch(base+'/shared/webhooks/sepay/seller',{method:'POST',headers,body:raw});
  return {status:response.status,body:await response.json()};
}
async function seedSharedOrder() {
  const code='SEVQR'+'A'.repeat(20);
  await db.doc('bankTransferReferences/'+code).set({orderId:'shared-order',sellerId:'seller'});
  await db.doc('orders/shared-order').set({id:'shared-order',sellerId:'seller',ownerId:'customer',ownerType:'CUSTOMER',
    status:'PENDING',paymentMethod:'BANK_TRANSFER',paymentStatus:'PENDING',totalAmount:20000,createdAt:ts(clock-10000)});
  await db.doc('payments/shared-order').set({id:'shared-order',orderId:'shared-order',method:'BANK_TRANSFER',status:'PENDING',
    verificationProvider:'SEPAY',transferContent:code,bankCode:'VCB',bankAccountNumber:'987654321',amount:20000});
  return {amount:20000,transferContent:code};
}
const call = async (path, body = {}, user) => {
  const response = await fetch(base + path, {method: 'POST', headers: {'content-type': 'application/json',
    ...(user ? {authorization: 'Bearer ' + user} : {})}, body: JSON.stringify(body)});
  return {status: response.status, body: await response.json()};
};
const api = (path, body, user) => call('/rescue-fund/' + path, body, user);
const date = n => new Date(n + 7 * 3600000).toISOString().slice(0, 19).replace('T', ' ');
const webhook = async payload => {
  const response = await fetch(base + '/webhooks/rescue-fund/sepay', {method: 'POST',
    headers: {'content-type': 'application/json', authorization: 'Apikey ' + secret}, body: JSON.stringify(payload)});
  return {status: response.status, body: await response.json()};
};
const donationInput = () => ({requestId: crypto.randomUUID(), accessToken: crypto.randomBytes(32).toString('hex'), amount: 20000});
const transfer = (donation, extra = {}) => ({id: 123, transferType: 'in', transferAmount: donation.amount,
  accountNumber: '123456789', gateway: 'VCB', content: donation.transferContent, code: '',
  referenceCode: 'BANK-123', transactionDate: date(clock), ...extra});
const proof = (kind, uid, resource) => `https://res.cloudinary.com/test-cloud/image/upload/v123/rescuefarm/${kind}/${uid}/${resource}/image-12345678-1234-1234-1234-123456789abc.jpg`;
const job = async () => (await db.collection('rescueFundDeliveries').get()).docs[0];
before(async () => {
  rules = await initializeTestEnvironment({projectId: 'demo-rescuefarm', firestore: {host: '127.0.0.1', port: Number(emulator.split(':')[1]),
    rules: require('node:fs').readFileSync(require('node:path').resolve(__dirname, '../firestore.rules'), 'utf8')}});
  const app = express(); app.use(express.json({verify:(req,res,bytes)=>{req.rawBody=Buffer.from(bytes);}}));
  app.use(fund.webhookRouter); app.use(fund.router);
  app.use('/shared',sharedFund.webhookRouter);app.use('/shared',sharedOrder.router);app.use('/shared',sharedFund.router);
  app.use((e, req, res, next) => res.status(e.status || 500).json({error: e.message}));
  server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (server) await new Promise(resolve => server.close(resolve)); if (rules) await rules.cleanup(); await db.terminate(); });
beforeEach(async () => {
  clock = Date.parse(dayKey(Date.now()) + 'T00:05:00+07:00') + sequence++ * 60000; await rules.clearFirestore();
  sharedEnv.SEPAY_MERCHANTS_JSON=JSON.stringify({seller:sharedMerchant});delete sharedEnv.RESCUE_FUND_SELLER_ID;
  const seed = {
    'users/admin': {role: 'ADMIN', status: 'ACTIVE'}, 'users/customer': {role: 'CUSTOMER', status: 'ACTIVE'},
    'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'},
    'users/other': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'},
    'rescueFunds/main': {...settings({name: 'Test fund', charityName: 'Test kitchen', address: 'Test address', phone: '',
      latitude: 10.8, longitude: 106.7, idleHours: 12, expiryHours: 24, dailyBudget: 80000, perSellerBudget: 40000, active: true}),
      available: 100000, reserved: 0, spent: 0, donated: 100000},
    'sellerPaymentProfiles/seller': {bankCode: 'VCB', bankAccountNumber: '987654321', bankAccountName: 'TEST SELLER'},
    'products/p': {name: 'Rau', sellerId: 'seller', status: 'ACTIVE', rescuePrice: 10000, unit: 'kg'},
    'productBatches/b': {productId: 'p', status: 'AVAILABLE', availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      expiryDate: ts(clock + 20 * 3600000), inventoryVersion: 0}
  };
  await Promise.all(Object.entries(seed).map(([path, value]) => db.doc(path).set(value)));
});
test('Vietnam midnight boundary is independent of host timezone', () => {
  assert.equal(dayKey(Date.parse('2026-10-22T16:59:59Z')), '2026-10-22');
  assert.equal(dayKey(Date.parse('2026-10-22T17:00:00Z')), '2026-10-23');
});
test('Guest QR replay is idempotent and receipt requires its opaque token', async () => {
  const input = donationInput(), first = await api('donate', input), second = await api('donate', input);
  assert.equal(first.status, 200); assert.equal(second.body.donation.id, first.body.donation.id);
  assert.equal((await api('donate', {...input, amount: 30000})).status, 409);
  assert.equal((await api('receipt', {donationId: first.body.donation.id, accessToken: 'a'.repeat(64)})).status, 403);
  assert.equal((await api('receipt', {donationId: first.body.donation.id, accessToken: input.accessToken})).status, 200);
  assert.equal((await get('rescueFunds', 'main')).get('available'), 100000);
});
test('Exact incoming webhook and replay credit once, conflicting replay is rejected', async () => {
  const d = (await api('donate', donationInput())).body.donation, p = transfer(d);
  const responses = await Promise.all([webhook(p), webhook(p)]);
  assert.deepEqual(responses.map(r => r.status), [200, 200]);
  assert.equal((await get('rescueFunds', 'main')).get('available'), 120000);
  assert.equal((await get('rescueFundDonations', d.id)).get('status'), 'PAID');
  assert.equal((await db.collection('rescueFundLedger').get()).size, 1);
  assert.equal((await webhook({...p, transferAmount: 30000})).status, 409);
});
for (const reason of ['AMOUNT', 'ACCOUNT', 'LATE', 'OUT', 'PROBE']) test('Webhook does not credit unsafe transfer: ' + reason, async () => {
  const d = (await api('donate', donationInput())).body.donation;
  const p = transfer(d, reason === 'AMOUNT' ? {transferAmount: 20001} : reason === 'ACCOUNT' ? {accountNumber: '999999'}
    : reason === 'LATE' ? {transactionDate: date(clock + 3600000)} : reason === 'OUT' ? {transferType: 'out'} : {id: 0});
  assert.equal((await webhook(p)).status, 200);
  assert.equal((await get('rescueFunds', 'main')).get('available'), 100000);
  assert.equal((await get('rescueFundDonations', d.id)).get('status'), 'PENDING');
});
test('Bank reference cannot credit a second donation and unsigned webhook is rejected', async () => {
  const one = (await api('donate', donationInput())).body.donation, two = (await api('donate', donationInput())).body.donation;
  assert.equal((await call('/webhooks/rescue-fund/sepay', transfer(one))).status, 401);
  await webhook(transfer(one)); await webhook(transfer(two, {id: 124}));
  assert.equal((await get('rescueFundDonations', two.id)).get('status'), 'PENDING');
  assert.equal((await get('rescueFunds', 'main')).get('available'), 120000);
});
test('Concurrent allocation reserves money and stock once and respects Seller budget', async () => {
  await Promise.all([fund.allocation('b', dayKey(clock)), fund.allocation('b', dayKey(clock))]);
  const j = await job(), f = await get('rescueFunds', 'main'), b = await get('productBatches', 'b');
  assert.equal(j.get('amount'), 40000); assert.equal(j.get('quantity'), 4);
  assert.equal(f.get('available'), 60000); assert.equal(f.get('reserved'), 40000);
  assert.equal(b.get('availableQuantity'), 6); assert.equal(b.get('reservedQuantity'), 4);
  assert.equal((await db.collection('notifications').get()).size, 1);
});
for (const state of ['EXPIRED', 'TOO_CLOSE', 'RECENT_PAID', 'RECENT_DELIVERED', 'RECENT_CLAIM', 'HIDDEN', 'UNAPPROVED', 'NO_MONEY']) {
  test('Allocation excludes ' + state, async () => {
    if(state==='EXPIRED'||state==='TOO_CLOSE')await db.doc('productBatches/b').update({expiryDate:ts(clock+(state==='EXPIRED'?-1:1)*3600000)});
    if(state==='RECENT_PAID')await db.doc('orders/recent').set({sellerId:'seller',paymentStatus:'PAID',updatedAt:ts(clock)});
    if(state==='RECENT_DELIVERED')await db.doc('orders/recent').set({sellerId:'seller',status:'DELIVERED',updatedAt:ts(clock)});
    if(state==='RECENT_CLAIM')await db.doc('rescueClaims/recent').set({sellerId:'seller',status:'COMPLETED',updatedAt:ts(clock)});
    if(state==='HIDDEN')await db.doc('products/p').update({status:'HIDDEN'});
    if(state==='UNAPPROVED')await db.doc('users/seller').update({sellerStatus:'REJECTED'});
    if(state==='NO_MONEY')await db.doc('rescueFunds/main').update({available:0});
    await fund.allocation('b',dayKey(clock));assert.equal((await db.collection('rescueFundDeliveries').get()).size,0);
  });
}
test('Complete delivery separates verification, stock finalization and real payout; retries never spend twice', async () => {
  await fund.allocation('b',dayKey(clock));const j=await job(), deliveryId=j.id;
  assert.equal((await api('delivery/action',{deliveryId,action:'ACCEPT'},'other')).status,403);
  assert.equal((await api('delivery/action',{deliveryId,action:'ACCEPT'},'seller')).status,200);
  assert.equal((await api('delivery/action',{deliveryId,action:'PAY'},'seller')).status,403);
  assert.equal((await api('delivery/action',{deliveryId,action:'DELIVER',proofUrl:'https://example.com/fake.jpg'},'seller')).status,400);
  assert.equal((await api('delivery/action',{deliveryId,action:'DELIVER',proofUrl:proof('fund-deliveries','seller',deliveryId)},'seller')).status,200);
  clock+=25*3600000; // Late admin review is allowed; handover was submitted before the deadline.
  assert.equal((await api('delivery/action',{deliveryId,action:'VERIFY'},'admin')).status,200);
  assert.equal((await get('productBatches','b')).get('soldQuantity'),4);
  assert.equal((await get('rescueFunds','main')).get('reserved'),40000);
  assert.equal((await get('rescueFunds','main')).get('spent'),0);
  const payout={deliveryId,action:'PAY',amount:40000,bankReference:'PAYOUT-1',proofUrl:proof('fund-receipts','admin',deliveryId)};
  assert.equal((await api('delivery/action',{...payout,amount:30000},'admin')).status,409);
  assert.equal((await api('delivery/action',payout,'admin')).status,200);
  assert.equal((await api('delivery/action',payout,'admin')).status,200);
  assert.equal((await api('delivery/action',{deliveryId,action:'VERIFY'},'admin')).status,200);
  const f=await get('rescueFunds','main');assert.equal(f.get('reserved'),0);assert.equal(f.get('spent'),40000);assert.equal(f.get('available'),60000);
  assert.equal((await get('productBatches','b')).get('soldQuantity'),4);
});
test('Expired undelivered allocation returns money and physical expired stock exactly once',async()=>{
  await fund.allocation('b',dayKey(clock));const j=await job();clock+=25*3600000;
  assert.equal((await api('delivery/action',{deliveryId:j.id,action:'ACCEPT'},'seller')).status,409);
  await fund.reconcile();await fund.action({deliveryId:j.id,action:'CANCEL',note:'Hết hạn'}, {role:'ADMIN',uid:'admin'});
  const b=await get('productBatches','b'),f=await get('rescueFunds','main');
  assert.equal(b.get('status'),'EXPIRED');assert.equal(b.get('availableQuantity'),10);assert.equal(b.get('reservedQuantity'),0);
  assert.equal(f.get('available'),100000);assert.equal(f.get('reserved'),0);
});
test('Campaign reservation counters finalize with the charity delivery',async()=>{
  await db.doc('productBatches/b').update({activeCampaignId:'c'});
  await db.doc('campaigns/c').set({sellerId:'seller',title:'Test campaign',status:'ACTIVE',quantityUnit:'kg',
    startDate:ts(clock-3600000),endDate:ts(clock+18*3600000),batchTargets:{b:3},reservedQuantity:0,rescuedQuantity:0,
    reservedByBatch:{},rescuedByBatch:{},allocationVersion:0});
  await fund.allocation('b',dayKey(clock));const j=await job();assert.equal(j.get('quantity'),3);
  assert.equal((await get('campaigns','c')).get('reservedQuantity'),3);
  await fund.action({deliveryId:j.id,action:'ACCEPT'},{role:'SELLER',uid:'seller'});
  await fund.action({deliveryId:j.id,action:'DELIVER',proofUrl:proof('fund-deliveries','seller',j.id)},{role:'SELLER',uid:'seller'});
  await fund.action({deliveryId:j.id,action:'VERIFY'},{role:'ADMIN',uid:'admin'});
  const c=await get('campaigns','c');assert.equal(c.get('reservedQuantity'),0);assert.equal(c.get('rescuedQuantity'),3);
});
test('Admin adjustment is audited and idempotent; no direct client ledger writes for any role',async()=>{
  const requestId=crypto.randomUUID(), body={requestId,amount:-5000,reason:'Đối soát chênh lệch sao kê',proofUrl:proof('fund-receipts','admin',requestId)};
  assert.equal((await api('admin/adjust',body,'seller')).status,403);
  assert.equal((await api('admin/adjust',body,'admin')).status,200);assert.equal((await api('admin/adjust',body,'admin')).status,200);
  assert.equal((await get('rescueFunds','main')).get('available'),95000);
  assert.equal((await api('admin/adjust',{...body,amount:-10000},'admin')).status,409);
  for(const uid of ['seller','admin','customer']){
    const client=rules.authenticatedContext(uid).firestore();
    for(const c of ['rescueFunds','rescueFundLedger','rescueFundDonations','rescueFundDeliveries']){
      await assertFails(getDoc(doc(client,c,'main')));await assertFails(setDoc(doc(client,c,'forged'),{amount:1}));
    }
  }
  assert.equal((await api('deliveries',{},'customer')).status,403);
});

test('Nightly run resumes after restart without reallocating an already processed day',async()=>{
  const make=()=>createRescueFund({db,admin:testAdmin,authenticate,env,now:()=>clock});
  await make().reconcile();await make().reconcile();
  assert.equal((await db.collection('rescueFundDeliveries').get()).size,1);
  assert.equal((await get('rescueFundRuns',dayKey(clock))).get('complete'),true);
  assert.equal((await get('rescueFunds','main')).get('reserved'),40000);
  assert.equal((await get('productBatches','b')).get('reservedQuantity'),4);
});
test('A newly added batch has not yet accumulated the configured idle window',async()=>{
  await db.doc('productBatches/b').update({createdAt:ts(clock-3600000)});
  await fund.allocation('b',dayKey(clock));assert.equal((await db.collection('rescueFundDeliveries').get()).size,0);
});
test('Late nightly worker does not extend eligibility into the following day',async()=>{
  const midnight=Date.parse(dayKey(clock)+'T00:00:00+07:00');clock=midnight+12*3600000;
  await db.doc('productBatches/b').update({expiryDate:ts(midnight+25*3600000)});
  await fund.allocation('b',dayKey(clock));assert.equal((await db.collection('rescueFundDeliveries').get()).size,0);
});
test('A missing Cloudinary image cannot become delivery evidence or change inventory',async()=>{
  await fund.allocation('b',dayKey(clock));const j=await job();
  await fund.action({deliveryId:j.id,action:'ACCEPT'},{role:'SELLER',uid:'seller'});
  const missing=createRescueFund({db,admin:testAdmin,authenticate,env,now:()=>clock,
    fetchImpl:async()=>new Response(null,{status:404})});
  await assert.rejects(()=>missing.action({deliveryId:j.id,action:'DELIVER',proofUrl:proof('fund-deliveries','seller',j.id)},
    {role:'SELLER',uid:'seller'}),e=>e.status===400);
  assert.equal((await get('rescueFundDeliveries',j.id)).get('status'),'ACCEPTED');
  assert.equal((await get('productBatches','b')).get('reservedQuantity'),4);
});
test('One outgoing bank reference cannot pay two different Sellers',async()=>{
  await db.doc('sellerPaymentProfiles/other').set({bankCode:'VCB',bankAccountNumber:'111111111',bankAccountName:'OTHER SELLER'});
  await db.doc('products/p2').set({name:'Other rau',sellerId:'other',status:'ACTIVE',rescuePrice:10000,unit:'kg'});
  await db.doc('productBatches/b2').set({productId:'p2',status:'AVAILABLE',availableQuantity:10,reservedQuantity:0,soldQuantity:0,expiryDate:ts(clock+20*3600000),inventoryVersion:0});
  await fund.allocation('b',dayKey(clock));await fund.allocation('b2',dayKey(clock));
  const jobs=(await db.collection('rescueFundDeliveries').get()).docs;
  assert.equal(jobs.length,2);
  for(const j of jobs){
    const seller={role:'SELLER',uid:j.get('sellerId')};
    await fund.action({deliveryId:j.id,action:'ACCEPT'},seller);
    await fund.action({deliveryId:j.id,action:'DELIVER',proofUrl:proof('fund-deliveries',seller.uid,j.id)},seller);
    await fund.action({deliveryId:j.id,action:'VERIFY'},{role:'ADMIN',uid:'admin'});
  }
  const pay=j=>({deliveryId:j.id,action:'PAY',amount:j.get('amount'),bankReference:'BANK-OUT-SAME',proofUrl:proof('fund-receipts','admin',j.id)});
  await fund.action(pay(jobs[0]),{role:'ADMIN',uid:'admin'});
  await assert.rejects(()=>fund.action(pay(jobs[1]),{role:'ADMIN',uid:'admin'}),e=>e.status===409);
  assert.equal((await get('rescueFunds','main')).get('spent'),40000);
  assert.equal((await get('rescueFunds','main')).get('reserved'),40000);
});
test('QR rate limit isolates guest devices behind one proxy without granting money',async()=>{
  const device=donationInput();
  for(let n=0;n<30;n++)assert.equal((await api('donate',{...device,requestId:crypto.randomUUID()})).status,200);
  assert.equal((await api('donate',{...device,requestId:crypto.randomUUID()})).status,429);
  assert.equal((await api('donate',donationInput())).status,200);
  assert.equal((await get('rescueFunds','main')).get('available'),100000);
});
test('Countable boxes never become fractional when the fund budget cannot buy another whole box',async()=>{
  await db.doc('products/p').update({unit:'hộp',rescuePrice:15000});
  await fund.allocation('b',dayKey(clock));const j=await job();
  assert.equal(j.get('quantity'),2);assert.equal(j.get('amount'),30000);
  assert.equal((await get('productBatches','b')).get('availableQuantity'),8);
});
test('Fund automatically reuses the single connected Seller bank without extra secrets',async()=>{
  const bank=await sharedFund.resolveBank();assert.equal(bank.accountNumber,'987654321');assert.equal(bank.sharedSellerId,'seller');
  assert.equal(bank.accountName,'TEST SELLER');assert.equal(bank.authMode,'hmac');
  const result=await sharedApi('donate',donationInput());assert.equal(result.status,200);
  assert.equal(result.body.donation.accountNumber,'987654321');assert.match(result.body.donation.qrUrl,/970436-987654321/);
});
for(const mode of ['hmac','apikey'])test('Existing '+mode+' Seller webhook credits fund exactly once',async()=>{
  sharedEnv.SEPAY_MERCHANTS_JSON=JSON.stringify({seller:{...sharedMerchant,authMode:mode}});
  assert.equal((await sharedApi('public',{})).body.fund.available,100000);
  const d=(await sharedApi('donate',donationInput())).body.donation,p=transfer(d,{accountNumber:'987654321'});
  assert.equal((await sharedWebhook(p,mode,true)).status,401);
  assert.equal((await sharedWebhook({...p,id:0},mode)).status,200);
  assert.equal((await get('rescueFunds','main')).get('available'),100000);
  const replies=await Promise.all([sharedWebhook(p,mode),sharedWebhook(p,mode)]);
  assert.deepEqual(replies.map(r=>r.status),[200,200]);
  assert.equal((await get('rescueFunds','main')).get('available'),120000);
  assert.equal((await sharedApi('public',{})).body.fund.available,120000);
  assert.equal((await get('rescueFundDonations',d.id)).get('status'),'PAID');
  assert.equal((await db.collection('orders').get()).size,0);
  assert.equal((await db.collection('sepayBankTransactions').get()).size,1);
});
test('Normal order code still follows existing SePay order confirmation, never the fund',async()=>{
  const order=await seedSharedOrder();const reply=await sharedWebhook(transfer(order,{accountNumber:'987654321'}));
  assert.equal(reply.status,200);assert.equal((await get('orders','shared-order')).get('paymentStatus'),'PAID');
  assert.equal((await get('rescueFunds','main')).get('available'),100000);
  assert.equal((await db.collection('rescueFundWebhookEvents').get()).size,0);
});
for(const first of ['ORDER','FUND'])test('Shared bank transaction cannot credit both streams: '+first+' first',async()=>{
  const order=await seedSharedOrder(),d=(await sharedApi('donate',donationInput())).body.donation;
  const targets=first==='ORDER'?[order,d]:[d,order];
  for(let n=0;n<2;n++)assert.equal((await sharedWebhook(transfer(targets[n],{id:123+n,accountNumber:'987654321'}))).status,200);
  assert.equal((await get('rescueFunds','main')).get('available'),first==='FUND'?120000:100000);
  assert.equal((await get('orders','shared-order')).get('paymentStatus'),first==='ORDER'?'PAID':'PENDING');
});
test('Shared recipient must match current SePay bank; multiple merchants need an explicit Seller UID',async()=>{
  sharedEnv.SEPAY_MERCHANTS_JSON=JSON.stringify({seller:sharedMerchant,other:{...sharedMerchant,accountNumber:'111111111'}});
  await assert.rejects(()=>sharedFund.resolveBank(),e=>e.status===503);
  sharedEnv.RESCUE_FUND_SELLER_ID='seller';assert.equal((await sharedFund.resolveBank()).accountNumber,'987654321');
  await db.doc('sellerPaymentProfiles/seller').update({bankAccountNumber:'222222222'});
  await assert.rejects(()=>sharedFund.resolveBank(),e=>e.status===409);
});
test('Concurrent order and fund callbacks share one atomic bank transaction lock',async()=>{
  const order=await seedSharedOrder(),d=(await sharedApi('donate',donationInput())).body.donation;
  const replies=await Promise.all([sharedWebhook(transfer(order,{id:125,accountNumber:'987654321'})),
    sharedWebhook(transfer(d,{id:126,accountNumber:'987654321'}))]);
  assert.deepEqual(replies.map(r=>r.status),[200,200]);
  const paid=(await get('orders','shared-order')).get('paymentStatus')==='PAID';
  const credited=(await get('rescueFundDonations',d.id)).get('status')==='PAID';
  assert.equal(Number(paid)+Number(credited),1);
  assert.equal((await db.collection('sepayBankTransactions').get()).size,1);
});
