const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8088' || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9098') throw Error('Local demo only');
const admin = require(path.resolve(__dirname, '../../../functions/node_modules/firebase-admin'));
admin.initializeApp({projectId:'demo-rescuefarm'});
const db = admin.firestore(), now = Date.now(), ts = n => admin.firestore.Timestamp.fromMillis(n);
const password = crypto.randomBytes(24).toString('base64url');
async function main() {
  for (const role of ['customer','seller','admin']) {
    const uid = 'report-'+role, email = role+'@report.example.test';
    try { await admin.auth().createUser({uid,email,password,emailVerified:true,displayName:'DEMO '+role}); }
    catch(e) { if(e.code!=='auth/uid-already-exists')throw e; await admin.auth().updateUser(uid,{password}); }
    await db.doc('users/'+uid).set({id:uid,email,fullName:'DEMO '+role.toUpperCase(),phone:'',role:role.toUpperCase(),status:'ACTIVE',sellerStatus:'APPROVED',
      customerType:'INDIVIDUAL',shopName:'Nông trại DEMO',storeName:'Nông trại DEMO',createdAt:ts(now-86400000*7),updatedAt:ts(now),latitude:10.8,longitude:106.7});
  }
  await db.doc('sellerPublicProfiles/report-seller').set({sellerId:'report-seller',shopName:'Nông trại DEMO',storeName:'Nông trại DEMO',fullName:'DEMO SELLER',description:'Dữ liệu tổng hợp cho báo cáo',address:'Điểm nhận DEMO',latitude:10.8,longitude:106.7,sellerStatus:'APPROVED'});
  await db.doc('categories/report-vegetables').set({id:'report-vegetables',name:'Rau củ DEMO',description:'Dữ liệu giả lập',active:true,displayOrder:1,imageUrl:''});
  for (const [i,name] of ['Cà chua DEMO','Xoài DEMO','Rau cải DEMO'].entries()) {
    const id='report-product-'+i;
    await db.doc('products/'+id).set({id,sellerId:'report-seller',categoryId:'report-vegetables',name,description:'Sản phẩm giả lập từ seed-demo.cjs, không bán hàng thật.',originalPrice:30000,rescuePrice:18000+i*2000,unit:'kg',origin:'Nông trại DEMO',province:'TP. Hồ Chí Minh',imageUrls:[],averageRating:0,reviewCount:0,status:'ACTIVE',createdAt:ts(now-7*86400000),updatedAt:ts(now-2*86400000)});
    await db.doc('productBatches/report-batch-'+i).set({id:'report-batch-'+i,productId:id,activeCampaignId:'',harvestDate:ts(now-86400000),expiryDate:ts(now+36*3600000),initialQuantity:20,availableQuantity:20,reservedQuantity:0,soldQuantity:0,inventoryVersion:1,status:'AVAILABLE',createdAt:ts(now-3*86400000),updatedAt:ts(now-2*86400000)});
  }
  await db.doc('customerCarts/report-customer').set({customerId:'report-customer',items:[{id:'report-product-0::report-batch-0',productId:'report-product-0',batchId:'report-batch-0',sellerId:'report-seller',quantity:2,selected:true}],updatedAt:ts(now)});
  await db.doc('appConfig/shipping').set({baseFeeVnd:15000,perKmFeeVnd:1000,freeDistanceKm:2});
  fs.mkdirSync(path.resolve(__dirname,'../../../build/report-private'),{recursive:true});
  fs.writeFileSync(path.resolve(__dirname,'../../../build/report-private/auth.json'),JSON.stringify({password}));
  console.log('Seeded 3 isolated demo identities, 3 products/batches, category and customer cart. No credentials logged.');
  await admin.app().delete();
}
main().catch(e=>{console.error(e.code||e.message);process.exit(1)});
