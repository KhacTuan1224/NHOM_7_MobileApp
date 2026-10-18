const {test}=require('node:test');
const assert=require('node:assert/strict');
const {buildPlan}=require('./mock-cleanup-plan.cjs');
function fixture(){
  const seed={categories:{cat:{name:'Rau',imageUrl:'https://images.unsplash.com/seed'}},products:{sample:{name:'Seed product',imageUrls:['https://images.unsplash.com/product']}},posts:{},banners:{}};
  const values={
    'categories/cat':{...seed.categories.cat,active:true,displayOrder:1},
    'products/sample':{...seed.products.sample,categoryId:'cat'},
    'products/real':{name:'Actual upload',imageUrls:['https://outside.example/real.jpg'],categoryId:'cat'},
    'productBatches/mock-batch':{productId:'sample'},
    'productBatches/real-batch':{productId:'real'},
    'campaigns/random-user-id':{batchTargets:{'mock-batch':20}},
    'orders/mock-order':{batchReservations:{'mock-batch':1},fulfillmentType:'PICKUP'},
    'orders/mock-order/items/item':{productId:'sample',batchId:'mock-batch'},
    'orders/real-order':{batchReservations:{'real-batch':1},fulfillmentType:'PICKUP'},
    'orders/real-order/items/item':{productId:'real',batchId:'real-batch'},
    'payments/mock-order':{orderId:'mock-order'},'payments/real-order':{orderId:'real-order'},
    'customerCarts/customer':{items:[{productId:'sample',batchId:'mock-batch'},{productId:'real',batchId:'real-batch'}]},
    'users/customer':{fullName:'Protected'},
    'notificationOutbox/event':{resourceType:'ORDER',resourceId:'mock-order'},
    'notifications/inbox':{eventId:'event'},
    'campaigns/random-user-id/comments/comment':{content:'Dependent comment'}
  };
  return {seed,records:Object.entries(values).map(([path,data])=>({path,data}))};
}
test('exact seed matching, cascade and preservation of real orders/users',()=>{
  const f=fixture(),p=buildPlan(f.records,f.seed),removed=p.actions.filter(a=>a.op==='delete').map(a=>a.path);
  assert(removed.includes('products/sample'));
  assert(removed.includes('campaigns/random-user-id'));
  assert(removed.includes('campaigns/random-user-id/comments/comment'));
  assert(removed.includes('payments/mock-order'));
  assert(removed.includes('notifications/inbox'));
  assert(!p.actions.some(a=>a.path.startsWith('users/')));
  assert(!p.actions.some(a=>a.path.startsWith('orders/real-order')));
  assert(!p.actions.some(a=>a.path==='payments/real-order'));
  assert.equal(p.keptOrders.length,1);
});
test('real cart lines survive and real products receive replacement category',()=>{
  const f=fixture(),p=buildPlan(f.records,f.seed);
  assert.equal(p.actions.find(a=>a.path==='customerCarts/customer').fields.items.length,1);
  const update=p.actions.find(a=>a.path==='products/real');
  const category=p.actions.find(a=>a.path==='categories/'+update.fields.categoryId);
  assert.equal(category.op,'create');assert.equal(category.data.name,'Rau');assert.equal(category.data.imageUrl,'');
  assert.deepEqual(Object.keys(update.fields).sort(),['categoryId','updatedAt']);
});
test('external image URLs alone do not classify real products as mock',()=>{
  const f=fixture();f.records.find(r=>r.path==='products/real').data.imageUrls=['https://images.unsplash.com/not-seeded'];
  assert(!buildPlan(f.records,f.seed).actions.some(a=>a.path==='products/real'&&a.op==='delete'));
});
test('mixed real/mock orders block deletion until stock can be reconciled',()=>{
  const f=fixture();f.records.push({path:'orders/mock-order/items/real',data:{productId:'real',batchId:'real-batch'}});
  assert.throws(()=>buildPlan(f.records,f.seed),/Mixed real\/mock order/);
});
test('mixed campaigns block deletion of shared allocation',()=>{
  const f=fixture();f.records.find(r=>r.path==='campaigns/random-user-id').data.batchTargets['real-batch']=2;
  assert.throws(()=>buildPlan(f.records,f.seed),/Mixed real\/mock campaign/);
});
test('modified seed-ID products are retained and flagged',()=>{
  const f=fixture();f.records.find(r=>r.path==='products/sample').data.name='Owner replaced this product';
  const p=buildPlan(f.records,f.seed);assert(p.warnings.some(w=>w.includes('products/sample')));
  assert(!p.actions.some(a=>a.path==='products/sample'&&a.op==='delete'));
});
