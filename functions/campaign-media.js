const express = require('express');
const crypto = require('node:crypto');
const {campaignUnit} = require('./quantity-units');
const {quantityMap, sum, validateProgress} = require('./campaign-lifecycle');
const {projectCampaign} = require('./campaign-projection');
const {enqueueNotification} = require('./notification-outbox');
const DAY=86400000, YEAR=365*DAY;
const fail=(status,message)=>Object.assign(Error(message),{status});
const text=v=>typeof v==='string'?v.trim():'';
const ms=v=>v&&typeof v.toMillis==='function'?v.toMillis():0;
const id=v=>/^[A-Za-z0-9_-]{1,128}$/.test(text(v));
const {leaseId,publicIdFromUrl,ownedImage,freezeCampaignAssets}=require('./campaign-media-policy');
function equalSecret(actual,expected) {
  const a=Buffer.from(actual||''),b=Buffer.from(expected||'');
  return b.length>=32&&a.length===b.length&&crypto.timingSafeEqual(a,b);
}
function createCampaignMedia({db,admin,authenticate,env=process.env,now=Date.now,destroy}) {
  const router=express.Router(), uploads=db.collection('campaignMediaUploads');
  const stamp=()=>admin.firestore.FieldValue.serverTimestamp(), at=n=>admin.firestore.Timestamp.fromMillis(n);
  const configured=()=> {if(!env.MEDIA_WORKER_SECRET||env.MEDIA_WORKER_SECRET.length<32||!/^https:\/\//.test(env.CLOUDINARY_SIGNING_URL||'')||!env.CLOUDINARY_CLOUD_NAME) throw fail(503,'Chưa cấu hình dịch vụ ảnh Campaign an toàn.');};
  async function editable(tx,uid,campaignId) {
    const [seller,c]=await Promise.all([tx.get(db.collection('users').doc(uid)),tx.get(db.collection('campaigns').doc(campaignId))]);
    if(!seller.exists||seller.get('role')!=='SELLER'||seller.get('status')!=='ACTIVE'||seller.get('sellerStatus')!=='APPROVED') throw fail(403,'Người bán phải đang hoạt động và đã được duyệt.');
    if(c.exists&&(c.get('sellerId')!==uid||!['DRAFT','REJECTED'].includes(c.get('status')))) throw fail(403,'Chỉ được thay ảnh/nội dung Campaign nháp hoặc bị từ chối của chính bạn.');
    return c;
  }
  async function authorize(caller,body) {
    configured(); const campaignId=text(body.resourceId),slot=body.slot;
    if(!id(campaignId)||!Number.isInteger(slot)||slot<0||slot>=6) throw fail(400,'Mỗi Campaign có các vị trí ảnh từ 0 đến 5.');
    const key=crypto.randomUUID(),ref=uploads.doc(key),draftRef=db.collection('campaignMediaDrafts').doc(campaignId);
    const publicId=`rescuefarm/campaigns/${caller.uid}/${campaignId}/image-${slot}-${key}`;
    await db.runTransaction(async tx=> {
      await editable(tx,caller.uid,campaignId); const draft=await tx.get(draftRef);
      if(draft.exists&&draft.get('ownerUid')!==caller.uid) throw fail(403,'Bản nháp thuộc người bán khác.');
      const requests=draft.exists&&ms(draft.get('expiresAt'))>now()?(draft.get('requests')||0):0;
      if(requests>=30) throw fail(429,'Đã đạt giới hạn tải ảnh cho bản nháp trong ngày.');
      tx.set(draftRef,{ownerUid:caller.uid,campaignId,requests:requests+1,expiresAt:at(now()+DAY),updatedAt:stamp()});
      tx.create(ref,{ownerUid:caller.uid,campaignId,slot,publicId,status:'AUTHORIZED',createdAt:stamp(),expiresAt:at(now()+DAY),retryCount:0});
    });
    return {leaseId:key,publicId,campaignId,slot};
  }
  async function uploaded(body) {
    const key=text(body.leaseId),url=text(body.secureUrl); if(!id(key)||!url.startsWith('https://res.cloudinary.com/')) throw fail(400,'Ảnh tải lên không hợp lệ.');
    return db.runTransaction(async tx=> {
      const ref=uploads.doc(key),lease=await tx.get(ref); if(!lease.exists) throw fail(404,'Không tìm thấy phiên tải ảnh.');
      if(lease.get('publicId')!==body.publicId||!ownedImage(url,body.publicId,lease.get('ownerUid'),lease.get('campaignId'),env.CLOUDINARY_CLOUD_NAME)) throw fail(400,'Ảnh không khớp phiên tải.');
      await editable(tx,lease.get('ownerUid'),lease.get('campaignId'));
      if(lease.get('status')==='UPLOADED'&&lease.get('secureUrl')===url) return {ok:true};
      if(lease.get('status')!=='AUTHORIZED'||ms(lease.get('expiresAt'))<=now()) throw fail(409,'Phiên tải đã hết hạn hoặc không còn khả dụng.');
      tx.update(ref,{secureUrl:url,status:'UPLOADED',uploadedAt:stamp(),expiresAt:at(now()+DAY)}); return {ok:true};
    });
  }
  async function save(caller,body) {
    const campaignId=text(body.id),submit=body.submit===true;
    if(!id(campaignId)||text(body.title).length<3||text(body.title).length>120||text(body.description).length>3000||text(body.locationName).length>250) throw fail(400,'Tiêu đề/mô tả Campaign không hợp lệ.');
    const targets=quantityMap(body.batchTargets,true),keys=Object.keys(targets),total=sum(targets);
    if(!keys.length||keys.length>8||total<=0||total>1e9||!Number.isFinite(body.targetQuantity)||Math.abs(total-body.targetQuantity)>.000001) throw fail(400,'Phân bổ/mục tiêu Campaign không hợp lệ.');
    const reasons=['OVER_SUPPLY','NEAR_EXPIRY','SEASONAL_HARVEST','WEATHER_IMPACT','ORDER_CANCELLED','LOGISTICS_DISRUPTION','DEMAND_DROP','COSMETIC_GRADE'];
    if(!reasons.includes(body.rescueReason)||!['FIXED_POINT','MOBILE_POINT'].includes(body.rescueMode)
        ||!Number.isFinite(body.startDate)||!Number.isFinite(body.endDate)||body.startDate>=body.endDate
        ||(submit&&body.endDate<=now())||![body.latitude,body.longitude].every(Number.isFinite)||Math.abs(body.latitude)>90||Math.abs(body.longitude)>180) throw fail(400,'Thời gian, loại hoặc vị trí Campaign không hợp lệ.');
    const urls=body.imageUrls||[],ids=body.imagePublicIds||[];
    if(!Array.isArray(urls)||!Array.isArray(ids)||urls.length>6||urls.length!==ids.length||new Set(urls).size!==urls.length||new Set(ids).size!==ids.length
        ||urls.some(u=>typeof u!=='string'||u.length>2000||!u.startsWith('https://'))) throw fail(400,'Danh sách ảnh Campaign không hợp lệ.');
    await db.runTransaction(async tx=> {
      const previous=await editable(tx,caller.uid,campaignId),old=previous.exists?previous.data():{};
      const draft=await tx.get(db.collection('campaignMediaDrafts').doc(campaignId));
      if(draft.exists&&draft.get('ownerUid')!==caller.uid)throw fail(403,'Bản nháp thuộc người bán khác.');
      const batches=await Promise.all(keys.map(k=>tx.get(db.collection('productBatches').doc(k))));
      const products=[];
      for(let i=0;i<batches.length;i++) {
        const batch=batches[i]; if(!batch.exists) throw fail(409,'Lô hàng không tồn tại.');
        const product=await tx.get(db.collection('products').doc(batch.get('productId')));
        if(!product.exists||product.get('sellerId')!==caller.uid||product.get('status')!=='ACTIVE') throw fail(409,'Lô hàng phải thuộc sản phẩm đang hoạt động của người bán.');
        products.push(product.data());
        if(submit) {
          if(!['AVAILABLE','RESERVED_PARTIAL'].includes(batch.get('status'))||ms(batch.get('expiryDate'))<=now()||batch.get('availableQuantity')+.000001<targets[keys[i]]) throw fail(409,'Lô hàng hết hạn hoặc thiếu lượng mục tiêu.');
          const linked=text(batch.get('activeCampaignId'));
          if(linked&&linked!==campaignId) {const other=await tx.get(db.collection('campaigns').doc(linked));if(other.exists&&other.get('status')==='ACTIVE'&&ms(other.get('endDate'))>now()) throw fail(409,'Lô đang tham gia chiến dịch khác.');}
        }
      }
      const unit=campaignUnit(products),newLeases=[],removed=[];
      for(let i=0;i<urls.length;i++) {
        if(ownedImage(urls[i],ids[i],caller.uid,campaignId,env.CLOUDINARY_CLOUD_NAME)&&(old.imageUrls||[]).some((u,j)=>u===urls[i]&&((old.imagePublicIds||[])[j]===ids[i]||(!(old.imagePublicIds||[])[j]&&publicIdFromUrl(u)===ids[i])))) continue;
        const key=leaseId(ids[i]); if(!key) throw fail(409,'Ảnh mới phải được tải qua dịch vụ ảnh Campaign.');
        const lease=await tx.get(uploads.doc(key));
        if(!lease.exists||lease.get('ownerUid')!==caller.uid||lease.get('campaignId')!==campaignId||lease.get('publicId')!==ids[i]
            ||lease.get('secureUrl')!==urls[i]||lease.get('status')!=='UPLOADED'||ms(lease.get('expiresAt'))<=now()) throw fail(409,'Ảnh chưa tải xong, đã hết hạn hoặc không thuộc Campaign.');
        newLeases.push(lease);
      }
      for(const publicId of old.imagePublicIds||[]) if(!ids.includes(publicId)&&leaseId(publicId)) {
        const lease=await tx.get(uploads.doc(leaseId(publicId))); if(lease.exists) removed.push(lease);
      }
      const mutationAt=stamp();
      const campaign={...old,id:campaignId,sellerId:caller.uid,title:text(body.title),description:text(body.description),rescueReason:body.rescueReason,
        rescueMode:body.rescueMode,batchTargets:targets,targetQuantity:total,reservedQuantity:old.reservedQuantity||0,rescuedQuantity:old.rescuedQuantity||0,
        reservedByBatch:old.reservedByBatch||{},rescuedByBatch:old.rescuedByBatch||{},allocationVersion:old.allocationVersion||0,
        quantityUnit:unit,unitStatus:'DECLARED',startDate:at(body.startDate),endDate:at(body.endDate),latitude:body.latitude,longitude:body.longitude,
        currentLatitude:0,currentLongitude:0,locationUpdatedAt:null,locationSharingEnabled:false,locationName:text(body.locationName),
        imageUrls:urls,imagePublicIds:ids,status:submit?'PENDING_APPROVAL':'DRAFT',createdAt:old.createdAt||mutationAt,updatedAt:mutationAt,viewCount:old.viewCount||0};
      campaign.urgencyLevel=projectCampaign(campaign,batches.map(d=>({...d.data(),id:d.id})),now()).urgencyLevel;
      validateProgress(campaign,campaign.reservedByBatch,campaign.rescuedByBatch);
      tx.set(db.collection('campaigns').doc(campaignId),campaign);
      if(submit)freezeCampaignAssets(tx,db,campaign);
      for(const lease of newLeases) tx.update(lease.ref,{status:'COMMITTED',committedAt:stamp(),expiresAt:at(now()+10*YEAR)});
      for(const lease of removed) tx.update(lease.ref,{status:'DETACHED',expiresAt:at(now()+DAY)});
      if(submit) enqueueNotification(tx,db,admin,crypto.randomUUID(),{kind:'ADMINS',resourceType:'CAMPAIGN',resourceId:campaignId,actorId:caller.uid,sourceUpdatedAt:mutationAt});
    });
    return {ok:true,campaignId};
  }
  const destroyAsset=destroy|| (async publicId=> {
    configured(); const response=await fetch(env.CLOUDINARY_SIGNING_URL.replace(/\/$/,'')+'/internal/delete-campaign',
      {method:'POST',headers:{'Content-Type':'application/json','X-Media-Worker-Secret':env.MEDIA_WORKER_SECRET},body:JSON.stringify({publicId}),signal:AbortSignal.timeout(30000)});
    if(!response.ok) throw Error('Worker cleanup HTTP '+response.status);
  });
  let running=false;
  async function reconcile() {
    if(running) return {busy:true}; running=true; let deleted=0,errors=0;
    try {
      const due=await uploads.where('expiresAt','<=',at(now())).limit(25).get();
      for(const candidate of due.docs) {
        try {
          const claimed=await db.runTransaction(async tx=> {
            const lease=await tx.get(candidate.ref); if(!lease.exists||ms(lease.get('expiresAt'))>now()) return null;
            const campaign=await tx.get(db.collection('campaigns').doc(lease.get('campaignId')));
            if(campaign.exists&&((campaign.get('imagePublicIds')||[]).includes(lease.get('publicId'))||(campaign.get('imageUrls')||[]).includes(lease.get('secureUrl')))) {
              tx.update(lease.ref,{status:'COMMITTED',expiresAt:at(now()+10*YEAR)});return null;
            }
            if(!['AUTHORIZED','UPLOADED','DETACHED','DELETING'].includes(lease.get('status'))) return null;
            tx.update(lease.ref,{status:'DELETING',retryCount:(lease.get('retryCount')||0)+1,expiresAt:at(now()+15*60000)}); return lease.get('publicId');
          });
          if(!claimed) continue;
          await destroyAsset(claimed);
          await db.runTransaction(async tx=>{const lease=await tx.get(candidate.ref);if(lease.get('status')==='DELETING')tx.update(candidate.ref,{status:'DELETED',deletedAt:stamp(),lastError:'',expiresAt:at(now()+10*YEAR)});}); deleted++;
        } catch(error) {
          errors++;console.error('Campaign media cleanup:',candidate.id,error.message);
          await db.runTransaction(async tx=>{const lease=await tx.get(candidate.ref);if(lease.exists&&lease.get('status')==='DELETING')tx.update(candidate.ref,{lastError:String(error.message).slice(0,500)});}).catch(()=>{});
        }
      }
      return {deleted,errors};
    } finally {running=false;}
  }
  async function migrateReferences() {
    const marker=db.collection('systemMigrations').doc('campaignAssetReferencesV1');
    if((await marker.get()).exists)return;
    let cursor=null;
    while(true) {
      let query=db.collection('campaigns').orderBy(admin.firestore.FieldPath.documentId()).limit(200);
      if(cursor)query=query.startAfter(cursor);const page=await query.get();
      for(const doc of page.docs) await db.runTransaction(async tx=>{
        const c=await tx.get(doc.ref);if(c.exists&&!['DRAFT','REJECTED'].includes(c.get('status')))freezeCampaignAssets(tx,db,c.data());
      });
      if(page.size<200)break;cursor=page.docs[page.docs.length-1];
    }
    await db.runTransaction(async tx=>{await tx.get(marker);tx.set(marker,{completedAt:stamp()});});
  }
  async function assetGuard(caller,body) {
    const publicId=text(body.publicId);
    const owned=['users','sellers','products','posts','reviews'].some(type=>publicId.startsWith(`rescuefarm/${type}/${caller.uid}/`));
    const adminAsset=caller.role==='ADMIN'&&/^rescuefarm\/(categories|banners)\/[A-Za-z0-9_-]+\/image(?:-[a-f0-9-]{36})?$/.test(publicId);
    if(!/^rescuefarm\/[A-Za-z0-9_/-]+$/.test(publicId)||(!owned&&!adminAsset))throw fail(403,'Asset ownership required');
    const reference=await db.collection('campaignAssetReferences').doc(crypto.createHash('sha256').update(publicId).digest('hex')).get();
    return {protected:reference.exists&&reference.get('protected')===true};
  }
  router.post('/media/asset-guard',async(req,res,next)=>{try{
    if(!equalSecret(req.get('X-Media-Worker-Secret'),env.MEDIA_WORKER_SECRET))throw fail(403,'Worker authentication required');
    res.json(await assetGuard(await authenticate(req,['CUSTOMER','SELLER','ADMIN']),req.body||{}));
  }catch(e){next(e);}});
  router.post('/media/campaign/authorize',async(req,res,next)=>{try{res.json(await authorize(await authenticate(req,['SELLER']),req.body||{}));}catch(e){next(e);}});
  router.post('/media/campaign/uploaded',async(req,res,next)=>{try{if(!equalSecret(req.get('X-Media-Worker-Secret'),env.MEDIA_WORKER_SECRET))throw fail(403,'Worker authentication required');res.json(await uploaded(req.body||{}));}catch(e){next(e);}});
  router.post('/campaigns/save',async(req,res,next)=>{try{res.json(await save(await authenticate(req,['SELLER']),req.body||{}));}catch(e){next(e);}});
  return {router,authorize,uploaded,save,reconcile,migrateReferences,assetGuard};
}
module.exports={createCampaignMedia,leaseId,equalSecret};
