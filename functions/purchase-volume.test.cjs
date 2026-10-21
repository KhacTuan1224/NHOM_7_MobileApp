const test = require('node:test');
const assert = require('node:assert/strict');
const {applyVolume, allocate} = require('./purchase-volume');
test('same seller uses the new inclusive 50k and 120k thresholds', () => {
  assert.equal(applyVolume([{sellerId:'s',subtotal:49999}]).groups[0].sellerDiscount,0);
  assert.equal(applyVolume([{sellerId:'s',subtotal:50000}]).groups[0].sellerDiscount,1500);
  assert.equal(applyVolume([{sellerId:'s',subtotal:119999}]).groups[0].sellerPercent,3);
  assert.equal(applyVolume([{sellerId:'s',subtotal:120000}]).groups[0].sellerDiscount,6000);
  assert.equal(applyVolume([{sellerId:'s',subtotal:200000}]).policy.version,2);
  assert.equal(applyVolume([{sellerId:'s',subtotal:1000000}]).platformDiscount,0);
});
test('cross seller uses the new inclusive 80k and 180k thresholds after seller discounts', () => {
  const v=applyVolume([{sellerId:'a',subtotal:60000},{sellerId:'b',subtotal:60000}]);
  assert.equal(v.eligibleSubtotal,116400);assert.equal(v.platformDiscount,2328);
  assert.deepEqual(v.groups.map(g=>g.netSubtotal),[57036,57036]);
  assert.equal(applyVolume([{sellerId:'a',subtotal:40000},{sellerId:'b',subtotal:39999}]).platformPercent,0);
  assert.equal(applyVolume([{sellerId:'a',subtotal:40000},{sellerId:'b',subtotal:40000}]).platformPercent,2);
  const amounts=[45000,45000,45000,44999];
  assert.equal(applyVolume(amounts.map((subtotal,i)=>({sellerId:String(i),subtotal}))).platformPercent,2);
  assert.equal(applyVolume([45000,45000,45000,45000].map((subtotal,i)=>({sellerId:String(i),subtotal}))).platformPercent,4);
  // 180k before seller savings is only 174600 eligible; it must not jump to 4%.
  assert.equal(applyVolume([{sellerId:'a',subtotal:90000},{sellerId:'b',subtotal:90000}]).platformPercent,2);
  assert.equal(applyVolume([{sellerId:'a',subtotal:1000000},{sellerId:'b',subtotal:0}]).platformPercent,0);
});
test('rounding is deterministic and discounts never exceed a line amount',()=>{
  assert.deepEqual(allocate(2,[1,1,1]),[1,1,0]);
  for(let i=1;i<300;i++) {const a=allocate(i,[i+1,i+2,i+3]);assert.equal(a.reduce((x,y)=>x+y,0),i);}
});
