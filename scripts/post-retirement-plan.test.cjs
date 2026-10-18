const {test}=require('node:test');const assert=require('node:assert/strict');
const {buildPlan}=require('./post-retirement-plan.cjs');
const record=(path,data)=>({path,data});
test('removes only retired content and its dependent notifications, preserving commerce and shared image users',()=>{
 const plan=buildPlan({project:'demo',records:[
  record('posts/old',{title:'test'}),record('posts/old/comments/c',{content:'comment'}),
  record('postViewReceipts/r',{postId:'old'}),record('notificationOutbox/e',{resourceType:'POST',resourceId:'old'}),
  record('notifications/n',{resourceType:'POST',referenceId:'old'}),record('notificationDeliveries/d',{eventId:'e',inboxId:'n'}),
  record('campaigns/real',{imageUrls:['https://res.cloudinary.com/x/rescuefarm/posts/shared.jpg']}),
  record('products/real',{name:'Food'}),record('orders/real',{campaignId:'real'}),record('users/seller',{role:'SELLER'}),
  record('notificationOutbox/keep',{resourceType:'CAMPAIGN',resourceId:'real'})]});
 assert.equal(plan.deletePaths.length,6);assert.deepEqual(plan.protectedLinks,[]);
 assert.equal(plan.deletePaths.some(p=>/^(campaigns|products|orders|users)\//.test(p.path)),false);
});
test('detects a protected document still referencing a Post instead of deleting it',()=>{
 const plan=buildPlan({records:[record('posts/old',{}),record('campaigns/real',{linkedPostId:'old'})]});
 assert.equal(plan.protectedLinks.length,1);assert.equal(plan.protectedLinks[0].path,'campaigns/real');
 assert.equal(plan.deletePaths.length,1);
});
test('retired comment reports cascade through report-result notifications without touching other reports',()=>{
 const plan=buildPlan({records:[record('reports/r',{targetType:'COMMENT',parentType:'POST',targetPath:'posts/p/comments/c'}),
  record('notifications/n',{resourceType:'REPORT',referenceId:'r'}),record('notificationDeliveries/d',{inboxId:'n'}),
  record('reports/keep',{targetType:'PRODUCT',targetId:'real'})]});
 assert.deepEqual(plan.deletePaths.map(p=>p.path).sort(),['notificationDeliveries/d','notifications/n','reports/r']);
});
