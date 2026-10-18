// Read-only diagnostic. Does not mark payments paid or print credentials.
const fs=require('node:fs'),path=require('node:path');
const admin=require('../functions/node_modules/firebase-admin');
const root=path.resolve(__dirname,'..');
const credentials=JSON.parse(fs.readFileSync(path.join(root,'serviceAccountKey.json'),'utf8'));
if(credentials.project_id!=='nhom7-rescuefarm')throw Error('Unexpected project');
const code=process.argv[2];if(!/^SEVQR[A-F0-9]{20}$/.test(code||''))throw Error('Provide the exact payment code');
async function main(){
 admin.initializeApp({credential:admin.credential.cert(credentials)});const db=admin.firestore();
 try{
  const payments=await db.collection('payments').where('transferContent','==',code).get();
  const events=await db.collection('sepayWebhookEvents').where('transferContent','==',code).get();
  const count=await db.collection('sepayWebhookEvents').count().get();
  const mapping=await db.collection('bankTransferReferences').doc(code).get();
  const results=[];
  for(const p of payments.docs){const o=await db.collection('orders').doc(p.id).get();
   results.push({orderId:p.id,sellerId:o.get('sellerId'),orderStatus:o.get('status'),orderPaymentStatus:o.get('paymentStatus'),
    total:o.get('totalAmount'),provider:p.get('verificationProvider'),paymentStatus:p.get('status'),verificationSource:p.get('verificationSource')||'',bankCode:p.get('bankCode'),accountEnding:String(p.get('bankAccountNumber')||'').slice(-4),createdAt:o.get('createdAt')?.toDate?.().toISOString()});
  }
  console.log(JSON.stringify({code,payments:results,mapping:mapping.exists?{orderId:mapping.get('orderId'),sellerId:mapping.get('sellerId')}:null,totalWebhookEvents:count.data().count,events:events.docs.map(e=>({id:e.id,status:e.get('status'),reason:e.get('reason')||'',orderId:e.get('orderId')||''}))},null,2));
 }finally{await admin.app().delete();}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
