import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,runTransaction,Timestamp,serverTimestamp,deleteField} from 'firebase/firestore';
let env;
const past=Timestamp.fromMillis(Date.now()-86400000),future=Timestamp.fromMillis(Date.now()+7*86400000);
const product={id:'p',sellerId:'seller',categoryId:'cat',name:'Bí đỏ hồ lô Đắk Lắk',description:'Nông sản',
  originalPrice:30000,rescuePrice:18000,unit:'kg',origin:'Đắk Lắk',province:'Đắk Lắk',imageUrls:[],
  averageRating:0,reviewCount:0,status:'ACTIVE',createdAt:past,updatedAt:past};
const batch={id:'b',productId:'p',activeCampaignId:'',harvestDate:past,expiryDate:future,
  initialQuantity:80,availableQuantity:80,reservedQuantity:0,soldQuantity:0,inventoryVersion:3,
  status:'AVAILABLE',createdAt:past,updatedAt:past};
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-rescuefarm',firestore:{host:'127.0.0.1',port:8088,
  rules:readFileSync(new URL('../../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{if(env)await env.cleanup();});
async function seed(values){await env.withSecurityRulesDisabled(async c=>{
  for(const [path,value] of Object.entries(values))await setDoc(doc(c.firestore(),path),value);
});}
beforeEach(async()=>{await env.clearFirestore();await seed({
  'users/seller':{role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'},
  'users/other':{role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'},
  'users/pending':{role:'SELLER',status:'ACTIVE',sellerStatus:'PENDING_APPROVAL'},
  'users/blocked':{role:'SELLER',status:'BLOCKED',sellerStatus:'APPROVED'},
  'users/customer':{role:'CUSTOMER',status:'ACTIVE'},'users/admin':{role:'ADMIN',status:'ACTIVE'},
  'categories/cat':{active:true},'products/p':product,'productBatches/b':batch
});});
const seller=()=>env.authenticatedContext('seller').firestore();
for(const status of ['ACTIVE','SOLD_OUT'])test(`Seller inventory creates a batch through the Android read-before-create transaction (${status})`,async()=>{
  await seed({'products/p':{...product,status}});
  const db=seller(),ref=doc(db,'productBatches/new-batch');
  await assertSucceeds(runTransaction(db,async tx=>{
    const p=await tx.get(doc(db,'products/p'));const old=await tx.get(ref);assert.equal(old.exists(),false);
    tx.set(ref,{...batch,id:'new-batch',inventoryVersion:1,activeCampaignId:null,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
    if(p.data().status==='SOLD_OUT')tx.update(doc(db,'products/p'),{status:'ACTIVE',updatedAt:serverTimestamp()});
  }));
  assert.equal((await getDoc(ref)).data().availableQuantity,80);
  assert.equal((await getDoc(doc(db,'products/p'))).data().status,'ACTIVE');
});
test('Seller inventory edits the existing live batch transaction without losing metadata',async()=>{
  const db=seller(),ref=doc(db,'productBatches/b');
  await assertSucceeds(runTransaction(db,async tx=>{
    await tx.get(doc(db,'products/p'));const current=await tx.get(ref);
    tx.update(ref,{initialQuantity:15,availableQuantity:15,inventoryVersion:current.data().inventoryVersion+1,
      harvestDate:past,expiryDate:future,status:'AVAILABLE',updatedAt:serverTimestamp()});
  }));
  const value=(await getDoc(ref)).data();assert.equal(value.inventoryVersion,4);assert.ok(value.createdAt.isEqual(past));
});
for(const status of ['ACTIVE','INACTIVE','SOLD_OUT'])test(`Seller inventory hides and restores own ${status} product with the app payload`,async()=>{
  await seed({'products/p':{...product,status}});const db=seller(),ref=doc(db,'products/p');
  await assertSucceeds(runTransaction(db,async tx=>{const p=await tx.get(ref);
    tx.update(ref,{status:'HIDDEN',sellerHiddenPreviousStatus:p.data().status,updatedAt:serverTimestamp()});
  }));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'products/p')));
  await assertSucceeds(updateDoc(ref,{status,sellerHiddenPreviousStatus:deleteField(),updatedAt:serverTimestamp()}));
  assert.equal((await getDoc(ref)).data().status,status);
});
test('Seller inventory cannot undo Admin moderation or forge Seller-hidden markers',async()=>{
  const db=seller(),ref=doc(db,'products/p');
  await assertFails(updateDoc(ref,{sellerHiddenPreviousStatus:'ACTIVE',updatedAt:serverTimestamp()}));
  await seed({'products/p':{...product,status:'HIDDEN',moderationPreviousStatus:'ACTIVE',moderatedBy:'admin',moderatedAt:past,moderationReason:'Kiểm tra'}});
  await assertFails(updateDoc(ref,{status:'ACTIVE',sellerHiddenPreviousStatus:deleteField(),updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{status:'ACTIVE',moderationPreviousStatus:'',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{status:'HIDDEN',sellerHiddenPreviousStatus:'ACTIVE',updatedAt:serverTimestamp()}));
});
test('Seller inventory rejects nonowners and inactive or unapproved actors for batch creation and hide',async()=>{
  for(const uid of ['other','pending','blocked','customer',null]){
    const db=uid?env.authenticatedContext(uid).firestore():env.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(db,'productBatches/forged'),{...batch,id:'forged',inventoryVersion:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
    await assertFails(updateDoc(doc(db,'products/p'),{status:'HIDDEN',sellerHiddenPreviousStatus:'ACTIVE',updatedAt:serverTimestamp()}));
    if(uid!=='other')await assertFails(getDoc(doc(db,'productBatches/missing')));
  }
});
test('Seller inventory archives server-expired batch without changing expiry evidence',async()=>{
  await seed({'productBatches/b':{...batch,status:'EXPIRED',harvestDate:Timestamp.fromMillis(past.toMillis()-86400000),expiryDate:past,expiredAt:past}});
  const db=seller(),ref=doc(db,'productBatches/b');
  await assertSucceeds(updateDoc(ref,{status:'ARCHIVED',inventoryVersion:4,updatedAt:serverTimestamp()}));
  assert.ok((await getDoc(ref)).data().expiredAt.isEqual(past));
  await assertFails(updateDoc(ref,{expiredAt:future,inventoryVersion:5,updatedAt:serverTimestamp()}));
});
test('Seller inventory edits preserve prior Admin metadata and cannot forge ratings or moderation',async()=>{
  await seed({'products/p':{...product,moderatedBy:'admin',moderatedAt:past,moderationReason:'Đã khôi phục',moderationPreviousStatus:''}});
  const db=seller(),ref=doc(db,'products/p');
  await assertSucceeds(updateDoc(ref,{name:'Bí đỏ mới',updatedAt:serverTimestamp()}));
  assert.equal((await getDoc(ref)).data().moderatedBy,'admin');
  await assertFails(setDoc(ref,{...product,name:'Thay toàn bộ dữ liệu',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{averageRating:5,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{moderationReason:'Giả nội dung Admin',updatedAt:serverTimestamp()}));
});
