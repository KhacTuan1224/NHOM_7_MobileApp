const {test}=require('node:test');
const assert=require('node:assert/strict');
const {permitted,fingerprint}=require('./retire-discounts.cjs');
test('retirement permits only the exact retired roots and promotion notifications',()=>{
  for(const p of ['promotions/p','customerPromotionWallets/u','customerPromotionWallets/u/items/p','platformDiscountClaims/order']) assert.equal(permitted(p),true);
  assert.equal(permitted('notifications/n',{type:'PROMOTION'}),true);
  assert.equal(permitted('notificationOutbox/n',{kind:'PROMOTION'}),true);
  for(const p of ['orders/o','orders/o/items/i','payments/p','products/p','productBatches/b','users/u','customerCarts/u','rescueOffers/o','notifications/n','notificationOutbox/n'])
    assert.equal(permitted(p,{type:'ORDER'}),false,p);
  assert.equal(permitted('orders/o/promotions/p',{type:'PROMOTION'}),false);
  assert.equal(permitted('promotions'),false);
  assert.equal(permitted('promotions//nested/x'),false);
});
test('backup fingerprint is order independent and changes with updateTime or paths',()=>{
  const a={path:'promotions/a',updateTime:{$timestamp:[1,1]}};
  const b={path:'promotions/b',updateTime:{$timestamp:[1,2]}};
  assert.equal(fingerprint([a,b]),fingerprint([b,a]));
  assert.notEqual(fingerprint([a]),fingerprint([b]));
  assert.notEqual(fingerprint([a]),fingerprint([{...a,updateTime:{$timestamp:[2,1]}}]));
});
