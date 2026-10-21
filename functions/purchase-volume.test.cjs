const test = require('node:test');
const assert = require('node:assert/strict');
const {applyVolume, allocate} = require('./purchase-volume');
test('same seller uses highest tier on money, including heterogeneous products', () => {
  assert.equal(applyVolume([{sellerId:'s',subtotal:199999}]).groups[0].sellerDiscount,0);
  assert.equal(applyVolume([{sellerId:'s',subtotal:200000}]).groups[0].sellerDiscount,6000);
  assert.equal(applyVolume([{sellerId:'s',subtotal:500000}]).groups[0].sellerDiscount,25000);
  assert.equal(applyVolume([{sellerId:'s',subtotal:1000000}]).platformDiscount,0);
});
test('cross seller qualifies after seller discounts and allocations conserve every VND', () => {
  const v=applyVolume([{sellerId:'a',subtotal:300000},{sellerId:'b',subtotal:300000}]);
  assert.equal(v.eligibleSubtotal,582000);assert.equal(v.platformDiscount,11640);
  assert.deepEqual(v.groups.map(g=>g.netSubtotal),[285180,285180]);
  assert.equal(applyVolume([{sellerId:'a',subtotal:500000},{sellerId:'b',subtotal:500000}]).platformPercent,2);
  assert.equal(applyVolume([{sellerId:'a',subtotal:600000},{sellerId:'b',subtotal:600000}]).platformPercent,4);
  assert.equal(applyVolume([{sellerId:'a',subtotal:1000000},{sellerId:'b',subtotal:0}]).platformPercent,0);
});
test('rounding is deterministic and discounts never exceed a line amount',()=>{
  assert.deepEqual(allocate(2,[1,1,1]),[1,1,0]);
  for(let i=1;i<300;i++) {const a=allocate(i,[i+1,i+2,i+3]);assert.equal(a.reduce((x,y)=>x+y,0),i);}
});
