// Pure classification: never connects to Firestore or mutates any data.
function buildPlan(snapshot) {
  const records = snapshot.records;
  const posts = records.filter(r => r.path.split('/').length === 2 && r.path.startsWith('posts/'));
  const postIds = new Set(posts.map(r => r.path.split('/')[1]));
  const removed = new Map();
  const root = r => r.path.split('/')[0];
  const mark = (r, why) => removed.set(r.path, why);
  for (const r of records) {
    const d = r.data, c = root(r);
    if (c === 'posts' || c === 'postViewReceipts') mark(r, 'Retired Post document or view receipt');
    if (['notifications','notificationOutbox','notificationDeliveries','reports','adminAuditLogs'].includes(c)) {
      const typed = ['resourceType','targetType','parentType'].some(k => ['POST','POSTS'].includes(d[k]));
      const direct = ['referenceId','resourceId','targetId','postId'].some(k => postIds.has(d[k]));
      const path = ['targetPath','deepLink','link'].some(k => typeof d[k] === 'string'
        && (d[k].startsWith('posts/') || d[k].startsWith('rescuefarm://posts/')));
      if (typed || direct || path) mark(r, 'Metadata exclusively referencing retired Post content');
    }
  }
  let changed = true;
  while (changed) {
    changed = false;
    const removedIds = new Set([...removed.keys()].map(p => p.split('/').at(-1)));
    for (const r of records) {
      if (removed.has(r.path)) continue;
      if (!['notifications','notificationOutbox','notificationDeliveries'].includes(root(r))) continue;
      if (['inboxId','eventId','notificationId','outboxId','referenceId'].some(k => removedIds.has(r.data[k]))) {
        mark(r, 'Dependent notification/event of retired content'); changed = true;
      }
    }
  }
  const protectedLinks = [];
  function inspect(value, field, path) {
    if (Array.isArray(value)) return value.forEach(v => inspect(v, field, path));
    if (value && typeof value === 'object') return Object.entries(value).forEach(([k,v]) => inspect(v,k,path));
    if (typeof value !== 'string') return;
    if ((/post.?ids?$/i.test(field) && value) || postIds.has(value)
        || ((field === 'targetPath' || field === 'deepLink') && /^(posts\/|rescuefarm:\/\/posts\/)/.test(value)))
      protectedLinks.push({path,field,value});
  }
  for (const r of records) if (!removed.has(r.path)) inspect(r.data,'',r.path);
  const counts = {};
  for (const path of removed.keys()) counts[path.split('/')[0]] = (counts[path.split('/')[0]] || 0) + 1;
  return {project:snapshot.project, snapshotAt:snapshot.createdAt,
    posts:posts.map(r => ({path:r.path,title:r.data.title,status:r.data.status,campaignId:r.data.campaignId || '',
      linkedProductIds:r.data.linkedProductIds || [],content:r.data.content || '',imageUrls:r.data.imageUrls || []})),
    counts, deletePaths:[...removed].map(([path,reason])=>({path,reason})), protectedLinks};
}
module.exports = {buildPlan};
if (require.main === module) {
  const fs=require('node:fs'),path=require('node:path');
  const file=process.argv[2];if(!file)throw Error('Snapshot path required');
  const plan=buildPlan(JSON.parse(fs.readFileSync(file,'utf8')));
  const target=path.join(path.dirname(file),'post-retirement-plan.json');
  fs.writeFileSync(target,JSON.stringify(plan,null,2));
  console.log(JSON.stringify({plan:target,posts:plan.posts.map(p=>({...p,imageUrls:p.imageUrls.length})),
    counts:plan.counts,protectedLinks:plan.protectedLinks},null,2));
}
