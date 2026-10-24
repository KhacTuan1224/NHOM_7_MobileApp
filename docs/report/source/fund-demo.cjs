// Uses the real fund module, a local database, injected time and a HEAD-only image stub.
// No bank transfer, upload, push, or external HTTP request is performed.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8088')throw Error('Local emulator only');
const requireApp=require('node:module').createRequire(path.resolve(__dirname,'../../../functions/package.json'));
const admin=requireApp('firebase-admin'),express=requireApp('express');
const {createRescueFund,dayKey}=requireApp('./rescue-fund');
admin.initializeApp({projectId:'demo-rescuefarm'});const db=admin.firestore();
const statePath=path.resolve(__dirname,'../../../build/report-private/fund.json');
let state=fs.existsSync(statePath)?JSON.parse(fs.readFileSync(statePath,'utf8')):{clock:Date.parse('2026-10-24T14:00:00Z')};
const ts=n=>admin.firestore.Timestamp.fromMillis(n);
const testAdmin={firestore:{Timestamp:admin.firestore.Timestamp,FieldPath:admin.firestore.FieldPath,FieldValue:{serverTimestamp:()=>ts(state.clock)}}};
const secret=crypto.randomBytes(32).toString('hex');
const env={CLOUDINARY_CLOUD_NAME:'report-demo-no-upload',RESCUE_FUND_BANK_JSON:JSON.stringify({bankCode:'VCB',accountNumber:'999990000001',accountName:'DEMO KHONG CHUYEN TIEN',webhookSecret:secret})};
const authenticate=async(req,roles)=>{const role=req.get('x-demo-role')||'ADMIN';assert(roles.includes(role));return {uid:'report-'+role.toLowerCase(),role}};
const fund=createRescueFund({db,admin:testAdmin,authenticate,env,now:()=>state.clock,fetchImpl:async()=>new Response(null,{status:200,headers:{'content-type':'image/png'}})});
const read=async()=>{
 const f=(await db.doc('rescueFunds/main').get()).data();
 const jobs=await db.collection('rescueFundDeliveries').get();
 const ledger=await db.collection('rescueFundLedger').get();
 return {clock:new Date(state.clock).toISOString(),fund:{available:f.available,reserved:f.reserved,spent:f.spent,donated:f.donated},
 jobs:jobs.docs.map(d=>({id:d.id,status:d.get('status'),amount:d.get('amount'),allocatedAt:d.get('allocatedAt').toDate().toISOString(),deadline:d.get('deadline').toDate().toISOString(),allocationPolicyVersion:d.get('allocationPolicyVersion')})),ledger:ledger.docs.map(d=>({id:d.id,kind:d.get('kind'),amount:d.get('amount')})),imageProvider:'HEAD image existence stub; no upload and no real delivery/payout'};
};
async function main(){
 const phase=process.argv[2];
 if(phase==='setup'){
   assert(!(await db.doc('rescueFunds/main').get()).exists,'Never overwrite an existing fund');
   await db.doc('sellerPaymentProfiles/report-seller').set({bankCode:'VCB',bankAccountName:'DEMO KHONG CHUYEN TIEN',bankAccountNumber:'999990000002'});
   for(let i=0;i<2;i++){
    await db.doc('products/report-fund-product-'+i).set({name:'Rau thiện nguyện DEMO '+i,sellerId:'report-seller',status:'ACTIVE',rescuePrice:18000,unit:'kg',createdAt:ts(state.clock-7*86400000)});
    await db.doc('productBatches/report-fund-batch-'+i).set({productId:'report-fund-product-'+i,initialQuantity:2,availableQuantity:2,reservedQuantity:0,soldQuantity:0,status:'AVAILABLE',expiryDate:ts(state.clock+36*3600000),createdAt:ts(state.clock-3*86400000),inventoryVersion:0});
   }
   const app=express();app.use(express.json());app.use(fund.router);app.use(fund.webhookRouter);app.use((e,req,res,next)=>res.status(e.status||500).json({error:e.message}));
   const server=await new Promise(r=>{const s=app.listen(0,'127.0.0.1',()=>r(s))});const base='http://127.0.0.1:'+server.address().port;
   const post=async(p,b,headers={})=>{const r=await fetch(base+p,{method:'POST',headers:{'content-type':'application/json',...headers},body:JSON.stringify(b)});const body=await r.json();assert.equal(r.status,200,JSON.stringify(body));return body};
   await post('/rescue-fund/admin/save',{name:'Hũ báo cáo DEMO',charityName:'Bếp thiện nguyện DEMO',address:'Điểm bàn giao tổng hợp cho kiểm thử',phone:'',idleHours:12,expiryHours:48,dailyBudget:72000,perSellerBudget:72000,active:true});
   const d=(await post('/rescue-fund/donate',{requestId:crypto.randomUUID(),accessToken:crypto.randomBytes(32).toString('hex'),amount:72000,shareName:true,supporterName:'NHÓM DEMO'})).donation;
   const payload={id:2026102401,transferType:'in',transferAmount:72000,accountNumber:'999990000001',gateway:'VCB',content:d.transferContent,referenceCode:'DEMO-IN-ONLY',transactionDate:new Date(state.clock+7*3600000).toISOString().slice(0,19).replace('T',' ')};
   await post('/webhooks/rescue-fund/sepay',payload,{authorization:'Apikey '+secret});
   await post('/webhooks/rescue-fund/sepay',payload,{authorization:'Apikey '+secret});
   await new Promise(r=>server.close(r));
   for(let i=0;i<2;i++)await fund.allocation('report-fund-batch-'+i,dayKey(state.clock));
   const jobs=(await db.collection('rescueFundDeliveries').get()).docs;
   state.job=jobs.find(d=>d.get('batchId')==='report-fund-batch-0').id;
   state.expiringJob=jobs.find(d=>d.get('batchId')==='report-fund-batch-1').id;
   assert.equal((await read()).fund.reserved,72000);
 }else if(phase==='accept'){
   await fund.action({deliveryId:state.job,action:'ACCEPT'},{uid:'report-seller',role:'SELLER'});
 }else if(phase==='deliver'){
   state.clock+=24*3600000-1000;
   await fund.action({deliveryId:state.job,action:'DELIVER',proofUrl:`https://res.cloudinary.com/report-demo-no-upload/image/upload/v1/rescuefarm/fund-deliveries/report-seller/${state.job}/image-${crypto.randomUUID()}.png`,note:'DEMO API harness; HEAD ảnh được stub, chưa upload thật'},{uid:'report-seller',role:'SELLER'});
 }else if(phase==='expire-verify'){
   state.clock+=2000;await fund.reconcile();
   assert.equal((await db.doc('rescueFundDeliveries/'+state.expiringJob).get()).get('status'),'CANCELLED');
   await fund.action({deliveryId:state.job,action:'VERIFY'},{uid:'report-admin',role:'ADMIN'});
 }else if(phase==='pay'){
   const body={deliveryId:state.job,action:'PAY',amount:36000,bankReference:'DEMO-NO-BANK-TRANSFER',proofUrl:`https://res.cloudinary.com/report-demo-no-upload/image/upload/v1/rescuefarm/fund-receipts/report-admin/${state.job}/image-${crypto.randomUUID()}.png`};
   await fund.action(body,{uid:'report-admin',role:'ADMIN'});await fund.action(body,{uid:'report-admin',role:'ADMIN'});
 }
 fs.writeFileSync(statePath,JSON.stringify(state));const evidence=await read();assert.equal(evidence.fund.available+evidence.fund.reserved+evidence.fund.spent,evidence.fund.donated);
 fs.writeFileSync(path.resolve(__dirname,'../evidence/fund-demo-'+phase+'.json'),JSON.stringify(evidence,null,2));
 console.log(phase+': conserved balances and real module transitions; local demo only');await admin.app().delete();
}
main().catch(e=>{console.error(e.message);process.exit(1)});
