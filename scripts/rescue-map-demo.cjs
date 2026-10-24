// Dedicated local UI harness. No production credentials, writes, media or push.
if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8088'
    || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9098') throw Error('Local emulator hosts required');
const admin=require('../functions/node_modules/firebase-admin');
const express=require('../functions/node_modules/express');
admin.initializeApp({projectId:'demo-rescuefarm'});
const db=admin.firestore(),now=Date.now(),ts=n=>admin.firestore.Timestamp.fromMillis(n);
async function main(){
  const uid='map-demo-seller';
  try {await admin.auth().createUser({uid,email:'seller@map.example.test',password:'demo-map-only-password',emailVerified:true});}
  catch(e){if(e.code!=='auth/uid-already-exists')throw e;}
  await db.doc('users/'+uid).set({id:uid,email:'seller@map.example.test',fullName:'SELLER BẢN ĐỒ DEMO',role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED',createdAt:ts(now),updatedAt:ts(now)});
  for(let i=0;i<17;i++){
    const id=i===16?'map-demo-mobile':'map-demo-fixed-'+i;
    await db.doc('campaigns/'+id).set({id,sellerId:uid,title:i===16?'Xe giải cứu DEMO':'Điểm cố định DEMO '+(i+1),description:'Fixture emulator, không bán hàng thật.',
      rescueReason:'OVER_SUPPLY',urgencyLevel:'NORMAL',rescueMode:i===16?'MOBILE_POINT':'FIXED_POINT',
      batchTargets:{'map-demo-batch':10},targetQuantity:10,reservedQuantity:0,rescuedQuantity:0,
      startDate:ts(now-60000),endDate:ts(now+86400000),latitude:10.77+(i%4)*.006,longitude:106.69+Math.floor(i/4)*.006,
      currentLatitude:0,currentLongitude:0,locationUpdatedAt:null,locationSharingEnabled:false,
      locationName:'Địa điểm giả lập '+(i+1),status:'ACTIVE',quantityUnit:'kg',unitStatus:'VERIFIED',
      createdAt:ts(now),updatedAt:ts(now),imageUrls:[],imagePublicIds:[],viewCount:0,
      reservedByBatch:{},rescuedByBatch:{},allocationVersion:1});
  }
  const api=require('../functions/rescue-map').createRescueMapApi({db,idleMs:1000});
  const app=express();app.use(api.router);
  app.listen(3008,'0.0.0.0',()=>console.log('MAP_DEMO_READY project=demo-rescuefarm port=3008 fixed=16 mobile=1'));
}
main().catch(e=>{console.error(e.code||e.message);process.exit(1);});
