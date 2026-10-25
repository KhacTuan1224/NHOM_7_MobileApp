const express=require('express');
const {createReadCache}=require('./read-cache');
const {publicIdFromUrl}=require('./campaign-media-policy');
const id=v=>typeof v==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(v);
const fail=(status,message)=>Object.assign(Error(message),{status});
const millis=v=>v&&typeof v.toMillis==='function'?v.toMillis():0;
function imageAllowed(url,uid,commentId,cloud){
  try{const u=new URL(url),asset=publicIdFromUrl(url),prefix=`rescuefarm/campaign-comments/${uid}/${commentId}/`;
    return !!cloud&&u.protocol==='https:'&&u.hostname==='res.cloudinary.com'&&u.pathname.startsWith('/'+cloud+'/image/upload/')
      &&asset.startsWith(prefix)&&/^image-[a-f0-9-]{36}$/.test(asset.slice(prefix.length))&&!u.search&&!u.hash;
  }catch{return false;}
}
function identity(data,anonymous=false){
  if(anonymous)return {name:'Người dùng ẩn danh',avatarUrl:'',anonymous:true};
  const name=typeof data.fullName==='string'?data.fullName.trim().slice(0,120):'';
  const avatar=typeof data.avatarUrl==='string'&&/^https:\/\//i.test(data.avatarUrl)?data.avatarUrl.slice(0,2048):'';
  return {name:name||'Người dùng',avatarUrl:avatar,anonymous:false};
}
function createCampaignCommentsApi({db,admin,authenticate,now=Date.now,cloudName=process.env.CLOUDINARY_CLOUD_NAME}){
  const router=express.Router(),reads=createReadCache({ttlMs:60000,maxEntries:1000,now});
  const read=(collection,key)=>reads.get(collection+'/'+key,()=>db.collection(collection).doc(key).get());
  router.post('/campaigns/comments/add',async(req,res,next)=>{try{
    const caller=await authenticate(req,['CUSTOMER']),b=req.body||{};
    if(!id(b.campaignId)||!id(b.commentId)||typeof b.content!=='string'||b.content.trim().length<1||b.content.trim().length>500)
      throw fail(400,'Nhập bình luận từ 1 đến 500 ký tự.');
    const images=b.imageUrls===undefined?[]:b.imageUrls;
    if(!Array.isArray(images)||images.length>3||new Set(images).size!==images.length||images.some(u=>!imageAllowed(u,caller.uid,b.commentId,cloudName)))
      throw fail(400,'Ảnh bình luận không hợp lệ hoặc không thuộc tài khoản này.');
    if(b.anonymous!==undefined&&typeof b.anonymous!=='boolean')throw fail(400,'Lựa chọn ẩn danh không hợp lệ.');
    const profile=await db.collection('users').doc(caller.uid).get();
    if(!profile.exists||profile.get('role')!=='CUSTOMER'||profile.get('status')!=='ACTIVE')throw fail(403,'Cần tài khoản khách hàng đang hoạt động.');
    const author=identity(profile.data(),b.anonymous===true),ref=db.collection('campaigns').doc(b.campaignId).collection('comments').doc(b.commentId);
    const replay=await db.runTransaction(async tx=>{
      const [campaign,old]=await Promise.all([tx.get(db.collection('campaigns').doc(b.campaignId)),tx.get(ref)]);
      if(old.exists){
        if(old.get('userId')!==caller.uid||old.get('content')!==b.content.trim()||old.get('anonymous')!==author.anonymous
            ||JSON.stringify(old.get('imageUrls')||[])!==JSON.stringify(images))throw fail(409,'Mã bình luận đã được sử dụng.');
        return true;
      }
      if(!campaign.exists||campaign.get('status')!=='ACTIVE'||millis(campaign.get('startDate'))>now()||millis(campaign.get('endDate'))<=now())
        throw fail(409,'Chiến dịch chưa hoạt động hoặc đã kết thúc.');
      tx.set(ref,{id:b.commentId,campaignId:b.campaignId,userId:caller.uid,status:'ACTIVE',content:b.content.trim(),
        senderName:author.name,senderAvatarUrl:author.avatarUrl,anonymous:author.anonymous,imageUrls:images,
        createdAt:admin.firestore.FieldValue.serverTimestamp(),updatedAt:admin.firestore.FieldValue.serverTimestamp()});return false;
    });res.json({ok:true,commentId:b.commentId,replay});
  }catch(e){next(e);}});
  // Older text-only comments have no identity snapshot. Resolve once per batch,
  // with shared caches rather than one users-document read for every rendered row.
  router.post('/campaigns/comments/presentation',async(req,res,next)=>{try{
    const b=req.body||{},ids=b.commentIds;
    if(!id(b.campaignId)||!Array.isArray(ids)||ids.length>100||ids.some(v=>!id(v)))throw fail(400,'Danh sách bình luận không hợp lệ.');
    const campaign=await read('campaigns',b.campaignId);
    if(!campaign.exists||campaign.get('status')!=='ACTIVE')throw fail(404,'Chiến dịch không công khai.');
    const authors=Object.create(null);
    await Promise.all([...new Set(ids)].map(async key=>{
      const comment=await read(`campaigns/${b.campaignId}/comments`,key);
      if(!comment.exists||!['ACTIVE','EDITED'].includes(comment.get('status')))return;
      if(comment.get('anonymous')===true){authors[key]=identity({},true);return;}
      if(typeof comment.get('senderName')==='string'){
        authors[key]={name:comment.get('senderName')||'Người dùng',avatarUrl:comment.get('senderAvatarUrl')||'',anonymous:false};return;
      }
      const uid=comment.get('userId');if(!id(uid)){authors[key]=identity({});return;}
      const user=await read('users',uid);authors[key]=identity(user.exists&&user.get('status')==='ACTIVE'?user.data():{});
    }));res.json({ok:true,authors});
  }catch(e){next(e);}});
  return {router};
}
module.exports={createCampaignCommentsApi,imageAllowed,identity};
