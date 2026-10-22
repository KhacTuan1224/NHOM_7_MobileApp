// Pure cleanup planner. Identification comes from seed source, never URL host alone.
const crypto = require('node:crypto');
const stamp = {$type:'serverTimestamp'};
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function buildPlan(records, seed) {
  const byPath=new Map(records.map(r=>[r.path,r])), remove=new Map(), patches=new Map(), creates=new Map();
  const warnings=[];
  const root=(c)=>records.filter(r=>r.path.startsWith(c+'/')&&r.path.split('/').length===2);
  const ids=(c)=>new Set([...remove.keys()].filter(p=>p.startsWith(c+'/')&&p.split('/').length===2).map(p=>p.split('/')[1]));
  function mark(p,reason){if(p.startsWith('users/'))throw Error('Users are protected');if(byPath.has(p))remove.set(p,reason);}
  function matchSeed(c,keys){for(const [id,expected]of Object.entries(seed[c]||{})){
    const r=byPath.get(c+'/'+id);if(!r)continue;
    if(keys.every(k=>equal(r.data[k],expected[k])))mark(r.path,'Exact seed ID and source fields: '+keys.join(', '));
    else warnings.push('Modified seed candidate retained: '+r.path);
  }}
  matchSeed('categories',['name','imageUrl']);
  matchSeed('products',['name','imageUrls']);
  const products=ids('products');
  for(const r of root('productBatches'))if(products.has(r.data.productId))mark(r.path,'Batch of verified seed product');
  const batches=ids('productBatches');
  for(const r of root('campaigns')) {
    const linked=Object.keys(r.data.batchTargets||{}), matching=linked.filter(id=>batches.has(id));
    if(matching.length&&matching.length!==linked.length)throw Error('Mixed real/mock campaign requires manual allocation review: '+r.path);
    if(matching.length)mark(r.path,'All target batches belong to verified seed products');
  }
  const campaigns=ids('campaigns');
  matchSeed('banners',['title','imageUrl']);
  matchSeed('posts',['title','imageUrls']);
  const posts=ids('posts');
  for(const r of root('orders')) {
    const items=records.filter(i=>i.path.startsWith(r.path+'/items/'));
    const linked=Object.keys(r.data.batchReservations||{});
    const affected=items.some(i=>products.has(i.data.productId)||batches.has(i.data.batchId))||linked.some(id=>batches.has(id))||campaigns.has(r.data.campaignId);
    if(!affected)continue;
    if(items.some(i=>!products.has(i.data.productId)||!batches.has(i.data.batchId))||linked.some(id=>!batches.has(id)))
      throw Error('Mixed real/mock order requires stock reconciliation: '+r.path);
    if(!items.length)throw Error('Cannot classify order without item snapshots: '+r.path);
    mark(r.path,'Order exclusively contains verified seed products/batches');
  }
  const orders=ids('orders');
  for(const c of ['payments','shipments','checkoutReceipts'])for(const r of root(c))
    if(orders.has(r.path.split('/')[1])||orders.has(r.data.orderId))mark(r.path,'Companion of deleted mock order');
  for(const c of ['guestOrderCodes','guestOrderGrants','bankTransferReferences'])for(const r of root(c))
    if(orders.has(r.data.orderId))mark(r.path,'Lookup/access mapping for deleted mock order');
  for(const r of root('favorites'))if(products.has(r.data.productId))mark(r.path,'Favorite of deleted mock product');
  for(const r of root('reviews'))if(products.has(r.data.productId)||orders.has(r.data.orderId))mark(r.path,'Review of deleted mock product/order');
  for(const r of root('campaignViewReceipts'))if(campaigns.has(r.data.campaignId))mark(r.path,'Receipt for deleted mock campaign');
  for(const r of root('postViewReceipts'))if(posts.has(r.data.postId))mark(r.path,'Receipt for deleted mock post');
  const typeSets={ORDER:orders,PRODUCT:products,CAMPAIGN:campaigns,POST:posts,CATEGORY:ids('categories'),BANNER:ids('banners')};
  const linkedResource=(d)=>typeSets[d.resourceType||d.type]?.has(d.resourceId||d.referenceId);
  for(const c of ['notificationOutbox','notificationDeliveries','notifications'])for(const r of root(c))
    if(linkedResource(r.data))mark(r.path,'Notification referring to deleted mock resource');
  const events=ids('notificationOutbox');
  for(const c of ['notificationDeliveries','notifications'])for(const r of root(c))
    if(events.has(r.data.eventId))mark(r.path,'Delivery/inbox for deleted mock event');
  // Children are enumerated explicitly: Firestore parent deletion is not recursive.
  for(const r of records)for(const p of [...remove.keys()])if(r.path.startsWith(p+'/')){mark(r.path,'Descendant of deleted mock resource');break;}
  for(const r of root('customerCarts')){
    const items=r.data.items||[], remaining=items.filter(i=>!products.has(i.productId)&&!batches.has(i.batchId));
    if(items.length!==remaining.length)patches.set(r.path,{reason:'Remove mock cart lines; retain real cart lines',fields:{items:remaining,updatedAt:stamp}});
  }
  // User requested replacement categories, preserving real products and their orders.
  const categories=ids('categories');
  for(const r of root('products').filter(r=>!remove.has(r.path)&&categories.has(r.data.categoryId))){
    const previous=byPath.get('categories/'+r.data.categoryId).data;
    const newId='category-'+crypto.createHash('sha256').update('live-catalog/'+r.data.categoryId).digest('hex').slice(0,20);
    const newPath='categories/'+newId;
    if(byPath.has(newPath))throw Error('Replacement category already exists; audit required: '+newPath);
    creates.set(newPath,{reason:'Real replacement taxonomy for existing user-created products',data:{id:newId,name:previous.name,description:'',imageUrl:'',imagePublicId:'',active:previous.active===true,displayOrder:previous.displayOrder,createdAt:stamp,updatedAt:stamp}});
    patches.set(r.path,{reason:'Move real product from seed category to replacement',fields:{categoryId:newId,updatedAt:stamp}});
  }
  const surviving=new Map(records.filter(r=>!remove.has(r.path)).map(r=>[r.path,r.data]));
  for(const[p,c]of creates)surviving.set(p,c.data);
  for(const[p,u]of patches)surviving.set(p,{...surviving.get(p),...u.fields});
  const problems=[];
  function requireRef(p,source){if(p&&!surviving.has(p))problems.push(source+' -> missing '+p);}
  for(const[p,d]of surviving){const[c,id]=p.split('/');if(p.split('/').length!==2)continue;
    if(c==='products')requireRef('categories/'+d.categoryId,p);
    if(c==='productBatches'){requireRef('products/'+d.productId,p);if(d.activeCampaignId)requireRef('campaigns/'+d.activeCampaignId,p);}
    if(c==='campaigns')for(const bid of Object.keys(d.batchTargets||{}))requireRef('productBatches/'+bid,p);
    if(c==='orders'){
      requireRef('payments/'+id,p);if(d.fulfillmentType==='DELIVERY')requireRef('shipments/'+id,p);
      if(d.campaignId)requireRef('campaigns/'+d.campaignId,p);
      for(const bid of Object.keys(d.batchReservations||{}))requireRef('productBatches/'+bid,p);
      for(const item of records.filter(r=>r.path.startsWith(p+'/items/'))){requireRef('products/'+item.data.productId,item.path);requireRef('productBatches/'+item.data.batchId,item.path);}
    }
    if(c==='posts'){if(d.campaignId)requireRef('campaigns/'+d.campaignId,p);for(const pid of d.linkedProductIds||[])requireRef('products/'+pid,p);}
    if(c==='banners'&&d.campaignId)requireRef('campaigns/'+d.campaignId,p);
  }
  if(problems.length)throw Error('Unsafe dangling references: '+problems.join('; '));
  const actions=[...remove].map(([path,reason])=>({op:'delete',path,reason}))
    .concat([...patches].map(([path,p])=>({op:'update',path,...p})),[...creates].map(([path,p])=>({op:'create',path,...p})));
  if(actions.some(a=>a.path.startsWith('users/')))throw Error('Protected users mutation');
  const summary={};for(const a of actions){const key=a.op+' '+a.path.split('/')[0];summary[key]=(summary[key]||0)+1;}
  return {actions,summary,warnings,keptOrders:root('orders').filter(r=>!remove.has(r.path)).map(r=>r.path),deletedOrders:[...orders],integrityProblems:problems};
}
module.exports={buildPlan};
