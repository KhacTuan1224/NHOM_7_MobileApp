const fail=message=>Object.assign(Error(message),{status:409});
function leaseId(publicId) { return typeof publicId==='string' ? publicId.match(/^rescuefarm\/campaigns\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+\/image-[0-5]-([a-f0-9-]{36})$/)?.[1] || '' : ''; }
function publicIdFromUrl(url) {
  try {const path=new URL(url).pathname,index=path.indexOf('/rescuefarm/');return index<0?'':decodeURIComponent(path.slice(index+1)).replace(/\.[a-zA-Z0-9]+$/,'');}catch{return '';}
}
function ownedImage(url,publicId,owner,campaignId,cloudName) {
  try {const parsed=new URL(url);return !!cloudName&&parsed.protocol==='https:'&&parsed.hostname==='res.cloudinary.com'
    &&parsed.pathname.startsWith('/'+cloudName+'/image/upload/')&&publicId.startsWith(`rescuefarm/campaigns/${owner}/${campaignId}/`)
    &&publicIdFromUrl(url)===publicId;}catch{return false;}
}
async function assertApprovalMedia(tx,db,id,campaign,cloudName=process.env.CLOUDINARY_CLOUD_NAME) {
  const urls=campaign.imageUrls||[],ids=campaign.imagePublicIds||[];
  if(!Array.isArray(urls)||!Array.isArray(ids)||urls.length>6)throw fail('Ảnh Campaign không hợp lệ.');
  for(let i=0;i<urls.length;i++) {
    const publicId=ids[i]||publicIdFromUrl(urls[i]);
    if(!ownedImage(urls[i],publicId,campaign.sellerId,id,cloudName))throw fail('Ảnh Campaign cũ chưa xác minh nguồn. Hãy tải lại qua dịch vụ ảnh an toàn trước khi duyệt.');
    const key=leaseId(publicId);if(!key)continue; // Static legacy IDs are frozen by the Worker; no new signatures/deletes are granted.
    const lease=await tx.get(db.collection('campaignMediaUploads').doc(key));
    if(!lease.exists||lease.get('status')!=='COMMITTED'||lease.get('ownerUid')!==campaign.sellerId||lease.get('campaignId')!==id
      ||lease.get('publicId')!==publicId||lease.get('secureUrl')!==urls[i])throw fail('Ảnh chưa được commit hợp lệ với Campaign.');
  }
}
function freezeCampaignAssets(tx,db,campaign) {
  const ids=new Set([...(campaign.imagePublicIds||[]),...(campaign.imageUrls||[]).map(publicIdFromUrl)]);
  for(const publicId of ids) if(typeof publicId==='string'&&/^rescuefarm\/[A-Za-z0-9_/-]+$/.test(publicId))
    tx.set(db.collection('campaignAssetReferences').doc(require('node:crypto').createHash('sha256').update(publicId).digest('hex')),
      {publicId,protected:true});
}
module.exports={leaseId,publicIdFromUrl,ownedImage,assertApprovalMedia,freezeCampaignAssets};
